package net.aero.visuals.mixin;

import net.aero.visuals.render.FullscreenPresentation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class FullscreenGameRendererMixin {
    @Shadow @Final private MinecraftClient client;

    // Run after vanilla's focus-loss handling, before starting the camera/render profiler sections.
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/profiler/Profilers;get()Lnet/minecraft/util/profiler/Profiler;",
            ordinal = 0), cancellable = true)
    private void aero$skipHiddenDraw(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (!FullscreenPresentation.canPresent(client)) ci.cancel();
    }
}
