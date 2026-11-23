package net.aero.visuals.module.impl.inventory;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.mixin.HandledScreenAccessor;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class InventoryButtonsModule extends Module {
    private ItemStack selectedType = ItemStack.EMPTY;
    private int buttonX;
    private int buttonY;

    public InventoryButtonsModule() {
        super("inventory_buttons", "InventoryButtons", "Кнопки массового выбрасывания предметов из инвентаря. Удерживайте Shift при нажатии.", ModuleCategory.INVENTORY);
    }

    public void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor context, int mouseX, int mouseY, int screenX, int screenY, int backgroundWidth) {
        if (!isEnabled()) return;
        Slot hovered = ((HandledScreenAccessor) screen).aero$getSlotAt(mouseX, mouseY);
        if (hovered != null && hovered.hasItem()) selectedType = hovered.getItem().copyWithCount(1);
        buttonX = Math.max(2, Math.min(context.guiWidth() - 106, screenX + backgroundWidth + 6));
        buttonY = Math.max(4, screenY + 4);
        drawButton(context, buttonX, buttonY, 0, "Выбросить тип");
        drawButton(context, buttonX, buttonY + 24, 1, "Выбросить всё");
    }

    private void drawButton(GuiGraphicsExtractor context, int x, int y, int index, String text) {
        context.fill(x, y, x + 104, y + 20, 0xDD080B12);
        context.fill(x, y, x + 104, y + 1, index == 0 ? 0xFF58DCE3 : 0xFFE78A8A);
        context.text(Minecraft.getInstance().font, text, x + 5, y + 6, 0xFFFFFF);
    }

    public boolean click(AbstractContainerScreen<?> screen, double mouseX, double mouseY, boolean shift) {
        if (!isEnabled() || mouseX < buttonX || mouseX >= buttonX + 104 || mouseY < buttonY || mouseY >= buttonY + 44) return false;
        int row = mouseY < buttonY + 20 ? 0 : mouseY >= buttonY + 24 ? 1 : -1;
        if (row < 0) return true;
        if (!shift) return true;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return true;
        if (row == 0 && selectedType.isEmpty()) return true;
        LockSlotModule lock = ClientRuntime.get().module(LockSlotModule.class);
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != client.player.getInventory() || slot.getContainerSlot() < 0 || slot.getContainerSlot() >= 36 || !slot.hasItem()) continue;
            if (lock != null && lock.protects(slot, client.player.getInventory())) continue;
            if (row == 0 && !ItemStack.isSameItemSameComponents(slot.getItem(), selectedType)) continue;
            client.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 1, ContainerInput.THROW, client.player);
        }
        return true;
    }

    @Override protected void onDisable() { selectedType = ItemStack.EMPTY; }
}
