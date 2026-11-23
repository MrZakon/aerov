package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
/** Exact player model overlay, so hand poses, clothing and emotes stay aligned. */
public final class ShowcasePlayerFeature extends RenderLayer<AvatarRenderState,PlayerModel> {
    public ShowcasePlayerFeature(RenderLayerParent<AvatarRenderState,PlayerModel> context){super(context);}
    @Override public void submit(PoseStack m,SubmitNodeCollector queue,int light,AvatarRenderState state,float yaw,float pitch){var mc=Minecraft.getInstance();if(!net.aero.visuals.guard.AeroGuard.allowed()||mc.player==null||state.id!=mc.player.getId()||state.isInvisible)return;
        var material=VisualExpansion.get(Materials.Player.class);if(material!=null&&material.isEnabled()){m.pushPose();try{m.scale(1.002f,1.002f,1.002f);queue.submitModel(getParentModel(),state,m,SceneGeometry.material(mc.player.getSkin().body().texturePath()),material.animation(),material.color(),0xFFFFFFFF,null,0,null);}finally{m.popPose();}}
    }
}
