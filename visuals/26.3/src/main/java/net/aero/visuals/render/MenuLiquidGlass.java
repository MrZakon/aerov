package net.aero.visuals.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.gui.AeroMultiplayerScreen;
import net.aero.visuals.gui.AeroWorldSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Refracts the actual artwork layer used by the two selection screens.
 * Uses the GUI render pipeline, so drawing remains ordered with native widgets.
 * No framebuffer feedback, readback, or previous-frame sampling is involved.
 */
public final class MenuLiquidGlass {
    private static final Identifier ART = Identifier.fromNamespaceAndPath("aerovisuals", "mainmenu/bg-sunny-forest.png");
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerovisuals", "pipeline/menu_liquid_glass"))
            .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals", "core/menu_liquid_glass"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals", "core/menu_liquid_glass"))
            .withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder().withUniform("Sampler0",com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build())
            .withVertexBinding(0,DefaultVertexFormat.POSITION_TEX_COLOR).withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS)
            .withDepthStencilState(java.util.Optional.empty()).withCull(false)
            .withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(BlendFunction.TRANSLUCENT)).build();

    private static double artX, artY, artW, artH;

    private MenuLiquidGlass() {}

    public static boolean applies() {
        var screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AeroWorldSelectScreen || screen instanceof AeroMultiplayerScreen;
    }

    /** Exactly matches AeroMenuChrome.background's cover, source inset and pointer parallax. */
    public static void prepare(int width, int height, int mouseX, int mouseY) {
        boolean still = ClientRuntime.get().settings().reducedMotion();
        double px = still ? 0 : (mouseX / (double) Math.max(1, width) - 0.5) * 16;
        double py = still ? 0 : (mouseY / (double) Math.max(1, height) - 0.5) * 10;
        double cover = Math.max(width / 1024.0, height / 573.0) * 1.08;
        artW = 1024 * cover;
        artH = 573 * cover;
        artX = (width - artW) / 2 - px;
        artY = (height - artH) / 2 - py;
    }

    public static void draw(GuiGraphicsExtractor context, double x, double y, double w, double h, double radius) {
        if (artW <= 0 || artH <= 0) return;
        // Vertex color carries physical corner radius and GUI scale; the shader does not tint by it.
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int r = Math.clamp((int) Math.round(radius * scale), 1, 255);
        int s = Math.clamp((int) Math.round(scale * 16), 1, 255);
        int parameters = 0xFF000000 | r << 16 | s << 8;
        float u = (float) (2 + (x - artX) / artW * 1020);
        float v = (float) (2 + (y - artY) / artH * 569);
        context.blit(PIPELINE, ART, (int) Math.round(x), (int) Math.round(y), u, v,
                (int) Math.round(w), (int) Math.round(h),
                Math.max(1, (int) Math.round(w / artW * 1020)),
                Math.max(1, (int) Math.round(h / artH * 569)), 1024, 573, parameters);
    }
}
