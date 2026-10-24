package top.bearcabbage.modpackupdater;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.jar.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import top.bearcabbage.modpackupdater.core.*;

public final class ModpackUpdaterClient implements ClientModInitializer {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MirrorTree updater"); thread.setDaemon(true); return thread;
    });
    static final Path ROOT = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    static volatile String status = "准备检查更新";
    static volatile UpdateClient.Plan plan;
    static volatile boolean busy;
    static volatile boolean restartAfterDownload;
    private static volatile boolean restartPrepared;
    private static volatile boolean startupPromptPending;
    private static volatile String restartNotice;
    static UpdateClient updater;
    static ModpackUpdaterConfig config;
    private static boolean helperStarted;

    @Override public void onInitializeClient() {
        try {
            config = ModpackUpdaterConfig.load();
            if (config.serverUrl == null || config.serverUrl.isBlank()
                    || config.trustedPublicKey == null || config.trustedPublicKey.isBlank()) {
                status = "请在 config/modpackupdater.json 设置更新服务器和签名公钥";
            } else updater = new UpdateClient(ROOT, config.serverUrl, config.trustedPublicKey);
            if (pending()) status = Files.exists(ROOT.resolve(UpdateApplier.STATE + "/transaction.properties"))
                    ? "上次更新中断，退出游戏后将先恢复备份" : "更新已暂存，退出游戏后即可应用";
        } catch (Exception e) { fail(e); }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            WORKER.execute(() -> {
                try {
                    if (RestartLauncher.ready(ROOT)) {
                        Path applied = UpdateApplier.confined(ROOT.toRealPath(), UpdateApplier.STATE + "/applied.properties");
                        String release = Files.exists(applied) ? UpdateApplier.readProperties(applied).getProperty("release", "新版整合包") : "新版整合包";
                        restartNotice = "更新完成，游戏已自动重启：" + release + "。个人额外内容已保留。";
                        status = restartNotice; startupPromptPending = true;
                    }
                } catch (Exception e) { fail(e); }
                if (updater != null) check(true);
            });
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (pending() && !helperStarted) {
                try { launchHelper(restartPrepared); } catch (Exception e) { fail(e); }
            }
            if (!pending()) try { RestartLauncher.discard(ROOT); } catch (IOException e) { fail(e); }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                var button = Button.builder(Component.literal(label()), b -> client.setScreen(new UpdateScreen(screen)))
                        .bounds(width - 132, 8, 124, 20).build();
                Screens.getWidgets(screen).add(button);
                ScreenEvents.afterTick(screen).register(s -> {
                    button.setMessage(Component.literal(label()));
                    if (startupPromptPending && client.screen == screen && client.level == null && !busy) {
                        startupPromptPending = false;
                        client.setScreen(new UpdateScreen(screen));
                    }
                });
            }
        });
    }

    static String label() {
        if (pending()) return "整合包更新 · 待应用";
        if (busy) return "整合包更新 · 处理中";
        if (plan != null && plan.changeCount() > 0) return "整合包更新 · " + plan.changeCount() + " 项";
        return "整合包更新";
    }
    static boolean pending() {
        return Files.exists(ROOT.resolve(UpdateApplier.STATE + "/pending.properties"))
                || Files.exists(ROOT.resolve(UpdateApplier.STATE + "/transaction.properties"));
    }
    static void fail(Exception e) {
        status = "更新未完成：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        ModpackUpdater.LOGGER.error("Modpack update failed", e);
    }
    static void check() { check(false); }
    private static synchronized void check(boolean startup) {
        if (updater == null || busy || !startup && pending()) return;
        busy = true; status = "正在后台检查更新…";
        WORKER.execute(() -> {
            try {
                if (pending()) {
                    updater.fetch();
                    status = "服务器检查完成；已有暂存更新，可应用并自动重启，或取消暂存后重新检查。";
                } else {
                    plan = updater.check();
                    status = plan.changeCount() == 0 ? restartNotice == null ? "已经是最新版本" : restartNotice
                            : "发现 " + plan.changeCount() + " 项更新 · " + plan.manifest().release();
                }
                if (startup && (pending() || plan != null && plan.changeCount() > 0)) startupPromptPending = true;
            } catch (Exception e) {
                plan = null; fail(e);
                if (restartNotice != null) status = restartNotice + " 暂时无法检查后续版本，可稍后重试。";
            }
            finally { busy = false; }
        });
    }
    static synchronized void download() {
        if (updater == null || busy || pending() || plan == null || plan.changeCount() == 0) return;
        busy = true;
        restartAfterDownload = true;
        UpdateClient.Plan selected = plan;
        WORKER.execute(() -> {
            try {
                RestartLauncher.prepare(ROOT, FabricLoader.getInstance().getLaunchArguments(false));
                restartPrepared = true;
                updater.stage(selected, text -> status = text);
                for (int seconds = 3; seconds > 0; seconds--) {
                    status = "校验完成，" + seconds + " 秒后关闭游戏。更新窗口将显示备份和应用进度，完成后自动重启。";
                    Thread.sleep(1000);
                }
                busy = false;
                Minecraft.getInstance().execute(ModpackUpdaterClient::applyAndRestart);
            } catch (Exception e) {
                restartAfterDownload = false; restartPrepared = false;
                try { RestartLauncher.discard(ROOT); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
                fail(e); busy = false;
            }
        });
    }
    static void discard() {
        if (busy) return;
        try {
            if (Files.exists(ROOT.resolve(UpdateApplier.STATE + "/transaction.properties")))
                throw new IOException("上次更新中断，请先退出并恢复更新");
            Files.deleteIfExists(ROOT.resolve(UpdateApplier.STATE + "/pending.properties"));
            RestartLauncher.discard(ROOT);
            restartPrepared = false; restartAfterDownload = false;
            plan = null; status = "已取消暂存更新，可重新检查";
        } catch (Exception e) { fail(e); }
    }
    static void applyAndRestart() {
        if (!pending() || busy || Minecraft.getInstance().level != null) return;
        if (!restartPrepared) {
            busy = true; restartAfterDownload = true;
            WORKER.execute(() -> {
                try {
                    RestartLauncher.prepare(ROOT, FabricLoader.getInstance().getLaunchArguments(false));
                    restartPrepared = true; busy = false;
                    Minecraft.getInstance().execute(ModpackUpdaterClient::applyAndRestart);
                } catch (Exception e) { restartAfterDownload = false; busy = false; fail(e); }
            });
            return;
        }
        try {
            launchHelper(true);
            Minecraft.getInstance().stop();
        } catch (Exception e) { restartAfterDownload = false; fail(e); }
    }
    private static synchronized void launchHelper(boolean restart) throws IOException {
            if (helperStarted) return;
            Path state = UpdateApplier.confined(ROOT.toRealPath(), UpdateApplier.STATE);
            Files.createDirectories(state);
            Path helper = state.resolve("apply-helper.jar");
            var manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, UpdateApplier.class.getName());
            try (var output = new JarOutputStream(Files.newOutputStream(helper), manifest)) {
                for (String name : new String[] {"UpdateApplier", "UpdateApplier$Change", "RestartLauncher", "UpdateProgress"}) {
                    String resource = "top/bearcabbage/modpackupdater/core/" + name + ".class";
                    try (var in = UpdateApplier.class.getClassLoader().getResourceAsStream(resource)) {
                        if (in == null) throw new IOException("缺少更新应用器");
                        output.putNextEntry(new JarEntry(resource)); in.transferTo(output); output.closeEntry();
                    }
                }
            }
            boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
            Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java");
            var command = new java.util.ArrayList<>(java.util.List.of(javaExecutable.toString(), "-jar", helper.toString(),
                    ROOT.toRealPath().toString(), Long.toString(ProcessHandle.current().pid())));
            if (restart) command.add("restart");
            new ProcessBuilder(command)
                    .redirectErrorStream(true).redirectOutput(state.resolve("apply.log").toFile()).start();
            helperStarted = true;
    }
}
