package net.aero.visuals.gui;

import net.aero.visuals.AeroVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Applies the same AeroVisuals artwork to native window chrome and in-game branding. */
public final class AeroBranding {
    private static final Identifier WINDOW_ICON = Identifier.of(AeroVisuals.MOD_ID, "textures/gui/logo.png");
    private static final int[] ICON_SIZES = {16, 32, 48, 64, 128};

    private AeroBranding() {}

    public static boolean applyWindowIcon(MinecraftClient client) {
        if (client.getWindow() == null || client.getWindow().getHandle() == 0L) return false;

        List<ByteBuffer> pixels = new ArrayList<>(ICON_SIZES.length);
        try {
            var resource = client.getResourceManager().getResource(WINDOW_ICON)
                    .orElseThrow(() -> new IllegalStateException("Missing AeroVisuals window icon"));
            try (InputStream stream = resource.getInputStream();
                 NativeImage source = NativeImage.read(stream);
                 MemoryStack stack = MemoryStack.stackPush()) {
                GLFWImage.Buffer icons = GLFWImage.malloc(ICON_SIZES.length, stack);
                for (int index = 0; index < ICON_SIZES.length; index++) {
                    int size = ICON_SIZES[index];
                    try (NativeImage scaled = new NativeImage(size, size, true)) {
                        source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), scaled);
                        ByteBuffer buffer = MemoryUtil.memAlloc(size * size * 4);
                        pixels.add(buffer);
                        buffer.asIntBuffer().put(scaled.copyPixelsAbgr());
                        icons.position(index).width(size).height(size).pixels(buffer);
                    }
                }
                GLFW.glfwSetWindowIcon(client.getWindow().getHandle(), icons.position(0));
            }
            return true;
        } catch (Exception exception) {
            AeroVisuals.LOGGER.warn("Could not apply the AeroVisuals window icon", exception);
            return true;
        } finally {
            pixels.forEach(MemoryUtil::memFree);
        }
    }
}
