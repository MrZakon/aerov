package net.aero.visuals.gui;

import net.aero.visuals.alt.AltManager;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import client.util.LegacyInput;

import java.util.List;
import java.util.Locale;

public final class AltManagerScreen extends Screen {
    private final Screen parent;
    private String nameInput = "";
    private boolean inputFocused = true;
    private double scrollY = 0.0;
    private String statusMessage = "";
    private int statusColor = 0xFF5CE1E6;
    private long statusTimer = 0;

    private static final int CARD_W = 340;
    private static final int CARD_H = 320;
    private static final int LIST_H = 164;

    public AltManagerScreen(Screen parent) {
        super(Component.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AltManager.load();
    }

    private void setStatus(String message, boolean success) {
        this.statusMessage = message;
        this.statusColor = success ? 0xFF33D17A : 0xFFFF5C77;
        this.statusTimer = System.currentTimeMillis() + 3500;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Dim background
        context.fill(0, 0, width, height, 0x99000000);

        int cardX = (width - CARD_W) / 2;
        int cardY = (height - CARD_H) / 2;

        // Card glass body and neon border
        AeroMenuChrome.glass(context, cardX, cardY, CARD_W, CARD_H, 10);
        UiRenderer.roundedBorder(context, cardX, cardY, CARD_W, CARD_H, 10, 1, 0x335CE1E6);

        // Header Title
        AeroMenuChrome.logo(context, cardX + 16, cardY + 14, 18);
        UiRenderer.font(context, FontFace.UNBOUNDED, "ALT MANAGER", cardX + 38, cardY + 14, 9.5, 0xFFF1F4FF);

        // Active Account Subtitle
        String current = AltManager.getCurrentUsername();
        String currentLabel = "Текущий: " + current;
        UiRenderer.font(context, FontFace.GOLOS, currentLabel, cardX + 38, cardY + 26, 7.0, 0xFF5CE1E6);

        // Close button (X)
        int closeBtnX = cardX + CARD_W - 26;
        int closeBtnY = cardY + 12;
        int closeBtnSize = 16;
        boolean closeHover = UiRenderer.inside(mouseX, mouseY, closeBtnX, closeBtnY, closeBtnSize, closeBtnSize);
        double closeW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, "✕", 8.5);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, "✕", closeBtnX + (closeBtnSize - closeW) / 2.0, closeBtnY + 3.5, 8.5, closeHover ? 0xFFFF5C77 : 0xFF7683AA);

        // 1. Text Input Field (cardX + 16, cardY + 38, 196, 20)
        int inputX = cardX + 16;
        int inputY = cardY + 38;
        int inputW = 196;
        int inputH = 20;
        UiRenderer.roundedRect(context, inputX, inputY, inputW, inputH, 5, 0x70202A3B);
        UiRenderer.roundedBorder(context, inputX, inputY, inputW, inputH, 5, 1, inputFocused ? 0xFF5CE1E6 : 0x2EFFFFFF);

        if (nameInput.isEmpty() && !inputFocused) {
            UiRenderer.font(context, FontFace.GOLOS, "Введите никнейм...", inputX + 8, inputY + 6.0, 7.5, 0xFF5F6D94);
        } else {
            String cursor = (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) ? "|" : "";
            UiRenderer.font(context, FontFace.GOLOS, nameInput + cursor, inputX + 8, inputY + 6.0, 7.5, 0xFFFFFFFF);
        }

        // 2. Random Nick Button (cardX + 216, cardY + 38, 50, 20)
        int rndX = cardX + 216;
        int rndW = 50;
        boolean rndHover = UiRenderer.inside(mouseX, mouseY, rndX, inputY, rndW, inputH);
        UiRenderer.roundedRect(context, rndX, inputY, rndW, inputH, 5, rndHover ? 0x903A455A : 0x70252E40);
        UiRenderer.roundedBorder(context, rndX, inputY, rndW, inputH, 5, 1, rndHover ? 0x665CE1E6 : 0x26FFFFFF);
        String rndLabel = "Рандом";
        double rndTextW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, rndLabel, 6.8);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, rndLabel, rndX + (rndW - rndTextW) / 2.0, inputY + 6.0, 6.8, rndHover ? 0xFFFFFFFF : 0xFFB9C2E3);

        // 3. Add Alt Button (cardX + 270, cardY + 38, 54, 20)
        int addX = cardX + 270;
        int addW = 54;
        boolean addHover = UiRenderer.inside(mouseX, mouseY, addX, inputY, addW, inputH);
        UiRenderer.roundedRect(context, addX, inputY, addW, inputH, 5, addHover ? 0x903A455A : 0x70252E40);
        UiRenderer.roundedBorder(context, addX, inputY, addW, inputH, 5, 1, addHover ? 0x665CE1E6 : 0x26FFFFFF);
        String addLabel = "+ Добавить";
        double addTextW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, addLabel, 6.8);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, addLabel, addX + (addW - addTextW) / 2.0, inputY + 6.0, 6.8, addHover ? 0xFFFFFFFF : 0xFFB9C2E3);

        // 4. Quick Login Button (cardX + 16, cardY + 62, 308, 18)
        int loginY = cardY + 62;
        int loginW = 308;
        int loginH = 18;
        boolean loginHover = UiRenderer.inside(mouseX, mouseY, inputX, loginY, loginW, loginH);
        int loginBg = loginHover ? ColorUtil.mix(0xFF5CE1E6, 0xFFFFFFFF, 0.12F) : 0xFF5CE1E6;
        UiRenderer.roundedRect(context, inputX, loginY, loginW, loginH, 5, loginBg);
        String loginLabel = "ВОЙТИ ПОД ЭТИМ НИКОМ";
        double loginTextW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, loginLabel, 7.2);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, loginLabel, inputX + (loginW - loginTextW) / 2.0, loginY + 5.5, 7.2, 0xFF05080E);

        // 5. Saved Alts List
        int listX = cardX + 16;
        int listY = cardY + 96;
        int listW = 308;
        List<String> alts = AltManager.getAlts();
        String listTitle = "Сохранённые аккаунты (" + alts.size() + "):";
        UiRenderer.font(context, FontFace.GOLOS_SEMI, listTitle, listX, cardY + 85, 7.2, 0xFF95A0C6);

        // List background
        UiRenderer.roundedRect(context, listX, listY, listW, LIST_H, 6, 0x60080B14);
        UiRenderer.roundedBorder(context, listX, listY, listW, LIST_H, 6, 1, 0x1AFFFFFF);

        double maxScroll = Math.max(0, alts.size() * 24 - LIST_H + 4);
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < 0) scrollY = 0;

        // Scissored area
        context.enableScissor(listX, listY, listX + listW, listY + LIST_H);
        int rowY = listY + 3 - (int) scrollY;

        for (int i = 0; i < alts.size(); i++) {
            String alt = alts.get(i);
            boolean isCur = alt.equalsIgnoreCase(current);
            boolean rowHover = UiRenderer.inside(mouseX, mouseY, listX, rowY, listW - 22, 22);
            boolean delHover = UiRenderer.inside(mouseX, mouseY, listX + listW - 20, rowY, 18, 22);

            if (rowY + 22 >= listY && rowY <= listY + LIST_H) {
                if (rowHover) {
                    UiRenderer.roundedRect(context, listX + 2, rowY, listW - 4, 22, 4, 0x225CE1E6);
                }

                // Avatar bubble with first letter (perfectly centered)
                String initial = alt.isEmpty() ? "L" : alt.substring(0, 1).toUpperCase(Locale.ROOT);
                UiRenderer.roundedRect(context, listX + 6, rowY + 3, 16, 16, 8, isCur ? 0xFF5CE1E6 : 0xFF212A40);
                double initW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, initial, 7.5);
                UiRenderer.font(context, FontFace.GOLOS_SEMI, initial, listX + 6 + (16.0 - initW) / 2.0, rowY + 6.0, 7.5, isCur ? 0xFF05080E : 0xFFFFFFFF);

                // Alt name (safely fitted to never collide with badge or delete button)
                double maxAltW = isCur ? (listW - 108.0) : (listW - 54.0);
                String displayAlt = alt;
                if (UiRenderer.fontWidth(FontFace.GOLOS_SEMI, displayAlt, 7.5) > maxAltW) {
                    while (displayAlt.length() > 3 && UiRenderer.fontWidth(FontFace.GOLOS_SEMI, displayAlt + "..", 7.5) > maxAltW) {
                        displayAlt = displayAlt.substring(0, displayAlt.length() - 1);
                    }
                    displayAlt = displayAlt + "..";
                }
                int nameColor = isCur ? 0xFF5CE1E6 : (rowHover ? 0xFFFFFFFF : 0xFFB9C2E3);
                UiRenderer.font(context, FontFace.GOLOS_SEMI, displayAlt, listX + 28, rowY + 6.5, 7.5, nameColor);

                // Active badge (perfectly centered text)
                if (isCur) {
                    double badgeX = listX + listW - 74;
                    double badgeW = 46;
                    UiRenderer.roundedRect(context, badgeX, rowY + 4, badgeW, 14, 3, 0x335CE1E6);
                    double actW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, "АКТИВЕН", 6.0);
                    UiRenderer.font(context, FontFace.GOLOS_SEMI, "АКТИВЕН", badgeX + (badgeW - actW) / 2.0, rowY + 6.5, 6.0, 0xFF5CE1E6);
                }

                // Delete button
                UiRenderer.font(context, FontFace.GOLOS_SEMI, "✕", listX + listW - 14, rowY + 6.5, 8.0, delHover ? 0xFFFF5C77 : 0xFF5F6D94);
            }
            rowY += 24;
        }
        context.disableScissor();

        // 6. Status message (centered across card)
        if (System.currentTimeMillis() < statusTimer && !statusMessage.isEmpty()) {
            double statusW = UiRenderer.fontWidth(FontFace.GOLOS, statusMessage, 7.0);
            UiRenderer.font(context, FontFace.GOLOS, statusMessage, cardX + (CARD_W - statusW) / 2.0, cardY + 271, 7.0, statusColor);
        }

        // 7. Bottom controls (cardY + 288, centered text labels)
        int btmY = cardY + 288;
        int clearW = 90;
        boolean clearHover = UiRenderer.inside(mouseX, mouseY, cardX + 16, btmY, clearW, 18);
        UiRenderer.roundedRect(context, cardX + 16, btmY, clearW, 18, 5, clearHover ? 0x44FF5C77 : 0x1AFF5C77);
        UiRenderer.roundedBorder(context, cardX + 16, btmY, clearW, 18, 5, 1, clearHover ? 0x88FF5C77 : 0x33FF5C77);
        String clearLabel = "Очистить список";
        double clearTextW = UiRenderer.fontWidth(FontFace.GOLOS, clearLabel, 6.5);
        UiRenderer.font(context, FontFace.GOLOS, clearLabel, cardX + 16 + (clearW - clearTextW) / 2.0, btmY + 5.5, 6.5, clearHover ? 0xFFFF7A90 : 0xFFFF5C77);

        int backW = 74;
        int backX = cardX + CARD_W - 16 - backW;
        boolean backHover = UiRenderer.inside(mouseX, mouseY, backX, btmY, backW, 18);
        UiRenderer.roundedRect(context, backX, btmY, backW, 18, 5, backHover ? 0x903A455A : 0x70252E40);
        UiRenderer.roundedBorder(context, backX, btmY, backW, 18, 5, 1, backHover ? 0x665CE1E6 : 0x26FFFFFF);
        String backLabel = "← Назад";
        double backTextW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, backLabel, 7.0);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, backLabel, backX + (backW - backTextW) / 2.0, btmY + 5.5, 7.0, backHover ? 0xFFFFFFFF : 0xFFB9C2E3);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (LegacyInput.button(click.button()) == LegacyInput.GLFW_MOUSE_BUTTON_LEFT) {
            double mx = click.x();
            double my = click.y();
            int cardX = (width - CARD_W) / 2;
            int cardY = (height - CARD_H) / 2;

            // Close button (X)
            if (UiRenderer.inside(mx, my, cardX + CARD_W - 26, cardY + 12, 16, 16)) {
                onClose();
                return true;
            }

            // Text input field focus
            int inputX = cardX + 16;
            int inputY = cardY + 38;
            if (UiRenderer.inside(mx, my, inputX, inputY, 196, 20)) {
                inputFocused = true;
                return true;
            } else {
                inputFocused = false;
            }

            // Random Nick button
            if (UiRenderer.inside(mx, my, cardX + 216, inputY, 50, 20)) {
                nameInput = AltManager.randomName();
                GuiSound.play("ui.click");
                return true;
            }

            // Add alt button
            if (UiRenderer.inside(mx, my, cardX + 270, inputY, 54, 20)) {
                String clean = nameInput.trim();
                if (!AltManager.isValidUsername(clean)) {
                    setStatus("⚠ Ник должен содержать 3-16 симв. (A-Z, 0-9, _)", false);
                } else {
                    if (AltManager.addAlt(clean)) {
                        setStatus("✓ Аккаунт " + clean + " добавлен!", true);
                        GuiSound.play("ui.toggle_on");
                    } else {
                        setStatus("⚠ Аккаунт уже есть в списке", false);
                    }
                }
                return true;
            }

            // Quick login button
            int loginY = cardY + 62;
            if (UiRenderer.inside(mx, my, inputX, loginY, 308, 18)) {
                doLogin(nameInput);
                return true;
            }

            // Alts list click handling
            int listX = cardX + 16;
            int listY = cardY + 96;
            int listW = 308;
            if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + LIST_H) {
                List<String> alts = AltManager.getAlts();
                int rowY = listY + 3 - (int) scrollY;
                for (int i = 0; i < alts.size(); i++) {
                    if (my >= rowY && my < rowY + 22) {
                        String alt = alts.get(i);
                        // Delete click
                        if (mx >= listX + listW - 20) {
                            AltManager.removeAlt(alt);
                            setStatus("✕ Аккаунт " + alt + " удалён", false);
                            GuiSound.play("ui.delete");
                            return true;
                        }
                        // Login click
                        doLogin(alt);
                        return true;
                    }
                    rowY += 24;
                }
            }

            // Clear all button
            int btmY = cardY + 288;
            if (UiRenderer.inside(mx, my, cardX + 16, btmY, 90, 18)) {
                AltManager.clearAlts();
                setStatus("✓ Список аккаунтов очищен", true);
                scrollY = 0;
                GuiSound.play("ui.delete");
                return true;
            }

            // Back button
            int backW = 74;
            int backX = cardX + CARD_W - 16 - backW;
            if (UiRenderer.inside(mx, my, backX, btmY, backW, 18)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void doLogin(String name) {
        String clean = name == null ? "" : name.trim();
        if (!AltManager.isValidUsername(clean)) {
            setStatus("⚠ Недопустимый ник (3-16 симв., A-Z, 0-9, _)", false);
            return;
        }
        if (AltManager.login(clean)) {
            setStatus("✓ Успешный вход под ником: " + clean, true);
            GuiSound.play("ui.toggle_on");
        } else {
            setStatus("⚠ Ошибка смены аккаунта", false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (AltManager.getAlts().size() * 24 > LIST_H) {
            this.scrollY -= verticalAmount * 18.0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (inputFocused && input.isAllowedChatCharacter() && nameInput.length() < 16) {
            char c = input.codepointAsString().charAt(0);
            if (Character.isLetterOrDigit(c) || c == '_') {
                nameInput += input.codepointAsString();
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = LegacyInput.key(input);
        if (inputFocused) {
            if ((key == LegacyInput.GLFW_KEY_BACKSPACE || key == LegacyInput.GLFW_KEY_DELETE) && !nameInput.isEmpty()) {
                nameInput = nameInput.substring(0, nameInput.length() - 1);
                return true;
            }
            if (key == LegacyInput.GLFW_KEY_ENTER || key == LegacyInput.GLFW_KEY_KP_ENTER) {
                doLogin(nameInput);
                return true;
            }
        }
        if (key == LegacyInput.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
