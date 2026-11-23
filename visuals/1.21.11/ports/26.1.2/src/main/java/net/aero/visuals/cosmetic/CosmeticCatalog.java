package net.aero.visuals.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads the bundled cosmetic catalog without requiring a server or external files. */
public final class CosmeticCatalog {
    public record Entry(int id, String type, String name, String resourcePath, boolean hasModel) {}
    private final List<Entry> entries;

    private CosmeticCatalog(List<Entry> entries) { this.entries = List.copyOf(entries); }

    public static CosmeticCatalog load() {
        List<Entry> result = new ArrayList<>();
        try (InputStream stream = CosmeticCatalog.class.getClassLoader().getResourceAsStream("assets/aerovisuals/cosmetics/catalog.json")) {
            if (stream == null) return new CosmeticCatalog(result);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray items = root.has("items") && root.get("items").isJsonArray() ? root.getAsJsonArray("items") : new JsonArray();
            for (JsonElement element : items) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (!item.has("id") || !item.has("type")) continue;
                String type = item.get("type").getAsString().toLowerCase(java.util.Locale.ROOT);
                if (!switch (type) { case "cape", "wings", "bodywear", "hat", "pet", "graffiti" -> true; default -> false; }) continue;
                result.add(new Entry(item.get("id").getAsInt(), item.get("type").getAsString(),
                        item.has("name") ? item.get("name").getAsString() : "", item.has("resourcePath") ? item.get("resourcePath").getAsString() : "",
                        item.has("has_model") && item.get("has_model").getAsBoolean()));
            }
        } catch (Exception ignored) {
            // Cosmetics are optional; a malformed catalog must never stop the client.
        }
        return new CosmeticCatalog(result);
    }

    public List<Entry> entries() { return Collections.unmodifiableList(entries); }
    public long countByType(String type) { return entries.stream().filter(e -> e.type().equals(type)).count(); }

    public BedrockGeometryLoader.Geometry loadGeometry(Entry entry) throws java.io.IOException {
        return BedrockGeometryLoader.load(entry.resourcePath() + "/model.geo.json");
    }
}
