package top.bearcabbage.modpackupdater.core;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Private, local restart instructions. Never obtained from the update server. JDK only. */
public final class RestartLauncher {
    private static final String RECIPE = UpdateApplier.STATE + "/restart.properties";
    private static String expectedRestartId;
    private RestartLauncher() {}

    public static void prepare(Path root, String[] gameArguments) throws IOException {
        var command = new ArrayList<String>();
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        command.add(Path.of(System.getProperty("java.home"), "bin", windows ? "javaw.exe" : "java").toString());
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        // The macOS Java launcher consumes this flag before MXBean input arguments are recorded.
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")
                && !command.contains("-XstartOnFirstThread")) command.add("-XstartOnFirstThread");
        String classpath = System.getProperty("java.class.path", "");
        if (classpath.isBlank() || gameArguments.length == 0) throw new IOException("当前启动器未提供可用的游戏启动参数");
        // Preserve properties applied by Fabric's development launcher as well as normal JVM options.
        for (String name : List.of("fabric.development", "fabric.gameJarPath", "fabric.gameJarPath.client",
                "fabric.gameJarPath.common", "fabric.remapClasspathFile", "fabric.classPathGroups", "fabric.addMods")) {
            String value = System.getProperty(name);
            if (value != null) command.add("-D" + name + "=" + value);
        }
        var nativeCopies = new HashMap<Path, String>();
        for (String property : List.of("java.library.path", "org.lwjgl.librarypath", "jna.tmpdir",
                "org.lwjgl.system.SharedLibraryExtractPath", "io.netty.native.workdir")) {
            String value = System.getProperty(property);
            if (value != null) command.add("-D" + property + "=" + preserveNatives(root, value, nativeCopies));
        }
        command.add("-cp"); command.add(classpath);
        command.add("net.fabricmc.loader.impl.launch.knot.KnotClient");
        command.addAll(Arrays.asList(gameArguments));
        prepareCommand(root, command, Path.of(System.getProperty("user.dir")));
    }

    private static String preserveNatives(Path root, String value, Map<Path, String> copies) throws IOException {
        var paths = new ArrayList<String>();
        for (String part : value.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (part.isBlank()) continue;
            Path original = Path.of(part).toAbsolutePath().normalize();
            if (copies.containsKey(original)) { paths.add(copies.get(original)); continue; }
            // Launchers may remove their extracted native libraries when the old process exits.
            if (Files.isDirectory(original) && (original.toString().toLowerCase(Locale.ROOT).contains("native")
                    || original.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()))) {
                Path copy = UpdateApplier.confined(root, UpdateApplier.STATE + "/restart-natives/" + UUID.randomUUID());
                try (var files = Files.walk(original, 3)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!name.endsWith(".dll") && !name.endsWith(".so") && !name.endsWith(".dylib") && !name.endsWith(".jnilib")) continue;
                        if (Files.size(file) > 100_000_000) throw new IOException("原生库文件过大，无法准备重启");
                        Path target = copy.resolve(original.relativize(file));
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                if (Files.isDirectory(copy)) { paths.add(copy.toString()); copies.put(original, copy.toString()); continue; }
            }
            paths.add(original.toString());
        }
        return String.join(File.pathSeparator, paths);
    }

    public static void prepareCommand(Path directory, List<String> command, Path workingDirectory) throws IOException {
        if (command.size() < 2 || command.size() > 8192 || !Files.isRegularFile(Path.of(command.getFirst()))
                || !Files.isDirectory(workingDirectory)) throw new IOException("无法保存游戏重启参数");
        Path root = directory.toRealPath();
        Path state = UpdateApplier.confined(root, UpdateApplier.STATE);
        Files.createDirectories(state);
        restrict(state, true);
        var values = new Properties();
        String restartId = UUID.randomUUID().toString();
        command = new ArrayList<>(command);
        command.removeIf(arg -> arg.startsWith("-Dmirrortree.updater.restartId="));
        command.add(1, "-Dmirrortree.updater.restartId=" + restartId);
        values.setProperty("schema", "1"); values.setProperty("cwd", workingDirectory.toRealPath().toString());
        values.setProperty("restartId", restartId);
        values.setProperty("count", Integer.toString(command.size()));
        for (int i = 0; i < command.size(); i++) {
            String arg = command.get(i);
            if (arg == null || arg.indexOf(0) >= 0) throw new IOException("游戏启动参数无效");
            values.setProperty("arg." + i, arg);
        }
        // This local file can contain login arguments. Create it privately before writing any bytes.
        Path target = UpdateApplier.confined(root, RECIPE);
        Path temp = Files.createTempFile(state, ".restart-", ".tmp");
        try {
            restrict(temp, false);
            try (var output = Files.newOutputStream(temp)) { values.store(output, "Private local restart instructions"); }
            UpdateApplier.move(temp, target);
        } finally { Files.deleteIfExists(temp); }
    }

    private static void restrict(Path path, boolean directory) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl == null) throw new IOException("无法保护本地重启参数");
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        }
    }

    public static void discard(Path root) throws IOException { Files.deleteIfExists(UpdateApplier.confined(root.toRealPath(), RECIPE)); }

    public static Process start(Path directory) throws IOException {
        Path root = directory.toRealPath(), recipe = UpdateApplier.confined(root, RECIPE);
        var values = UpdateApplier.readProperties(recipe);
        try {
            int count = Integer.parseInt(values.getProperty("count"));
            if (!"1".equals(values.getProperty("schema")) || count < 2 || count > 8192) throw new IOException("游戏重启参数损坏");
            var command = new ArrayList<String>();
            for (int i = 0; i < count; i++) command.add(Objects.requireNonNull(values.getProperty("arg." + i)));
            Path cwd = Path.of(values.getProperty("cwd"));
            expectedRestartId = values.getProperty("restartId");
            if (expectedRestartId == null || !expectedRestartId.matches("[a-f0-9-]{36}")) throw new IOException("游戏重启标识损坏");
            if (!Files.isRegularFile(Path.of(command.getFirst())) || !Files.isDirectory(cwd)) throw new IOException("游戏启动环境已变化");
            // Preserve embedded quotes and trailing backslashes in Windows Java arguments.
            if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))
                System.setProperty("jdk.lang.Process.allowAmbiguousCommands", "false");
            Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true)
                    .redirectOutput(root.resolve(UpdateApplier.STATE + "/restarted-game.log").toFile()).start();
            System.out.println("Updated game started; process " + process.pid());
            return process;
        } catch (IllegalArgumentException | NullPointerException e) { throw new IOException("游戏重启参数损坏", e); }
        finally { Files.deleteIfExists(recipe); }
    }

    public static boolean ready(Path directory) throws IOException {
        String id = System.getProperty("mirrortree.updater.restartId");
        if (id == null || !id.matches("[a-f0-9-]{36}")) return false;
        var values = new Properties(); values.setProperty("restartId", id);
        values.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        UpdateApplier.writeProperties(UpdateApplier.confined(directory.toRealPath(), UpdateApplier.STATE + "/restart-ready.properties"), values);
        return true;
    }

    public static void awaitReady(Path directory, Process game) throws IOException, InterruptedException {
        Path marker = UpdateApplier.confined(directory.toRealPath(), UpdateApplier.STATE + "/restart-ready.properties");
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(marker) && expectedRestartId.equals(UpdateApplier.readProperties(marker).getProperty("restartId"))) return;
            if (!game.isAlive()) throw new IOException("新版游戏未能启动，请查看 restarted-game.log");
            Thread.sleep(250);
        }
        throw new IOException("游戏加载时间较长；文件已更新，可查看 restarted-game.log");
    }
}
