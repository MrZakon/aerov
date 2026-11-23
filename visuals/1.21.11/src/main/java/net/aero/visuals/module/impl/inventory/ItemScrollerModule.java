package net.aero.visuals.module.impl.inventory;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

public final class ItemScrollerModule extends Module {
    private final DoubleSetting delay = add(new DoubleSetting("delay", "Задержка (мс)", "Интервал между перемещением соседних слотов при перетаскивании.", 80, 30, 200, 5));
    private int lastSlot = -1;
    private long lastMove;
    private java.lang.ref.WeakReference<Object> menu = new java.lang.ref.WeakReference<>(null), screenOwner = new java.lang.ref.WeakReference<>(null);

    public ItemScrollerModule() {
        super("item_scroller", "ItemScroller", "Удерживайте Shift и перетаскивайте левую кнопку по слотам, чтобы быстро перемещать стопки.", ModuleCategory.INVENTORY);
    }

    public boolean drag(HandledScreen<?> screen, int button, double x, double y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isEnabled() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || client.player == null || client.interactionManager == null) return false;
        boolean shift = net.aero.visuals.alt.MultiAccountWorker.managesClient()
                ? net.aero.visuals.alt.MultiAccountWorker.remoteKeyDown(340) || net.aero.visuals.alt.MultiAccountWorker.remoteKeyDown(344)
                : !(!InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) && !InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT));
        if (!shift) { reset(); return false; }
        if (menu.get() != screen.getScreenHandler() || screenOwner.get() != screen) { reset(); menu = new java.lang.ref.WeakReference<>(screen.getScreenHandler()); screenOwner = new java.lang.ref.WeakReference<>(screen); }
        Slot slot = ((HandledScreenAccessor) screen).aero$getSlotAt(x, y);
        if (slot == null || !slot.hasStack() || slot.id == lastSlot) return true;
        long now = System.nanoTime();
        if (lastMove != 0 && now - lastMove < delay.get() * 1_000_000.0) return true;
        client.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, client.player);
        lastSlot = slot.id;
        lastMove = now;
        return true;
    }

    @Override protected void onDisable() { reset(); }
    public void reset() {
        lastSlot = -1;
        lastMove = 0;
        menu.clear(); screenOwner.clear();
    }
}
