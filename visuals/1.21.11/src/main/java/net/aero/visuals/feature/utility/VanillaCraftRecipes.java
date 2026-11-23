package net.aero.visuals.feature.utility;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;

public final class VanillaCraftRecipes {
    private VanillaCraftRecipes() {}

    public static Map<String, List<CraftPlan.Recipe>> load(MinecraftClient client) {
        var recipes = new HashMap<String, List<CraftPlan.Recipe>>();
        client.getDefaultResourcePack().findResources(ResourceType.SERVER_DATA, "minecraft", "recipe", (id, source) -> {
            if (!id.getPath().endsWith(".json")) return;
            try (var reader = new InputStreamReader(source.get(), StandardCharsets.UTF_8)) {
                var recipe = parse(id.toString(), JsonParser.parseReader(reader).getAsJsonObject());
                if (recipe != null) recipes.computeIfAbsent(recipe.result(), ignored -> new ArrayList<>()).add(recipe);
            } catch (java.io.IOException | RuntimeException ignored) {
                // Recipe types with dynamic results cannot be expanded into a material list.
            }
        });
        recipes.replaceAll((id, list) -> list.stream().sorted(Comparator.comparing(CraftPlan.Recipe::id)).toList());
        return Map.copyOf(recipes);
    }

    static CraftPlan.Recipe parse(String id, JsonObject recipe) {
        String type = recipe.get("type").getAsString();
        var inputs = new LinkedHashMap<List<String>, Integer>();
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                var key = recipe.getAsJsonObject("key");
                for (var row : recipe.getAsJsonArray("pattern")) {
                    String cells = row.getAsString();
                    for (int i = 0; i < cells.length(); i++) {
                        char cell = cells.charAt(i);
                        if (cell != ' ') add(inputs, key.get(String.valueOf(cell)));
                    }
                }
            }
            case "minecraft:crafting_shapeless" -> {
                for (var item : recipe.getAsJsonArray("ingredients")) add(inputs, item);
            }
            case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:stonecutting" -> add(inputs, recipe.get("ingredient"));
            case "minecraft:smithing_transform" -> {
                add(inputs, recipe.get("template")); add(inputs, recipe.get("base")); add(inputs, recipe.get("addition"));
            }
            default -> { return null; }
        }
        JsonObject output = recipe.getAsJsonObject("result");
        String item = output.get("id").getAsString();
        int count = output.has("count") ? output.get("count").getAsInt() : 1;
        if (count <= 0 || inputs.isEmpty()) return null;
        return new CraftPlan.Recipe(id, item, count, inputs.entrySet().stream()
                .map(entry -> new CraftPlan.Ingredient(entry.getKey(), entry.getValue())).toList());
    }

    private static void add(Map<List<String>, Integer> inputs, JsonElement value) {
        var alternatives = new LinkedHashSet<String>();
        decode(value, alternatives);
        if (alternatives.isEmpty()) throw new IllegalArgumentException("Empty recipe ingredient");
        inputs.merge(List.copyOf(alternatives), 1, Integer::sum);
    }
    private static void decode(JsonElement value, Set<String> target) {
        if (value.isJsonArray()) {
            for (var element : value.getAsJsonArray()) decode(element, target);
        } else if (value.isJsonPrimitive()) target.add(value.getAsString());
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("item")) target.add(object.get("item").getAsString());
            else if (object.has("tag")) target.add("#" + object.get("tag").getAsString());
        }
    }
}
