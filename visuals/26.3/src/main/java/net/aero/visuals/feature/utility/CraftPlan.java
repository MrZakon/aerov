package net.aero.visuals.feature.utility;

import java.util.*;
import java.util.function.Function;

/** Allocates each existing item once and carries surplus from whole recipe batches into later steps. */
public final class CraftPlan {
    public record Ingredient(List<String> options, int quantity) {
        public Ingredient { options = List.copyOf(options); }
    }
    public record Recipe(String id, String result, int output, List<Ingredient> inputs) {
        public Recipe { inputs = List.copyOf(inputs); }
    }
    public record Result(Map<String, Integer> remaining, Map<String, Integer> missing) {}
    private static final int MAX_DEPTH = 8, MAX_ATTEMPTS = 2048, MAX_ALTERNATIVES = 16;
    private final Map<String, List<Recipe>> recipes;
    private final Function<String, List<String>> resolveTag;
    private final Map<String, List<String>> tags = new HashMap<>();

    public CraftPlan(Map<String, List<Recipe>> recipes, Function<String, List<String>> resolveTag) {
        this.recipes = recipes;
        this.resolveTag = resolveTag;
    }

    public Result estimate(Map<String, Integer> targets, Map<String, Integer> inventory) {
        var state = new State(new HashMap<>(inventory), new LinkedHashMap<>(), 0);
        var remaining = new LinkedHashMap<String, Integer>();
        for (var target : targets.entrySet()) {
            int needed = target.getValue() - take(state.stock, target.getKey(), target.getValue());
            if (needed > 0) remaining.put(target.getKey(), needed);
        }
        for (var target : remaining.entrySet()) {
            state = require(state, target.getKey(), target.getValue(), new HashSet<>(), 0, new int[]{MAX_ATTEMPTS});
        }
        return new Result(Collections.unmodifiableMap(remaining), Collections.unmodifiableMap(state.missing));
    }

    private record State(Map<String, Integer> stock, LinkedHashMap<String, Integer> missing, int steps) {
        private State next() { return new State(new HashMap<>(stock), new LinkedHashMap<>(missing), steps + 1); }
        private long cost() {
            long count = 0;
            for (int amount : missing.values()) count += amount;
            return count;
        }
    }

    private State require(State state, String item, int amount, Set<String> path, int depth, int[] attempts) {
        amount -= take(state.stock, item, amount);
        if (amount == 0) return state;
        if (depth >= MAX_DEPTH || attempts[0] <= 0 || !path.add(item)) return shortage(state, item, amount);
        try {
            State best = null;
            var variants = recipes.getOrDefault(item, List.of());
            for (var recipe : variants) {
                if (--attempts[0] < 0) break;
                int batches = Math.ceilDiv(amount, recipe.output());
                State candidate = state.next();
                boolean valid = true;
                for (var ingredient : recipe.inputs()) {
                    int needed = batches * ingredient.quantity();
                    var options = alternatives(ingredient);
                    if (options.isEmpty()) { valid = false; break; }
                    for (var option : options) needed -= take(candidate.stock, option, needed);
                    if (needed == 0) continue;
                    if (variants.size() > 1 && reversiblePacking(recipe, ingredient)) { valid = false; break; }
                    State bestIngredient = null;
                    int considered = 0;
                    for (var option : options) {
                        if (path.contains(option)) continue;
                        if (++considered > MAX_ALTERNATIVES || attempts[0] <= 0) break;
                        var optionPlan = require(candidate.next(), option, needed, path, depth + 1, attempts);
                        bestIngredient = better(bestIngredient, optionPlan);
                        if (bestIngredient.cost() == candidate.cost()) break;
                    }
                    if (bestIngredient == null) { valid = false; break; }
                    candidate = bestIngredient;
                }
                if (!valid) continue;
                int surplus = batches * recipe.output() - amount;
                if (surplus > 0) candidate.stock.merge(item, surplus, Integer::sum);
                best = better(best, candidate);
                if (best.cost() == state.cost()) return best;
            }
            return best == null ? shortage(state, item, amount) : best;
        } finally { path.remove(item); }
    }

    private static State better(State best, State candidate) {
        if (best == null) return candidate;
        long a = best.cost(), b = candidate.cost();
        return b < a || b == a && candidate.steps < best.steps ? candidate : best;
    }

    private boolean reversiblePacking(Recipe recipe, Ingredient input) {
        if (recipe.inputs().size() != 1 || input.options().size() != 1) return false;
        for (var reverse : recipes.getOrDefault(input.options().getFirst(), List.of())) {
            if (reverse.inputs().size() != 1) continue;
            var reverseInput = reverse.inputs().getFirst();
            if (reverseInput.options().equals(List.of(recipe.result()))
                    && reverse.output() * recipe.output() == reverseInput.quantity() * input.quantity()) return true;
        }
        return false;
    }
    private List<String> alternatives(Ingredient ingredient) {
        if (ingredient.options().size() == 1 && !ingredient.options().getFirst().startsWith("#")) return ingredient.options();
        var result = new LinkedHashSet<String>();
        for (String token : ingredient.options()) {
            if (token.startsWith("#")) result.addAll(tags.computeIfAbsent(token.substring(1), resolveTag));
            else result.add(token);
        }
        return List.copyOf(result);
    }
    private static int take(Map<String, Integer> items, String id, int count) {
        if (count <= 0) return 0;
        int available = items.getOrDefault(id, 0), used = Math.min(available, count);
        if (used == available) items.remove(id);
        else items.put(id, available - used);
        return used;
    }
    private static State shortage(State state, String id, int count) {
        state.missing.merge(id, count, Integer::sum);
        return state;
    }
}
