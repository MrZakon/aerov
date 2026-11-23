package net.aero.visuals.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Persistent local selection of cosmetic IDs. IDs are intentionally unrestricted. */
public final class CosmeticSelection {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<String, Integer> selected = new HashMap<>();
    public CosmeticSelection(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public synchronized void load() {
        if (!Files.exists(file)) return;
        try {
            Map<String, Integer> values = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Integer>>() {}.getType());
            if (values != null) {
                selected.clear();
                values.forEach((type, id) -> {
                    if (type != null && !type.isBlank() && id != null) selected.put(canonicalType(type), id);
                });
            }
        } catch (IOException | RuntimeException ignored) { }
    }
    public synchronized void set(String type, int id) { if (type == null || type.isBlank()) return; selected.put(canonicalType(type), id); save(); }
    public synchronized void clear(String type) { if (type == null || type.isBlank()) return; if (selected.remove(canonicalType(type)) != null) save(); }
    public synchronized int get(String type) { return selected.getOrDefault(canonicalType(type), -1); }
    public synchronized void replaceRemote(Map<String, Integer> remote) {
        if (remote == null) return;
        selected.keySet().removeIf(CosmeticSelection::isWebsiteType);
        remote.forEach((type, id) -> {
            if (isWebsiteType(type) && id != null && id >= 0) selected.put(canonicalType(type), id);
        });
        save();
    }
    public synchronized Map<String,Integer> all() { return Map.copyOf(selected); }
    private static String canonicalType(String type) {
        return isWebsiteType(type) ? type.toLowerCase(java.util.Locale.ROOT) : type;
    }
    private static boolean isWebsiteType(String type) {
        return switch (String.valueOf(type).toLowerCase(java.util.Locale.ROOT)) {
            case "cape", "wings", "bodywear", "hat", "pet", "graffiti" -> true;
            default -> false;
        };
    }
    private void save() {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "aero-cosmetics-", ".tmp");
            Files.writeString(temporary, GSON.toJson(selected), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        } finally {
            try { if (temporary != null) Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
