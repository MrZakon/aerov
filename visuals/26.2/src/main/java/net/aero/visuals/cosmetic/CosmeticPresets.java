package net.aero.visuals.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aero.visuals.AeroVisuals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CosmeticPresets {
    private final Path file;
    private final Map<String, Map<String, Integer>> presets = new LinkedHashMap<>();

    public CosmeticPresets(Path file) {
        this.file = file;
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 65_536) return;
            JsonObject data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var entry : data.entrySet()) {
                if (entry.getKey().isBlank() || entry.getKey().length() > 40 || !entry.getValue().isJsonObject()) continue;
                presets.put(entry.getKey(), CosmeticLoadout.decode(entry.getValue().getAsJsonObject()));
                if (presets.size() == 24) break;
            }
        } catch (IOException | RuntimeException failure) {
            AeroVisuals.LOGGER.warn("Could not read cosmetic presets", failure);
        }
    }

    public Map<String, Map<String, Integer>> entries() {
        return Collections.unmodifiableMap(presets);
    }

    public void saveNew(String name, Map<String, Integer> equipped) throws IOException {
        name = name.strip();
        if (name.isEmpty() || name.length() > 40 || presets.size() >= 24
                || presets.keySet().stream().anyMatch(name::equalsIgnoreCase)) {
            throw new IllegalArgumentException("Invalid cosmetic preset name or limit reached");
        }
        var updated = new LinkedHashMap<>(presets);
        updated.put(name, Map.copyOf(equipped));
        write(updated);
    }

    public void remove(String name) throws IOException {
        var updated = new LinkedHashMap<>(presets);
        if (updated.remove(name) != null) write(updated);
    }

    private void write(Map<String, Map<String, Integer>> updated) throws IOException {
        JsonObject data = new JsonObject();
        updated.forEach((name, pieces) -> {
            JsonObject outfit = new JsonObject();
            pieces.forEach(outfit::addProperty);
            data.add(name, outfit);
        });
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "aero-outfit-", ".tmp");
        try {
            Files.writeString(temporary, data.toString());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            presets.clear();
            presets.putAll(updated);
        } finally {
            try { Files.deleteIfExists(temporary); }
            catch (IOException failure) { AeroVisuals.LOGGER.warn("Could not remove temporary cosmetic preset file", failure); }
        }
    }
}

