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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;

public final class InventoryHudModule extends Module implements net.aero.visuals.hud.DraggableHud {
    private static final int COLUMNS = 9;
    private static final int CELL = 20;
    private static final int SLOT = 18;
    private static final int PADDING = 4;
    private static final int SLOT_GRID_WIDTH = (COLUMNS - 1) * CELL + SLOT;
    private static final int SLOT_GRID_HEIGHT = 3 * CELL + 3 + SLOT;
    private static final int SLOT_TEXTURE_SCALE = 4;
    private static final Identifier SLOT_GRID_TEXTURE =
            Identifier.fromNamespaceAndPath("aerovisuals", "textures/gui/inventory_hud_slots.png");
    private static final int WIDTH = PADDING * 2 + SLOT_GRID_WIDTH;

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
        positionX.onChanged(ignored -> useSliderPosition());
        positionY.onChanged(ignored -> useSliderPosition());
        setEnabled(false);
    }

    private void useSliderPosition() {
        var runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.config() != null) {
            runtime.config().clearHudPosition(id());
        }
    }

    @Subscribe
    private void render(Render2DEvent event) {
        if(Minecraft.getInstance().screen instanceof ChatScreen)return;
        draw(event.context());
    }
    @Override public boolean isVisible(){var c=Minecraft.getInstance();return isEnabled()&&c.player!=null&&!c.options.hideGui&&(c.screen==null||c.screen instanceof ChatScreen||c.screen instanceof client.gui.screen.ClickGuiScreen);}
    @Override public void draw(GuiGraphicsExtractor context){
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || context == null || client.player == null || client.options.hideGui) return;
        if (client.screen != null && !(client.screen instanceof ChatScreen)
                && !(client.screen instanceof client.gui.screen.ClickGuiScreen)) return;

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        if (screenWidth <= 0 || screenHeight <= 0) return;
        boolean includeHotbar = showHotbar.get();
        int rows = includeHotbar ? 4 : 3;
        int gridHeight = includeHotbar ? SLOT_GRID_HEIGHT : 2 * CELL + SLOT;
        int height = PADDING * 2 + gridHeight;
        // Keep the regular margins where possible, but cap the pane at the real viewport.
        double preferredScale = Math.min(scale.get(), Math.min(
                Math.max(1, screenWidth - 16) / (double) WIDTH,
                Math.max(1, screenHeight - 64) / (double) height));
        double viewportScale = Math.min(screenWidth / (double) WIDTH, screenHeight / (double) height);
        float fittedScale = (float) Math.min(viewportScale, Math.max(0.1, preferredScale));
        double width = WIDTH * fittedScale;
        double scaledHeight = height * fittedScale;
        float x = (float) (8 + Math.max(0, screenWidth - 16 - width) * positionX.get() / 100);
        float y = (float) (8 + Math.max(0, screenHeight - 64 - scaledHeight) * positionY.get() / 100);
        var runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.config() != null) {
            var saved = runtime.config().hudPosition(id(), null);
            if (saved != null) {
                x = (float) saved.screenX(width, screenWidth);
                y = (float) saved.screenY(scaledHeight, screenHeight);
            }
        }
        x = (float) Math.clamp(net.aero.visuals.hud.HudDragController.x(this, x), 0, Math.max(0, screenWidth - width));
        y = (float) Math.clamp(net.aero.visuals.hud.HudDragController.y(this, y), 0, Math.max(0, screenHeight - scaledHeight));
        if (runtime != null) {
            var bounds = new HudBounds(x, y, width, scaledHeight);
            runtime.hudLayout().put(this, bounds);
            net.aero.visuals.hud.HudDragController.updateBounds(this, bounds);
        }
        int alpha = (int) Math.round(slotOpacity.get() * 255 / 100);
        int background = alpha << 24 | 0x080A10;
        int outline = Math.round(alpha * 0.3F) << 24 | 0xFFFFFF;

        context.pose().pushMatrix();
        try {
            context.pose().translate(x, y);
            context.pose().scale(fittedScale, fittedScale);
            if (alpha > 0) {
                // Two texture regions cover the whole inventory, including empty slots.
                int textureWidth = SLOT_GRID_WIDTH * SLOT_TEXTURE_SCALE;
                int layerHeight = SLOT_GRID_HEIGHT * SLOT_TEXTURE_SCALE;
                int regionHeight = gridHeight * SLOT_TEXTURE_SCALE;
                UiRenderer.textureRegion(context, SLOT_GRID_TEXTURE, PADDING, PADDING, SLOT_GRID_WIDTH, gridHeight,
                        0, 0, textureWidth, regionHeight, textureWidth, 2 * layerHeight, background);
                if ((outline >>> 24) != 0) {
                    UiRenderer.textureRegion(context, SLOT_GRID_TEXTURE, PADDING, PADDING, SLOT_GRID_WIDTH, gridHeight,
                            0, layerHeight, textureWidth, regionHeight, textureWidth, 2 * layerHeight, outline);
                }
            }
            var inventory = client.player.getInventory();
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    int slotX = PADDING + column * CELL;
                    int slotY = PADDING + row * CELL + (row == 3 ? 3 : 0);
                    int inventorySlot = row < 3 ? 9 + row * COLUMNS + column : column;
                    var stack = inventory.getItem(inventorySlot);
                    if (!stack.isEmpty()) {
                        context.item(stack, slotX + 1, slotY + 1);
                        context.itemDecorations(client.font, stack, slotX + 1, slotY + 1);
                    }
                }
            }
        } finally {
            context.pose().popMatrix();
        }
    }
}
