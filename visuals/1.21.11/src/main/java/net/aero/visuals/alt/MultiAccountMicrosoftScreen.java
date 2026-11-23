package net.aero.visuals.alt;

import client.api.Theme;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Desktop;
import java.io.IOException;

final class MultiAccountMicrosoftScreen extends Screen {
    private final MultiAccountScreen parent;
    private volatile MultiAccountMicrosoft.DeviceCode code;
    private volatile MultiAccountMicrosoft.Profile profile;
    private volatile String error;
    private Thread login;
    private ButtonWidget browser;
    private ButtonWidget copy;
    private int left, top, panelWidth;

    MultiAccountMicrosoftScreen(MultiAccountScreen parent) {
        super(Text.literal("Microsoft"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 12, 380);
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - 126) / 2);
        int cell = (panelWidth - 32) / 3;
        browser = button(tr("Открыть сайт", "Open website", "Відкрити сайт"), left + 12, cell, this::openBrowser);
        copy = button(tr("Копировать код", "Copy code", "Копіювати код"), left + 16 + cell, cell, () -> {
            var current = code;
            if (current != null) client.keyboard.setClipboard(current.userCode());
        });
        button(tr("Назад", "Back", "Назад"), left + 20 + cell * 2, cell, this::close);
        updateButtons();
        if (login == null) {
            login = Thread.ofVirtual().name("Aero-Microsoft-Login").start(() -> {
                try {
                    profile = MultiAccountMicrosoft.signIn(value -> code = value);
                } catch (InterruptedException cancelled) {
                    Thread.currentThread().interrupt();
                } catch (IOException | RuntimeException failure) {
                    error = failure instanceof IOException ? failure.getMessage()
                            : tr("Не удалось выполнить вход", "Could not sign in", "Не вдалося ввійти");
                }
            });
        }
    }

    private ButtonWidget button(String label, int x, int width, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
                .dimensions(x, top + 94, width, 20).build());
    }

    private void openBrowser() {
        var current = code;
        if (current == null) return;
        try { Desktop.getDesktop().browse(current.verificationUri()); }
        catch (IOException | RuntimeException failure) {
            error = tr("Не удалось открыть браузер", "Could not open the browser", "Не вдалося відкрити браузер");
        }
    }

    private void updateButtons() {
        browser.active = code != null && profile == null;
        copy.active = browser.active;
    }

    @Override public void tick() {
        updateButtons();
        var result = profile;
        if (result == null) return;
        try {
            parent.acceptMicrosoft(result);
            close();
        } catch (IOException failure) {
            profile = null;
            code = null;
            error = failure.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x78000000);
        UiRenderer.roundedRect(context, left, top, panelWidth, 126, 10, Theme.background());
        UiRenderer.roundedBorder(context, left, top, panelWidth, 126, 10, 1, Theme.border());
        context.drawText(textRenderer, title, left + 12, top + 12, Theme.foreground(), false);
        var current = code;
        String status = error != null ? error : current == null
                ? tr("Запрашиваем код…", "Requesting code…", "Запитуємо код…")
                : tr("Введи код на сайте Microsoft", "Enter the code on Microsoft’s website", "Введи код на сайті Microsoft");
        context.drawText(textRenderer, textRenderer.trimToWidth(status, panelWidth - 24), left + 12, top + 32,
                error == null ? Theme.mutedFg() : Theme.caution(), false);
        if (current != null) context.drawText(textRenderer, current.userCode(), left + 12, top + 52, Theme.foreground(), false);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void removed() { if (login != null) login.interrupt(); }

    private static String tr(String ru, String en, String uk) { return LocalText.pick(ru, en, uk); }
}
