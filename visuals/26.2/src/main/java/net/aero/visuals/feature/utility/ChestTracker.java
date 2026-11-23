package net.aero.visuals.feature.utility;

import java.util.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.command.ClientCommandHandler;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.feature.enhanced.Waypoints;
import net.aero.visuals.module.ModuleCategory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

public final class ChestTracker extends VisualExpansion.Feature {
    private record Opening(Object world, ChestIndex.Spot spot, String label, int slots, boolean shulker,
                           AbstractContainerMenu previous, long expires) {}
    private ChestIndex index;
    private Opening opening;
    private AbstractContainerMenu shown;
    private boolean dirty;
    public record SearchResult(WorldScope scope, int x, int y, int z, String label, int amount, int distance, long minutes) {}
    public record Search(List<SearchResult> results, String status) {
        public Search { results = List.copyOf(results); }
    }
    private List<SearchResult> commandResults = List.of();

    public ChestTracker() {
        super("chest_tracker", "Chest Tracker", "Запоминает открытые сундуки, бочки и шалкеры. В настройках модуля можно искать предметы и ставить метки.", ModuleCategory.INVENTORY);
    }

    @Override protected void onEnable() {
        if (index == null) index = new ChestIndex(FabricLoader.getInstance().getConfigDir().resolve("aerovisuals-chests.json"));
    }
    @Override protected void onDisable() { resetOpen(); commandResults = List.of(); }
    public void closeIndex() { if (index != null) index.close(); }
    private void resetOpen() { opening = null; shown = null; dirty = false; }

    public void clicked(Minecraft client, BlockPos pos) {
        if (!isEnabled() || client.player == null || client.level == null) return;
        var state = client.level.getBlockState(pos);
        var block = state.getBlock();
        int slots = 27;
        if (block instanceof ChestBlock) {
            var type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                slots = 54;
                if (type == ChestType.RIGHT) pos = pos.relative(ChestBlock.getConnectedDirection(state));
            }
        } else if (!(block instanceof BarrelBlock) && !(block instanceof ShulkerBoxBlock)) return;
        var scope = WorldScope.current(client);
        if (scope == null) return;
        flush();
        var spot = new ChestIndex.Spot(scope, pos.getX(), pos.getY(), pos.getZ());
        opening = new Opening(client.level, spot, block.getName().getString(), slots,
                block instanceof ShulkerBoxBlock, client.player.containerMenu, System.nanoTime() + 3_000_000_000L);
        shown = null;
        dirty = false;
    }

    /** A full server inventory update distinguishes an empty container from one still loading. */
    public void contents(Minecraft client, int menuId, boolean initial) {
        if (!isEnabled() || opening == null || client.player == null || opening.world() != client.level) return;
        var menu = client.player.containerMenu;
        if (menu.containerId != menuId || menu == opening.previous()) return;
        if (shown == null) {
            if (!initial || System.nanoTime() - opening.expires() >= 0 || !matches(menu)) return;
            shown = menu;
        }
        if (shown == menu) dirty = true;
    }

    private boolean matches(AbstractContainerMenu menu) {
        return opening.shulker() ? menu instanceof ShulkerBoxMenu
                : menu instanceof ChestMenu chest && chest.getRowCount() * 9 == opening.slots();
    }

    @Subscribe private void tick(TickEvent.Client event) {
        if (opening == null) return;
        var client = event.client();
        if (client.player == null || client.level != opening.world()) { resetOpen(); return; }
        flush();
        if (shown == null ? System.nanoTime() - opening.expires() >= 0 : shown != client.player.containerMenu) resetOpen();
    }

    private void flush() {
        if (!dirty || shown == null || index == null || !index.ready()) return;
        dirty = false;
        var items = new ArrayList<ChestIndex.Item>();
        for (int i = 0; i < opening.slots(); i++) {
            var stack = shown.getSlot(i).getItem();
            if (!stack.isEmpty()) items.add(describe(stack));
        }
        index.record(opening.spot(), opening.label(), items);
    }

    private static ChestIndex.Item describe(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name = clip(stack.getHoverName().getString());
        StringBuilder search = new StringBuilder(id.replace('_', ' ')).append(' ').append(name);
        for (var component : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
            var enchantments = stack.get(component);
            if (enchantments == null) continue;
            for (var enchantment : enchantments.keySet()) {
                search.append(' ').append(enchantment.value().description().getString());
                enchantment.unwrapKey().ifPresent(key -> search.append(' ').append(key.identifier()));
            }
        }
        return new ChestIndex.Item(id, name, clip(search.toString()).toLowerCase(Locale.ROOT), stack.getCount());
    }

    private static String clip(String text) {
        return text.substring(0, Math.min(384, text.length())).replace('§', ' ');
    }

    public void command(String argument) {
        if (argument.equalsIgnoreCase("on") || argument.equalsIgnoreCase("off")) {
            setEnabled(argument.equalsIgnoreCase("on"));
            tell(isEnabled() ? "Включён. Открой контейнеры, которые нужно запомнить." : "Выключен.");
            return;
        }
        if (argument.isBlank()) { tell(".chest <название или зачарование> · .chest mark <номер> · .chest forget <номер>"); return; }
        var args = argument.split("\\s+", 2);
        if (args[0].equalsIgnoreCase("mark") || args[0].equalsIgnoreCase("forget")) {
            try {
                int i = args.length == 2 ? Integer.parseInt(args[1]) - 1 : -1;
                if (i < 0 || i >= commandResults.size()) { tell("Сначала выполни поиск и выбери номер."); return; }
                boolean remove = args[0].equalsIgnoreCase("forget");
                tell(remove ? forget(commandResults.get(i)) : mark(commandResults.get(i)));
                if (remove) commandResults = List.of();
            } catch (NumberFormatException failure) { tell("Укажи номер контейнера из списка."); }
            return;
        }
        var search = search(argument);
        commandResults = search.results();
        tell(search.status());
        for (int i = 0; i < commandResults.size(); i++) {
            var result = commandResults.get(i);
            tell((i + 1) + ". " + result.label() + " ×" + result.amount() + " · " + result.x() + " " + result.y() + " " + result.z() + " · " + result.distance() + " блоков · " + result.minutes() + " мин назад");
        }
        if (!commandResults.isEmpty()) tell(".chest mark <номер> — поставить метку");
    }

    public Search search(String input) {
        var client = Minecraft.getInstance();
        var scope = WorldScope.current(client);
        if (scope == null) return new Search(List.of(), "Открой мир перед поиском.");
        if (!isEnabled()) return new Search(List.of(), "Включи Chest Tracker и открой нужные контейнеры.");
        if (index == null || !index.ready()) return new Search(List.of(), index != null && index.failed()
                ? "Не удалось прочитать индекс сундуков." : "Индекс сундуков загружается.");
        String query = input.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return new Search(List.of(), "Введи название предмета или зачарования.");
        long now = System.currentTimeMillis();
        var matches = new ArrayList<SearchResult>();
        for (var entry : index.entries()) {
            var spot = entry.spot();
            if (!scope.equals(spot.scope())) continue;
            int amount = 0;
            for (var item : entry.items()) if (item.search().contains(query) || item.id().contains(query)) amount += item.amount();
            if (amount != 0) {
                double x = client.player.getX() - spot.x(), y = client.player.getY() - spot.y(), z = client.player.getZ() - spot.z();
                matches.add(new SearchResult(scope, spot.x(), spot.y(), spot.z(), entry.label(), amount,
                        (int) Math.round(Math.sqrt(x * x + y * y + z * z)), Math.max(0, (now - entry.seenAt()) / 60_000)));
            }
        }
        matches.sort(Comparator.comparingInt(SearchResult::distance));
        return new Search(matches.subList(0, Math.min(8, matches.size())), matches.isEmpty()
                ? "В открытых контейнерах этого измерения ничего не найдено."
                : "Последнее известное содержимое · найдено: " + matches.size());
    }

    private boolean validResult(SearchResult result) {
        return result != null && isEnabled() && result.scope().equals(WorldScope.current(Minecraft.getInstance()));
    }
    public String mark(SearchResult result) {
        if (!validResult(result)) return "Повтори поиск в текущем измерении.";
        var waypoints = ClientRuntime.get().module(Waypoints.class);
        return waypoints == null ? "Метки недоступны." : waypoints.markChest(result.x(), result.y(), result.z());
    }
    public String forget(SearchResult result) {
        if (!validResult(result) || index == null || !index.ready()) return "Повтори поиск в текущем измерении.";
        index.forget(new ChestIndex.Spot(result.scope(), result.x(), result.y(), result.z()));
        return "Запись удалена.";
    }

    private static void tell(String message) { ClientCommandHandler.send("§bChest Tracker §7· §f" + message); }
}
