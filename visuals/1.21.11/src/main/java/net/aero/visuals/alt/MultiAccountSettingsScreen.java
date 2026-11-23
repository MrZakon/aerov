package net.aero.visuals.alt;

import client.api.Theme;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.function.IntFunction;

final class MultiAccountSettingsScreen extends Screen {
    private static final int HEAP_STEPS = 1
            + (MultiAccountBudget.MAX_HEAP_MB - MultiAccountBudget.MIN_HEAP_MB) / MultiAccountProfile.HEAP_STEP_MB;
    private static final int DISTANCE_STEPS = 1
            + MultiAccountProfile.MAX_RENDER_DISTANCE - MultiAccountProfile.MIN_RENDER_DISTANCE;

    private final Screen parent;
    private final MultiAccountSessions.Account account;
    private final int defaultMemoryMb;
    private OptionSlider memory;
    private OptionSlider distance;
    private boolean saveFailed;
    private int left, top, panelWidth;

    MultiAccountSettingsScreen(Screen parent, MultiAccountSessions.Account account, int defaultMemoryMb) {
        super(Text.literal(account.name));
        this.parent = parent;
        this.account = account;
        this.defaultMemoryMb = defaultMemoryMb;
    }

    @Override
    protected void init() {
        int memoryIndex = memory != null ? memory.index() : account.profile.heapMb() == 0 ? 0
                : 1 + (account.profile.heapMb() - MultiAccountBudget.MIN_HEAP_MB) / MultiAccountProfile.HEAP_STEP_MB;
        int distanceIndex = distance != null ? distance.index() : account.profile.renderDistance() == 0 ? 0
                : 1 + account.profile.renderDistance() - MultiAccountProfile.MIN_RENDER_DISTANCE;

        panelWidth = Math.min(308, width - 12);
        left = (width - panelWidth) / 2;
        top = Math.max(3, (height - 132) / 2);
        int x = left + 12;
        int controlWidth = panelWidth - 24;
        memory = addDrawableChild(new OptionSlider(x, top + 28, controlWidth, HEAP_STEPS, memoryIndex, index ->
                index == 0 ? tr("Память: общая (%d МБ)", "Memory: default (%d MB)", "Пам’ять: загальна (%d МБ)").formatted(defaultMemoryMb)
                        : tr("Память: %d МБ", "Memory: %d MB", "Пам’ять: %d МБ").formatted(heapMb(index))));
        distance = addDrawableChild(new OptionSlider(x, top + 54, controlWidth, DISTANCE_STEPS, distanceIndex, index ->
                index == 0 ? tr("Прорисовка: из Minecraft", "Render distance: Minecraft settings", "Промальовування: з Minecraft")
                        : tr("Прорисовка: %d чанков", "Render distance: %d chunks", "Промальовування: %d чанків").formatted(renderDistance(index))));
        int buttonWidth = (controlWidth - 8) / 3;
        button(tr("Сохранить", "Save", "Зберегти"), x, buttonWidth, this::save);
        button(tr("Сбросить", "Reset", "Скинути"), x + buttonWidth + 4, buttonWidth, () -> {
            memory.setIndex(0);
            distance.setIndex(0);
        });
        button(tr("Назад", "Back", "Назад"), x + 2 * (buttonWidth + 4), buttonWidth, this::close);
    }

    private void button(String label, int x, int width, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
                .dimensions(x, top + 100, width, 20).build());
    }

    private void save() {
        var profile = new MultiAccountProfile(heapMb(memory.index()), renderDistance(distance.index()));
        try {
            profile.save(account.name);
            account.profile = profile;
            close();
        } catch (IOException failure) {
            saveFailed = true;
            AeroVisuals.LOGGER.warn("Could not save settings for background account {}", account.name, failure);
        }
    }

    private static int heapMb(int index) {
        return index == 0 ? 0 : MultiAccountBudget.MIN_HEAP_MB + (index - 1) * MultiAccountProfile.HEAP_STEP_MB;
    }

    private static int renderDistance(int index) {
        return index == 0 ? 0 : MultiAccountProfile.MIN_RENDER_DISTANCE + index - 1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x78000000);
        UiRenderer.roundedRect(context, left, top, panelWidth, 132, 10, Theme.background());
        UiRenderer.roundedBorder(context, left, top, panelWidth, 132, 10, 1, Theme.border());
        context.drawText(textRenderer, title, left + 12, top + 12, Theme.foreground(), false);
        String note = saveFailed ? tr("Не удалось сохранить настройки", "Could not save settings", "Не вдалося зберегти налаштування")
                : tr("Применится при запуске аккаунта", "Applies when the account starts", "Застосується під час запуску акаунта");
        context.drawText(textRenderer, textRenderer.trimToWidth(note, panelWidth - 24), left + 12, top + 83,
                saveFailed ? Theme.caution() : Theme.mutedFg(), false);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }

    private static String tr(String ru, String en, String uk) { return LocalText.pick(ru, en, uk); }

    private static final class OptionSlider extends SliderWidget {
        private final int steps;
        private final IntFunction<String> label;

        OptionSlider(int x, int y, int width, int steps, int index, IntFunction<String> label) {
            super(x, y, width, 20, Text.literal(label.apply(index)), (double) index / steps);
            this.steps = steps;
            this.label = label;
        }

        int index() { return (int) Math.round(value * steps); }
        void setIndex(int index) { setValue((double) index / steps); }
        @Override protected void updateMessage() { setMessage(Text.literal(label.apply(index()))); }
        @Override protected void applyValue() {}
    }
}
