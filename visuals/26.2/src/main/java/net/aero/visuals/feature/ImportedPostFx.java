package net.aero.visuals.feature;

import net.aero.visuals.ClientRuntime;
import net.minecraft.client.renderer.DynamicUniformStorage;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;

import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;

import net.minecraft.resources.Identifier;
import org.joml.*;
import java.nio.*;
import java.util.OptionalInt;
/** Depth fog before the hand depth clear; hand materials only after hand commands are drawn. */
public final class ImportedPostFx {
    private static final RenderPipeline PIPELINE=RenderPipeline.builder().withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/imported_fx"))
        .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/imported_fx")).withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/imported_fx"))
        .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform("Effects",UniformType.UNIFORM_BUFFER).withSampler("Scene").withSampler("Depth").build()).withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
        .withDepthStencilState(java.util.Optional.empty()).withCull(false).withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT).build();
    private static final RenderPipeline BLEND_PIPELINE=RenderPipeline.builder().withShaderDefine("AERO_BLEND").withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/imported_fx_blend"))
        .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/imported_fx")).withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/imported_fx"))
        .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform("Effects",UniformType.UNIFORM_BUFFER).withSampler("Depth").build()).withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.TRIANGLES)
        .withDepthStencilState(java.util.Optional.empty()).withCull(false).withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT)).build();
    private static final int UNIFORM_BYTES = 160;
    private static TextureTarget source;
    private static DynamicUniformStorage<DynamicUniformStorage.DynamicUniform> uniforms;
    private static boolean wroteUniforms;
    private static final ByteBuffer data = ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder());
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
        return hand!=null&&hand.isEnabled()&&mc.options.getCameraType().isFirstPerson()&&!net.aero.visuals.render.ClientView.hud().isHidden()&&!mc.player.isSpectator();
    }
    public static void apply(Matrix4f projection,boolean hands) {
        long started = net.aero.visuals.render.performance.PerformanceProbe.begin();
        try { applyMeasured(projection, hands); }
        finally { net.aero.visuals.render.performance.PerformanceProbe.end(hands ? "Материал рук" : "Туман", started); }
    }
    private static void applyMeasured(Matrix4f projection,boolean hands) {var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var fog=ImportedFeatures.get(ImportedFeatures.Fog.class);var hand=ImportedFeatures.get(ImportedFeatures.Hands.class);
        boolean f=!hands&&fog!=null&&fog.isEnabled(),h=hands&&hand!=null&&hand.isEnabled()&&mc.options.getCameraType().isFirstPerson()&&!net.aero.visuals.render.ClientView.hud().isHidden()&&!mc.player.isSpectator();if(!f&&!h)return;
        var main=mc.gameRenderer.mainRenderTarget();int w=main.width,height=main.height;if(main.getDepthTextureView()==null||w<=0||height<=0)return;
        boolean copyScene = f ? fogNeedsSceneCopy(fog) : hand.style.get() == ImportedFeatures.Hands.Style.GLASS;
        if (copyScene && (source == null || source.width != w || source.height != height)) {
            releaseSource();
            source = new TextureTarget("Aero scene snapshot",w,height,false,net.aero.visuals.render.GpuFormats.sceneFormat());
        }
        if (uniforms == null) uniforms=new DynamicUniformStorage<>("Aero depth effects", UNIFORM_BYTES, 2);
        data.clear();if(f)inverseProjection.set(projection).invert().get(data);data.position(64);
        put(w,height,(System.nanoTime()%3_600_000_000_000L)/1e9f,hands?1:0);put(f?fog.start.get().floatValue():0,f?fog.range.get().floatValue():1,f?fog.strength.get().floatValue():0,f?net.aero.visuals.module.impl.misc.SmartEffectsModule.fogBlur(fog.blur.get().floatValue()):0);color(f?fog.color.get().eval():0);
        put(h?hand.style.get().ordinal()+1:0,h?hand.opacity.get().floatValue():0,h?hand.speed.get().floatValue():1,h?hand.scale.get().floatValue():1);color(h?hand.color.get().eval():0);
        var runtime = ClientRuntime.getNullable();
        int taps = runtime == null ? 6 : switch (runtime.settings().shaderQuality()) {
            case PERFORMANCE -> 2;
            case BALANCED -> 4;
            case QUALITY -> 6;
        };
        boolean zeroToOneDepth = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        put(net.aero.visuals.module.impl.misc.SmartEffectsModule.fogTaps(taps), zeroToOneDepth ? 1 : 0, 0, 0);
        data.flip();
        var encoder=RenderSystem.getDevice().createCommandEncoder();if(copyScene)encoder.copyTextureToTexture(main.getColorTexture(),source.getColorTexture(),0,0,0,0,0,w,height);
        var payload = data.slice();
        var uniform = uniforms.writeUniform(targetBuffer -> targetBuffer.put(payload));
        wroteUniforms = true;
        try(var pass=encoder.createRenderPass(()->"Aero depth effects",main.getColorTextureView(),java.util.Optional.empty())){pass.setPipeline(copyScene ? PIPELINE : BLEND_PIPELINE);pass.setUniform("Effects",uniform);if(copyScene)pass.bindTexture("Scene",source.getColorTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));pass.bindTexture("Depth",main.getDepthTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));pass.draw(3,1,0,0);}
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
