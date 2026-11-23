package net.aero.visuals.module.impl.inventory;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class LockSlotModule extends Module {
    private final BooleanSetting[] locked = new BooleanSetting[9];

    public LockSlotModule() {
        super("lock_slot", "LockSlot", "Защищает выбранные слоты хотбара от выбрасывания клавишей Q и из инвентаря.", ModuleCategory.INVENTORY);
        for (int i = 0; i < locked.length; i++) {
            locked[i] = add(new BooleanSetting("slot_" + (i + 1), "Слот " + (i + 1), "Запретить выбрасывание предметов из этого слота хотбара.", false));
        }
    }

    public boolean protects(int selectedSlot) {
        return isEnabled() && selectedSlot >= 0 && selectedSlot < locked.length && locked[selectedSlot].get();
    }

    public boolean protects(Slot slot, Inventory inventory) {
        return slot != null && slot.container == inventory && protects(slot.getContainerSlot());
    }
}
