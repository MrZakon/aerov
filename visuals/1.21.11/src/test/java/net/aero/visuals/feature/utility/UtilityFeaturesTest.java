package net.aero.visuals.feature.utility;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UtilityFeaturesTest {
    @Test void reservesExistingGoalsAndSharesRecipeSurplus() {
        var recipes = Map.of(
                "rocket", List.of(recipe("rocket", 3, ingredient("paper", 1), ingredient("gunpowder", 1))),
                "paper", List.of(recipe("paper", 3, ingredient("cane", 3))),
                "book", List.of(recipe("book", 1, ingredient("paper", 3))));
        var planner = new CraftPlan(recipes, ignored -> List.of());
        var targets = new LinkedHashMap<String, Integer>();
        targets.put("rocket", 4);
        targets.put("book", 1);
        targets.put("paper", 1);
        var estimate = planner.estimate(targets, Map.of("rocket", 1, "paper", 1, "gunpowder", 1, "cane", 5));
        assertEquals(Map.of("rocket", 3, "book", 1), estimate.remaining());
        assertEquals(Map.of("cane", 1), estimate.missing());
    }

    @Test void reversiblePackingUsesExistingBlocksButDoesNotSuggestGatheringThem() {
        var recipes = Map.of(
                "gold", List.of(recipe("gold", 9, ingredient("gold_block", 1)), recipe("gold", 1, ingredient("raw_gold", 1))),
                "gold_block", List.of(recipe("gold_block", 1, ingredient("gold", 9))));
        var planner = new CraftPlan(recipes, ignored -> List.of());
        assertEquals(Map.of("raw_gold", 8), planner.estimate(Map.of("gold", 8), Map.of()).missing());
        assertTrue(planner.estimate(Map.of("gold", 8), Map.of("gold_block", 1)).missing().isEmpty());
    }

    @Test void ingredientTagAcceptsMixedInventory() {
        var plank = new CraftPlan.Ingredient(List.of("#planks"), 2);
        var planner = new CraftPlan(Map.of("stick", List.of(recipe("stick", 4, plank))), ignored -> List.of("birch", "oak"));
        assertTrue(planner.estimate(Map.of("stick", 4), Map.of("oak", 1, "birch", 1)).missing().isEmpty());
    }

    @Test void petSleepsGreetsOnlyOnReturnAndWakesInWater() {
        var pet = new PetBehavior();
        for (int i = 0; i < 100; i++) pet.tick(132, false, false, false, false, true, true);
        assertEquals(PetBehavior.Mood.SLEEP, pet.mood());
        pet.tick(132, true, false, false, false, true, false);
        pet.tick(132, true, false, false, false, true, true);
        assertEquals(PetBehavior.Mood.GREETING, pet.mood());
        pet.tick(132, true, true, true, true, true, true);
        assertEquals(PetBehavior.Mood.WET, pet.mood());
        pet.reset();
        assertEquals(1f, pet.height(1));
        assertEquals(0f, pet.bob(1));
    }

    private static CraftPlan.Ingredient ingredient(String id, int quantity) { return new CraftPlan.Ingredient(List.of(id), quantity); }
    private static CraftPlan.Recipe recipe(String result, int output, CraftPlan.Ingredient... inputs) {
        return new CraftPlan.Recipe(result, result, output, List.of(inputs));
    }
}
