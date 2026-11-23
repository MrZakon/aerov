package net.aero.visuals.module.impl.inventory;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import com.mojang.blaze3d.platform.InputConstants;
import net.aero.visuals.mixin.HandledScreenAccessor;
import org.lwjgl.glfw.GLFW;

public final class ItemScrollerModule extends Module {
    private final DoubleSetting delay = add(new DoubleSetting("delay", "Задержка (мс)", "Интервал между перемещением соседних слотов при перетаскивании.", 80, 30, 200, 5));
    private int lastSlot = -1;
    private long lastMove;
    private java.lang.ref.WeakReference<Object> menu = new java.lang.ref.WeakReference<>(null), screenOwner = new java.lang.ref.WeakReference<>(null);

    public ItemScrollerModule() {
        super("item_scroller", "ItemScroller", "Удерживайте Shift и перетаскивайте левую кнопку по слотам, чтобы быстро перемещать стопки.", ModuleCategory.INVENTORY);
    }

    public boolean drag(AbstractContainerScreen<?> screen, int button, double x, double y) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || client.player == null || client.gameMode == null) return false;
        boolean shift = net.aero.visuals.alt.MultiAccountWorker.managesClient()
                ? net.aero.visuals.alt.MultiAccountWorker.remoteKeyDown(340) || net.aero.visuals.alt.MultiAccountWorker.remoteKeyDown(344)
                : !(!InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) && !InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT));
        if (!shift) { reset(); return false; }
        if (menu.get() != screen.getMenu() || screenOwner.get() != screen) { reset(); menu = new java.lang.ref.WeakReference<>(screen.getMenu()); screenOwner = new java.lang.ref.WeakReference<>(screen); }
        Slot slot = ((HandledScreenAccessor) screen).aero$getSlotAt(x, y);
        if (slot == null || !slot.hasItem() || slot.index == lastSlot) return true;
        long now = System.nanoTime();
        if (lastMove != 0 && now - lastMove < delay.get() * 1_000_000.0) return true;
        client.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, client.player);
        lastSlot = slot.index;
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
