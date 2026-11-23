package net.aero.visuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.module.setting.Setting;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Версионированная конфигурация с атомарной заменой файла и резервированием
 * повреждённого JSON.
 */
public final class ConfigManager {
    public static final int SCHEMA_VERSION = 6;
    private static final long SAVE_DEBOUNCE_NANOS = 500_000_000L;
    private static final Executor SAVE_EXECUTOR = task -> Thread.ofVirtual().name("AeroVisuals config save").start(task);

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path configPath;
    private final ModuleManager modules;
    private final ClientSettings clientSettings;
    private final Map<String, HudPosition> hudPositions = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean dirty;
    private long dirtySinceNanos;
    private boolean loading;
    private JsonObject serverProfileBase;
    private CompletableFuture<Boolean> pendingSave;

    private boolean changeTrackingBound = false;

    public ConfigManager(Path configPath, ModuleManager modules) {
        this(configPath, modules, null);
    }

    public ConfigManager(Path configPath, ModuleManager modules, ClientSettings clientSettings) {
        this.configPath = Objects.requireNonNull(configPath, "configPath").toAbsolutePath().normalize();
        this.modules = Objects.requireNonNull(modules, "modules");
        this.clientSettings = clientSettings;
    }

    public void bindChangeTracking() {
        if (changeTrackingBound) {
            return;
        }
        changeTrackingBound = true;
        modules.all().forEach(module -> {
            module.onStateChanged(ignored -> markDirty());
            module.settings().forEach(setting -> setting.onChanged(ignored -> markDirty()));
        });
        if (clientSettings != null) {
            clientSettings.all().forEach(setting -> setting.onChanged(ignored -> markDirty()));
        }
    }

    public void load() {
        finishPendingSave();
        JsonObject previous = snapshot();
        boolean compactMigration = false;
        loading = true;
        try {
            if (!Files.isRegularFile(configPath)) {
                bindChangeTracking();
                markDirty();
                flush();
                return;
            }

            try {
                JsonObject saved = readConfigObject(configPath);
                boolean needsMigration = needsCompactDefaults(saved);
                if (needsMigration) backupBeforeCompactDefaults();
                readRoot(saved);
                compactMigration = needsMigration;
            } catch (Exception exception) {
                readRoot(previous);
                backupCorruptedFile();
                AeroVisuals.LOGGER.error("Could not read {}; defaults will be used", configPath, exception);
            }
            bindChangeTracking();
        } finally {
            loading = false;
        }
        if (compactMigration) { markDirty(); flush(); }
    }

    private static JsonObject readConfigObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) throw new IOException("Config root is not a JSON object");
            return root.getAsJsonObject();
        }
    }

    public void tick() {
        if (pendingSave != null) {
            if (!pendingSave.isDone()) return;
            finishPendingSave();
        }
        if (dirty && System.nanoTime() - dirtySinceNanos >= SAVE_DEBOUNCE_NANOS) {
            // Capture live settings on the client thread; only the detached JSON goes to the writer.
            JsonObject root = persistentRoot();
            pendingSave = CompletableFuture.supplyAsync(() -> writeFile(root), SAVE_EXECUTOR);
            dirty = false;
        }
    }

    public void markDirty() {
        if (loading) {
            return;
        }
        if (!dirty) {
            dirtySinceNanos = System.nanoTime();
        }
        dirty = true;
    }

    public void flush() {
        flushAndCheck();
    }

    public boolean flushAndCheck() {
        // Wait for the older snapshot before committing a newer explicit save.
        finishPendingSave();
        dirty = !writeFile(persistentRoot());
        if (dirty) dirtySinceNanos = System.nanoTime();
        return !dirty;
    }

    private void finishPendingSave() {
        if (pendingSave == null) return;
        boolean saved = false;
        try {
            saved = pendingSave.join();
        } catch (CompletionException exception) {
            AeroVisuals.LOGGER.error("Could not save configuration to {}", configPath, exception.getCause());
        } finally {
            pendingSave = null;
        }
        if (!saved) {
            dirty = true;
            dirtySinceNanos = System.nanoTime();
        }
    }

    private boolean writeFile(JsonObject root) {
        Path parent = configPath.getParent();
        long uniqueTime = System.nanoTime();
        Path temporary = configPath.resolveSibling(configPath.getFileName() + "." + uniqueTime + ".tmp");
        
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            atomicReplace(temporary, configPath);
            return true;
        } catch (IOException exception) {
            AeroVisuals.LOGGER.error("Could not save configuration to {}", configPath, exception);
            return false;
        } finally {
            try { Files.deleteIfExists(temporary); } catch (Throwable ignored) {}
        }
    }

    public HudPosition hudPosition(String id, HudPosition fallback) {
        return hudPositions.getOrDefault(id, fallback);
    }

    public void setHudPosition(String id, HudPosition position) {
        hudPositions.put(id, position);
        markDirty();
    }

    public void clearHudPosition(String id) {
        if (!loading && hudPositions.remove(id) != null) markDirty();
    }

    public JsonObject snapshot() { return writeRoot().deepCopy(); }

    public JsonObject portableSnapshot() {
        JsonObject root = snapshot();
        var entries = root.getAsJsonObject("modules");
        entries.remove("cosmetics");
        entries.remove("pet");
        return root;
    }

    public void restore(JsonObject root) { restore(root, false); }
    public void restorePortable(JsonObject root) { restore(root, true); }
    private void restore(JsonObject root, boolean preserveAccount) {
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject())
            throw new IllegalArgumentException("Profile must contain modules");
        JsonObject previous = snapshot();
        loading = true;
        try {
            readRoot(root, preserveAccount);
        } catch (RuntimeException failure) {
            readRoot(previous, preserveAccount);
            throw failure;
        } finally { loading = false; }
        markDirty();
    }

    private void readRoot(JsonObject root) { readRoot(root, false); }
    private void readRoot(JsonObject root, boolean preserveAccount) {
        int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
        if (schema > SCHEMA_VERSION) {
            AeroVisuals.LOGGER.warn("Config schema {} is newer than supported schema {}", schema, SCHEMA_VERSION);
        }

        JsonObject moduleObject = childObject(root, "modules");
        for (Module module : modules.all()) {
            if (preserveAccount && isAccountModule(module.id())) continue;
            JsonObject saved = childObject(moduleObject, module.id());
            for (Setting<?> setting : module.settings()) {
                if (!saved.has(setting.key())) {
                    continue;
                }
                if (schema < 4 && module.id().equals("watermark") && setting.key().equals("accent")) {
                    continue;
                }
                try {
                    setting.readJson(saved.get(setting.key()));
                } catch (RuntimeException exception) {
                    AeroVisuals.LOGGER.warn("Ignoring invalid {}.{} config value", module.id(), setting.key());
                }
            }
            if (schema < 6) {
                module.setEnabled(module.id().equals("wm_island"));
            } else if (saved.has("enabled")) {
                module.setEnabled(saved.get("enabled").getAsBoolean());
            }
            net.aero.visuals.gui.ClickGuiScreen.setKeybind(module.id(),
                    saved.has("keybind") ? saved.get("keybind").getAsInt() : 0);
        }

        if (root.has("menuKey")) {
            net.aero.visuals.gui.ClickGuiScreen.setMenuKey(root.get("menuKey").getAsInt());
        }

        // Armor HUD used to be forced on by the startup profile. Keep it opt-in after migration.
        if (schema < 4) {
            modules.find("armor_hud").ifPresent(module -> module.setEnabled(false));
        }

        if (clientSettings != null) {
            JsonObject clientObject = childObject(root, "client");
            for (Setting<?> setting : clientSettings.all()) {
                if (!clientObject.has(setting.key())) continue;
                // Schema 3 replaces the discarded light-reference UI with the premium green/violet system.
                // Keep behavioural preferences, but migrate legacy presentation values to the new defaults.
                if (schema < 3 && switch (setting.key()) {
                    case "theme", "accent", "menu_backdrop", "hud_style", "shader_preset" -> true;
                    default -> false;
                }) continue;
                try {
                    setting.readJson(clientObject.get(setting.key()));
                } catch (RuntimeException exception) {
                    AeroVisuals.LOGGER.warn("Ignoring invalid client.{} config value", setting.key());
                }
            }
        }

        // Saved coordinates replace the previous layout, including widgets omitted by this profile.
        hudPositions.clear();
        JsonObject hudObject = childObject(root, "hud");
        hudObject.entrySet().forEach(entry -> {
            try {
                JsonObject saved = entry.getValue().getAsJsonObject();
                HudAnchor anchor = HudAnchor.valueOf(getString(saved, "anchor", HudAnchor.TOP_LEFT.name()));
                hudPositions.put(entry.getKey(), new HudPosition(
                        anchor,
                        getDouble(saved, "x", 8.0),
                        getDouble(saved, "y", 8.0),
                        getDouble(saved, "scale", 1.0)
                ));
            } catch (RuntimeException exception) {
                AeroVisuals.LOGGER.warn("Ignoring invalid HUD position for {}", entry.getKey());
            }
        });
        if (schema < 6) applyCompactSizing(preserveAccount);
    }

    public static boolean needsCompactDefaults(JsonObject root) {
        return !root.has("schemaVersion") || root.get("schemaVersion").getAsInt() < 6;
    }

    private void applyCompactSizing(boolean preserveAccount) {
        for (Module module : modules.all()) {
            if (preserveAccount && isAccountModule(module.id())) continue;
            for (Setting<?> setting : module.settings()) {
                if (setting.key().equals("scale") && setting instanceof net.aero.visuals.module.setting.DoubleSetting scale
                        && scale.get() > scale.defaultValue()) scale.reset();
            }
        }
        if (clientSettings != null) {
            if (clientSettings.clickGuiScale().get() > clientSettings.clickGuiScale().defaultValue()) clientSettings.clickGuiScale().reset();
            if (clientSettings.clickGuiTextScale().get() > clientSettings.clickGuiTextScale().defaultValue()) clientSettings.clickGuiTextScale().reset();
        }
    }

    private void backupBeforeCompactDefaults() {
        Path backup = configPath.resolveSibling(configPath.getFileName() + ".pre-compact-v6.bak");
        if (Files.exists(backup)) return;
        try { Files.copy(configPath, backup); }
        catch (IOException exception) { AeroVisuals.LOGGER.warn("Could not back up configuration before compact defaults", exception); }
    }

    void beginServerProfile(JsonObject saved) {
        if (serverProfileBase != null) throw new IllegalStateException("Server profile already active");
        // Preserve the global values before applying the temporary profile.
        serverProfileBase = snapshot();
        try {
            restorePortable(saved);
        } catch (RuntimeException failure) {
            serverProfileBase = null;
            throw failure;
        }
    }

    void endServerProfile() {
        if (serverProfileBase == null) return;
        restorePortable(serverProfileBase);
        serverProfileBase = null;
    }

    @FunctionalInterface
    interface SavedChange {
        void run() throws IOException;
    }

    // An explicit global operation takes precedence for the current session.
    void commitGlobalChange(Runnable change, SavedChange afterSave) throws IOException {
        JsonObject previous = snapshot();
        JsonObject previousServerBase = serverProfileBase;
        boolean written = false;
        try {
            endServerProfile();
            change.run();
            written = flushAndCheck();
            if (!written) throw new IOException("Could not save global configuration");
            afterSave.run();
        } catch (IOException | RuntimeException failure) {
            serverProfileBase = previousServerBase;
            try {
                restore(previous);
                if (written && !flushAndCheck()) {
                    failure.addSuppressed(new IOException("Could not restore the previous configuration on disk"));
                }
            } catch (RuntimeException rollback) {
                if (rollback != failure) failure.addSuppressed(rollback);
            }
            throw failure;
        }
    }

    private JsonObject persistentRoot() {
        if (serverProfileBase == null) return writeRoot();
        JsonObject root = serverProfileBase.deepCopy();
        JsonObject entries = root.getAsJsonObject("modules");
        // Account preferences remain global while a server profile is active.
        for (Module module : modules.all()) {
            String id = module.id();
            if (isAccountModule(id)) entries.add(id, writeModule(module));
        }
        return root;
    }

    private static boolean isAccountModule(String id) {
        return id.equals("cosmetics") || id.equals("pet");
    }

    private static JsonObject writeModule(Module module) {
        JsonObject saved = new JsonObject();
        saved.addProperty("enabled", module.isConfiguredEnabled());
        int keybind = net.aero.visuals.gui.ClickGuiScreen.getKeybind(module);
        if (keybind != 0) saved.addProperty("keybind", keybind);
        for (Setting<?> setting : module.settings()) saved.add(setting.key(), setting.toJson());
        return saved;
    }

    private JsonObject writeRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("menuKey", net.aero.visuals.gui.ClickGuiScreen.menuKeyCode);

        JsonObject moduleObject = new JsonObject();
        for (Module module : modules.all()) moduleObject.add(module.id(), writeModule(module));
        root.add("modules", moduleObject);

        if (clientSettings != null) {
            JsonObject clientObject = new JsonObject();
            for (Setting<?> setting : clientSettings.all()) {
                clientObject.add(setting.key(), setting.toJson());
            }
            root.add("client", clientObject);
        }

        JsonObject hudObject = new JsonObject();
        hudPositions.forEach((id, position) -> {
            JsonObject saved = new JsonObject();
            saved.addProperty("anchor", position.anchor().name());
            saved.addProperty("x", position.x());
            saved.addProperty("y", position.y());
            saved.addProperty("scale", position.scale());
            hudObject.add(id, saved);
        });
        root.add("hud", hudObject);
        return root;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public Path getProfilesDir() {
        return configPath.resolveSibling("lunacy_visuals_profiles");
    }

    public java.util.List<String> listProfiles() {
        Path dir = getProfilesDir();
        if (!Files.isDirectory(dir)) return java.util.Collections.emptyList();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replaceFirst("\\.json$", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return java.util.Collections.emptyList();
        }
    }

    public boolean saveProfile(String name) {
        if (name == null || name.isBlank()) return false;
        String clean = name.trim().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
        Path dir = getProfilesDir();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(clean + ".json");
            Path temp = Files.createTempFile(dir, "aero-profile-", ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    gson.toJson(writeRoot(), writer);
                }
                atomicReplace(temp, target);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        } catch (Exception ex) {
            AeroVisuals.LOGGER.error("Failed to save profile {}", clean, ex);
            return false;
        }
    }

    public boolean loadProfile(String name) {
        if (name == null || name.isBlank()) return false;
        String clean = name.trim().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
        Path target = getProfilesDir().resolve(clean + ".json");
        if (!Files.isRegularFile(target)) return false;
        try {
            JsonObject saved = readConfigObject(target);
            boolean compactMigration = needsCompactDefaults(saved);
            commitGlobalChange(() -> restore(saved), () -> {});
            if (compactMigration) saveProfile(name);
            return true;
        } catch (Exception ex) {
            AeroVisuals.LOGGER.error("Failed to load profile {}", clean, ex);
            return false;
        }
    }

    public boolean resetToDefaults() {
        try {
            commitGlobalChange(this::applyDefaults, () -> {});
            return true;
        } catch (IOException | RuntimeException failure) {
            AeroVisuals.LOGGER.error("Could not reset the global configuration", failure);
            return false;
        }
    }

    private void applyDefaults() {
        loading = true;
        try {
            hudPositions.clear();
            for (Module module : modules.all()) {
                module.setEnabled(module.id().equals("wm_island"));
                for (Setting<?> setting : module.settings()) {
                    setting.reset();
                }
                net.aero.visuals.gui.ClickGuiScreen.setKeybind(module.id(), 0);
            }
            if (clientSettings != null) {
                for (Setting<?> setting : clientSettings.all()) {
                    setting.reset();
                }
            }
            net.aero.visuals.gui.ClickGuiScreen.setMenuKey(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        } finally {
            loading = false;
        }
        markDirty();
    }

    private void backupCorruptedFile() {
        if (!Files.isRegularFile(configPath)) {
            return;
        }
        String suffix = ".corrupt-" + Instant.now().toEpochMilli() + ".json";
        Path backup = configPath.resolveSibling("lunacy_visuals" + suffix);
        try {
            Files.move(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            AeroVisuals.LOGGER.error("Could not back up corrupted config {}", configPath, exception);
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject childObject(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            return new JsonObject();
        }
        return parent.getAsJsonObject(key);
    }

    private static String getString(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private static double getDouble(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }
}
