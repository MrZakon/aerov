package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.opengl.BufferStorage;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Locale;

@Mixin(BufferStorage.class)
public abstract class NvidiaBufferStorageMixin {
    @ModifyExpressionValue(method = "create", at = @At(value = "FIELD",
            target = "Lcom/mojang/blaze3d/opengl/GlDevice;USE_GL_ARB_buffer_storage:Z"))
    private static boolean aero$avoidPersistentMappingOnWindowsNvidia(boolean vanilla) {
        if (!vanilla || !System.getProperty("os.name", "").startsWith("Windows")) return vanilla;
        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        // Use Minecraft's mutable storage path for NVIDIA's Windows OpenGL driver.
        return vendor == null || !vendor.toLowerCase(Locale.ROOT).contains("nvidia");
    }
}
