package net.aero.visuals.module;

import net.aero.visuals.event.EventBus;
import net.aero.visuals.module.impl.inventory.InventoryHudModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {
    @Test
    void registersInventoryHudInInventoryCategory() {
        ModuleManager manager = new ModuleManager(new EventBus());
        InventoryHudModule registered = manager.register(new InventoryHudModule());
        Module inventory = manager.find("inventory_display").orElseThrow();
        assertSame(registered, inventory);
        assertEquals(ModuleCategory.INVENTORY, inventory.category());
        assertEquals("InventoryHud", inventory.name());
    }
}
