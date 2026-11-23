package net.aero.visuals.module.impl.inventory;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.Render2DEvent;
import net.aero.visuals.hud.HudBounds;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

public final class InventoryHudModule extends Module implements net.aero.visuals.hud.DraggableHud {
    private static final int COLUMNS = 9;
    private static final int CELL = 20;
    private static final int SLOT = 18;
    private static final int PADDING = 4;
    private static final int WIDTH = PADDING * 2 + COLUMNS * CELL - (CELL - SLOT);

    private final DoubleSetting slotOpacity = add(new DoubleSetting(
            "slot_opacity", "Непрозрачность слотов (%)", "0% — полностью прозрачный фон слотов, 100% — непрозрачный. Предметы остаются видимыми.", 55, 0, 100, 1));
    private final BooleanSetting showHotbar = add(new BooleanSetting(
            "show_hotbar", "Показывать хотбар", "Добавляет 9 слотов панели быстрого доступа под основным инвентарём.", false));
    private final DoubleSetting scale = add(new DoubleSetting(
            "scale", "Масштаб", "Изменяет размер инвентаря на экране. 1 — обычный размер.", 0.65, 0.5, 1.5, 0.05));
    private final DoubleSetting positionX = add(new DoubleSetting(
            "position_x", "Положение по X (%)", "0% — левый край экрана, 100% — правый.", 100, 0, 100, 1));
    private final DoubleSetting positionY = add(new DoubleSetting(
            "position_y", "Положение по Y (%)", "0% — верх экрана, 100% — низ, над хотбаром Minecraft.", 100, 0, 100, 1));

    public InventoryHudModule() {
        super("inventory_display", "InventoryHud",
                "Показывает содержимое инвентаря и пустые слоты.", ModuleCategory.HUD);
        setEnabled(false);
    }

    @Subscribe
    private void render(Render2DEvent event) {
        if(MinecraftClient.getInstance().currentScreen instanceof ChatScreen)return;
        draw(event.context());
    }
    @Override public boolean isVisible(){var c=MinecraftClient.getInstance();return isEnabled()&&c.player!=null&&!c.options.hudHidden&&(c.currentScreen==null||c.currentScreen instanceof ChatScreen||c.currentScreen instanceof client.gui.screen.ClickGuiScreen);}
    @Override public void draw(DrawContext context){
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || context == null || client.player == null || client.options.hudHidden) return;
        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)
                && !(client.currentScreen instanceof client.gui.screen.ClickGuiScreen)) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int rows = showHotbar.get() ? 4 : 3;
        int height = PADDING * 2 + rows * CELL - (CELL - SLOT) + (showHotbar.get() ? 3 : 0);
        float fittedScale = (float) Math.max(0.1, Math.min(scale.get(),
                Math.min(Math.max(1, screenWidth - 16) / (double) WIDTH,
                        Math.max(1, screenHeight - 64) / (double) height)));
        double width = WIDTH * fittedScale;
        double scaledHeight = height * fittedScale;
        float x = (float) (8 + Math.max(0, screenWidth - 16 - width) * positionX.get() / 100);
        float y = (float) (8 + Math.max(0, screenHeight - 64 - scaledHeight) * positionY.get() / 100);
        var active=ClientRuntime.getNullable();if(active!=null){var saved=active.config().hudPosition(id(),new net.aero.visuals.hud.HudPosition(net.aero.visuals.hud.HudAnchor.TOP_LEFT,x,y,1));x=(float)Math.clamp(net.aero.visuals.hud.HudDragController.x(this,saved.x()),0,Math.max(0,screenWidth-width));y=(float)Math.clamp(net.aero.visuals.hud.HudDragController.y(this,saved.y()),0,Math.max(0,screenHeight-scaledHeight));net.aero.visuals.hud.HudDragController.updateBounds(this,new HudBounds(x,y,width,scaledHeight));}
        int alpha = (int) Math.round(slotOpacity.get() * 255 / 100);
        int background = alpha << 24 | 0x080A10;
        int outline = Math.round(alpha * 0.3F) << 24 | 0xFFFFFF;

        context.getMatrices().pushMatrix();
        try {
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(fittedScale, fittedScale);
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    int slotX = PADDING + column * CELL;
                    int slotY = PADDING + row * CELL + (row == 3 ? 3 : 0);
                    if (alpha > 0) {
                        UiRenderer.roundedRect(context, slotX, slotY, SLOT, SLOT, 3, background);
                        UiRenderer.roundedBorder(context, slotX, slotY, SLOT, SLOT, 3, 0.7, outline);
                    }
                    int inventorySlot = row < 3 ? 9 + row * COLUMNS + column : column;
                    var stack = client.player.getInventory().getStack(inventorySlot);
                    if (!stack.isEmpty()) {
                        context.drawItem(stack, slotX + 1, slotY + 1);
                        context.drawStackOverlay(client.textRenderer, stack, slotX + 1, slotY + 1);
                    }
                }
            }
        } finally {
            context.getMatrices().popMatrix();
        }
        var runtime = ClientRuntime.getNullable();
        if (runtime != null) runtime.hudLayout().put(id(), new HudBounds(x, y, width, scaledHeight));
    }
}
