package net.aero.visuals.animation;
import net.aero.visuals.ClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.lang.ref.WeakReference;
import java.util.*;
public final class ChatMotion {
    private static final Map<net.minecraft.util.FormattedCharSequence,Long> arrivals=Collections.synchronizedMap(new WeakHashMap<>());
    private static final long MESSAGE_DURATION_NANOS = 220_000_000L;
    private static long opened,closed,lastArrival;
    private static float closeAmount;
    private static WeakReference<Object> world;
    public static boolean reduced(){var r=ClientRuntime.getNullable();return r!=null&&r.settings().reducedMotion();}
    private static void resetWorld() {
        var current = Minecraft.getInstance().level;
        if (current == null ? world == null : world != null && world.get() == current) return;
        world = current == null ? null : new WeakReference<>(current);
        arrivals.clear();
        opened = closed = lastArrival = 0;
        closeAmount = 0;
    }
    public static void arrived(net.minecraft.util.FormattedCharSequence text) {
        resetWorld();
        if (reduced()) return;
        lastArrival = System.nanoTime();
        arrivals.put(text, lastArrival);
    }

    public static boolean hasActiveMessages() {
        resetWorld();
        if (lastArrival == 0) return false;
        if (reduced() || System.nanoTime() - lastArrival >= MESSAGE_DURATION_NANOS) {
            arrivals.clear();
            lastArrival = 0;
            return false;
        }
        return true;
    }

    public static float message(net.minecraft.util.FormattedCharSequence text) {
        Long time = arrivals.get(text);
        return time == null ? 1 : ease((System.nanoTime() - time) / (float) MESSAGE_DURATION_NANOS);
    }
    public static void open(){resetWorld();opened=System.nanoTime();closed=0;}
    public static float input(){resetWorld();return reduced()||opened==0?1:ease((System.nanoTime()-opened)/200_000_000f);}
    public static void close() {
        resetWorld();
        if (opened == 0 || reduced()) {
            opened = closed = 0;
            closeAmount = 0;
            return;
        }
        closeAmount = input();
        closed = System.nanoTime();
        opened = 0;
    }
    public static void drawClosing(GuiGraphicsExtractor d) {
        resetWorld();
        if (closed == 0) return;
        float t = Math.clamp((System.nanoTime() - closed) / 150_000_000f, 0, 1);
        if (t >= 1) {
            closed = 0;
            return;
        }
        var c = Minecraft.getInstance();
        if (c.gui.screen() != null || c.player == null || net.aero.visuals.render.ClientView.hud().isHidden() || reduced()) return;
        float amount = closeAmount * (1 - ease(t));
        int y = d.guiHeight() - 14 + Math.round((1 - amount) * 16);
        d.fill(2, y, d.guiWidth() - 2, y + 12, ((int)(amount * 128) << 24));
    }
    public static float ease(float t){t=Math.clamp(t,0,1);return 1-(1-t)*(1-t)*(1-t);}
}
