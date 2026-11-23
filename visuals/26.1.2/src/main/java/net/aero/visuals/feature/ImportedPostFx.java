package net.aero.visuals.feature;
import net.aero.visuals.ClientRuntime;
import net.minecraft.client.renderer.DynamicUniformStorage;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;

import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;

import net.minecraft.resources.Identifier;
import org.joml.*;
import java.nio.*;
import java.util.OptionalDouble;
import java.util.OptionalInt;
/** Depth fog before the hand depth clear; hand materials only after hand commands are drawn. */
public final class ImportedPostFx {
    private static final RenderPipeline GLASS_PIPELINE=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/imported_fx"))
        .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/imported_fx")).withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/imported_fx"))
        .withUniform("Effects",UniformType.UNIFORM_BUFFER).withSampler("Scene").withSampler("Depth").withVertexFormat(DefaultVertexFormat.EMPTY,VertexFormat.Mode.TRIANGLES)
        .withDepthStencilState(java.util.Optional.empty()).withCull(false).withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT).build();
    private static final RenderPipeline FOG_TINT_PIPELINE=RenderPipeline.builder().withShaderDefine("AERO_BLEND").withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/imported_fx_blend"))
        .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/imported_fx")).withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/imported_fx"))
        .withUniform("Effects",UniformType.UNIFORM_BUFFER).withSampler("Depth").withVertexFormat(DefaultVertexFormat.EMPTY,VertexFormat.Mode.TRIANGLES)
        .withDepthStencilState(java.util.Optional.empty()).withCull(false).withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)).build();
    private static final RenderPipeline HAND_DEPTH_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerovisuals", "pipeline/imported_hand_depth"))
            .withShaderDefine("AERO_HAND_DEPTH")
            .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals", "core/imported_fx"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals", "core/imported_fx"))
            .withUniform("Effects", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, false))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();
    private static final RenderPipeline FOG_BLUR_TWO = createFogBlurPipeline(2);
    private static final RenderPipeline FOG_BLUR_FOUR = createFogBlurPipeline(4);
    private static final RenderPipeline FOG_BLUR_SIX = createFogBlurPipeline(6);
    private static final int UNIFORM_BYTES = 144;

    private static RenderPipeline createFogBlurPipeline(int samples) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("aerovisuals", "pipeline/imported_fog_" + samples))
                .withShaderDefine("AERO_FOG_TAPS", samples)
                .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals", "core/imported_fx"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals", "core/imported_fx"))
                .withUniform("Effects", UniformType.UNIFORM_BUFFER)
                .withSampler("Scene").withSampler("Depth")
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(java.util.Optional.empty())
                .withCull(false)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .build();
    }

    private static RenderPipeline fogBlurPipeline() {
        var runtime = ClientRuntime.getNullable();
        if (runtime == null) return FOG_BLUR_SIX;
        int requested = switch (runtime.settings().shaderQuality()) {
            case PERFORMANCE -> 2;
            case BALANCED -> 4;
            case QUALITY -> 6;
        };
        return switch (net.aero.visuals.module.impl.misc.SmartEffectsModule.fogTaps(requested)) {
            case 2 -> FOG_BLUR_TWO;
            case 4 -> FOG_BLUR_FOUR;
            default -> FOG_BLUR_SIX;
        };
    }
    private static TextureTarget source;
    private static DynamicUniformStorage<DynamicUniformStorage.DynamicUniform> uniforms;
    private static final ByteBuffer data = ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder());
    private static boolean wroteUniforms;
    private static final Matrix4f inverseProjection = new Matrix4f();
    public static void beginFrame() {
        if (source != null) {
            var fog = ImportedFeatures.get(ImportedFeatures.Fog.class);
            var hand = ImportedFeatures.get(ImportedFeatures.Hands.class);
            boolean needsFogCopy = fog != null && fog.isEnabled() && fogNeedsSceneCopy(fog);
            boolean needsHandCopy = needsPass(true) && hand.style.get() == ImportedFeatures.Hands.Style.GLASS;
            if (!needsFogCopy && !needsHandCopy) releaseSource();
        }
        if (uniforms != null && !needsPass(false) && !needsPass(true)) close();
    }

    private static boolean fogNeedsSceneCopy(ImportedFeatures.Fog fog) {
        return net.aero.visuals.module.impl.misc.SmartEffectsModule.fogBlur(fog.blur.get().floatValue()) * fog.strength.get().floatValue() > .5f;
    }

    public static boolean needsPass(boolean hands){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return false;
        if(!hands){var fog=ImportedFeatures.get(ImportedFeatures.Fog.class);return fog!=null&&fog.isEnabled();}
        var hand=ImportedFeatures.get(ImportedFeatures.Hands.class);
        return hand!=null&&hand.isEnabled()&&mc.options.getCameraType().isFirstPerson()&&!mc.options.hideGui&&!mc.player.isSpectator();
    }
    public static void apply(Matrix4f projection,boolean hands) {
        long started = net.aero.visuals.render.performance.PerformanceProbe.begin();
        try { applyMeasured(projection, hands); }
        finally { net.aero.visuals.render.performance.PerformanceProbe.end(hands ? "Материал рук" : "Туман", started); }
    }
    private static void applyMeasured(Matrix4f projection,boolean hands) {var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var fog=ImportedFeatures.get(ImportedFeatures.Fog.class);var hand=ImportedFeatures.get(ImportedFeatures.Hands.class);
        boolean f=!hands&&fog!=null&&fog.isEnabled(),h=hands&&hand!=null&&hand.isEnabled()&&mc.options.getCameraType().isFirstPerson()&&!mc.options.hideGui&&!mc.player.isSpectator();if(!f&&!h)return;
        var main=mc.getMainRenderTarget();int w=main.width,height=main.height;if(main.getDepthTextureView()==null||w<=0||height<=0)return;
        boolean copyScene = f ? fogNeedsSceneCopy(fog) : hand.style.get() == ImportedFeatures.Hands.Style.GLASS;
        if (copyScene && (source == null || source.width != w || source.height != height)) {
            releaseSource();
            source = new TextureTarget("Aero scene snapshot",w,height,false);
        }
        if (uniforms == null) uniforms = new DynamicUniformStorage<>("Aero depth effects", UNIFORM_BYTES, 2);
        data.clear();if(f)inverseProjection.set(projection).invert().get(data);data.position(64);
        put(w,height,(System.nanoTime()%3_600_000_000_000L)/1e9f,0);put(f?fog.start.get().floatValue():0,f?fog.range.get().floatValue():1,f?fog.strength.get().floatValue():0,f?net.aero.visuals.module.impl.misc.SmartEffectsModule.fogBlur(fog.blur.get().floatValue()):0);color(f?fog.color.get().eval():0);
        put(h?hand.style.get().ordinal()+1:0,h?hand.opacity.get().floatValue():0,h?hand.speed.get().floatValue():1,h?hand.scale.get().floatValue():1);color(h?hand.color.get().eval():0);
        data.flip();
        var encoder=RenderSystem.getDevice().createCommandEncoder();if(copyScene)encoder.copyTextureToTexture(main.getColorTexture(),source.getColorTexture(),0,0,0,0,0,w,height);
        var payload = data.slice();
        var uniform = uniforms.writeUniform(targetBuffer -> targetBuffer.put(payload));
        wroteUniforms = true;
        var depth = main.getDepthTextureView();
        if (hands && !copyScene) {
            // Keep the hand depth produced by Minecraft. The GPU rejects untouched pixels before shading.
            try (var pass = encoder.createRenderPass(() -> "Aero hand material", main.getColorTextureView(),
                    OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(HAND_DEPTH_PIPELINE);
                pass.setUniform("Effects", uniform);
                pass.draw(0, 3);
            }
            return;
        }
        try (var pass = encoder.createRenderPass(() -> "Aero depth effects", main.getColorTextureView(),
                OptionalInt.empty())) {
            RenderPipeline pipeline = GLASS_PIPELINE;
            if (!hands) pipeline = copyScene ? fogBlurPipeline() : FOG_TINT_PIPELINE;
            pass.setPipeline(pipeline);
            pass.setUniform("Effects", uniform);
            if (copyScene) {
                pass.bindTexture("Scene", source.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            }
            pass.bindTexture("Depth", depth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }

    public static void finishFrame() {
        if (!wroteUniforms) return;
        uniforms.endFrame();
        wroteUniforms = false;
    }

    private static void put(float x,float y,float z,float w){data.putFloat(x).putFloat(y).putFloat(z).putFloat(w);}private static void color(int c){put((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,(c>>>24)/255f);}
    private static void releaseSource() {
        var previous = source;
        source = null;
        if (previous != null) previous.destroyBuffers();
    }

    public static void close() {
        releaseSource();
        var previous = uniforms;
        uniforms = null;
        wroteUniforms = false;
        if (previous != null) previous.close();
    }
}
