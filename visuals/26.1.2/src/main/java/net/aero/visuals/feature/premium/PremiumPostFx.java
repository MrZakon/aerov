package net.aero.visuals.feature.premium;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;

import net.minecraft.resources.Identifier;
import org.joml.*;
import java.nio.*;
import java.util.OptionalInt;

/** GPU-only scene snapshots and an independent glint mask. No readbacks, fences or window changes. */
public final class PremiumPostFx {
    private static final RenderPipeline PIPELINE=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/premium_fx"))
        .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/premium_fx")).withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/premium_fx"))
        .withUniform("Premium",UniformType.UNIFORM_BUFFER).withSampler("Scene").withSampler("Depth").withSampler("Glint")
        .withVertexFormat(DefaultVertexFormat.EMPTY,VertexFormat.Mode.TRIANGLES).withDepthStencilState(java.util.Optional.empty()).withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
    private static final RenderPipeline WARMTH_PIPELINE = RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/premium_warmth"))
        .withShaderDefine("AERO_WARMTH").withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/premium_fx")).withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/premium_fx"))
        .withUniform("Premium",UniformType.UNIFORM_BUFFER)
        .withVertexFormat(DefaultVertexFormat.EMPTY,VertexFormat.Mode.TRIANGLES).withDepthStencilState(java.util.Optional.empty()).withCull(false).withColorTargetState(new ColorTargetState(new BlendFunction(com.mojang.blaze3d.platform.SourceFactor.DST_COLOR, com.mojang.blaze3d.platform.DestFactor.SRC_COLOR, com.mojang.blaze3d.platform.SourceFactor.ONE, com.mojang.blaze3d.platform.DestFactor.ZERO))).build();
    private static final RenderPipeline SKY_PIPELINE = RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/premium_sky"))
        .withShaderDefine("AERO_REPLACE_SKY").withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/premium_fx")).withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/premium_fx"))
        .withUniform("Premium",UniformType.UNIFORM_BUFFER).withSampler("Depth")
        .withVertexFormat(DefaultVertexFormat.EMPTY,VertexFormat.Mode.TRIANGLES).withDepthStencilState(java.util.Optional.empty()).withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
    private static TextureTarget source;private static Mask mask;private static GpuBuffer uniforms;
    private static final ByteBuffer data=ByteBuffer.allocateDirect(192).order(ByteOrder.nativeOrder());
    private static boolean active,maskUsed;private static final RenderType[] glints=new RenderType[4];
    private static final long STARTED=System.nanoTime();
    private static final Matrix4f inverseProjection = new Matrix4f();
    private static final Quaternionf inverseCameraRotation = new Quaternionf();
    private static final Matrix4f skyProjection = new Matrix4f();
    private static boolean skyProjectionCaptured;
    private static final OutputTarget TARGET = new OutputTarget("aero_glint_mask", () -> {
        maskUsed = true;
        return mask;
    });
    private static final class Mask extends TextureTarget {
        Mask(int w,int h){super("Aero enchantment mask",w,h,true);
            // Retain the depth-enabled flag, but borrow the live scene depth. Never own or delete it.
            if(depthTextureView!=null)depthTextureView.close();
            if(depthTexture!=null)depthTexture.close();
            depthTextureView=null;depthTexture=null;}
        @Override public GpuTexture getDepthTexture(){return Minecraft.getInstance().getMainRenderTarget().getDepthTexture();}
        @Override public GpuTextureView getDepthTextureView(){return Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();}
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
        var mc = Minecraft.getInstance();
        boolean sky = PremiumFeatures.on(PremiumFeatures.SkyShader.class);
        boolean needsGlint = glintEnabled();
        boolean drawBackground = needsBackground();
        var shading = PremiumFeatures.get(PremiumFeatures.Shading.class);
        boolean shadingEnabled = shading != null && shading.isEnabled();
        boolean mayCopyScene = needsGlint || shadingEnabled && shading.bloom.get() > 0 || !sky && drawBackground;
        if (mc.level == null || !drawBackground && !needsGlint && !needsSceneShading()) {
            close();
            return;
        }
        var main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        if (w <= 0 || h <= 0 || main.getDepthTextureView() == null) return;
        if (source != null && (!mayCopyScene || source.width != w || source.height != h)) releaseSource();
        if (mask != null && (!needsGlint || mask.width != w || mask.height != h)) releaseMask();
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

    private static void clearMask(){if(mask!=null)RenderSystem.getDevice().createCommandEncoder().clearColorTexture(mask.getColorTexture(),0);}
    public static RenderType glint(RenderType vanilla,int kind){var m=PremiumFeatures.get(PremiumFeatures.CustomGlint.class);if(!active||mask==null||m==null||!m.isEnabled()||m.strength.get()<=0||(kind==0?!m.armor.get():!m.items.get()))return vanilla;var layer=glints[kind];if(layer==null){var setup=RenderSetup.builder(vanilla.pipeline()).withTexture("Sampler0",kind==0?ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR:ItemFeatureRenderer.ENCHANTED_GLINT_ITEM).setTextureTransform(kind==0?TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING:kind==3?TextureTransform.ENTITY_GLINT_TEXTURING:TextureTransform.GLINT_TEXTURING).setOutputTarget(TARGET);if(kind==0)setup.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);glints[kind]=layer=RenderType.create("aero_enchantment_"+kind,setup.createRenderSetup());}return layer;}
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
    private static void applyMeasured(Matrix4f projection,boolean world,boolean background){if(!active)return;var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();if(main.getDepthTextureView()==null)return;
        var sky=PremiumFeatures.get(PremiumFeatures.SkyShader.class);var shading=PremiumFeatures.get(PremiumFeatures.Shading.class);var glint=PremiumFeatures.get(PremiumFeatures.CustomGlint.class);
        boolean s=background&&sky!=null&&sky.isEnabled(),l=world&&shading!=null&&shading.isEnabled(),g=!background&&maskUsed&&glint!=null&&glint.isEnabled()&&glint.strength.get()>0;
        float bloom = !background && l ? shading.bloom.get().floatValue() : 0;
        float warmth = !background && l ? shading.warmth.get().floatValue() : 0;
        if (!background && !g && bloom == 0 && warmth == 0) return;
        boolean warmthOnly = !background && !g && bloom == 0;
        boolean copyScene = !s && !warmthOnly;
        data.clear();
        if (background) inverseProjection.set(projection).rotate(inverseCameraRotation.set(mc.gameRenderer.getMainCamera().rotation()).conjugate()).invert().get(data);
        data.position(64);
        put(main.width,main.height,(System.nanoTime()-STARTED)/1e9f,background?1:0);
        put(s?sky.style.get().ordinal()+1:0,s?sky.speed.get().floatValue():0,s?sky.strength.get().floatValue():0,background&&l?shading.clouds.get().floatValue():0);color(s?sky.tint.get().eval():0);
        put(bloom, warmth, bloom > 0 && useFastBloom() ? 1 : 0, 0);
        put(g?glint.strength.get().floatValue():0,g?glint.radius.get().floatValue():1,0,0);color(g?glint.tint.get().eval():0);
        var p=mc.gameRenderer.getMainCamera().position();put((float)p.x,(float)p.y,(float)p.z,0);put(l?shading.bloomRadius.get().floatValue():10,l?shading.bloomThreshold.get().floatValue():.8f,l?shading.cloudHeight.get().floatValue():192,world&&sky!=null&&sky.isEnabled()?1:0);data.flip();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (copyScene) {
            if (source == null) {
                int w = main.width, h = main.height;
                source = new TextureTarget("Aero premium snapshot", w, h, false);
            }

            encoder.copyTextureToTexture(main.getColorTexture(), source.getColorTexture(),
                    0, 0, 0, 0, 0, main.width, main.height);
        }
        encoder.writeToBuffer(uniforms.slice(), data);
        try (var pass = encoder.createRenderPass(() -> "Aero premium effects", main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(s ? SKY_PIPELINE : warmthOnly ? WARMTH_PIPELINE : PIPELINE);
            pass.setUniform("Premium", uniforms);
            if (!warmthOnly) pass.bindTexture("Depth", main.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            if (copyScene) {
                pass.bindTexture("Scene", source.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                // The shader samples Glint only when glint strength is nonzero.
                pass.bindTexture("Glint", g ? mask.getColorTextureView() : source.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
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
        if (previous != null) previous.destroyBuffers();
    }

    private static void releaseMask() {
        var previous = mask;
        mask = null;
        maskUsed = false;
        if (previous != null) previous.destroyBuffers();
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
