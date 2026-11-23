package net.aero.visuals.mixin;
import net.aero.visuals.animation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerTabOverlay.class)
public abstract class AnimatedPlayerListMixin implements AnimatedPlayerList {
    @Unique private boolean aero$target;
    @Unique private double aero$progress;
    @Unique private long aero$frame;
    @Unique private java.lang.ref.WeakReference<Object> aero$world;
    @Unique private Scoreboard aero$board;
    @Unique private Objective aero$objective;

    @Unique
    private void aero$syncWorld() {
        var current = Minecraft.getInstance().level;
        if (aero$world == null ? current == null : current != null && aero$world.get() == current) return;
        aero$world = current == null ? null : new java.lang.ref.WeakReference<>(current);
        aero$target = false;
        aero$clearAnimation();
    }

    @Unique
    private void aero$clearAnimation() {
        aero$progress = 0;
        aero$frame = 0;
        aero$board = null;
        aero$objective = null;
    }

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void aero$visible(boolean visible, CallbackInfo ci) {
        aero$syncWorld();
        aero$target = visible;
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void aero$start(GuiGraphicsExtractor draw, int width, Scoreboard board, Objective objective, CallbackInfo ci) {
        if (aero$target) {
            aero$board = board;
            aero$objective = objective;
        }
        long now = System.nanoTime();
        double delta = aero$frame == 0 ? .016 : Math.clamp((now - aero$frame) / 1e9, 0, aero$target ? .05 : .15);
        aero$frame = now;
        aero$progress = ChatMotion.reduced() ? (aero$target ? 1 : 0)
                : Math.clamp(aero$progress + (aero$target ? delta / .2 : -delta / .15), 0, 1);
        float ease = ChatMotion.ease((float) aero$progress);
        float scale = .94f + .06f * ease;
        var matrices = draw.pose();
        matrices.pushMatrix();
        matrices.translate(width / 2f, 10 - (1 - ease) * 14);
        matrices.scale(scale, scale);
        matrices.translate(-width / 2f, -10);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void aero$end(GuiGraphicsExtractor draw, int width, Scoreboard board, Objective objective, CallbackInfo ci) {
        draw.pose().popMatrix();
    }

    @Override
    public void aero$closing(GuiGraphicsExtractor draw) {
        aero$syncWorld();
        if (aero$target || aero$board == null) return;
        if (aero$progress <= 0 || ChatMotion.reduced()
                || System.nanoTime() - aero$frame >= aero$progress * 150_000_000L) {
            aero$clearAnimation();
            return;
        }
        ((PlayerTabOverlay) (Object) this).extractRenderState(draw, draw.guiWidth(), aero$board, aero$objective);
    }
}
