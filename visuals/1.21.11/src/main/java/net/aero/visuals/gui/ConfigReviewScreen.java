package net.aero.visuals.gui;

import client.api.Theme;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Supplier;

public final class ConfigReviewScreen extends Screen {
    private final Screen parent;
    private final String name;
    private final Supplier<List<String>> changes;
    private final java.util.function.BooleanSupplier apply;
    private List<String> lines = List.of();
    private int page;
    private int pageSize;
    private int left;
    private int panelWidth;
    private boolean refreshOnFailure;
    private boolean showStatus;

    public ConfigReviewScreen(Screen parent, String name, Supplier<List<String>> changes,
                              java.util.function.BooleanSupplier apply) {
        super(Text.literal(LocalText.pick("Предпросмотр конфига", "Config preview", "Перегляд конфіга")));
        this.parent = parent;
        this.name = name;
        this.changes = changes;
        this.apply = apply;
    }

    private void button(String label, int x, int y, int width, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run()).dimensions(x, y, width, 20).build());
    }

    @Override
    protected void init() {
        lines = changes.get();
        panelWidth = Math.min(560, width - 20);
        left = (width - panelWidth) / 2;
        pageSize = Math.max(1, (height - 110) / 16);
        int cell = (panelWidth - 32) / 4;
        int x = left + 10;
        button("‹", x, height - 34, cell, () -> { if (page > 0) page--; });
        button("›", x + cell + 4, height - 34, cell, () -> { if ((page + 1) * pageSize < lines.size()) page++; });
        button(LocalText.pick("Применить", "Apply", "Застосувати"), x + 2 * (cell + 4), height - 34, cell, () -> {
            if (apply.getAsBoolean()) close();
            else { refreshOnFailure = true; showStatus = true; }
        });
        button(LocalText.pick("Назад", "Back", "Назад"), x + 3 * (cell + 4), height - 34, cell, this::close);
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        if (refreshOnFailure) {
            refreshOnFailure = false;
            lines = changes.get();
            page = 0;
        }
        draw.fill(0, 0, width, height, 0xA0000000);
        UiRenderer.roundedRect(draw, left, 6, panelWidth, height - 12, 8, Theme.background());
        int x = left + 10;
        AccountPanelStyle.text(draw, textRenderer, title.getString(), x, 16, Theme.foreground(), false);
        AccountPanelStyle.text(draw, textRenderer, AccountPanelStyle.trim(name, panelWidth - 20), x, 33, Theme.mutedFg(), false);
        for (int i = 0; i < pageSize && page * pageSize + i < lines.size(); i++) {
            AccountPanelStyle.text(draw, textRenderer, AccountPanelStyle.trim(lines.get(page * pageSize + i), panelWidth - 20),
                    x, 54 + i * 16, Theme.foreground(), false);
        }
        String position = (page + 1) + " / " + Math.max(1, (lines.size() + pageSize - 1) / pageSize);
        if (showStatus) {
            String status = net.aero.visuals.ClientRuntime.get().cloudConfigs().status();
            position += " · " + status;
        }
        position = AccountPanelStyle.trim(position, panelWidth - 20);
        AccountPanelStyle.text(draw, textRenderer, position, x, height - 51, Theme.mutedFg(), false);
        super.render(draw, mouseX, mouseY, delta);
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}

