package net.aero.visuals.module.impl.inventory;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.aero.visuals.mixin.HandledScreenAccessor;

public final class ShulkerPreviewModule extends Module {
    public ShulkerPreviewModule() {
        super("shulker_preview", "ShulkerPreview", "Показывает содержимое шалкера при наведении на него в инвентаре.", ModuleCategory.INVENTORY);
    }

    public void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        Slot slot = ((HandledScreenAccessor) screen).aero$getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasItem()) return;
        ItemStack box = slot.getItem();
        if (!(box.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;
        var contents = box.get(DataComponents.CONTAINER);
        if (contents == null) return;
        NonNullList<ItemStack> stacks = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(stacks);
        int width = 182;
        int height = 68;
        int x = mouseX + 14;
        int y = mouseY + 12;
        if (x + width > context.guiWidth()) x = Math.max(2, mouseX - width - 14);
        if (y + height > context.guiHeight()) y = Math.max(2, context.guiHeight() - height - 2);
        context.fill(x, y, x + width, y + height, 0xEC080B13);
        context.fill(x, y, x + width, y + 1, 0xFF58DCE3);
        Minecraft client = Minecraft.getInstance();
        context.text(client.font, box.getHoverName(), x + 5, y + 4, 0xFFFFFF);
        for (int i = 0; i < 27; i++) {
            int sx = x + 4 + i % 9 * 20;
            int sy = y + 16 + i / 9 * 17;
            context.fill(sx, sy, sx + 18, sy + 16, 0x60404A5A);
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                context.item(stack, sx + 1, sy);
                context.itemDecorations(client.font, stack, sx + 1, sy);
            }
        }
    }
}
