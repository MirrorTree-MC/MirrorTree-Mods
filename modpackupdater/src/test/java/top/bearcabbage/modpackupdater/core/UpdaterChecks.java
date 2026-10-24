package top.bearcabbage.modpackupdater.core;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public final class UpdaterChecks {
    private static int checks;
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++; System.out.println("PASS " + description);
    }
    private static void rejects(IORunnable task, String message) throws Exception {
        try { task.run(); throw new AssertionError("Expected rejection: " + message); }
        catch (IOException expected) { check(true, message); }
    }
    private interface IORunnable { void run() throws Exception; }
    private static byte[] jar(String id, String version) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\"id\":\""+id+"\",\"version\":\""+version+"\"}").getBytes());
            zip.closeEntry();
        }
        return output.toByteArray();
    }
    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    private static Properties plan(String path, String oldHash, String newHash, long size, String stage) {
        var p = new Properties(); p.setProperty("schema", "1"); p.setProperty("release", "test");
        p.setProperty("count", "1"); p.setProperty("path.0", path); p.setProperty("old.0", oldHash);
        p.setProperty("new.0", newHash); p.setProperty("size.0", Long.toString(size)); p.setProperty("stage.0", stage);
        return p;
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("wait-target")) { Thread.sleep(2000); return; }
        if (args.length == 3 || args.length == 4) {
            var client = new UpdateClient(Path.of(args[0]), args[1], args[2]);
            var plan = client.check();
            System.out.println("VERIFIED_RELEASE " + plan.manifest().release() + " files=" + plan.manifest().files().size()
                    + " changes=" + plan.changeCount() + " bytes=" + plan.downloadBytes());
            if (args.length == 4 && args[3].equals("apply")) {
                if (!Files.exists(Path.of(args[0], ".updater-integration-fixture")))
                    throw new IOException("Public apply test requires a disposable fixture marker");
                client.stage(plan, System.out::println);
                UpdateApplier.apply(Path.of(args[0]));
                check(client.check().changeCount() == 0, "public signed release download and application converge to exact hashes");
            }
            return;
        }
        Path runs = Path.of("build", "run", "updaterChecks").toAbsolutePath();
        Files.createDirectories(runs);
        Path work = Files.createTempDirectory(runs, "run-").toRealPath();
        System.out.println("Updater check artifacts (retained on failure): " + work);
        Path root = Files.createDirectory(work.resolve("instance"));
        Files.createDirectories(root.resolve("mods"));
        byte[] old = jar("example", "1"), current = jar("example", "2");
        Files.write(root.resolve("mods/old.jar"), old);
        Files.write(root.resolve("mods/unrelated.jar"), jar("unrelated", "9"));
        Map<String, byte[]> personalFiles = Map.of("resourcepacks/personal.zip", new byte[] {1, 2, 3},
                "saves/personal-world/datapacks/personal.zip", new byte[] {4, 5, 6},
                "config/personal.json", "{\"personal\":true}".getBytes());
        for (var file : personalFiles.entrySet()) {
            Path path = root.resolve(file.getKey()); Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
        }
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String key = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        var entry = new UpdateClient.FileEntry("mods/new.jar", sha(current), current.length, "example", "2");
        var manifest = new UpdateClient.Manifest(1, "26.1.2", "integration", 1, List.of(entry), List.of());
        byte[] json = new Gson().toJson(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var signer = Signature.getInstance("Ed25519"); signer.initSign(pair.getPrivate()); signer.update(json);
        String release = sha(json);
        Map<String, byte[]> responses = new java.util.concurrent.ConcurrentHashMap<>();
        responses.put("/v1/latest", release.getBytes());
        responses.put("/v1/releases/"+release+"/manifest.json", json);
        responses.put("/v1/releases/"+release+"/manifest.sig", signer.sign());
        responses.put("/v1/files/"+sha(current), current);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] data = responses.get(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(data == null ? 404 : 200, data == null ? -1 : data.length);
            if (data != null) exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:"+server.getAddress().getPort()+"/";
            var client = new UpdateClient(root, url, key);
            var update = client.check();
            check(update.changeCount() == 2 && update.downloads().size() == 1, "replace by mod ID despite renamed JAR");
            client.stage(update, ignored -> {});
            check(Files.exists(root.resolve("mods/old.jar")) && !Files.exists(root.resolve("mods/new.jar")), "downloads do not change loaded mods");
            // Use only the JDK and the two helper class files in a fresh JVM.
            Path helper = root.resolve("helper.jar");
            var meta = new Manifest(); meta.getMainAttributes().putValue("Manifest-Version", "1.0");
            meta.getMainAttributes().putValue("Main-Class", UpdateApplier.class.getName());
            try (var out = new JarOutputStream(Files.newOutputStream(helper), meta)) {
                for (String name : List.of("UpdateApplier", "UpdateApplier$Change")) {
                    String resource = "top/bearcabbage/modpackupdater/core/"+name+".class";
                    out.putNextEntry(new JarEntry(resource));
                    try (var in = UpdateApplier.class.getClassLoader().getResourceAsStream(resource)) { in.transferTo(out); }
                    out.closeEntry();
                }
            }
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            var game = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    UpdaterChecks.class.getName(), "wait-target").start();
            var applied = new ProcessBuilder(java, "-jar", helper.toString(), root.toString(),
                    Long.toString(game.pid())).inheritIO().start();
            Thread.sleep(300);
            check(Files.exists(root.resolve("mods/old.jar")) && !Files.exists(root.resolve("mods/new.jar")),
                    "helper waits for the game process to exit before changing files");
            check(applied.waitFor() == 0, "standalone helper works without Fabric, Minecraft or Gson");
            check(Files.readAllBytes(root.resolve("mods/new.jar")).length == current.length && !Files.exists(root.resolve("mods/old.jar")), "new mod replaced old version");
            check(Files.exists(root.resolve("mods/unrelated.jar")), "unmanaged mods preserved");
            for (var file : personalFiles.entrySet())
                check(Arrays.equals(Files.readAllBytes(root.resolve(file.getKey())), file.getValue()),
                        "user additions preserved byte-for-byte: " + file.getKey());
            check(client.check().changeCount() == 0, "same signed release is up to date");
            var legacyRemoval = new UpdateClient.Manifest(1, "26.1.2", "legacy-removal", 2,
                    List.of(entry), List.of("unrelated"));
            check(client.plan(legacyRemoval).changeCount() == 0, "legacy removal declarations cannot delete user-added mods");
            byte[] extra = Files.readAllBytes(root.resolve("mods/unrelated.jar"));
            var unpublish = new UpdateClient.Manifest(1, "26.1.2", "unpublish", 2,
                    List.of(new UpdateClient.FileEntry("mods/unrelated.jar", sha(extra), extra.length, "unrelated", "9")),
                    List.of("example"));
            check(client.plan(unpublish).changeCount() == 0 && Files.exists(root.resolve("mods/new.jar")),
                    "unpublishing a formerly managed mod preserves its client file");
            try (var backups = Files.walk(root.resolve("mod-update-backups"))) {
                check(backups.anyMatch(p -> p.getFileName().toString().equals("old.jar")), "replaced mod has verified backup");
            }
            responses.put("/v1/releases/"+release+"/manifest.sig", new byte[64]);
            rejects(client::check, "reject altered or untrusted signature");
            for (String path : List.of("../escape.jar", "mods/../../escape.jar", "/tmp/mod.jar", "config/modpackupdater.json", "saves/level.dat", "mods\\escape.jar"))
                rejects(() -> UpdateApplier.target(root, path), "reject protected path " + path);
            Path outside = Files.createDirectory(work.resolve("outside"));
            Files.delete(root.resolve("config/personal.json")); Files.delete(root.resolve("config"));
            Files.createSymbolicLink(root.resolve("config"), outside);
            rejects(() -> UpdateApplier.target(root, "config/test.json"), "reject symlink escape");
            Files.delete(root.resolve("config"));
            var malicious = new UpdateClient.Manifest(1,"26.1.2","bad",2,
                    List.of(new UpdateClient.FileEntry("mods/new.jar",sha(current),current.length,"wrong-id","2")),List.of());
            rejects(() -> client.plan(malicious), "never overwrite a different mod at the same filename");
            responses.put("/v1/files/"+sha(current), new byte[current.length]);
            Path other = Files.createDirectory(work.resolve("bad-download")); Files.createDirectories(other.resolve("mods"));
            var otherClient = new UpdateClient(other,url,key);
            rejects(() -> otherClient.stage(otherClient.plan(manifest), ignored -> {}), "corrupt downloads cannot produce a pending update");
            check(!Files.exists(other.resolve(".modpackupdater/pending.properties")), "failed download leaves no applicable plan");
            // A changed local file causes a clean abort before touching any other live file.
            var changed = plan("mods/new.jar", sha(old), "-",0,"");
            UpdateApplier.writeProperties(root.resolve(".modpackupdater/pending.properties"),changed);
            rejects(() -> UpdateApplier.apply(root), "local changes since staging prevent application");
            check(UpdateApplier.hash(root.resolve("mods/new.jar")).equals(sha(current)), "precondition failure preserved live files");
            Files.delete(root.resolve(".modpackupdater/pending.properties"));
            // Simulate process termination after replacing a file but before the transaction completes.
            Path backup=root.resolve("mod-update-backups/updater-crash/mods"); Files.createDirectories(backup);
            Files.write(backup.resolve("new.jar"),old);
            var interrupted = plan("mods/new.jar",sha(old),sha(current),current.length,".modpackupdater/staging/test/file");
            interrupted.setProperty("backup","mod-update-backups/updater-crash");
            UpdateApplier.writeProperties(root.resolve(".modpackupdater/transaction.properties"),interrupted);
            UpdateApplier.recover(root);
            check(UpdateApplier.hash(root.resolve("mods/new.jar")).equals(sha(old)), "interrupted transaction rolls back from journal");
            check(!Files.exists(root.resolve(".modpackupdater/transaction.properties")), "recovery clears transaction marker");
        } finally { server.stop(0); }
        // Failed checks leave their isolated run for diagnosis; successful runs leave no fixtures.
        try (var files = Files.walk(work)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
        System.out.println("PASS " + checks + " updater checks");
    }
}
