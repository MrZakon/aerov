package net.aero.visuals.module;

import net.aero.visuals.event.EventBus;
import net.aero.visuals.module.impl.inventory.InventoryHudModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {
    @Test
    void registersInventoryHudInHudCategory() {
        ModuleManager manager = new ModuleManager(new EventBus());
        InventoryHudModule registered = manager.register(new InventoryHudModule());
        Module inventory = manager.find("inventory_display").orElseThrow();
        assertSame(registered, inventory);
        assertEquals(ModuleCategory.HUD, inventory.category());
        assertEquals("InventoryHud", inventory.name());
    }

    @Test
    void performanceToolsAreRegisteredAsOptInModules() {
        ModuleManager manager = new ModuleManager(new EventBus());
        var inspector = manager.register(new net.aero.visuals.module.impl.hud.FpsInspectorModule());
        var budget = manager.register(new net.aero.visuals.module.impl.misc.SmartEffectsModule());
        assertSame(inspector, manager.find("fps_inspector").orElseThrow());
        assertSame(budget, manager.find("smart_effects").orElseThrow());
        assertEquals(ModuleCategory.HUD, inspector.category());
        assertEquals(ModuleCategory.RENDER, budget.category());
        assertFalse(inspector.isConfiguredEnabled());
        assertFalse(budget.isConfiguredEnabled());
    }
}
