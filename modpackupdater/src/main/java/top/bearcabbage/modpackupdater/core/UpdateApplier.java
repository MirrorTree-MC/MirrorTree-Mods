package top.bearcabbage.modpackupdater.core;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Standalone helper: deliberately uses only the JDK, including for self-updates. */
public final class UpdateApplier {
    public static final String STATE = ".modpackupdater";
    public record Change(String path, String oldHash, String newHash, long size, String stage) {}
    private UpdateApplier() {}

    public static String hash(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) >= 0;) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    public static Path confined(Path root, String relative) throws IOException {
        if (relative == null || relative.isBlank() || relative.contains("\\") || relative.contains(":"))
            throw new IOException("Invalid relative path: " + relative);
        Path rel = Path.of(relative);
        if (rel.isAbsolute() || !rel.normalize().equals(rel) || rel.startsWith(".."))
            throw new IOException("Path escapes instance: " + relative);
        Path target = root.resolve(rel).normalize();
        if (!target.startsWith(root)) throw new IOException("Path escapes instance");
        Path cursor = root;
        for (Path part : rel) {
            cursor = cursor.resolve(part);
            try {
                var attributes = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                // Windows junctions are reparse points reported as "other", not symbolic links.
                if (attributes.isSymbolicLink() || attributes.isOther())
                    throw new IOException("Refusing link or special file: " + cursor);
            } catch (NoSuchFileException ignored) {
                // Staging and backup paths may not exist yet.
            }
        }
        return target;
    }

    public static Path target(Path root, String relative) throws IOException {
        Path path = confined(root, relative);
        String[] parts = relative.split("/");
        boolean allowed = parts.length == 2 && parts[0].equals("mods") && parts[1].endsWith(".jar")
                || parts.length == 2 && parts[0].equals("resourcepacks") && parts[1].endsWith(".zip")
                || parts.length >= 2 && parts[0].equals("config")
                    && !relative.equalsIgnoreCase("config/modpackupdater.json");
        if (!allowed || relative.endsWith("/")) throw new IOException("Protected update path: " + relative);
        if (Files.exists(path) && !Files.isRegularFile(path)) throw new IOException("Not a regular file: " + path);
        return path;
    }

    public static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
        catch (AccessDeniedException e) {
            // Windows atomic move may refuse an existing recovery target.
            if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") || !Files.isRegularFile(to)) throw e;
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void writeProperties(Path path, Properties values) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Refusing symlink: " + path);
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".properties-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { values.store(out, "MirrorTree update transaction"); }
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            move(temp, path);
        } finally { Files.deleteIfExists(temp); }
    }

    public static Properties readProperties(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Refusing symlink: " + path);
        if (Files.size(path) > 4_000_000) throw new IOException("Oversized update plan");
        var values = new Properties();
        try (var in = Files.newInputStream(path)) { values.load(in); }
        return values;
    }

    public static List<Change> changes(Path root, Properties plan) throws IOException {
        try {
            if (!"1".equals(plan.getProperty("schema"))) throw new IOException("Unsupported update plan");
            int count = Integer.parseInt(plan.getProperty("count"));
            if (count < 1 || count > 4096) throw new IOException("Invalid update count");
            var result = new ArrayList<Change>();
            var names = new HashSet<String>();
            for (int i = 0; i < count; i++) {
                String path = plan.getProperty("path." + i);
                Path resolved = target(root, path);
                if (!names.add(resolved.toString().toLowerCase(Locale.ROOT))) throw new IOException("Duplicate target: " + path);
                String oldHash = plan.getProperty("old." + i), newHash = plan.getProperty("new." + i);
                if (!validHash(oldHash) || !validHash(newHash) || oldHash.equals("-") && newHash.equals("-"))
                    throw new IOException("Invalid hash in update plan");
                long size = Long.parseLong(plan.getProperty("size." + i, "0"));
                String stage = plan.getProperty("stage." + i, "");
                if (!newHash.equals("-")) {
                    if (!stage.startsWith(STATE + "/staging/") || size < 0 || size > 1_073_741_824L)
                        throw new IOException("Invalid staged file");
                    confined(root, stage);
                }
                result.add(new Change(path, oldHash, newHash, size, stage));
            }
            return result;
        } catch (IllegalArgumentException | NullPointerException e) { throw new IOException("Malformed update plan", e); }
    }

    private static boolean validHash(String hash) { return hash != null && (hash.equals("-") || hash.matches("[a-f0-9]{64}")); }

    /** Last delivered server content, independent of mods rewriting their live configuration. */
    public static Properties configReceipts(Path root) throws IOException {
        Path file = confined(root, STATE + "/config-deliveries.properties");
        Properties receipts = Files.exists(file) ? readProperties(file) : new Properties();
        for (String path : receipts.stringPropertyNames()) {
            if (path.equals("_applied")) continue;
            target(root, path);
            if (!path.startsWith("config/") || !receipts.getProperty(path).matches("[a-f0-9]{64}"))
                throw new IOException("Invalid configuration delivery receipt");
        }
        // Also migrates 3.1.0 installations. An in-flight transaction is not proof of delivery.
        Path applied = confined(root, STATE + "/applied.properties");
        if (!Files.exists(confined(root, STATE + "/transaction.properties")) && Files.exists(applied)) {
            Properties plan = readProperties(applied);
            String transaction = plan.getProperty("backup", "");
            if (transaction.isEmpty()) throw new IOException("Missing applied transaction identity");
            if (!transaction.equals(receipts.getProperty("_applied"))) {
                for (Change c : changes(root, plan)) if (c.path().startsWith("config/")) {
                    if (c.newHash().equals("-")) receipts.remove(c.path());
                    else receipts.setProperty(c.path(), c.newHash());
                }
                receipts.setProperty("_applied", transaction);
            }
        }
        return receipts;
    }

    public static void rememberConfigs(Path root, Properties receipts) throws IOException {
        if (Files.exists(confined(root, STATE + "/transaction.properties"))) return;
        Path file = confined(root, STATE + "/config-deliveries.properties");
        if (!Files.exists(file) || !readProperties(file).equals(receipts)) writeProperties(file, receipts);
    }

    public static boolean apply(Path directory) throws IOException {
        Path root = directory.toRealPath();
        Path state = confined(root, STATE);
        Files.createDirectories(state);
        try (var channel = FileChannel.open(state.resolve("apply.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another update helper is running");
            Path journal = state.resolve("transaction.properties");
            if (Files.exists(journal)) recover(root);
            Path pending = state.resolve("pending.properties");
            if (!Files.exists(pending)) return false;
            Properties plan = readProperties(pending);
            UpdateProgress.message("Verifying files / 游戏已关闭，正在核对更新文件…");
            List<Change> changes = changes(root, plan);
            for (Change c : changes) {
                Path file = target(root, c.path());
                String actual = Files.exists(file) ? hash(file) : "-";
                if (!actual.equals(c.oldHash())) throw new IOException("Local file changed after download: " + c.path());
                if (!c.newHash().equals("-")) {
                    Path staged = confined(root, c.stage());
                    if (!Files.isRegularFile(staged) || Files.size(staged) != c.size() || !hash(staged).equals(c.newHash()))
                        throw new IOException("Staged file failed verification: " + c.path());
                }
            }
            // Preserve earlier successful deliveries before applied.properties is replaced.
            // New deliveries are imported only after the journal has been cleared successfully.
            rememberConfigs(root, configReceipts(root));
            String backupName = "mod-update-backups/updater-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
            UpdateProgress.message("Backing up files / 正在备份原文件，玩家额外内容将保留…");
            Path backup = confined(root, backupName);
            for (Change c : changes) if (!c.oldHash().equals("-")) {
                Path copy = confined(backup, c.path());
                Files.createDirectories(copy.getParent());
                Files.copy(target(root, c.path()), copy, StandardCopyOption.COPY_ATTRIBUTES);
                if (!hash(copy).equals(c.oldHash())) throw new IOException("Backup failed: " + c.path());
            }
            plan.setProperty("backup", backupName);
            writeProperties(journal, plan); // Written before the first live file changes.
            UpdateProgress.message("Applying update / 正在应用已验证的更新，请勿关闭此窗口…");
            try {
                for (Change c : changes) {
                    Path file = target(root, c.path());
                    if (c.newHash().equals("-")) Files.delete(file);
                    else replace(confined(root, c.stage()), file);
                }
                for (Change c : changes) {
                    Path file = target(root, c.path());
                    if (c.newHash().equals("-") ? Files.exists(file) : !hash(file).equals(c.newHash()))
                        throw new IOException("Applied file failed verification: " + c.path());
                }
                writeProperties(backup.resolve("transaction.properties"), plan);
                writeProperties(state.resolve("applied.properties"), plan);
                Files.delete(pending);
                Files.delete(journal);
                System.out.println("Update applied: " + plan.getProperty("release") + "; backup: " + backup);
            } catch (Exception failure) {
                try { recover(root); } catch (Exception rollback) { failure.addSuppressed(rollback); }
                throw new IOException("Update failed; recovery information is in " + journal, failure);
            }
            // The transaction is committed. Cleanup must never enter the rollback path.
            cleanupStaged(root, changes);
            return true;
        }
    }

    public static void discard(Path directory) throws IOException {
        Path root = directory.toRealPath();
        Path state = confined(root, STATE);
        Files.createDirectories(state);
        try (var channel = FileChannel.open(state.resolve("apply.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another update helper is running");
            if (Files.exists(confined(root, STATE + "/transaction.properties")))
                throw new IOException("An interrupted update must be recovered before discarding it");
            Path pending = confined(root, STATE + "/pending.properties");
            if (!Files.exists(pending)) return;
            List<Change> changes = changes(root, readProperties(pending));
            Files.delete(pending);
            cleanupStaged(root, changes);
        }
    }

    /** Delete only the verified files named by this finished or explicitly discarded plan. */
    private static void cleanupStaged(Path root, List<Change> changes) {
        for (Change change : changes) {
            if (change.newHash().equals("-")) continue;
            try {
                Path staging = confined(root, STATE + "/staging");
                Path file = confined(root, change.stage());
                if (!file.startsWith(staging) || file.equals(staging)) throw new IOException("Invalid cleanup path");
                if (Files.exists(file)) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            || Files.size(file) != change.size() || !hash(file).equals(change.newHash()))
                        throw new IOException("Staged file changed; preserving " + file);
                    Files.delete(file);
                }
                // Unknown files and other downloads in the same directory are retained.
                for (Path parent = file.getParent(); !parent.equals(staging); parent = parent.getParent()) {
                    confined(root, root.relativize(parent).toString().replace('\\', '/'));
                    try { Files.deleteIfExists(parent); }
                    catch (DirectoryNotEmptyException retained) { break; }
                }
            } catch (IOException | RuntimeException cleanup) {
                System.err.println("Update staging cleanup deferred: " + cleanup.getMessage());
            }
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".update-", ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            move(temp, target);
        } finally { Files.deleteIfExists(temp); }
    }

    public static void recover(Path root) throws IOException {
        Path journal = confined(root, STATE + "/transaction.properties");
        Properties plan = readProperties(journal);
        List<Change> changes = changes(root, plan);
        String backupName = plan.getProperty("backup", "");
        if (!backupName.startsWith("mod-update-backups/updater-")) throw new IOException("Invalid recovery path");
        Path backup = confined(root, backupName);
        for (Change c : changes) if (!c.oldHash().equals("-")) {
            Path file = confined(backup, c.path());
            if (!Files.isRegularFile(file) || !hash(file).equals(c.oldHash()))
                throw new IOException("Backup damaged; manual recovery required: " + file);
        }
        // A crash can leave the journal after pending was deleted during commit.
        // Restore the plan durably before rolling back so apply() can retry it.
        Path pending = confined(root, STATE + "/pending.properties");
        if (!Files.exists(pending)) writeProperties(pending, plan);
        for (Change c : changes) {
            Path file = target(root, c.path());
            if (c.oldHash().equals("-")) Files.deleteIfExists(file);
            else replace(confined(backup, c.path()), file);
        }
        Path applied = confined(root, STATE + "/applied.properties");
        if (Files.exists(applied) && backupName.equals(readProperties(applied).getProperty("backup")))
            Files.delete(applied); // Never acknowledge files that were subsequently rolled back.
        Files.delete(journal);
        System.out.println("Restored interrupted update from " + backup);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3 || args.length == 3 && !args[2].equals("restart"))
            throw new IllegalArgumentException("Usage: java -jar apply-helper.jar INSTANCE GAME_PID [restart]");
        long pid = Long.parseLong(args[1]);
        UpdateProgress.open();
        boolean applied = false;
        try {
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(10).toNanos();
        while (pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            if (System.nanoTime() > deadline) throw new IOException("Game is still running; files were not replaced");
            Thread.sleep(500);
        }
        applied = apply(Path.of(args[0]));
        if (!applied) {
            if (args.length == 3) RestartLauncher.discard(Path.of(args[0]));
            UpdateProgress.message("No pending update / 没有待应用更新，未重新启动游戏。");
            UpdateProgress.close();
            return;
        }
        if (args.length == 3) {
            UpdateProgress.message("Restarting Minecraft / 更新成功，正在重新启动 Minecraft…");
            Process game = RestartLauncher.start(Path.of(args[0]));
            RestartLauncher.awaitReady(Path.of(args[0]), game);
            UpdateProgress.message("Minecraft restarted / 游戏已重新启动，更新完成。");
        }
        UpdateProgress.close();
        } catch (Exception failure) {
            if (args.length == 3) try { RestartLauncher.discard(Path.of(args[0])); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            UpdateProgress.error(applied ? "Files updated; launch needs attention / 文件已更新，可从启动器重试并查看日志。"
                    : "Update failed; see apply.log / 更新未完成，已保留恢复信息，请查看日志。");
            throw failure;
        }
    }
}
