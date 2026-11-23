package net.aero.visuals.feature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
public final class CosmeticFeature extends RenderLayer<AvatarRenderState,PlayerModel> {
    private final java.util.Map<java.util.UUID,Motion> motions=new java.util.LinkedHashMap<>(16,.75f,true);
    private Object world;
    private static final class Motion{final CapeSimulation simulation=new CapeSimulation();int age=-1;}
    public CosmeticFeature(RenderLayerParent<AvatarRenderState,PlayerModel> context){super(context);}
    @Override public void submit(PoseStack matrices,SubmitNodeCollector queue,int light,AvatarRenderState state,float yaw,float pitch){
        if(!net.aero.visuals.guard.AeroGuard.allowed())return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||state.isInvisible)return;
        if(world!=mc.level){motions.clear();world=mc.level;}
        if(!(mc.level.getEntity(state.id) instanceof net.minecraft.client.player.AbstractClientPlayer owner))return;
        boolean local=owner==mc.player,preview=state instanceof CosmeticPreviewState;
        var ids=local?net.aero.visuals.cosmetic.CosmeticLoadout.capture():net.aero.visuals.user.AeroUserService.cosmetics(owner.getUUID(),owner.getGameProfile().name());
        if(ids.isEmpty())return;
        if(!preview&&ids.containsKey("pet")){var copy=new java.util.HashMap<>(ids);copy.remove("pet");ids=copy;}
        if(ids.isEmpty())return;
        var module=ImportedFeatures.get(ImportedFeatures.Cosmetics.class);
        CapeSimulation simulation=null;
        if(ids.containsKey("cape")){
            if(local&&module!=null)simulation=module.simulation;
            else{var motion=motions.computeIfAbsent(owner.getUUID(),key->new Motion());if(motion.age!=owner.tickCount){motion.simulation.b(owner);motion.simulation.c(owner);motion.age=owner.tickCount;}simulation=motion.simulation;if(motions.size()>128)motions.remove(motions.keySet().iterator().next());}
        }
        ImportedModels.attachedLoadout(ids,getParentModel(),matrices,queue,simulation,mc.getDeltaTracker().getGameTimeDeltaPartialTick(true),preview,owner.onGround(),local&&module!=null?module.size.get():1,local&&module!=null?module.color.get().eval():0xFFFFFFFF);
    }
}
