package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
/** Exact player model overlay, so hand poses, clothing and emotes stay aligned. */
public final class ShowcasePlayerFeature extends FeatureRenderer<PlayerEntityRenderState,PlayerEntityModel> {
    public ShowcasePlayerFeature(FeatureRendererContext<PlayerEntityRenderState,PlayerEntityModel> context){super(context);}
    @Override public void render(MatrixStack m,OrderedRenderCommandQueue queue,int light,PlayerEntityRenderState state,float yaw,float pitch){var mc=MinecraftClient.getInstance();if(!net.aero.visuals.guard.AeroGuard.allowed()||mc.player==null||state.id!=mc.player.getId()||state.invisible)return;
        var material=VisualExpansion.get(Materials.Player.class);if(material!=null&&material.isEnabled()){m.push();try{m.scale(1.002f,1.002f,1.002f);queue.submitModel(getContextModel(),state,m,SceneGeometry.material(mc.player.getSkin().body().texturePath()),material.animation(),material.color(),0xFFFFFFFF,null,0,null);}finally{m.pop();}}
    }
}
