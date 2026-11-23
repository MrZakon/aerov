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

    public ItemScrollerModule() {
        super("item_scroller", "ItemScroller", "Удерживайте Shift и перетаскивайте левую кнопку по слотам, чтобы быстро перемещать стопки.", ModuleCategory.INVENTORY);
    }

    public boolean drag(AbstractContainerScreen<?> screen, int button, double x, double y) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || client.player == null || client.gameMode == null) return false;
        if (!InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) && !InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT)) return false;
        Slot slot = ((HandledScreenAccessor) screen).aero$getSlotAt(x, y);
        if (slot == null || !slot.hasItem() || slot.index == lastSlot) return true;
        long now = System.currentTimeMillis();
        if (now - lastMove < delay.get()) return true;
        client.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, client.player);
        lastSlot = slot.index;
        lastMove = now;
        return true;
    }

    public void reset() {
        lastSlot = -1;
    }
}
