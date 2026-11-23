package net.aero.visuals.render;

import net.aero.visuals.render.font.MsdfFontRenderer;
import net.aero.visuals.render.shader.ShaderManager;

/** Единый владелец GPU-ресурсов и шрифтов клиента. */
public final class RenderEngine implements AutoCloseable {
    private final ShaderManager shaders = new ShaderManager();
    private final MsdfFontRenderer fonts = new MsdfFontRenderer();

    public void beginFrame(boolean enableCustomShaders) {
        if (enableCustomShaders) shaders.initializeOnRenderThread();
        else if (shaders.isInitialized()) shaders.close();
    }

    public ShaderManager shaders() { return shaders; }
    public MsdfFontRenderer fonts() { return fonts; }

    public void reloadResources() {
        fonts.close();
        net.aero.visuals.notification.NotificationManager.onResourcesReloaded();
        if (shaders.isInitialized()) shaders.reload();
    }

    @Override
    public void close() {
        fonts.close();
        shaders.close();
    }
}
