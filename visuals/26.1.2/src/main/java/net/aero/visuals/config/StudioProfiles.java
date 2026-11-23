package net.aero.visuals.config;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Portable profiles, isolated by game version. Server addresses stay in the local bindings file. */
public final class StudioProfiles {
    private final ConfigManager config;
    private final Path directory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Properties bindings = new Properties();
    private String server = "";
    private final java.util.function.LongSupplier nanoTime;
    private String failedServer;
    private long retryAfterNanos;
    public StudioProfiles(ConfigManager config, Path directory) {
        this(config, directory, System::nanoTime);
    }
    StudioProfiles(ConfigManager config, Path directory, java.util.function.LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.config = config; this.directory = directory;
        try {
            Files.createDirectories(directory.resolve("imports"));
            Files.createDirectories(directory.resolve("exports"));
            Path file = directory.resolve("servers.properties");
            try (var reader = Files.newBufferedReader(file)) {
                Properties loaded = new Properties();
                loaded.load(reader);
                bindings.putAll(loaded);
            } catch (NoSuchFileException ignored) {
                // No server bindings have been saved yet.
            }
        } catch (IOException | IllegalArgumentException failure) {
            net.aero.visuals.AeroVisuals.LOGGER.warn("Could not load server profile bindings from {}", directory, failure);
        }
    }
    public static String safeName(String name) {
        if (name == null || !name.matches("[\\p{L}\\p{N}_ -]{1,48}") || name.isBlank())
            throw new IllegalArgumentException("Use 1–48 letters, numbers, spaces, - or _");
        return name.strip();
    }
    public List<String> list() throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().replaceFirst("\\.json$", "")).sorted().toList();
        }
    }
    private void write(Path path, JsonObject root) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), "aero-profile-", ".tmp");
        try {
            Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8);
            try { Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    private JsonObject read(Path path) throws IOException {
        if (Files.size(path) > 2_000_000) throw new IOException("Profile exceeds 2 MB");
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("modules") || !root.get("modules").isJsonObject()) throw new IOException("Invalid profile");
            return root;
        } catch (RuntimeException ex) { throw new IOException("Invalid profile JSON", ex); }
    }
    public void save(String name) throws IOException { write(directory.resolve(safeName(name)+".json"), config.portableSnapshot()); }
    public void load(String name) throws IOException {
        load(name, false);
    }

    private void load(String name, boolean serverProfile) throws IOException {
        try {
            Path path = directory.resolve(safeName(name)+".json");
            JsonObject saved = read(path);
            boolean needsMigration = ConfigManager.needsCompactDefaults(saved);
            if (serverProfile) config.beginServerProfile(saved);
            else config.restorePortable(saved);
            if (needsMigration) {
                try {
                    write(path, config.portableSnapshot());
                } catch (IOException | RuntimeException failure) {
                    net.aero.visuals.AeroVisuals.LOGGER.warn("Could not persist migrated profile {}", path, failure);
                }
            }
        } catch (RuntimeException ex) {
            throw new IOException("Invalid profile: " + name, ex);
        }
    }
    public Path exportProfile(String name) throws IOException {
        Path output = directory.resolve("exports").resolve(safeName(name)+".json");
        write(output, config.portableSnapshot()); return output;
    }
    public void importProfile(String name) throws IOException {
        String safe = safeName(name);
        JsonObject root = read(directory.resolve("imports").resolve(safe+".json"));
        write(directory.resolve(safe+".json"), root);
    }
    public void bind(String name) throws IOException {
        if (server.isEmpty()) throw new IOException("Join a server first");
        save(name);
        updateBinding(safeName(name));
        failedServer = null;
    }

    public void unbind() throws IOException {
        if (bindings.containsKey(server)) updateBinding(null);
        config.endServerProfile();
        failedServer = null;
    }

    private void updateBinding(String name) throws IOException {
        Properties updated = new Properties();
        updated.putAll(bindings);
        if (name == null) updated.remove(server);
        else updated.setProperty(server, name);
        persistBindings(updated);
        bindings.clear();
        bindings.putAll(updated);
    }
    private void persistBindings(Properties updated) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve("servers.properties");
        Path tmp = Files.createTempFile(directory, "aero-bindings-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(tmp)) {
                updated.store(writer, "Local server/profile bindings");
            }
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    public void switchServer(String next) throws IOException {
        next = next == null ? "" : next;
        boolean retrying = Objects.equals(failedServer, next);
        if (Objects.equals(server, next) && !retrying) return;
        if (retrying && nanoTime.getAsLong() - retryAfterNanos < 0) return;

        config.endServerProfile();
        server = next;
        failedServer = null;
        String profile = bindings.getProperty(next);
        if (profile == null) return;

        try {
            load(profile, true);
        } catch (IOException | RuntimeException failure) {
            failedServer = next;
            retryAfterNanos = nanoTime.getAsLong() + 5_000_000_000L;
            if (failure instanceof IOException io) throw io;
            throw new IOException("Could not switch server profile", failure);
        }
    }
    public void close() { config.endServerProfile(); }
    public Path directory() { return directory; }
}
