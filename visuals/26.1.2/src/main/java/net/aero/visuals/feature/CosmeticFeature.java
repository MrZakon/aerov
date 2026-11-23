package net.aero.visuals.feature;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
public final class CosmeticFeature extends RenderLayer<AvatarRenderState,PlayerModel> {
    private final java.util.Map<java.util.UUID,Motion> motions=new java.util.LinkedHashMap<>(16,.75f,true);
    private final EquipmentAssetManager equipmentModels;
    private java.lang.ref.WeakReference<Object> world = new java.lang.ref.WeakReference<>(null);
    private static final class Motion{final CapeSimulation simulation=new CapeSimulation();int age=-1;}
    public CosmeticFeature(RenderLayerParent<AvatarRenderState,PlayerModel> context, EquipmentAssetManager equipmentModels) {
        super(context);
        this.equipmentModels = equipmentModels;
    }
    @Override public void submit(PoseStack matrices,SubmitNodeCollector queue,int light,AvatarRenderState state,float yaw,float pitch){
        long started = net.aero.visuals.render.performance.PerformanceProbe.begin();
        try { submitMeasured(matrices, queue, light, state, yaw, pitch); }
        finally { net.aero.visuals.render.performance.PerformanceProbe.end("Косметика", started); }
    }
    private void submitMeasured(PoseStack matrices,SubmitNodeCollector queue,int light,AvatarRenderState state,float yaw,float pitch){
        if(!net.aero.visuals.guard.AeroGuard.allowed())return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||state.isInvisible)return;
        if (world.get() != mc.level) {
            motions.clear();
            world = new java.lang.ref.WeakReference<>(mc.level);
        }
        if(!(mc.level.getEntity(state.id) instanceof net.minecraft.client.player.AbstractClientPlayer owner))return;
        boolean local=owner==mc.player,preview=state instanceof CosmeticPreviewState;
        if (!local && !preview && owner.distanceToSqr(mc.player) > net.aero.visuals.module.impl.misc.SmartEffectsModule.cosmeticRangeSquared()) return;
        var ids=local?net.aero.visuals.cosmetic.CosmeticLoadout.capture():net.aero.visuals.user.AeroUserService.cosmetics(owner.getUUID(),owner.getGameProfile().name());
        ids=net.aero.visuals.regulator.ServerRestrictions.visibleCosmetics(ids);
        if(ids.isEmpty() || !preview && ids.size() == 1 && ids.containsKey("pet")) return;
        var module=ImportedFeatures.get(ImportedFeatures.Cosmetics.class);
        CapeSimulation simulation=null;
        if(ids.containsKey("cape")){
            if(local&&module!=null)simulation=module.simulation;
            else{var motion=motions.computeIfAbsent(owner.getUUID(),key->new Motion());if(motion.age!=owner.tickCount){motion.simulation.b(owner);motion.simulation.c(owner);motion.age=owner.tickCount;}simulation=motion.simulation;if(motions.size()>128)motions.remove(motions.keySet().iterator().next());}
        }
        boolean helmet = ids.containsKey("hat") && hasArmorModel(state.headEquipment);
        boolean chestplate = (ids.containsKey("cape") || ids.containsKey("wings") || ids.containsKey("bodywear"))
                && hasArmorModel(state.chestEquipment);
        ImportedModels.attachedLoadout(ids,getParentModel(),matrices,queue,simulation,mc.getDeltaTracker().getGameTimeDeltaPartialTick(true),preview,helmet,chestplate,owner.onGround(),local&&module!=null?module.size.get():1,local&&module!=null?module.color.get().eval():0xFFFFFFFF);
    }

    private boolean hasArmorModel(ItemStack stack) {
        var equipment = stack.get(DataComponents.EQUIPPABLE);
        if (equipment == null || equipment.assetId().isEmpty()) return false;
        return !equipmentModels.get(equipment.assetId().get()).getLayers(EquipmentClientInfo.LayerType.HUMANOID).isEmpty();
    }
}
