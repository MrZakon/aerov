package net.aero.visuals.feature.premium;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.util.Identifier;
import org.joml.*;
import java.nio.*;
import java.util.OptionalInt;

/** GPU-only scene snapshots and an independent glint mask. No readbacks, fences or window changes. */
public final class PremiumPostFx {
    private static final RenderPipeline PIPELINE=RenderPipeline.builder().withLocation(Identifier.of("aerovisuals","pipeline/premium_fx"))
        .withVertexShader(Identifier.of("aerovisuals","core/premium_fx")).withFragmentShader(Identifier.of("aerovisuals","core/premium_fx"))
        .withUniform("Premium",UniformType.UNIFORM_BUFFER).withSampler("Scene").withSampler("Depth").withSampler("Glint")
        .withVertexFormat(VertexFormats.EMPTY,VertexFormat.DrawMode.TRIANGLES).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).withoutBlend().build();
    private static final RenderPipeline WARMTH_PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.of("aerovisuals", "pipeline/premium_warmth"))
        .withVertexShader(Identifier.of("aerovisuals", "core/premium_fx"))
        .withFragmentShader(Identifier.of("aerovisuals", "core/premium_fx"))
        .withShaderDefine("AERO_WARMTH").withUniform("Premium", UniformType.UNIFORM_BUFFER)
        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).withBlend(new BlendFunction(
                SourceFactor.DST_COLOR, DestFactor.SRC_COLOR, SourceFactor.ONE, DestFactor.ZERO)).build();
    private static final RenderPipeline SKY_PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.of("aerovisuals", "pipeline/premium_sky"))
        .withVertexShader(Identifier.of("aerovisuals", "core/premium_fx"))
        .withFragmentShader(Identifier.of("aerovisuals", "core/premium_fx"))
        .withShaderDefine("AERO_REPLACE_SKY").withUniform("Premium", UniformType.UNIFORM_BUFFER).withSampler("Depth")
        .withVertexFormat(VertexFormats.EMPTY, VertexFormat.DrawMode.TRIANGLES)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).withoutBlend().build();
    private static SimpleFramebuffer source;private static Mask mask;private static GpuBuffer uniforms;
    private static final ByteBuffer data=ByteBuffer.allocateDirect(192).order(ByteOrder.nativeOrder());
    private static boolean active,maskUsed;private static final RenderLayer[] glints=new RenderLayer[4];
    private static final long STARTED=System.nanoTime();
    private static final Matrix4f inverseProjection = new Matrix4f();
    private static final Quaternionf inverseCameraRotation = new Quaternionf();
    private static final Matrix4f skyProjection = new Matrix4f();
    private static boolean skyProjectionCaptured;
    private static final OutputTarget TARGET = new OutputTarget("aero_glint_mask", () -> {
        maskUsed = true;
        return mask;
    });
    private static final class Mask extends SimpleFramebuffer {
        Mask(int w,int h){super("Aero enchantment mask",w,h,true);
            // Retain the depth-enabled flag, but borrow the live scene depth. Never own or delete it.
            if(depthAttachmentView!=null)depthAttachmentView.close();
            if(depthAttachment!=null)depthAttachment.close();
            depthAttachmentView=null;depthAttachment=null;}
        @Override public GpuTexture getDepthAttachment(){return MinecraftClient.getInstance().getFramebuffer().getDepthAttachment();}
        @Override public GpuTextureView getDepthAttachmentView(){return MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();}
    }
    private static boolean glintEnabled() {
        var glint = PremiumFeatures.get(PremiumFeatures.CustomGlint.class);
        return glint != null && glint.isEnabled() && glint.strength.get() > 0 && (glint.armor.get() || glint.items.get());
    }

    public static boolean hasGlintMask() { return active && maskUsed; }

    private static boolean useFastBloom() {
        var runtime = net.aero.visuals.ClientRuntime.getNullable();
        return net.aero.visuals.module.impl.misc.SmartEffectsModule.fastBloom() || runtime != null && runtime.settings().shaderQuality()
                == net.aero.visuals.config.ClientSettings.ShaderQuality.PERFORMANCE;
    }

    public static boolean needsSceneShading() {
        var shading = PremiumFeatures.get(PremiumFeatures.Shading.class);
        return shading != null && shading.isEnabled() && (shading.bloom.get() > 0 || shading.warmth.get() != 0);
    }

    public static boolean needsBackground() {
        if (PremiumFeatures.on(PremiumFeatures.SkyShader.class)) return true;
        var shading = PremiumFeatures.get(PremiumFeatures.Shading.class);
        return shading != null && shading.isEnabled() && shading.clouds.get() > 0;
    }
    public static void begin() {
        active = false;
        skyProjectionCaptured = false;
        var mc = MinecraftClient.getInstance();
        boolean sky = PremiumFeatures.on(PremiumFeatures.SkyShader.class);
        boolean needsGlint = glintEnabled();
        boolean drawBackground = needsBackground();
        var shading = PremiumFeatures.get(PremiumFeatures.Shading.class);
        boolean shadingEnabled = shading != null && shading.isEnabled();
        boolean mayCopyScene = needsGlint || shadingEnabled && shading.bloom.get() > 0 || !sky && drawBackground;
        if (mc.world == null || !drawBackground && !needsGlint && !needsSceneShading()) {
            close();
            return;
        }
        var main = mc.getFramebuffer();
        int w = main.textureWidth, h = main.textureHeight;
        if (w <= 0 || h <= 0 || main.getDepthAttachmentView() == null) return;
        if (source != null && (!mayCopyScene || source.textureWidth != w || source.textureHeight != h)) releaseSource();
        if (mask != null && (!needsGlint || mask.textureWidth != w || mask.textureHeight != h)) releaseMask();
        if (needsGlint && mask == null) {
            mask = new Mask(w, h);
            maskUsed = true;
        }
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(() -> "Aero premium effects",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 192);
        }
        clearUsedMask();
        active = true;
    }
    private static void clearUsedMask() {
        if (!maskUsed || mask == null) return;
        clearMask();
        maskUsed = false;
    }

    private static void clearMask(){if(mask!=null)RenderSystem.getDevice().createCommandEncoder().clearColorTexture(mask.getColorAttachment(),0);}
    public static RenderLayer glint(RenderLayer vanilla,int kind){var m=PremiumFeatures.get(PremiumFeatures.CustomGlint.class);if(!active||mask==null||m==null||!m.isEnabled()||m.strength.get()<=0||(kind==0?!m.armor.get():!m.items.get()))return vanilla;var layer=glints[kind];if(layer==null){var setup=RenderSetup.builder(vanilla.getRenderPipeline()).texture("Sampler0",kind==0?ItemRenderer.ENTITY_ENCHANTMENT_GLINT:ItemRenderer.ITEM_ENCHANTMENT_GLINT).textureTransform(kind==0?TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING:kind==3?TextureTransform.ENTITY_GLINT_TEXTURING:TextureTransform.GLINT_TEXTURING).outputTarget(TARGET);if(kind==0)setup.layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);glints[kind]=layer=RenderLayer.of("aero_enchantment_"+kind,setup.build());}return layer;}
    public static void captureProjection(Matrix4f projection) {
        if (!active || !needsBackground()) return;
        skyProjection.set(projection);
        skyProjectionCaptured = true;
    }
    /** Composite the background before depthless particles/entities reach the scene. */
    public static void background(){
        if (active && skyProjectionCaptured && needsBackground()) apply(skyProjection, true, true);
    }
    public static void apply(Matrix4f projection,boolean world){apply(projection,world,false);}
    private static void apply(Matrix4f projection,boolean world,boolean background){
        long started = net.aero.visuals.render.performance.PerformanceProbe.begin();
        try { applyMeasured(projection, world, background); }
        finally { net.aero.visuals.render.performance.PerformanceProbe.end("Шейдинг / bloom", started); }
    }
    private static void applyMeasured(Matrix4f projection,boolean world,boolean background){if(!active)return;var mc=MinecraftClient.getInstance();var main=mc.getFramebuffer();if(main.getDepthAttachmentView()==null)return;
        var sky=PremiumFeatures.get(PremiumFeatures.SkyShader.class);var shading=PremiumFeatures.get(PremiumFeatures.Shading.class);var glint=PremiumFeatures.get(PremiumFeatures.CustomGlint.class);
        boolean s=background&&sky!=null&&sky.isEnabled(),l=world&&shading!=null&&shading.isEnabled(),g=!background&&maskUsed&&glint!=null&&glint.isEnabled()&&glint.strength.get()>0;
        float bloom = !background && l ? shading.bloom.get().floatValue() : 0;
        float warmth = !background && l ? shading.warmth.get().floatValue() : 0;
        if (!background && !g && bloom == 0 && warmth == 0) return;
        boolean warmthOnly = !background && !g && bloom == 0;
        boolean copyScene = !s && !warmthOnly;
        data.clear();
        if (background) inverseProjection.set(projection).rotate(inverseCameraRotation.set(mc.gameRenderer.getCamera().getRotation()).conjugate()).invert().get(data);
        data.position(64);
        put(main.textureWidth,main.textureHeight,(System.nanoTime()-STARTED)/1e9f,background?1:0);
        put(s?sky.style.get().ordinal()+1:0,s?sky.speed.get().floatValue():0,s?sky.strength.get().floatValue():0,background&&l?shading.clouds.get().floatValue():0);color(s?sky.tint.get().eval():0);
        put(bloom, warmth, bloom > 0 && useFastBloom() ? 1 : 0, 0);
        put(g?glint.strength.get().floatValue():0,g?glint.radius.get().floatValue():1,0,0);color(g?glint.tint.get().eval():0);
        var p=mc.gameRenderer.getCamera().getCameraPos();put((float)p.x,(float)p.y,(float)p.z,0);put(l?shading.bloomRadius.get().floatValue():10,l?shading.bloomThreshold.get().floatValue():.8f,l?shading.cloudHeight.get().floatValue():192,world&&sky!=null&&sky.isEnabled()?1:0);data.flip();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (copyScene) {
            if (source == null) {
                int w = main.textureWidth, h = main.textureHeight;
                source = new SimpleFramebuffer("Aero premium snapshot", w, h, false);
            }

            encoder.copyTextureToTexture(main.getColorAttachment(), source.getColorAttachment(),
                    0, 0, 0, 0, 0, main.textureWidth, main.textureHeight);
        }
        encoder.writeToBuffer(uniforms.slice(), data);
        try (var pass = encoder.createRenderPass(() -> "Aero premium effects", main.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(s ? SKY_PIPELINE : warmthOnly ? WARMTH_PIPELINE : PIPELINE);
            pass.setUniform("Premium", uniforms);
            if (!warmthOnly) pass.bindTexture("Depth", main.getDepthAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            if (copyScene) {
                pass.bindTexture("Scene", source.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                // The shader samples Glint only when glint strength is nonzero.
                pass.bindTexture("Glint", g ? mask.getColorAttachmentView() : source.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            }
            pass.draw(0, 3);
        }
        if (!background) clearUsedMask();
    }
    public static void finish(Matrix4f projection){apply(projection,false);active=false;}
    private static void put(float x,float y,float z,float w){data.putFloat(x).putFloat(y).putFloat(z).putFloat(w);}
    private static void color(int c){put((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,(c>>>24)/255f);}
    private static void releaseSource() {
        var previous = source;
        source = null;
        if (previous != null) previous.delete();
    }

    private static void releaseMask() {
        var previous = mask;
        mask = null;
        maskUsed = false;
        if (previous != null) previous.delete();
    }

    public static void close() {
        active = false;
        skyProjectionCaptured = false;
        releaseSource();
        releaseMask();
        var previous = uniforms;
        uniforms = null;
        if (previous != null) previous.close();
    }
}
