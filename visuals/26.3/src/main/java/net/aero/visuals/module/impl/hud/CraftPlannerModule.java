package net.aero.visuals.module.impl.hud;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.aero.visuals.command.ClientCommandHandler;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.feature.utility.CraftPlan;
import net.aero.visuals.feature.utility.VanillaCraftRecipes;
import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

public final class CraftPlannerModule extends HudWidgetModule {
    private static final int LIMIT = 12;
    private static final String HELP = ".craft add <количество> <предмет> · .craft list · .craft remove <ID> · .craft clear";
    private final Map<String, Integer> targets = new LinkedHashMap<>();
    private CompletableFuture<Map<String, List<CraftPlan.Recipe>>> loading;
    private Map<String, List<CraftPlan.Recipe>> recipes;
    private CompletableFuture<CraftPlan.Result> calculating;
    private CraftPlan planner;
    private Object world;
    private Map<String, Integer> lastStock = Map.of();
    private CraftPlan.Result result;
    private int ticks, generation, submittedGeneration, resultGeneration = -1;
    private List<String> lines = List.of();
    private String status = "Добавь цель в настройках плана крафта";

    public CraftPlannerModule() {
        super("craft_planner", "План крафта", "Список целей и недостающих ресурсов по обычным рецептам Minecraft с учётом рюкзака.", new HudPosition(HudAnchor.TOP_RIGHT, 8, 153, 1));
    }

    @Subscribe private void tick(TickEvent.Client event) {
        var client = event.client();
        if (world != client.level) {
            world = client.level;
            planner = null;
            invalidate();
        }
        if (client.player == null || world == null || targets.isEmpty()) return;
        if (recipes == null) {
            if (loading == null) {
                status = "Читаю рецепты Minecraft…";
                loading = CompletableFuture.supplyAsync(() -> VanillaCraftRecipes.load(client), Thread::startVirtualThread)
                        .exceptionally(failure -> Map.of());
            }
            if (!loading.isDone()) return;
            recipes = loading.getNow(Map.of());
            loading = null;
            if (recipes.isEmpty()) status = "Рецепты недоступны · показываю цели";
        }
        if (calculating != null) {
            if (!calculating.isDone()) return;
            var calculated = calculating.getNow(null);
            calculating = null;
            if (generation == submittedGeneration) {
                result = calculated;
                resultGeneration = submittedGeneration;
                updateLines();
            }
        }
        if (++ticks % 10 != 0 && generation == resultGeneration) return;
        var inventory = new HashMap<String, Integer>();
        var bag = client.player.getInventory();
        for (int i = 0; i < 36; i++) {
            var stack = bag.getItem(i);
            if (!stack.isEmpty()) inventory.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        var offhand = client.player.getOffhandItem();
        if (!offhand.isEmpty()) inventory.merge(BuiltInRegistries.ITEM.getKey(offhand.getItem()).toString(), offhand.getCount(), Integer::sum);
        if (generation == resultGeneration && inventory.equals(lastStock)) return;
        if (planner == null) planner = new CraftPlan(recipes, tagResolver());
        lastStock = inventory;
        var goals = new LinkedHashMap<>(targets);
        var currentPlanner = planner;
        submittedGeneration = generation;
        calculating = CompletableFuture.supplyAsync(() -> currentPlanner.estimate(goals, inventory), Thread::startVirtualThread)
                .exceptionally(failure -> null);
    }

    private java.util.function.Function<String, List<String>> tagResolver() {
        var tags = new HashMap<String, List<String>>();
        for (var variants : recipes.values()) for (var recipe : variants) for (var ingredient : recipe.inputs()) {
            for (String token : ingredient.options()) if (token.startsWith("#")) {
                tags.computeIfAbsent(token.substring(1), CraftPlannerModule::tagItems);
            }
        }
        return id -> tags.getOrDefault(id, List.of());
    }

    private static List<String> tagItems(String token) {
        var id = Identifier.tryParse(token);
        if (id == null) return List.of();
        var items = new ArrayList<String>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
            items.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString());
        }
        items.sort(Comparator.naturalOrder());
        return List.copyOf(items);
    }

    private void invalidate() {
        generation++;
        result = null;
        lines = List.of();
        status = targets.isEmpty() ? "Добавь цель в настройках плана крафта" : "Обновляю список…";
    }

    private void updateLines() {
        if (result == null) { status = "План пока недоступен"; return; }
        status = result.remaining().isEmpty() ? "Всё уже в инвентаре" : result.missing().isEmpty()
                ? "Материалы для целей собраны" : "Ещё нужно собрать";
        var displayed = new ArrayList<String>();
        var source = result.missing().isEmpty() ? targets : result.missing();
        for (var item : source.entrySet()) {
            if (displayed.size() == 6) break;
            displayed.add(itemName(item.getKey()) + " ×" + item.getValue());
        }
        if (source.size() > displayed.size()) displayed.add("+ ещё " + (source.size() - displayed.size()));
        lines = List.copyOf(displayed);
    }

    public static String itemName(String itemId) {
        var id = Identifier.tryParse(itemId);
        return id == null ? itemId : BuiltInRegistries.ITEM.get(id).map(entry -> entry.value().getDefaultInstance().getHoverName().getString()).orElse(itemId);
    }

    public Map<String, Integer> goals() { return Collections.unmodifiableMap(targets); }
    public Map<String, Integer> missing() { return result == null ? Map.of() : result.missing(); }
    public String status() { return status; }
    public String addGoal(String input, String quantity) {
        int count;
        try { count = Integer.parseInt(quantity.strip()); }
        catch (NumberFormatException failure) { return "Укажи число от 1 до 4096."; }
        String item = resolve(input);
        if (item == null) return "Предмет не найден. Попробуй точное название или ID, например golden_apple.";
        if (count < 1 || count > 4096 || !targets.containsKey(item) && targets.size() >= LIMIT) return "До " + LIMIT + " целей по 1–4096 предметов.";
        setEnabled(true);
        if (!isEnabled()) return "План крафта недоступен.";
        targets.put(item, count);
        invalidate();
        return "Цель: " + itemName(item) + " ×" + count;
    }
    public String removeGoal(String id) {
        if (!net.aero.visuals.guard.AeroGuard.allowed() || targets.remove(id) == null) return "Цель не найдена.";
        invalidate();
        return "Удалено: " + itemName(id);
    }

    private static String resolve(String input) {
        String query = input.strip().toLowerCase(Locale.ROOT);
        var id = Identifier.tryParse(query);
        if (id != null && !query.equals("air") && !query.equals("minecraft:air") && BuiltInRegistries.ITEM.get(id).isPresent()) return id.toString();
        String match = null;
        for (var item : BuiltInRegistries.ITEM) {
            String key = BuiltInRegistries.ITEM.getKey(item).toString();
            if (key.equals("minecraft:air") || !item.getDefaultInstance().getHoverName().getString().equalsIgnoreCase(query)) continue;
            if (match != null) return null;
            match = key;
        }
        return match;
    }

    public void command(String argument) {
        var args = argument.split("\\s+", 3);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length != 3) { tell(HELP); return; }
                tell(addGoal(args[2], args[1]));
            }
            case "remove", "del" -> {
                var item = args.length < 2 ? null : resolve(argument.substring(argument.indexOf(' ') + 1));
                tell(removeGoal(item));
            }
            case "clear" -> { targets.clear(); invalidate(); tell("Список очищен."); }
            case "list" -> {
                if (targets.isEmpty()) { tell(HELP); return; }
                targets.forEach((id, amount) -> tell(itemName(id) + " ×" + amount + " · " + id));
                tell(status);
                if (result != null) result.missing().forEach((id, amount) -> tell(itemName(id) + " ×" + amount));
            }
            default -> tell(HELP);
        }
    }

    @Override protected void onDisable() { world = null; planner = null; invalidate(); }
    @Override protected void refresh(Minecraft client, float delta) {
        content = !targets.isEmpty();
        width = 206;
        height = 45 + lines.size() * 12;
    }
    @Override protected void renderHud(GuiGraphicsExtractor draw, Minecraft client) {
        title(draw, "План крафта");
        text(draw, fit(status, width - 16, 9), 8, 28, 9, 0xFFABC6D9);
        for (int i = 0; i < lines.size(); i++) text(draw, fit(lines.get(i), width - 16, 9), 8, 43 + i * 12, 9, 0xFFE8ECF4);
    }
    private static void tell(String message) { ClientCommandHandler.send("§bПлан крафта §7· §f" + message); }
}
