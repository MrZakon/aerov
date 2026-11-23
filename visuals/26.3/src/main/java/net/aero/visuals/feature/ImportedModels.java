package net.aero.visuals.feature;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.cosmetic.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;

/** Bundled models retain their authored scale and offsets in the player's model space. */
public final class ImportedModels {
    private record Model(BedrockGeometryLoader.Geometry geometry,Identifier texture,double cx,double bottom,double cz,double height,double radius){}
    private static final Map<String,Model> cache=new LinkedHashMap<>(64,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,Model> entry){return size()>96;}};
    private static final Map<String,List<CosmeticCatalog.Entry>> catalogs=new HashMap<>();
    private static final Map<String,Map<Integer,CosmeticCatalog.Entry>> catalogIds=new HashMap<>();
    private static final Map<String,Identifier> textures=new HashMap<>();
    private static CosmeticCatalog catalogOwner;
    private static final Set<String> failed=new HashSet<>();
    public static java.util.List<CosmeticCatalog.Entry> catalog(String type){var runtime=ClientRuntime.getNullable();if(runtime==null||runtime.cosmetics()==null)return List.of();if(catalogOwner!=runtime.cosmetics()){catalogOwner=runtime.cosmetics();catalogs.clear();catalogIds.clear();textures.clear();cache.clear();failed.clear();}return catalogs.computeIfAbsent(type,key->runtime.cosmetics().entries().stream().filter(e->e.type().equals(key)&&!e.resourcePath().isBlank()&&(key.equals("cape")||key.equals("graffiti")||e.hasModel())).toList());}
    public static int count(String type){return catalog(type).size();}
    private static CosmeticCatalog.Entry entry(String type,int index){
        var runtime=ClientRuntime.getNullable();if(runtime==null)return null;
        var entries=catalog(type);
        return entries.isEmpty()?null:entries.get(Math.floorMod(index,entries.size()));
    }
    private static Identifier texture(CosmeticCatalog.Entry e){return textures.computeIfAbsent(e.resourcePath(),path->Identifier.fromNamespaceAndPath("aerovisuals",path.substring("assets/aerovisuals/".length())+"/texture.png"));}
    private static Model model(CosmeticCatalog.Entry e){
        if(e==null||failed.contains(e.resourcePath()))return null;
        Model known=cache.get(e.resourcePath());if(known!=null)return known;
        try{
            var geometry=ClientRuntime.get().cosmetics().loadGeometry(e);
            var bounds=BedrockMesh.bounds(geometry);
            Model result=new Model(geometry,texture(e),(bounds.minX()+bounds.maxX())/2,bounds.maxY(),(bounds.minZ()+bounds.maxZ())/2,Math.max(.01,bounds.maxY()-bounds.minY()),
                    Math.hypot(bounds.maxX()-bounds.minX(),bounds.maxZ()-bounds.minZ())*.5);
            cache.put(e.resourcePath(),result);return result;
        }catch(Exception error){failed.add(e.resourcePath());net.aero.visuals.AeroVisuals.LOGGER.warn("Could not load cosmetic {}",e.resourcePath(),error);return null;}
    }
    private static void draw(LevelRenderContext context,PoseStack matrices,Model model,double targetHeight){
        if(model==null)return;
        matrices.pushPose();
        try {
            float scale=(float)(targetHeight/model.height);matrices.scale(scale,scale,scale);matrices.translate(-model.cx,-model.bottom,-model.cz);
            var consumer=net.aero.visuals.render.WorldGeometry.buffers(context).getBuffer(RenderTypes.entityCutout(model.texture));
            BedrockMesh.draw(model.geometry,consumer,matrices.last());
        } finally {
            matrices.popPose();
        }
    }
    private static void queued(com.mojang.blaze3d.vertex.PoseStack m,net.minecraft.client.renderer.SubmitNodeCollector q,Model model,double targetHeight){
        if(model==null)return;
        m.pushPose();
        try {
            float scale=(float)(targetHeight/model.height);m.scale(scale,scale,scale);m.translate(-model.cx,-model.bottom,-model.cz);
            q.submitCustomGeometry(m,RenderTypes.entityCutout(model.texture),(matrix,v)->BedrockMesh.draw(model.geometry,v,matrix));
        } finally {
            m.popPose();
        }
    }
    private static void attached(CosmeticCatalog.Entry entry,PoseStack m,net.minecraft.client.renderer.SubmitNodeCollector q,boolean back,double size){
        var model=model(entry);if(model==null)return;var attachment=model.geometry.attachment();
        m.pushPose();
        try {
            m.translate(-attachment.x(),-attachment.y()+(back?.35:0),attachment.z()+(back?.02:0));
            m.rotate(Axis.YP.rotationDegrees(-attachment.yaw()));m.rotate(Axis.XP.rotationDegrees(-attachment.pitch()));m.rotate(Axis.ZP.rotationDegrees(attachment.roll()));
            float scale=(float)(size*attachment.scale()/16);m.scale(scale,scale,scale);
            boolean wings=entry.type().equals("wings");double animationTime=wings?WingAnimator.time():0;
            q.submitCustomGeometry(m,RenderTypes.entityCutout(model.texture),(matrix,v)->{
                if(wings)WingAnimator.draw(model.geometry,v,matrix,animationTime);else BedrockMesh.draw(model.geometry,v,matrix);
            });
        } finally {
            m.popPose();
        }
    }
    private static CosmeticCatalog.Entry byId(String type, Integer id) {
        if (id == null) return null;
        var entries = catalog(type);
        if (entries.isEmpty()) return null;
        return catalogIds.computeIfAbsent(type, key -> {
            Map<Integer, CosmeticCatalog.Entry> index = new HashMap<>();
            for (var entry : entries) index.putIfAbsent(entry.id(), entry);
            return index;
        }).get(id);
    }
    // Vanilla outer armor adds one model pixel. Leave a small gap outside the shell.
    private static final double CHEST_ARMOR_CLEARANCE = 1.1 / 16.0;
    private static final double CAPE_ARMOR_LIFT = 0.85 / 16.0;
    private static final double HEAD_CENTER_Y = -4.0 / 16.0;
    private static final float HELMET_CLEARANCE_SCALE = 1.28f;

    public static void attachedLoadout(Map<String,Integer> ids,net.minecraft.client.model.player.PlayerModel player,PoseStack m,net.minecraft.client.renderer.SubmitNodeCollector q,CapeSimulation simulation,float delta,boolean preview,boolean helmet,boolean chestplate,boolean grounded,double size,int color){
        var hat=byId("hat",ids.get("hat"));
        if (hat != null) {
            m.pushPose();
            try {
                player.head.translateAndRotate(m);
                if (helmet) {
                    m.translate(0, HEAD_CENTER_Y, 0);
                    m.scale(HELMET_CLEARANCE_SCALE, HELMET_CLEARANCE_SCALE, HELMET_CLEARANCE_SCALE);
                    m.translate(0, -HEAD_CENTER_Y, 0);
                }
                attached(hat,m,q,false,size);
            } finally {
                m.popPose();
            }
        }
        m.pushPose();
        try {
            player.body.translateAndRotate(m);
            if (chestplate) m.translate(0, 0, CHEST_ARMOR_CLEARANCE);
            var wings=byId("wings",ids.get("wings"));
            if(wings!=null)attached(wings,m,q,true,size);
            var bodywear=byId("bodywear",ids.get("bodywear"));
            if(bodywear!=null)attached(bodywear,m,q,true,size);
            var cape=byId("cape",ids.get("cape"));
            if(cape!=null){
                m.pushPose();
                try {
                    m.translate(0, chestplate ? .03 - CAPE_ARMOR_LIFT : .03, .16);float[] ys=new float[17],zs=new float[17];
                    boolean moving=!preview&&simulation!=null&&simulation.bD();
                    float baseY = moving ? simulation.d(0, delta) : 0;
                    float baseZ = moving ? simulation.c(0, delta) : 0;
                    for(int i=0;i<=16;i++){float sample=i*15/16f;int lo=(int)sample,hi=Math.min(15,lo+1);float fraction=sample-lo;
                        ys[i]=moving?((baseY-Mth.lerp(fraction,simulation.d(lo,delta),simulation.d(hi,delta)))/15f):i/16f;
                        zs[i]=.025f+(moving?Math.abs(Mth.lerp(fraction,simulation.c(lo,delta),simulation.c(hi,delta))-baseZ)*.0625f:i*.004f);}
                    float half=(float)(.31*size);
                    q.submitCustomGeometry(m,RenderTypes.entityCutout(texture(cape)),(matrix,v)->{for(int i=0;i<16;i++){
                        float top=(i+1)/32f,bottom=(i+2)/32f,y0=ys[i]*(float)size,y1=ys[i+1]*(float)size,z0=zs[i],z1=zs[i+1];
                        BedrockCosmeticRenderer.vertex(v,matrix,-half,y0,z0,color,12/64f,top,0,0,1);BedrockCosmeticRenderer.vertex(v,matrix,half,y0,z0,color,22/64f,top,0,0,1);BedrockCosmeticRenderer.vertex(v,matrix,half,y1,z1,color,22/64f,bottom,0,0,1);BedrockCosmeticRenderer.vertex(v,matrix,-half,y1,z1,color,12/64f,bottom,0,0,1);
                        BedrockCosmeticRenderer.vertex(v,matrix,half,y0,z0-.008f,color,1/64f,top,0,0,-1);BedrockCosmeticRenderer.vertex(v,matrix,-half,y0,z0-.008f,color,11/64f,top,0,0,-1);BedrockCosmeticRenderer.vertex(v,matrix,-half,y1,z1-.008f,color,11/64f,bottom,0,0,-1);BedrockCosmeticRenderer.vertex(v,matrix,half,y1,z1-.008f,color,1/64f,bottom,0,0,-1);
                    }});
                } finally {
                    m.popPose();
                }
            }
        } finally {
            m.popPose();
        }
        var pet=preview?byId("pet",ids.get("pet")):null;
        if (pet!=null) {
            m.pushPose();
            try {
                m.translate(.85,1.5,0);queued(m,q,model(pet),.6);
            } finally {
                m.popPose();
            }
        }
        var graffiti=byId("graffiti",ids.get("graffiti"));
        if(graffiti!=null&&(grounded||preview)){
            m.pushPose();
            try {
                m.translate(0,1.49,0);
                q.submitCustomGeometry(m,RenderTypes.entityTranslucent(texture(graffiti)),(matrix,v)->{
                    BedrockCosmeticRenderer.vertex(v,matrix,-.6f,0,-.6f,0xFFFFFFFF,0,0,0,-1,0);BedrockCosmeticRenderer.vertex(v,matrix,.6f,0,-.6f,0xFFFFFFFF,1,0,0,-1,0);BedrockCosmeticRenderer.vertex(v,matrix,.6f,0,.6f,0xFFFFFFFF,1,1,0,-1,0);BedrockCosmeticRenderer.vertex(v,matrix,-.6f,0,.6f,0xFFFFFFFF,0,1,0,-1,0);
                });
            } finally {
                m.popPose();
            }
        }
    }
    private static final Map<UUID,Follower> followers=new LinkedHashMap<>();private static java.lang.ref.WeakReference<Object> followerWorld;
    private static final class Follower{int id,ownerEntityId;Vec3 previous,position; final net.aero.visuals.feature.utility.PetBehavior behavior = new net.aero.visuals.feature.utility.PetBehavior();Follower(int id,Vec3 position){this.id=id;this.position=this.previous=position;}}
    private static int petId(net.minecraft.world.entity.player.Player owner,Minecraft client){return owner==client.player?CosmeticLoadout.selectedPetId():net.aero.visuals.user.AeroUserService.cosmetics(owner.getUUID(),owner.getGameProfile().name()).getOrDefault("pet",-1);}
    public static void tickPets(Minecraft client){
        var world = client.level;
        if (world == null || client.player == null || !net.aero.visuals.guard.AeroGuard.allowed()
                || !net.aero.visuals.regulator.ServerRestrictions.allowed("pet")) {
            followers.clear();
            followerWorld = null;
            return;
        }
        if (followerWorld == null || followerWorld.get() != world) {
            followers.clear();
            followerWorld = new java.lang.ref.WeakReference<>(world);
        }
        Set<UUID> present=new HashSet<>();var module=ImportedFeatures.get(ImportedFeatures.Pet.class);
        var owners = client.level.players();
        if (owners.size() > 128) {
            owners = owners.stream().sorted(Comparator.comparingDouble(owner -> owner.distanceToSqr(client.player))).toList();
        }
        for (var owner : owners) {
            if (present.size() >= 128) break;
            if (owner.distanceToSqr(client.player) > Math.min(16384, net.aero.visuals.module.impl.misc.SmartEffectsModule.cosmeticRangeSquared())) continue;
            int id = petId(owner, client);
            if (id < 0 || byId("pet", id) == null) continue;
            UUID uuid=owner.getUUID();present.add(uuid);double distance=owner==client.player&&module!=null?module.distance.get():1.5,angle=Math.toRadians(owner.getVisualRotationYInDegrees());
            Vec3 desired=owner.position().add(Math.sin(angle)*distance+Math.cos(angle)*.7,0,-Math.cos(angle)*distance+Math.sin(angle)*.7);
            Follower follower=followers.computeIfAbsent(uuid,key->new Follower(id,desired));
            if (follower.id != id) follower.behavior.reset();
            follower.id = id;
            if (module != null && module.living.get()) {
                boolean local = owner == client.player;
                var waypoints = local ? net.aero.visuals.feature.enhanced.VisualExpansion.get(net.aero.visuals.feature.enhanced.Waypoints.class) : null;
                double home = waypoints == null ? Double.POSITIVE_INFINITY : waypoints.petHomeDistanceSquared(owner.position());
                boolean music = local && module.danceToMusic.get() && net.aero.visuals.media.WindowsMediaTracker.get().isPlaying();
                follower.behavior.tick(id, owner.getDeltaMovement().horizontalDistanceSqr() > .003, owner.isInWater(),
                        client.level.isRainingAt(owner.blockPosition()), music, Double.isFinite(home), home <= 64);
            } else follower.behavior.reset();
            follower.ownerEntityId = owner.getId();
            if(follower.position.distanceToSqr(desired)>144)follower.position=desired;
            follower.previous=follower.position;Vec3 change=desired.subtract(follower.position);double step=Math.min(.24,change.length());
            Vec3 next=change.lengthSqr()>.0001?follower.position.add(change.normalize().scale(step)):follower.position;
            var ground=client.level.clip(new net.minecraft.world.level.ClipContext(next.add(0,2.5,0),next.add(0,-4,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,owner));
            if(ground.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)next=new Vec3(next.x,follower.position.y+(ground.getLocation().y-follower.position.y)*.3,next.z);
            if(client.level.noCollision(owner,new AABB(next.x-.15,next.y+.1,next.z-.15,next.x+.15,next.y+.55,next.z+.15)))follower.position=next;
        }
        followers.keySet().retainAll(present);
    }
    public static void renderPets(LevelRenderContext context){
        if (followers.isEmpty()) return;
        if(!net.aero.visuals.guard.AeroGuard.allowed()||!net.aero.visuals.regulator.ServerRestrictions.allowed("pet"))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level == null || followerWorld == null || followerWorld.get() != mc.level||context.poseStack()==null||net.aero.visuals.render.WorldGeometry.buffers(context)==null)return;
        float delta=mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);var camera=mc.gameRenderer.mainCamera().position();var module=ImportedFeatures.get(ImportedFeatures.Pet.class);
        var cameraState = context.levelState().cameraRenderState;
        var frustum = cameraState == null ? null : cameraState.cullFrustum;
        for(var entry:followers.entrySet()){
            var follower = entry.getValue();
            if (!(mc.level.getEntity(follower.ownerEntityId) instanceof net.minecraft.client.player.AbstractClientPlayer owner)
                    || !entry.getKey().equals(owner.getUUID())) continue;
            if (owner.isInvisible() || petId(owner, mc) != follower.id) continue;
            var model = model(byId("pet", follower.id));
            if (model == null) continue;
            Vec3 position = follower.previous.lerp(follower.position, delta);
            double height = owner == mc.player && module != null ? module.size.get() : .6;
            double radius = model.radius * height / model.height + .05;
            if (frustum != null && !frustum.isVisible(new AABB(position.x - radius, position.y - .05,
                    position.z - radius, position.x + radius, position.y + height + .05, position.z + radius))) continue;

            var matrices = context.poseStack();
            matrices.pushPose();
            try {
                matrices.translate(position.x - camera.x, position.y - camera.y + follower.behavior.bob(delta), position.z - camera.z);
                float yaw = (float) Math.toDegrees(Math.atan2(position.x - owner.getX(), position.z - owner.getZ()));
                matrices.rotate(Axis.YP.rotationDegrees(yaw));
                matrices.rotate(Axis.XP.rotationDegrees(follower.behavior.pitch(delta)));
                matrices.rotate(Axis.ZP.rotationDegrees(follower.behavior.roll(delta)));
                matrices.scale(-1, -follower.behavior.height(delta), 1);
                draw(context, matrices, model, height);
            } finally {
                matrices.popPose();
            }
        }
    }
}
