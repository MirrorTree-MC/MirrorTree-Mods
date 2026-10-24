package top.bearcabbage.modpackupdater.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Signed, origin-confined downloads. The live instance is read-only until the helper runs. */
public final class UpdateClient {
    public record FileEntry(String path, String sha256, long size, String modId, String version) {}
    public record Manifest(int schemaVersion, String minecraft, String release, long sequence,
                           List<FileEntry> files, List<String> removeModIds) {}
    public record ModInfo(String id, String version) {}
    public record Plan(Manifest manifest, List<FileEntry> downloads, Map<String, String> oldFiles) {
        public int changeCount() {
            Set<String> paths = new HashSet<>(oldFiles.keySet());
            for (FileEntry file : downloads) paths.add(file.path());
            return paths.size();
        }
        public long downloadBytes() { return downloads.stream().mapToLong(FileEntry::size).sum(); }
    }
    private static final Gson GSON = new Gson();
    private static final java.util.concurrent.ScheduledExecutorService TIMEOUTS =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "MirrorTree download timeout"); thread.setDaemon(true); return thread;
            });
    private final Path root;
    private final URI base;
    private final PublicKey key;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public UpdateClient(Path directory, String serverUrl, String publicKey) throws IOException {
        root = directory.toRealPath();
        try {
            base = URI.create(serverUrl.endsWith("/") ? serverUrl : serverUrl + "/");
            if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null)
                throw new IllegalArgumentException("Use an HTTP(S) update server URL");
            key = KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.trim())));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IOException("更新服务器地址或签名公钥无效", e);
        }
        UpdateApplier.confined(root, UpdateApplier.STATE);
    }

    private InputStream request(String path) throws IOException {
        URI uri = base.resolve(path);
        if (!Objects.equals(uri.getAuthority(), base.getAuthority()) || !uri.getScheme().equals(base.getScheme()))
            throw new IOException("Update URL left configured origin");
        try {
            var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("更新服务器返回 HTTP " + response.statusCode());
            }
            InputStream body = response.body();
            long started = System.nanoTime();
            var lastProgress = new java.util.concurrent.atomic.AtomicLong(started);
            var timeout = TIMEOUTS.scheduleAtFixedRate(() -> {
                long now = System.nanoTime();
                if (now - lastProgress.get() > Duration.ofSeconds(30).toNanos()
                        || now - started > Duration.ofMinutes(30).toNanos()) {
                    try { body.close(); } catch (IOException ignored) { }
                }
            }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
            return new FilterInputStream(body) {
                @Override public int read() throws IOException {
                    int value = in.read();
                    if (value >= 0) lastProgress.set(System.nanoTime());
                    return value;
                }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    int count = in.read(bytes, offset, length);
                    if (count > 0) lastProgress.set(System.nanoTime());
                    return count;
                }
                @Override public void close() throws IOException { timeout.cancel(false); super.close(); }
            };
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("更新已取消", e); }
    }

    private byte[] limited(String path, int maximum) throws IOException {
        try (var in = request(path)) {
            byte[] data = in.readNBytes(maximum + 1);
            if (data.length > maximum) throw new IOException("Update response is too large");
            return data;
        }
    }

    public Manifest fetch() throws IOException {
        // The release pointer is immutable once resolved, so publication cannot mix manifest/signature versions.
        String releaseId = new String(limited("v1/latest", 200), java.nio.charset.StandardCharsets.US_ASCII).trim();
        if (!releaseId.matches("[a-f0-9]{64}")) throw new IOException("Invalid release pointer");
        byte[] bytes = limited("v1/releases/" + releaseId + "/manifest.json", 1_000_000);
        byte[] signature = limited("v1/releases/" + releaseId + "/manifest.sig", 128);
        try {
            var verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key); verifier.update(bytes);
            if (!verifier.verify(signature)) throw new IOException("更新清单签名验证失败，已拒绝更新");
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(releaseId))
                throw new IOException("更新清单与发布编号不一致");
        } catch (GeneralSecurityException e) { throw new IOException("更新清单签名验证失败", e); }
        Manifest manifest;
        try { manifest = GSON.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), Manifest.class); }
        catch (RuntimeException e) { throw new IOException("更新清单格式无效", e); }
        validate(manifest);
        Path seen = UpdateApplier.confined(root, UpdateApplier.STATE + "/seen.properties");
        Properties state = Files.exists(seen) ? UpdateApplier.readProperties(seen) : new Properties();
        try {
            long previous = Long.parseLong(state.getProperty("sequence", "-1"));
            if (manifest.sequence() < previous || manifest.sequence() == previous
                    && !releaseId.equals(state.getProperty("manifest")))
                throw new IOException("更新清单早于已确认的版本，已拒绝回退");
        } catch (NumberFormatException e) { throw new IOException("本地更新状态损坏", e); }
        state.setProperty("sequence", Long.toString(manifest.sequence()));
        state.setProperty("manifest", releaseId);
        UpdateApplier.writeProperties(seen, state);
        return manifest;
    }

    private void validate(Manifest m) throws IOException {
        if (m == null || m.schemaVersion() != 1 || !"26.1.2".equals(m.minecraft())
                || m.release() == null || m.release().length() > 200 || m.sequence() < 0
                || m.files() == null || m.files().isEmpty() || m.files().size() > 4096
                || m.removeModIds() == null || m.removeModIds().size() > 4096)
            throw new IOException("Unsupported or incomplete update manifest");
        var paths = new HashSet<String>();
        var ids = new HashSet<String>();
        long size = 0;
        for (FileEntry file : m.files()) {
            if (file == null || file.path() == null || file.sha256() == null
                    || !file.sha256().matches("[a-f0-9]{64}") || file.size() < 0
                    || file.size() > 1_073_741_824L) throw new IOException("Invalid file entry");
            UpdateApplier.target(root, file.path());
            if (!paths.add(file.path().toLowerCase(Locale.ROOT))) throw new IOException("Duplicate update path");
            if (file.path().startsWith("mods/")) {
                if (file.modId() == null || !file.modId().matches("[a-z][a-z0-9_-]{1,63}")
                        || file.version() == null || file.version().isBlank() || !ids.add(file.modId()))
                    throw new IOException("Invalid or duplicate Fabric mod ID");
            } else if (file.modId() != null) throw new IOException("Mod outside mods directory");
            size += file.size();
        }
        if (size > 8_589_934_592L) throw new IOException("Update exceeds 8 GiB");
        for (String id : m.removeModIds())
            if (id == null || !id.matches("[a-z][a-z0-9_-]{1,63}") || ids.contains(id) || id.equals("modpackupdater"))
                throw new IOException("Invalid removed mod ID");
    }

    public static ModInfo modInfo(Path jar) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return null;
            try (var in = zip.getInputStream(entry)) {
                byte[] bytes = in.readNBytes(262145);
                if (bytes.length > 262144) throw new IOException("Oversized Fabric metadata");
                var meta = GSON.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
                return new ModInfo(meta.get("id").getAsString(), meta.get("version").getAsString());
            }
        } catch (RuntimeException e) { throw new IOException("Invalid mod JAR: " + jar.getFileName(), e); }
    }

    public Plan check() throws IOException { return plan(fetch()); }

    public Plan plan(Manifest manifest) throws IOException {
        validate(manifest);
        Map<String, List<Path>> local = new HashMap<>();
        Path mods = UpdateApplier.confined(root, "mods");
        if (Files.exists(mods)) try (var entries = Files.list(mods)) {
            for (Path path : entries.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                UpdateApplier.target(root, "mods/" + path.getFileName());
                ModInfo info = modInfo(path);
                if (info != null) local.computeIfAbsent(info.id(), ignored -> new ArrayList<>()).add(path);
            }
        }
        var downloads = new ArrayList<FileEntry>();
        var old = new LinkedHashMap<String, String>();
        Set<String> targets = new HashSet<>();
        for (FileEntry file : manifest.files()) targets.add(file.path());
        for (FileEntry file : manifest.files()) {
            Path path = UpdateApplier.target(root, file.path());
            if (Files.exists(path)) {
                if (file.modId() != null) {
                    ModInfo actual = modInfo(path);
                    if (actual == null || !actual.id().equals(file.modId()))
                        throw new IOException("更新路径被其他模组占用: " + file.path());
                }
                String hash = UpdateApplier.hash(path);
                if (!hash.equals(file.sha256())) { old.put(file.path(), hash); downloads.add(file); }
            } else downloads.add(file);
            if (file.modId() != null) for (Path prior : local.getOrDefault(file.modId(), List.of())) {
                String relative = root.relativize(prior).toString().replace('\\', '/');
                if (!relative.equals(file.path()) && !(Files.exists(path) && Files.isSameFile(prior, path))) {
                    if (targets.contains(relative)) throw new IOException("Manifest conflicts with existing mod IDs");
                    old.put(relative, UpdateApplier.hash(prior));
                }
            }
        }
        // The release is an additive requirement, not a mirror of the whole instance.
        // Legacy removeModIds are intentionally ignored. Only obsolete filenames of
        // a mod ID still present in this release may be replaced above.
        return new Plan(manifest, List.copyOf(downloads), Map.copyOf(old));
    }

    public Path stage(Plan plan, Consumer<String> progress) throws IOException {
        Path pending = UpdateApplier.confined(root, UpdateApplier.STATE + "/pending.properties");
        if (Files.exists(pending) || Files.exists(root.resolve(UpdateApplier.STATE + "/transaction.properties")))
            throw new IOException("已有暂存或中断的更新，请先应用或恢复");
        Path directory = UpdateApplier.confined(root, UpdateApplier.STATE + "/staging/" + UUID.randomUUID());
        Files.createDirectories(directory);
        try {
        Map<String, FileEntry> downloads = new LinkedHashMap<>();
        int index = 0;
        for (FileEntry entry : plan.downloads()) {
            progress.accept("正在下载 " + (++index) + "/" + plan.downloads().size() + " · " + Path.of(entry.path()).getFileName());
            Path staged = directory.resolve(entry.sha256());
            try (var in = request("v1/files/" + entry.sha256()); var out = Files.newOutputStream(staged)) {
                byte[] buffer = new byte[65536]; long total = 0;
                for (int n; (n = in.read(buffer)) >= 0;) {
                    total += n;
                    if (total > entry.size()) throw new IOException("下载文件超过清单大小");
                    out.write(buffer, 0, n);
                }
                if (total != entry.size()) throw new IOException("下载文件不完整");
            }
            if (!UpdateApplier.hash(staged).equals(entry.sha256())) throw new IOException("文件校验失败: " + entry.path());
            if (entry.modId() != null) {
                ModInfo info = modInfo(staged);
                if (info == null || !info.id().equals(entry.modId()) || !info.version().equals(entry.version()))
                    throw new IOException("模组身份与清单不符: " + entry.path());
            }
            downloads.put(entry.path(), entry);
        }
        Properties values = new Properties();
        values.setProperty("schema", "1"); values.setProperty("release", plan.manifest().release());
        values.setProperty("sequence", Long.toString(plan.manifest().sequence()));
        Set<String> paths = new TreeSet<>(plan.oldFiles().keySet()); paths.addAll(downloads.keySet());
        index = 0;
        for (String path : paths) {
            FileEntry file = downloads.get(path);
            values.setProperty("path." + index, path);
            values.setProperty("old." + index, plan.oldFiles().getOrDefault(path, "-"));
            values.setProperty("new." + index, file == null ? "-" : file.sha256());
            values.setProperty("size." + index, file == null ? "0" : Long.toString(file.size()));
            values.setProperty("stage." + index, file == null ? "" : root.relativize(directory.resolve(file.sha256())).toString().replace('\\', '/'));
            index++;
        }
        if (index == 0) throw new IOException("没有需要更新的文件");
        values.setProperty("count", Integer.toString(index));
        UpdateApplier.changes(root, values);
        UpdateApplier.writeProperties(pending, values);
        return pending;
        } catch (IOException | RuntimeException failure) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
