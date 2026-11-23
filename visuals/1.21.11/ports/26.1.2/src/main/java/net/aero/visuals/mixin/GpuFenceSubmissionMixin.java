package net.aero.visuals.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlFence;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Submit queued GPU work before a blocking fence wait; never skip buffer synchronization. */
@Mixin(GlFence.class)
public abstract class GpuFenceSubmissionMixin {
    @Redirect(method="awaitCompletion",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/opengl/GlStateManager;_glClientWaitSync(JIJ)I"))
    private int aero$submitBeforeBlockingWait(long handle,int flags,long timeout){
        if(timeout>0&&Platform.get()==Platform.WINDOWS)flags|=GL32.GL_SYNC_FLUSH_COMMANDS_BIT;
        return GlStateManager._glClientWaitSync(handle,flags,timeout);
    }
}
