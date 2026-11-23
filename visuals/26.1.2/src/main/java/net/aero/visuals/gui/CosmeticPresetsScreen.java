package net.aero.visuals.gui;

import client.api.Theme;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.cosmetic.CosmeticLoadout;
import net.aero.visuals.cosmetic.CosmeticPresets;
import net.aero.visuals.feature.ImportedModels;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;

public final class CosmeticPresetsScreen extends Screen {
    private final Screen parent;
    private final CosmeticPresets presets;
    private AccountTextField name;
    private String draft = "";
    private String selected = "";
    private String status = "";
    private String deleteConfirmation = "";
    private int page;
    private int pageSize;
    private int left;
    private int panelWidth;

    public CosmeticPresetsScreen(Screen parent) {
        super(Component.literal(LocalText.pick("Наборы косметики", "Cosmetic sets", "Набори косметики")));
        this.parent = parent;
        var config = ClientRuntime.get().config().getConfigPath();
        presets = new CosmeticPresets(config.resolveSibling("aerovisuals-cosmetic-presets.json"));
    }

    private void button(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        panelWidth = Math.min(410, width - 20);
        left = (width - panelWidth) / 2;
        int x = left + 10;
        int w = panelWidth - 20;
        int cell = (w - 8) / 3;
        name = addRenderableWidget(new AccountTextField(font, x, 34, w - 85, 20, Component.literal(title.getString())));
        name.setMaxLength(40);
        name.setValue(draft);
        name.setHint(Component.literal(LocalText.pick("Название образа", "Outfit name", "Назва образу")));
        name.setResponder(value -> draft = value);
        button(LocalText.pick("Сохранить", "Save", "Зберегти"), x + w - 80, 34, 80, this::save);
        pageSize = Math.max(1, (height - 155) / 22);
        var names = new ArrayList<>(presets.entries().keySet());
        int lastPage = Math.max(0, (names.size() - 1) / pageSize);
        page = Math.clamp(page, 0, lastPage);
        for (int row = 0; row < pageSize && page * pageSize + row < names.size(); row++) {
            String item = names.get(page * pageSize + row);
            String label = (item.equals(selected) ? "✓ " : "") + item + " · " + presets.entries().get(item).size();
            button(AccountPanelStyle.trim(label, w - 12), x, 59 + row * 22, w, () -> {
                selected = item;
                deleteConfirmation = "";
                rebuild();
            });
        }
        button("‹", x, height - 90, cell, () -> { if (page > 0) { page--; rebuild(); } });
        button("›", x + cell + 4, height - 90, cell, () -> { if (page < lastPage) { page++; rebuild(); } });
        button(LocalText.pick("Назад", "Back", "Назад"), x + 2 * (cell + 4), height - 90, cell, this::onClose);
        if (presets.entries().containsKey(selected)) {
            button(LocalText.pick("Надеть", "Equip", "Одягнути"), x, height - 63, cell, this::equip);
            String delete = deleteConfirmation.equals(selected) ? LocalText.pick("Подтвердить", "Confirm", "Підтвердити")
                    : LocalText.pick("Удалить", "Delete", "Видалити");
            button(delete, x + cell + 4, height - 63, w - cell - 4, this::remove);
        }
    }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        init();
    }

    private void save() {
        if (!net.aero.visuals.guard.AeroGuard.allowed()) { status = net.aero.visuals.guard.AeroGuard.message(); return; }
        try {
            presets.saveNew(draft, CosmeticLoadout.capture());
            selected = draft.strip();
            draft = "";
            page = (presets.entries().size() - 1) / pageSize;
            deleteConfirmation = "";
            status = LocalText.pick("Набор сохранён на ПК", "Set saved on this PC", "Набір збережено на ПК");
            rebuild();
        } catch (IllegalArgumentException invalid) {
            status = LocalText.pick("Нужны уникальное имя и свободное место (до 24 наборов)",
                    "Enter a unique name. Up to 24 sets can be saved.", "Введи унікальну назву. До 24 наборів.");
        } catch (IOException failure) {
            status = LocalText.pick("Не удалось сохранить набор", "Could not save set", "Не вдалося зберегти набір");
        }
    }

    private void equip() {
        if (!net.aero.visuals.guard.AeroGuard.allowed()) { status = net.aero.visuals.guard.AeroGuard.message(); return; }
        var outfit = presets.entries().get(selected);
        if (outfit == null) return;
        if (outfit.entrySet().stream().anyMatch(piece -> ImportedModels.catalog(piece.getKey()).stream().noneMatch(model -> model.id() == piece.getValue()))) {
            status = LocalText.pick("Некоторые предметы набора недоступны", "Some items in this set are unavailable", "Деякі предмети набору недоступні");
            return;
        }
        CosmeticLoadout.apply(outfit);
        onClose();
    }

    private void remove() {
        if (!selected.equals(deleteConfirmation)) {
            deleteConfirmation = selected;
            rebuild();
            return;
        }
        try {
            presets.remove(selected);
            deleteConfirmation = selected = "";
            rebuild();
        } catch (IOException failure) {
            status = LocalText.pick("Не удалось удалить набор", "Could not delete set", "Не вдалося видалити набір");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, 0xA0000000);
        UiRenderer.roundedRect(draw, left, 6, panelWidth, height - 12, 8, Theme.background());
        AccountPanelStyle.text(draw, font, title.getString(), left + 10, 16, Theme.foreground(), false);
        AccountPanelStyle.text(draw, font, AccountPanelStyle.trim(status, panelWidth - 20),
                left + 10, height - 29, Theme.mutedFg(), false);
        super.extractRenderState(draw, mouseX, mouseY, delta);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}

