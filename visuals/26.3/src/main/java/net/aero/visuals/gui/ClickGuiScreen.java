package net.aero.visuals.gui;

import client.concurrent.SystemClient;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import client.util.LegacyInput;

import java.util.HashMap;
import java.util.Map;

public final class ClickGuiScreen extends client.gui.screen.ClickGuiScreen {
    // --- Global Client Settings & Customization ---
    public static int accentColor = 0xFF8A5CFF; // AeroVisuals Neon Violet
    public static int clientColor = 0xFF00E5FF; // AeroVisuals Neon Cyan
    public static float uiScale = 1.0F;
    public static boolean soundEnabled = true;
    public static float soundVolume = 75.0F;
    public static int menuKeyCode = LegacyInput.GLFW_KEY_RIGHT_SHIFT; // 344 = RSHIFT
    public static String menuKeyName = "RSHFT";
    public static ModuleCategory lastCategory = ModuleCategory.HUD;
    public static float lastScrollY = 0.0F;
    public static boolean lastShowingClientSettings = false;

    // --- Module Keybinds Memory ---
    private static final Map<String, Integer> MODULE_KEYBINDS = new HashMap<>();

    private boolean showingDragmode = false;
    private boolean showingClientSettings = false;

    private static ClickGuiScreen cachedScreen;

    public static ClickGuiScreen getOrInstance() {
        if (cachedScreen == null) {
            cachedScreen = new ClickGuiScreen();
        }
        return cachedScreen;
    }

    public ClickGuiScreen() {
        this(lastCategory);
    }

    public ClickGuiScreen(boolean hudEditorMode) {
        this(lastCategory);
        this.showingDragmode = hudEditorMode;
    }

    public ClickGuiScreen(ModuleCategory initialCategory) {
        super();
        if (initialCategory != null) {
            lastCategory = initialCategory;
        }
        if (SystemClient.getInstance() != null && SystemClient.getInstance().getModuleRegistry() != null) {
            var reg = SystemClient.getInstance().getModuleRegistry();
            if (reg.getList().isEmpty()) {
                reg.update7();
            }
        }
    }

    public void setShowingClientSettings(boolean show) {
        this.showingClientSettings = show;
    }

    public boolean isEditingHud() {
        return showingDragmode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static int getKeybind(Module module) {
        return MODULE_KEYBINDS.getOrDefault(module.id(), 0);
    }

    public static boolean matchesMenuKey(int code, int modifiers) {
        int expected = KeyUtils.isMouseBind(menuKeyCode) ? KeyUtils.getMouseButton(menuKeyCode) : menuKeyCode;
        int ownModifier = (code == 344 || code == 340) ? 1 : (code == 341 || code == 345) ? 2 : (code == 342 || code == 346) ? 4 : 0;
        return expected >= 0 && code == expected && (modifiers & ~ownModifier & 7) == 0;
    }

    public static void setMenuKey(int code) {
        menuKeyCode = code;
        menuKeyName = KeyUtils.getShortKeyName(code);
        if (ClientRuntime.getNullable() != null && ClientRuntime.get().config() != null) ClientRuntime.get().config().markDirty();
    }

    public static void setKeybind(Module module, int key) {
        setKeybind(module.id(), key);
    }

    public static void setKeybind(String moduleId, int key) {
        if (key <= 0) {
            MODULE_KEYBINDS.remove(moduleId);
        } else {
            MODULE_KEYBINDS.put(moduleId, key);
        }
        if (ClientRuntime.getNullable() != null && ClientRuntime.get().config() != null) {
            ClientRuntime.get().config().markDirty();
        }
    }

    public static void onGlobalKeyPress(int keyCode) {
        if (keyCode <= 0) return;
        for (Map.Entry<String, Integer> entry : MODULE_KEYBINDS.entrySet()) {
            if (entry.getValue() == keyCode) {
                if (ClientRuntime.getNullable() != null) {
                    ClientRuntime.get().modules().find(entry.getKey()).ifPresent(Module::toggle);
                }
            }
        }
    }
}
