package net.aero.visuals.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Minimal Bedrock geometry reader used by AeroVisuals cosmetics. */
public final class BedrockGeometryLoader {
    public record Cube(float x, float y, float z, float sx, float sy, float sz,
                       float rx, float ry, float rz, float u, float v, float uw, float vh, float px,float py,float pz,JsonObject faces,float inflate,boolean mirror) {}
    public record Bone(String name, String parent, float px, float py, float pz,
                       float rx, float ry, float rz, List<Cube> cubes) {}
    public record Attachment(float x,float y,float z,float scale,float yaw,float pitch,float roll) {}
    public record Geometry(String identifier, float width, float height, List<Bone> bones,Attachment attachment) {}

    private BedrockGeometryLoader() {}

    public static Geometry load(String resourcePath) throws IOException {
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        ClassLoader loader = BedrockGeometryLoader.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(normalized)) {
            if (in == null) throw new IOException("Missing cosmetic geometry: " + resourcePath);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Attachment attachment=new Attachment(num(root,"x",0),num(root,"y",0),num(root,"z",0),
                    Math.max(.01f,num(root,"scale",1)),num(root,"yaw",0),num(root,"pitch",0),num(root,"roll",0));
            if(root.has("model")){JsonElement nested=root.get("model");if(nested.isJsonPrimitive())root=JsonParser.parseString(nested.getAsString()).getAsJsonObject();else if(nested.isJsonObject())root=nested.getAsJsonObject();}
            JsonArray geometries = root.has("minecraft:geometry") ? root.getAsJsonArray("minecraft:geometry") : new JsonArray();
            if (geometries.isEmpty()) throw new IOException("No minecraft:geometry in " + resourcePath);
            JsonObject model = geometries.get(0).getAsJsonObject();
            JsonObject description = model.has("description") ? model.getAsJsonObject("description") : new JsonObject();
            String id = str(description, "identifier", "lunacy:unknown");
            float width = Math.max(1,num(description, "texture_width", 64));
            float height = Math.max(1,num(description, "texture_height", 64));
            List<Bone> bones = new ArrayList<>();
            JsonArray boneArray = model.has("bones") ? model.getAsJsonArray("bones") : new JsonArray();
            for (JsonElement element : boneArray) {
                if (!element.isJsonObject()) continue;
                JsonObject b = element.getAsJsonObject();
                List<Cube> cubes = new ArrayList<>();
                if (b.has("cubes") && b.get("cubes").isJsonArray()) {
                    for (JsonElement cubeElement : b.getAsJsonArray("cubes")) {
                        if (!cubeElement.isJsonObject()) continue;
                        JsonObject c = cubeElement.getAsJsonObject();
                        JsonArray origin = array(c, "origin", 3);
                        JsonArray size = array(c, "size", 3);
                        JsonArray uv = array(c, "uv", 2);
                        JsonArray pivot = c.has("pivot")?array(c, "pivot", 3):origin;
                        JsonArray rotation = array(c, "rotation", 3);
                        cubes.add(new Cube(
                                value(origin, 0), value(origin, 1), value(origin, 2),
                                value(size, 0), value(size, 1), value(size, 2),
                                value(rotation, 0), value(rotation, 1), value(rotation, 2),
                                value(uv, 0), value(uv, 1), value(size, 0), value(size, 1),value(pivot,0),value(pivot,1),value(pivot,2),c.has("uv")&&c.get("uv").isJsonObject()?c.getAsJsonObject("uv"):new JsonObject(),
                                num(c,"inflate",num(b,"inflate",0)),c.has("mirror")?c.get("mirror").getAsBoolean():b.has("mirror")&&b.get("mirror").getAsBoolean()));
                    }
                }
                JsonArray pivot = array(b, "pivot", 3);
                JsonArray rotation = array(b, "rotation", 3);
                bones.add(new Bone(str(b, "name", "bone"), str(b, "parent", ""),
                        value(pivot, 0), value(pivot, 1), value(pivot, 2),
                        value(rotation, 0), value(rotation, 1), value(rotation, 2),
                        Collections.unmodifiableList(cubes)));
            }
            return new Geometry(id, width, height, Collections.unmodifiableList(bones),attachment);
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }
    private static float num(JsonObject o, String key, float fallback) {
        float value=o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsFloat() : fallback;
        return Float.isFinite(value)?value:fallback;
    }
    private static JsonArray array(JsonObject o, String key, int size) {
        if (o.has(key) && o.get(key).isJsonArray()) return o.getAsJsonArray(key);
        JsonArray result = new JsonArray(); for (int i = 0; i < size; i++) result.add(0); return result;
    }
    private static float value(JsonArray array, int index) {
        if (index >= array.size()) return 0f;
        return finite(array.get(index));
    }

    static float finite(JsonElement element) {
        float value = element.getAsFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite cosmetic geometry coordinate");
        return value;
    }
}
