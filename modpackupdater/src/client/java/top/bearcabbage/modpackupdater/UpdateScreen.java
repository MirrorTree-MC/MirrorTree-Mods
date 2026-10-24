package top.bearcabbage.modpackupdater;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class UpdateScreen extends Screen {
    private final Screen parent;
    private Button check, download, apply, discard, back;
    UpdateScreen(Screen parent) { super(Component.literal("整合包更新")); this.parent = parent; }
    @Override protected void init() {
        int y = height - 74;
        check = addRenderableWidget(Button.builder(Component.literal("检查更新"), b -> ModpackUpdaterClient.check())
                .bounds(width / 2 - 154, y, 100, 20).build());
        download = addRenderableWidget(Button.builder(Component.literal("更新并重启"), b -> ModpackUpdaterClient.download())
                .bounds(width / 2 - 50, y, 100, 20).build());
        apply = addRenderableWidget(Button.builder(Component.literal("应用并重启"), b -> ModpackUpdaterClient.applyAndRestart())
                .bounds(width / 2 + 54, y, 100, 20).build());
        discard = addRenderableWidget(Button.builder(Component.literal("取消暂存"), b -> ModpackUpdaterClient.discard())
                .bounds(width / 2 - 102, y + 25, 100, 20).build());
        back = addRenderableWidget(Button.builder(Component.literal("稍后更新"), b -> onClose())
                .bounds(width / 2 + 2, y + 25, 100, 20).build());
    }
    @Override public void tick() {
        check.active = !ModpackUpdaterClient.busy && ModpackUpdaterClient.updater != null && !ModpackUpdaterClient.pending();
        var plan = ModpackUpdaterClient.plan;
        download.active = check.active && plan != null && plan.changeCount() > 0;
        apply.active = !ModpackUpdaterClient.busy && ModpackUpdaterClient.pending() && minecraft.level == null;
        discard.active = !ModpackUpdaterClient.busy && ModpackUpdaterClient.pending();
        back.active = !ModpackUpdaterClient.restartAfterDownload;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 22, 0xffffffff);
        int x = Math.max(14, width / 2 - 230), y = 52;
        for (var line : font.split(Component.literal(ModpackUpdaterClient.status), Math.min(460, width - 28))) {
            graphics.text(font, line, x, y, 0xffffffff, false); y += 12;
        }
        var plan = ModpackUpdaterClient.plan;
        if (plan != null && plan.changeCount() > 0) {
            y += 10;
            graphics.text(font, Component.literal(String.format(java.util.Locale.ROOT, "下载 %.1f MiB；替换前自动备份", plan.downloadBytes() / 1048576.0)), x, y, 0xffb0d8ff, false);
            y += 18;
            for (var file : plan.downloads().stream().limit(Math.max(0, (height - y - 94) / 12)).toList()) {
                String line = file.modId() == null ? file.path() : file.modId() + " → " + file.version();
                graphics.text(font, Component.literal(font.plainSubstrByWidth(line, width - x * 2)), x, y, 0xffcccccc, false);
                y += 12;
            }
        }
    }
    @Override public void onClose() {
        if (!ModpackUpdaterClient.restartAfterDownload) minecraft.setScreen(parent);
    }
}
