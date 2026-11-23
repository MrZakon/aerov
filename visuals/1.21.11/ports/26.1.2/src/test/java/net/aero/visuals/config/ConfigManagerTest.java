package net.aero.visuals.config;

import net.aero.visuals.event.Event;
import net.aero.visuals.event.EventBus;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.module.setting.DoubleSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    // На Windows антивирус может кратко удерживать атомарно заменённый JSON и сорвать cleanup JUnit.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path temporaryDirectory;

    @Test
    void roundTripsModuleStateAndClampsValues() {
        Path path = temporaryDirectory.resolve("lunacy_visuals.json");
        ModuleManager firstModules = new ModuleManager(new EventBus());
        TestModule first = firstModules.register(new TestModule());
        ConfigManager firstConfig = new ConfigManager(path, firstModules);
        firstConfig.load();
        first.amount.set(10.7);
        first.setEnabled(true);
        firstConfig.markDirty();
        firstConfig.flush();

        ModuleManager secondModules = new ModuleManager(new EventBus());
        TestModule second = secondModules.register(new TestModule());
        ConfigManager secondConfig = new ConfigManager(path, secondModules);
        secondConfig.load();

        assertTrue(second.isConfiguredEnabled());
        assertEquals(10.0, second.amount.get());
        second.setEnabled(false);
        assertFalse(second.isConfiguredEnabled());
    }

    @Test
    void roundTripsGlobalClientSettingsWithoutAddingModules() {
        Path path = temporaryDirectory.resolve("lunacy_global_settings.json");
        ModuleManager firstModules = new ModuleManager(new EventBus());
        ClientSettings firstSettings = new ClientSettings();
        ConfigManager firstConfig = new ConfigManager(path, firstModules, firstSettings);
        firstConfig.load();
        firstSettings.all().stream().filter(setting -> setting.key().equals("theme")).findFirst().orElseThrow()
                .readJson(new com.google.gson.JsonPrimitive("dark"));
        firstSettings.all().stream().filter(setting -> setting.key().equals("ui_scale")).findFirst().orElseThrow()
                .readJson(new com.google.gson.JsonPrimitive(1.15));
        firstSettings.clickGuiScale().set(1.30);
        firstSettings.clickGuiTextScale().set(0.85);
        firstConfig.flush();

        ClientSettings restored = new ClientSettings();
        ConfigManager restoredConfig = new ConfigManager(path, new ModuleManager(new EventBus()), restored);
        restoredConfig.load();

        assertEquals(ClientSettings.ThemeMode.DARK, restored.theme());
        assertEquals(1.15, restored.uiScale(), 0.000001);
        assertEquals(1.30, restored.clickGuiScale().get(), 0.000001);
        assertEquals(0.85, restored.clickGuiTextScale().get(), 0.000001);
    }

    @Test
    void migratesLegacyPresentationWithoutDroppingBehaviourPreferences() throws Exception {
        Path path = temporaryDirectory.resolve("lunacy_legacy_settings.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 2,
                  "modules": {},
                  "client": {
                    "theme": "light",
                    "menu_backdrop": "clean",
                    "hud_style": "clean",
                    "shader_preset": "soft",
                    "ui_scale": 1.15,
                    "reduced_motion": true
                  },
                  "hud": {}
                }
                """);

        ClientSettings migrated = new ClientSettings();
        new ConfigManager(path, new ModuleManager(new EventBus()), migrated).load();

        assertEquals(ClientSettings.ThemeMode.DARK, migrated.theme());
        assertEquals(ClientSettings.MenuBackdrop.ARTWORK, migrated.menuBackdrop());
        assertEquals(ClientSettings.HudStyle.GLASS, migrated.hudStyle());
        assertEquals(ClientSettings.ShaderPreset.NEON, migrated.shaderPreset());
        assertEquals(1.15, migrated.uiScale(), 0.000001);
        assertTrue(migrated.reducedMotion());
    }

    @Test
    void profileWithoutKeybindClearsPreviousBinding() {
        ModuleManager modules = new ModuleManager(new EventBus());
        TestModule module = modules.register(new TestModule());
        ConfigManager config = new ConfigManager(temporaryDirectory.resolve("keys.json"), modules);
        var unbound = config.snapshot();
        try {
            net.aero.visuals.gui.ClickGuiScreen.setKeybind(module.id(), 65);
            config.restore(unbound);
            assertEquals(0, net.aero.visuals.gui.ClickGuiScreen.getKeybind(module));
        } finally {
            net.aero.visuals.gui.ClickGuiScreen.setKeybind(module.id(), 0);
        }
    }

    @Test
    void retriesFailedInitialSaveOnTick() throws Exception {
        Path parent = temporaryDirectory.resolve("blocked");
        Files.writeString(parent, "prevents directory creation");
        Path path = parent.resolve("config.json");
        ConfigManager config = new ConfigManager(path, new ModuleManager(new EventBus()));
        config.load();
        assertFalse(Files.exists(path));
        Files.delete(parent);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!Files.isRegularFile(path) && System.nanoTime() - deadline < 0) {
            config.tick();
            Thread.sleep(10);
        }
        assertTrue(Files.isRegularFile(path));
    }

    @Test
    void failedLoadRestoresValuesAlreadyApplied() throws Exception {
        ModuleManager modules = new ModuleManager(new EventBus());
        TestModule module = modules.register(new TestModule());
        Path path = temporaryDirectory.resolve("partial.json");
        Files.writeString(path, """
                {"schemaVersion":5,"modules":{"test":{"amount":8}},
                 "menuKey":{}}
                """);
        new ConfigManager(path, modules).load();
        assertEquals(1.0, module.amount.get());
        assertFalse(Files.exists(path));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.anyMatch(file -> file.getFileName().toString().contains(".corrupt-")));
        }
    }

    private static final class TestModule extends Module {
        private final DoubleSetting amount = add(new DoubleSetting(
                "amount", "Amount", "Test amount", 1.0, 0.0, 10.0, 0.5
        ));

        private TestModule() {
            super("test", "Test", "Test module", ModuleCategory.MISC);
        }

        @Subscribe
        private void onTest(TestEvent ignored) {
        }
    }

    private static final class TestEvent implements Event {
    }
}
