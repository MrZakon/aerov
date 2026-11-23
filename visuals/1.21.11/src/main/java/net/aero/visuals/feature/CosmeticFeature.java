package net.aero.visuals.feature;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
public final class CosmeticFeature extends FeatureRenderer<PlayerEntityRenderState,PlayerEntityModel> {
    private final java.util.Map<java.util.UUID,Motion> motions=new java.util.LinkedHashMap<>(16,.75f,true);
    private final EquipmentModelLoader equipmentModels;
    private java.lang.ref.WeakReference<Object> world = new java.lang.ref.WeakReference<>(null);
    private static final class Motion{final CapeSimulation simulation=new CapeSimulation();int age=-1;}
    public CosmeticFeature(FeatureRendererContext<PlayerEntityRenderState,PlayerEntityModel> context, EquipmentModelLoader equipmentModels) {
        super(context);
        this.equipmentModels = equipmentModels;
    }
    @Override public void render(MatrixStack matrices,OrderedRenderCommandQueue queue,int light,PlayerEntityRenderState state,float yaw,float pitch){
        long started = net.aero.visuals.render.performance.PerformanceProbe.begin();
        try { submitMeasured(matrices, queue, light, state, yaw, pitch); }
        finally { net.aero.visuals.render.performance.PerformanceProbe.end("Косметика", started); }
    }
    private void submitMeasured(MatrixStack matrices,OrderedRenderCommandQueue queue,int light,PlayerEntityRenderState state,float yaw,float pitch){
        if(!net.aero.visuals.guard.AeroGuard.allowed())return;
        var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||state.invisible)return;
        if (world.get() != mc.world) {
            motions.clear();
            world = new java.lang.ref.WeakReference<>(mc.world);
        }
        if(!(mc.world.getEntityById(state.id) instanceof net.minecraft.client.network.AbstractClientPlayerEntity owner))return;
        boolean local=owner==mc.player,preview=state instanceof CosmeticPreviewState;
        if (!local && !preview && owner.squaredDistanceTo(mc.player) > net.aero.visuals.module.impl.misc.SmartEffectsModule.cosmeticRangeSquared()) return;
        var ids=local?net.aero.visuals.cosmetic.CosmeticLoadout.capture():net.aero.visuals.user.AeroUserService.cosmetics(owner.getUuid(),owner.getGameProfile().name());
        ids=net.aero.visuals.regulator.ServerRestrictions.visibleCosmetics(ids);
        if(ids.isEmpty() || !preview && ids.size() == 1 && ids.containsKey("pet")) return;
        var module=ImportedFeatures.get(ImportedFeatures.Cosmetics.class);
        CapeSimulation simulation=null;
        if(ids.containsKey("cape")){
            if(local&&module!=null)simulation=module.simulation;
            else{var motion=motions.computeIfAbsent(owner.getUuid(),key->new Motion());if(motion.age!=owner.age){motion.simulation.b(owner);motion.simulation.c(owner);motion.age=owner.age;}simulation=motion.simulation;if(motions.size()>128)motions.remove(motions.keySet().iterator().next());}
        }
        boolean helmet = ids.containsKey("hat") && hasArmorModel(state.equippedHeadStack);
        boolean chestplate = (ids.containsKey("cape") || ids.containsKey("wings") || ids.containsKey("bodywear"))
                && hasArmorModel(state.equippedChestStack);
        ImportedModels.attachedLoadout(ids,getContextModel(),matrices,queue,simulation,mc.getRenderTickCounter().getTickProgress(true),preview,helmet,chestplate,owner.isOnGround(),local&&module!=null?module.size.get():1,local&&module!=null?module.color.get().eval():0xFFFFFFFF);
    }

    private boolean hasArmorModel(ItemStack stack) {
        var equipment = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equipment == null || equipment.assetId().isEmpty()) return false;
        return !equipmentModels.get(equipment.assetId().get()).getLayers(EquipmentModel.LayerType.HUMANOID).isEmpty();
    }
}
