package net.aero.visuals.cosmetic;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** Resolves bundled graffiti textures by cosmetic id. */
public final class GraffitiCatalog {
    private static final Map<Integer, String> TEXTURES = load();
    private GraffitiCatalog() {}
    private static Map<Integer, String> load() {
        Map<Integer, String> result = new HashMap<>();
        try (var in = GraffitiCatalog.class.getClassLoader().getResourceAsStream("assets/aerovisuals/cosmetics/graffiti-index.json")) {
            if (in == null) return result;
            var root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var item : root.getAsJsonArray("items")) result.put(item.getAsJsonObject().get("id").getAsInt(), item.getAsJsonObject().get("texturePath").getAsString());
        } catch (Exception ignored) {}
        return result;
    }
    public static Identifier texture(int id) {
        String path = TEXTURES.get(id);
        return path == null ? null : Identifier.fromNamespaceAndPath("aerovisuals", path.startsWith("assets/aerovisuals/") ? path.substring("assets/aerovisuals/".length()) : path);
    }
}
