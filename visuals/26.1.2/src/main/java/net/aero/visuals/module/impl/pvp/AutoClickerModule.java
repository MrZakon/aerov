package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.mixin.AutoClickerKeyAccess;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;

public final class AutoClickerModule extends Module {
    public enum MouseType { LEFT, RIGHT, BOTH }

    private final EnumSetting<MouseType> mouse = add(new EnumSetting<>(
            "type_mouse", "TypeMouse", "Левая — атака моба под прицелом, правая — повтор при удержании.", MouseType.LEFT, MouseType.class));
    private final DoubleSetting cps = add(new DoubleSetting(
            "click_cps", "CPS", "Число повторов в секунду при 20 тиках клиента.", 8, 1, 20, 1));
    private final BooleanSetting attackInBackground = add(new BooleanSetting(
            "attack_minimized", "Атака в фоне", "Продолжает автоатаку при Alt+Tab, переключении окна или сворачивании Minecraft.", true));
    private int leftPhase = 20;
    private int rightPhase;

    public AutoClickerModule() {
        super("auto_clicker", "AutoClicker", "Атакует моба под прицелом без удержания кнопки.", ModuleCategory.PVP);
        attackInBackground.visibleWhen(() -> mouse.get() != MouseType.RIGHT);
    }

    public void poll(Minecraft client) {
        if (!canRepeat(client) || client.player.isUsingItem()) {
            clearPhases();
            return;
        }

        MouseType type = mouse.get();
        boolean focused = client.isWindowActive();
        if (!focused && !allowsBackgroundAttack()) {
            clearPhases();
            return;
        }
        int rate = cps.get().intValue();
        boolean aimingAtMob = isAimingAtMob(client);
        pollAttack(client.options.keyAttack, type != MouseType.RIGHT && aimingAtMob, rate);
        rightPhase = repeat(client.options.keyUse, focused && type != MouseType.LEFT, rightPhase, rate);
    }

    private void pollAttack(KeyMapping key, boolean aimingAtMob, int rate) {
        var queue = (AutoClickerKeyAccess)(Object)key;
        if (aimingAtMob && queue.aero$getPendingClicks() != 0) {
            leftPhase = rate;
            return;
        }
        if (advanceAttack(aimingAtMob, rate)) queue.aero$setPendingClicks(1);
    }

    private boolean advanceAttack(boolean aimingAtMob, int rate) {
        if (!aimingAtMob) {
            leftPhase = Math.min(20, leftPhase + rate);
            return false;
        }
        boolean attack = leftPhase >= 20;
        if (attack) leftPhase -= 20;
        leftPhase += rate;
        return attack;
    }

    public boolean pollCoveredAttack(Minecraft client) {
        if (!allowsBackgroundAttack() || !canAttack(client) || client.gameMode == null
                || client.isPaused() || !client.player.isAlive() || client.player.isUsingItem()) {
            clearPhases();
            return false;
        }
        return advanceAttack(isAimingAtMob(client), cps.get().intValue());
    }

    private static boolean isAimingAtMob(Minecraft client) {
        return client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof Mob mob && mob.isAlive() && mob.isAttackable();
    }

    public boolean replacesHeldUse(Minecraft client) {
        return mouse.get() != MouseType.LEFT && client.isWindowActive() && canRepeat(client);
    }

    private boolean canRepeat(Minecraft client) {
        return client.screen == null && canAttack(client);
    }

    private boolean canAttack(Minecraft client) {
        return isEnabled() && client.player != null && client.level != null
                && !client.player.isSpectator();
    }

    private boolean allowsBackgroundAttack() {
        return attackInBackground.get() && mouse.get() != MouseType.RIGHT;
    }

    public static boolean keepPlayingInBackground(Minecraft client) {
        var runtime = ClientRuntime.getNullable();
        var module = runtime == null ? null : runtime.module(AutoClickerModule.class);
        return module != null && module.allowsBackgroundAttack() && module.canRepeat(client);
    }

    private static int repeat(KeyMapping key, boolean enabled, int phase, int rate) {
        if (!enabled || !key.isDown()) return 0;
        var queue = (AutoClickerKeyAccess)(Object)key;
        if (queue.aero$getPendingClicks() != 0) return 0;
        int next = phase + rate;
        if (next < 20) return next;
        queue.aero$setPendingClicks(1);
        return next - 20;
    }

    private void clearPhases() {
        leftPhase = 20;
        rightPhase = 0;
    }

    @Override
    protected void onDisable() {
        clearPhases();
    }
}
