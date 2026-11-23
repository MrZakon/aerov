package net.aero.visuals.alt;

import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.KeyEvent;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.guard.AeroGuard;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.regulator.ServerRestrictions;
import net.minecraft.client.Minecraft;
import client.util.LegacyInput;

public final class MultiAccountModule extends Module {
    private final MultiAccountSessions sessions = new MultiAccountSessions(this);
    private final DoubleSetting cycleKey = add(new DoubleSetting("cycle_key", "Следующий аккаунт (F10=299)",
            "F9 открывает панель, F11 переключает полноэкранный режим.", 299, 32, 348, 1) {
        @Override
        protected Double normalize(Double requested) {
            double key = super.normalize(requested);
            return key == 256 || key == 298 || key == 300 ? defaultValue() : key;
        }
    });
    private final DoubleSetting memory = number("background_ram", "Память по умолчанию (МБ)", MultiAccountBudget.DEFAULT_HEAP_MB, MultiAccountBudget.MIN_HEAP_MB, MultiAccountBudget.MAX_HEAP_MB, MultiAccountProfile.HEAP_STEP_MB);

    public MultiAccountModule() {
        super("multi_account", "Multi Account",
                "Подключает дополнительные аккаунты к текущему серверу. F9 открывает список аккаунтов.",
                ModuleCategory.MISC);
    }

    public void tickSessions() {
        if (MultiAccountWorker.requested()) return;
        if (mustStopSessions()) sessions.stopAll();
        sessions.tick(cycleKey());
    }

    private boolean mustStopSessions() {
        return !isConfiguredEnabled() || !AeroGuard.allowed() || ServerRestrictions.hidesModule(id());
    }

    @Subscribe
    private void key(KeyEvent event) {
        if (MultiAccountWorker.managesClient() || event.action() != LegacyInput.GLFW_PRESS
                || event.modifiers() != 0) return;
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() != null) return;
        if (event.key() == LegacyInput.GLFW_KEY_F9) {
            sessions.openMenu(client, null, null);
            event.setCancelled(true);
        } else if (event.key() == cycleKey() && sessions.playNext(client)) {
            event.setCancelled(true);
        }
    }

    public void openMenu() {
        if (!enableForMenu()) return;
        Minecraft client = Minecraft.getInstance();
        sessions.openMenu(client, client.gui.screen(), null);
    }

    public void swapAccount() {
        if (!enableForMenu()) return;
        Minecraft client = Minecraft.getInstance();
        if (!sessions.playNext(client)) sessions.openMenu(client, client.gui.screen(), null);
    }

    private boolean enableForMenu() {
        if (MultiAccountWorker.requested() || isServerBlocked() || !AeroGuard.allowed()) return false;
        if (!isConfiguredEnabled()) setEnabled(true);
        return isEnabled();
    }

    private int cycleKey() {
        return cycleKey.get().intValue();
    }

    int memoryMb() {
        return memory.get().intValue();
    }

    @Override
    protected void onDisable() {
        // Policy negotiation suspends controls while the existing worker connections stay alive.
        if (!isServerBlocked() || mustStopSessions()) sessions.stopAll();
    }
}
