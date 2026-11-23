package net.aero.visuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import client.util.LegacyInput;

public final class KeybindSetting extends Setting<KeybindSetting.Binding> {
    private static final int UNKNOWN = -1, LAST_MOUSE_BUTTON = 7, FIRST_KEY = 32, LAST_KEY = 348;
    private static final int SHIFT = 1, CONTROL = 2, ALT = 4;

    public record Binding(int keyCode, int modifiers) {
        public boolean isHeld(long window) {
            if (net.aero.visuals.alt.MultiAccountWorker.managesClient()) {
                return remoteHeld();
            }
            if (keyCode == UNKNOWN || !client.setting.KeybindSetting.isIntLong(modifiers, window)) {
                return false;
            }
            if (keyCode <= LAST_MOUSE_BUTTON) {
                return LegacyInput.glfwGetMouseButton(window, keyCode) == LegacyInput.GLFW_PRESS;
            }
            return LegacyInput.glfwGetKey(window, keyCode) == LegacyInput.GLFW_PRESS;
        }
        private boolean remoteHeld() {
            if ((modifiers & SHIFT) != 0 && !eitherDown(340, 344)) return false;
            if ((modifiers & CONTROL) != 0 && !eitherDown(341, 345)) return false;
            if ((modifiers & ALT) != 0 && !eitherDown(342, 346)) return false;
            if (keyCode < 0) return false;
            return keyCode <= LAST_MOUSE_BUTTON
                    ? net.aero.visuals.alt.MultiAccountWorker.remoteButtonDown(keyCode)
                    : net.aero.visuals.alt.MultiAccountWorker.remoteKeyDown(keyCode);
        }

        private static boolean eitherDown(int left, int right) {
            return net.aero.visuals.alt.MultiAccountWorker.remoteKeyDown(left)
                    || net.aero.visuals.alt.MultiAccountWorker.remoteKeyDown(right);
        }
    }

    public KeybindSetting(String key, String name, String description, int defaultKey) {
        super(key, name, description, new Binding(defaultKey, 0));
    }

    @Override
    protected Binding normalize(Binding value) {
        int code = value.keyCode();
        if (code < UNKNOWN || code > LAST_KEY
                || (code > LAST_MOUSE_BUTTON && code < FIRST_KEY)) {
            return defaultValue();
        }
        int supportedModifiers = SHIFT | CONTROL | ALT;
        return new Binding(code, code == UNKNOWN ? 0 : value.modifiers() & supportedModifiers);
    }

    @Override
    protected JsonElement encode(Binding value) {
        JsonObject json = new JsonObject();
        json.addProperty("keyCode", value.keyCode());
        json.addProperty("modifierFlags", value.modifiers());
        return json;
    }

    @Override
    protected Binding decode(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new Binding(element.getAsInt(), 0);
        }
        JsonObject json = element.getAsJsonObject();
        int modifiers = json.has("modifierFlags") ? json.get("modifierFlags").getAsInt() : 0;
        return new Binding(json.get("keyCode").getAsInt(), modifiers);
    }
}
