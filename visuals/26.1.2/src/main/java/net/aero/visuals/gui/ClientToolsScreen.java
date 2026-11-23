package net.aero.visuals.gui;

import client.api.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.feature.ImportedFeatures;
import net.aero.visuals.feature.enhanced.Waypoints;
import net.aero.visuals.feature.utility.ChestTracker;
import net.aero.visuals.feature.utility.PairedEmotes;
import net.aero.visuals.module.impl.hud.CraftPlannerModule;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Editors opened from the corresponding ClickGUI module or cosmetics tab. */
public final class ClientToolsScreen extends Screen {
    public enum Mode { CHESTS, CRAFT, PAIR, PET }
    private record Row(String title, String detail, String action, Runnable run, String second, Runnable secondRun) {}
    private static final int ROW_TOP = 82, ROW_HEIGHT = 27;
    private final Screen parent;
    private final Mode mode;
    private final ChestTracker chest;
    private final CraftPlannerModule craft;
    private final PairedEmotes pair;
    private final ImportedFeatures.Pet pet;
    private final Waypoints waypoints;
    private List<Row> rows = List.of();
    private List<PairedEmotes.Candidate> friends = List.of();
    private List<ChestTracker.SearchResult> matches = List.of();
    private PairedEmotes.Pose pose = PairedEmotes.Pose.HIGH_FIVE;
    private String input = "", quantity = "1", feedback = "";
    private boolean submitted, materials;
    private int left, panelWidth, page, perPage, tick;
    private Button accept, cancel;

    public ClientToolsScreen(Screen parent, Mode mode) {
        super(Component.literal(switch (mode) {
            case CHESTS -> "Chest Tracker";
            case CRAFT -> "План крафта";
            case PAIR -> "Парные эмоции";
            case PET -> "Настройки питомца";
        }));
        this.parent = parent;
        this.mode = mode;
        var runtime = ClientRuntime.get();
        chest = runtime.module(ChestTracker.class);
        craft = runtime.module(CraftPlannerModule.class);
        pair = runtime.module(PairedEmotes.class);
        pet = runtime.module(ImportedFeatures.Pet.class);
        waypoints = runtime.module(Waypoints.class);
    }

    @Override protected void init() {
        panelWidth = Math.min(510, width - 12);
        left = (width - panelWidth) / 2;
        perPage = Math.max(1, (height - ROW_TOP - 51) / ROW_HEIGHT);
        int x = left + 10, available = panelWidth - 20;
        switch (mode) {
            case CHESTS -> {
                field(input, "Название предмета или зачарования", x, 34, available - 63, 100, value -> input = value);
                button("Поиск", x + available - 58, 34, 58, () -> search(true));
                button(chest.isEnabled() ? "Запись включена" : "Включить запись", x, 58, 126, () -> {
                    chest.setEnabled(!chest.isConfiguredEnabled());
                    if (!chest.isEnabled()) matches = List.of();
                    feedback = chest.isEnabled() ? "Открой контейнеры, которые нужно запомнить." : "Запись выключена.";
                    rebuild();
                });
            }
            case CRAFT -> {
                field(input, "Название предмета или ID", x, 34, available - 106, 100, value -> input = value);
                field(quantity, "Количество", x + available - 102, 34, 36, 4, value -> quantity = value);
                button("Добавить", x + available - 62, 34, 62, () -> {
                    feedback = craft.addGoal(input, quantity);
                    if (feedback.startsWith("Цель:")) { input = ""; materials = false; page = 0; }
                    rebuild();
                });
                button("Цели", x, 58, 60, () -> { materials = false; page = 0; rebuild(); }).active = materials;
                button("Материалы", x + 64, 58, 81, () -> { materials = true; page = 0; rebuild(); }).active = !materials;
            }
            case PAIR -> {
                friends = pair.nearbyFriends();
                int middle = available / 2;
                button(pair.isEnabled() ? "Приглашения включены" : "Включить приглашения", x, 34, middle - 2, () -> {
                    pair.setEnabled(!pair.isConfiguredEnabled());
                    rebuild();
                });
                button(PairedEmotes.poseName(pose) + " ›", x + middle + 2, 34, available - middle - 2, () -> {
                    var options = PairedEmotes.Pose.values();
                    pose = options[(pose.ordinal() + 1) % options.length];
                    rebuild();
                });
                accept = button("Принять", x, 58, 72, () -> pair.reply(true));
                cancel = button("Отменить", x + 76, 58, 74, () -> pair.reply(false));
            }
            case PET -> {
                button(pet.living.get() ? "Поведение: вкл" : "Поведение: выкл", x, 34, available / 2 - 2, () -> {
                    if (permitted()) pet.living.set(!pet.living.get());
                    rebuild();
                });
                button(pet.danceToMusic.get() ? "Музыка: вкл" : "Музыка: выкл", x + available / 2 + 2, 34, (available + 1) / 2 - 2, () -> {
                    if (permitted()) pet.danceToMusic.set(!pet.danceToMusic.get());
                    rebuild();
                });
                button("Дом здесь", x, 58, 85, () -> feedback = waypoints.petHome(false));
                button("Сбросить дом", x + 89, 58, 96, () -> feedback = waypoints.petHome(true));
            }
        }
        rows = makeRows();
        page = Math.clamp(page, 0, lastPage());
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            var row = rows.get(i);
            int y = ROW_TOP + (i % perPage) * ROW_HEIGHT + 2;
            if (row.run() != null) button(row.action(), x + available - (row.secondRun() == null ? 66 : 104), y,
                    row.secondRun() == null ? 66 : 68, row.run());
            if (row.secondRun() != null) button(row.second(), x + available - 32, y, 32, row.secondRun());
        }
        int bottom = height - 29;
        button("Назад", x, bottom, 68, this::onClose);
        if (lastPage() > 0) {
            button("‹", x + available - 99, bottom, 23, () -> { page--; rebuild(); }).active = page > 0;
            button("›", x + available - 23, bottom, 23, () -> { page++; rebuild(); }).active = page < lastPage();
        }
        updatePairButtons();
    }

    private boolean permitted() {
        var module = switch (mode) {
            case CHESTS -> chest;
            case CRAFT -> craft;
            case PAIR -> pair;
            case PET -> pet;
        };
        return net.aero.visuals.guard.AeroGuard.allowed() && !module.isServerBlocked();
    }

    private void search(boolean resetPage) {
        submitted = true;
        var result = chest.search(input);
        matches = result.results();
        feedback = result.status();
        if (resetPage) page = 0;
        rebuild();
    }

    private List<Row> makeRows() {
        var list = new ArrayList<Row>();
        switch (mode) {
            case CHESTS -> {
                for (var match : matches) list.add(new Row(match.label() + " ×" + match.amount(),
                        match.x() + " " + match.y() + " " + match.z() + " · " + match.distance() + " бл. · " + match.minutes() + " мин назад",
                        "Метка", () -> feedback = chest.mark(match), "×", () -> {
                            String removed = chest.forget(match);
                            search(false);
                            feedback = removed;
                        }));
            }
            case CRAFT -> {
                var source = materials ? craft.missing() : craft.goals();
                for (var item : source.entrySet()) {
                    String id = item.getKey();
                    list.add(new Row(CraftPlannerModule.itemName(id) + " ×" + item.getValue(), id, "Убрать",
                            materials ? null : () -> { feedback = craft.removeGoal(id); rebuild(); }, "", null));
                }
            }
            case PAIR -> {
                if (pair.isEnabled()) for (var friend : friends) list.add(new Row(friend.name(), "Друг Aero ID рядом", "Пригласить",
                        () -> pair.invite(friend, pose), "", null));
            }
            case PET -> list.add(new Row("Возвращение домой", "Отметь место, где питомец будет тебя встречать.", "", null, "", null));
        }
        return List.copyOf(list);
    }

    private void updatePairButtons() {
        if (mode != Mode.PAIR || accept == null || cancel == null) return;
        accept.active = pair.isEnabled() && !pair.busy() && pair.canAccept();
        cancel.active = pair.isEnabled() && !pair.busy() && pair.hasInvitation();
    }

    @Override public void tick() {
        if (!permitted()) { onClose(); return; }
        updatePairButtons();
        if (++tick % 10 != 0) return;
        if (mode == Mode.PAIR && !friends.equals(pair.nearbyFriends())) rebuild();
        if (mode == Mode.CRAFT && materials) {
            var current = craft.missing();
            if (current.size() != rows.size() || current.entrySet().stream().anyMatch(entry -> rows.stream().noneMatch(row ->
                    row.detail().equals(entry.getKey()) && row.title().endsWith(" ×" + entry.getValue())))) rebuild();
        }
    }

    private int lastPage() { return Math.max(0, (rows.size() - 1) / perPage); }
    private void rebuild() { setFocused(null); clearWidgets(); init(); }
    private Button button(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, Math.max(1, width), 20).build());
    }
    private void field(String value, String hint, int x, int y, int width, int max, Consumer<String> changed) {
        var widget = addRenderableWidget(new AccountTextField(font, x, y, Math.max(1, width), 20, Component.literal(hint)));
        widget.setMaxLength(max);
        widget.setValue(value);
        widget.setHint(Component.literal(hint));
        widget.setResponder(changed);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);
        UiRenderer.roundedRect(context, left, 7, panelWidth, height - 14, 8, Theme.background());
        int x = left + 10, room = panelWidth - 20;
        label(context, title.getString(), x, 17, room, Theme.foreground());
        for (int i = page * perPage; i < Math.min(rows.size(), (page + 1) * perPage); i++) {
            var row = rows.get(i);
            int y = ROW_TOP + (i % perPage) * ROW_HEIGHT;
            int textWidth = room - (row.secondRun() != null ? 110 : row.run() != null ? 72 : 0);
            UiRenderer.roundedRect(context, x, y, room, 24, 5, Theme.surface());
            label(context, row.title(), x + 5, y + 3, textWidth - 9, Theme.foreground());
            label(context, row.detail(), x + 5, y + 13, textWidth - 9, Theme.mutedFg());
        }
        String status = switch (mode) {
            case CHESTS -> feedback.isEmpty() && !submitted ? "Поиск по ранее открытым контейнерам" : feedback;
            case CRAFT -> materials || feedback.isEmpty() ? craft.status() : feedback;
            case PAIR -> pair.isEnabled() ? pair.status() : "Включи приглашения, чтобы сыграть эмоцию вместе";
            case PET -> feedback;
        };
        label(context, status, x, height - 43, room, Theme.mutedFg());
        if (lastPage() > 0) label(context, (page + 1) + "/" + (lastPage() + 1), x + room - 69, height - 23, 41, Theme.foreground());
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void label(GuiGraphicsExtractor context, String text, int x, int y, int room, int color) {
        if (room > 0 && text != null && !text.isBlank()) AccountPanelStyle.text(context, font, AccountPanelStyle.trim(text, room), x, y, color, false);
    }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
