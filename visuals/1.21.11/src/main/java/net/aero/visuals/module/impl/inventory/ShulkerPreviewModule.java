package net.aero.visuals.module.impl.inventory;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.mixin.HandledScreenAccessor;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;

public final class ShulkerPreviewModule extends Module {
    public ShulkerPreviewModule() {
        super("shulker_preview", "ShulkerPreview", "Показывает содержимое шалкера при наведении на него в инвентаре.", ModuleCategory.INVENTORY);
    }

    public void render(HandledScreen<?> screen, DrawContext context, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        Slot slot = ((HandledScreenAccessor) screen).aero$getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) return;
        ItemStack box = slot.getStack();
        if (!(box.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;
        var contents = box.get(DataComponentTypes.CONTAINER);
        if (contents == null) return;
        DefaultedList<ItemStack> stacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
        contents.copyTo(stacks);
        int width = 182;
        int height = 68;
        int x = mouseX + 14;
        int y = mouseY + 12;
        if (x + width > context.getScaledWindowWidth()) x = Math.max(2, mouseX - width - 14);
        if (y + height > context.getScaledWindowHeight()) y = Math.max(2, context.getScaledWindowHeight() - height - 2);
        context.fill(x, y, x + width, y + height, 0xEC080B13);
        context.fill(x, y, x + width, y + 1, 0xFF58DCE3);
        MinecraftClient client = MinecraftClient.getInstance();
        context.drawTextWithShadow(client.textRenderer, box.getName(), x + 5, y + 4, 0xFFFFFF);
        for (int i = 0; i < 27; i++) {
            int sx = x + 4 + i % 9 * 20;
            int sy = y + 16 + i / 9 * 17;
            context.fill(sx, sy, sx + 18, sy + 16, 0x60404A5A);
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                context.drawItem(stack, sx + 1, sy);
                context.drawStackOverlay(client.textRenderer, stack, sx + 1, sy);
            }
        }
    }
}
