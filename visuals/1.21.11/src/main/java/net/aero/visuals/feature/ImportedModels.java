package net.aero.visuals.feature;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.cosmetic.*;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
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
    private static Identifier texture(CosmeticCatalog.Entry e){return textures.computeIfAbsent(e.resourcePath(),path->Identifier.of("aerovisuals",path.substring("assets/aerovisuals/".length())+"/texture.png"));}
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
    private static void draw(WorldRenderContext context,MatrixStack matrices,Model model,double targetHeight){
        if(model==null)return;
        matrices.push();
        try {
            float scale=(float)(targetHeight/model.height);matrices.scale(scale,scale,scale);matrices.translate(-model.cx,-model.bottom,-model.cz);
            var consumer=context.consumers().getBuffer(RenderLayers.entityCutoutNoCull(model.texture));
            BedrockMesh.draw(model.geometry,consumer,matrices.peek());
        } finally {
            matrices.pop();
        }
    }
    private static void queued(net.minecraft.client.util.math.MatrixStack m,net.minecraft.client.render.command.OrderedRenderCommandQueue q,Model model,double targetHeight){
        if(model==null)return;
        m.push();
        try {
            float scale=(float)(targetHeight/model.height);m.scale(scale,scale,scale);m.translate(-model.cx,-model.bottom,-model.cz);
            q.submitCustom(m,RenderLayers.entityCutoutNoCull(model.texture),(matrix,v)->BedrockMesh.draw(model.geometry,v,matrix));
        } finally {
            m.pop();
        }
    }
    private static void attached(CosmeticCatalog.Entry entry,MatrixStack m,net.minecraft.client.render.command.OrderedRenderCommandQueue q,boolean back,double size){
        var model=model(entry);if(model==null)return;var attachment=model.geometry.attachment();
        m.push();
        try {
            m.translate(-attachment.x(),-attachment.y()+(back?.35:0),attachment.z()+(back?.02:0));
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-attachment.yaw()));m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-attachment.pitch()));m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(attachment.roll()));
            float scale=(float)(size*attachment.scale()/16);m.scale(scale,scale,scale);
            boolean wings=entry.type().equals("wings");double animationTime=wings?WingAnimator.time():0;
            q.submitCustom(m,RenderLayers.entityCutoutNoCull(model.texture),(matrix,v)->{
                if(wings)WingAnimator.draw(model.geometry,v,matrix,animationTime);else BedrockMesh.draw(model.geometry,v,matrix);
            });
        } finally {
            m.pop();
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

    public static void attachedLoadout(Map<String,Integer> ids,net.minecraft.client.render.entity.model.PlayerEntityModel player,MatrixStack m,net.minecraft.client.render.command.OrderedRenderCommandQueue q,CapeSimulation simulation,float delta,boolean preview,boolean helmet,boolean chestplate,boolean grounded,double size,int color){
        var hat=byId("hat",ids.get("hat"));
        if (hat != null) {
            m.push();
            try {
                player.head.applyTransform(m);
                if (helmet) {
                    m.translate(0, HEAD_CENTER_Y, 0);
                    m.scale(HELMET_CLEARANCE_SCALE, HELMET_CLEARANCE_SCALE, HELMET_CLEARANCE_SCALE);
                    m.translate(0, -HEAD_CENTER_Y, 0);
                }
                attached(hat,m,q,false,size);
            } finally {
                m.pop();
            }
        }
        m.push();
        try {
            player.body.applyTransform(m);
            if (chestplate) m.translate(0, 0, CHEST_ARMOR_CLEARANCE);
            var wings=byId("wings",ids.get("wings"));
            if(wings!=null)attached(wings,m,q,true,size);
            var bodywear=byId("bodywear",ids.get("bodywear"));
            if(bodywear!=null)attached(bodywear,m,q,true,size);
            var cape=byId("cape",ids.get("cape"));
            if(cape!=null){
                m.push();
                try {
                    m.translate(0, chestplate ? .03 - CAPE_ARMOR_LIFT : .03, .16);float[] ys=new float[17],zs=new float[17];
                    boolean moving=!preview&&simulation!=null&&simulation.bD();
                    float baseY = moving ? simulation.d(0, delta) : 0;
                    float baseZ = moving ? simulation.c(0, delta) : 0;
                    for(int i=0;i<=16;i++){float sample=i*15/16f;int lo=(int)sample,hi=Math.min(15,lo+1);float fraction=sample-lo;
                        ys[i]=moving?((baseY-MathHelper.lerp(fraction,simulation.d(lo,delta),simulation.d(hi,delta)))/15f):i/16f;
                        zs[i]=.025f+(moving?Math.abs(MathHelper.lerp(fraction,simulation.c(lo,delta),simulation.c(hi,delta))-baseZ)*.0625f:i*.004f);}
                    float half=(float)(.31*size);
                    q.submitCustom(m,RenderLayers.entityCutoutNoCull(texture(cape)),(matrix,v)->{for(int i=0;i<16;i++){
                        float top=(i+1)/32f,bottom=(i+2)/32f,y0=ys[i]*(float)size,y1=ys[i+1]*(float)size,z0=zs[i],z1=zs[i+1];
                        BedrockCosmeticRenderer.vertex(v,matrix,-half,y0,z0,color,12/64f,top,0,0,1);BedrockCosmeticRenderer.vertex(v,matrix,half,y0,z0,color,22/64f,top,0,0,1);BedrockCosmeticRenderer.vertex(v,matrix,half,y1,z1,color,22/64f,bottom,0,0,1);BedrockCosmeticRenderer.vertex(v,matrix,-half,y1,z1,color,12/64f,bottom,0,0,1);
                        BedrockCosmeticRenderer.vertex(v,matrix,half,y0,z0-.008f,color,1/64f,top,0,0,-1);BedrockCosmeticRenderer.vertex(v,matrix,-half,y0,z0-.008f,color,11/64f,top,0,0,-1);BedrockCosmeticRenderer.vertex(v,matrix,-half,y1,z1-.008f,color,11/64f,bottom,0,0,-1);BedrockCosmeticRenderer.vertex(v,matrix,half,y1,z1-.008f,color,1/64f,bottom,0,0,-1);
                    }});
                } finally {
                    m.pop();
                }
            }
        } finally {
            m.pop();
        }
        var pet=preview?byId("pet",ids.get("pet")):null;
        if (pet!=null) {
            m.push();
            try {
                m.translate(.85,1.5,0);queued(m,q,model(pet),.6);
            } finally {
                m.pop();
            }
        }
        var graffiti=byId("graffiti",ids.get("graffiti"));
        if(graffiti!=null&&(grounded||preview)){
            m.push();
            try {
                m.translate(0,1.49,0);
                q.submitCustom(m,RenderLayers.entityTranslucent(texture(graffiti)),(matrix,v)->{
                    BedrockCosmeticRenderer.vertex(v,matrix,-.6f,0,-.6f,0xFFFFFFFF,0,0,0,-1,0);BedrockCosmeticRenderer.vertex(v,matrix,.6f,0,-.6f,0xFFFFFFFF,1,0,0,-1,0);BedrockCosmeticRenderer.vertex(v,matrix,.6f,0,.6f,0xFFFFFFFF,1,1,0,-1,0);BedrockCosmeticRenderer.vertex(v,matrix,-.6f,0,.6f,0xFFFFFFFF,0,1,0,-1,0);
                });
            } finally {
                m.pop();
            }
        }
    }
    private static final Map<UUID,Follower> followers=new LinkedHashMap<>();private static java.lang.ref.WeakReference<Object> followerWorld;
    private static final class Follower{int id,ownerEntityId;Vec3d previous,position; final net.aero.visuals.feature.utility.PetBehavior behavior = new net.aero.visuals.feature.utility.PetBehavior();Follower(int id,Vec3d position){this.id=id;this.position=this.previous=position;}}
    private static int petId(net.minecraft.entity.player.PlayerEntity owner,MinecraftClient client){return owner==client.player?CosmeticLoadout.selectedPetId():net.aero.visuals.user.AeroUserService.cosmetics(owner.getUuid(),owner.getGameProfile().name()).getOrDefault("pet",-1);}
    public static void tickPets(MinecraftClient client){
        var world = client.world;
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
        var owners = client.world.getPlayers();
        if (owners.size() > 128) {
            owners = owners.stream().sorted(Comparator.comparingDouble(owner -> owner.squaredDistanceTo(client.player))).toList();
        }
        for (var owner : owners) {
            if (present.size() >= 128) break;
            if (owner.squaredDistanceTo(client.player) > Math.min(16384, net.aero.visuals.module.impl.misc.SmartEffectsModule.cosmeticRangeSquared())) continue;
            int id = petId(owner, client);
            if (id < 0 || byId("pet", id) == null) continue;
            UUID uuid=owner.getUuid();present.add(uuid);double distance=owner==client.player&&module!=null?module.distance.get():1.5,angle=Math.toRadians(owner.getBodyYaw());
            Vec3d desired=owner.getEntityPos().add(Math.sin(angle)*distance+Math.cos(angle)*.7,0,-Math.cos(angle)*distance+Math.sin(angle)*.7);
            Follower follower=followers.computeIfAbsent(uuid,key->new Follower(id,desired));
            if (follower.id != id) follower.behavior.reset();
            follower.id = id;
            if (module != null && module.living.get()) {
                boolean local = owner == client.player;
                var waypoints = local ? net.aero.visuals.feature.enhanced.VisualExpansion.get(net.aero.visuals.feature.enhanced.Waypoints.class) : null;
                double home = waypoints == null ? Double.POSITIVE_INFINITY : waypoints.petHomeDistanceSquared(owner.getEntityPos());
                boolean music = local && module.danceToMusic.get() && net.aero.visuals.media.WindowsMediaTracker.get().isPlaying();
                follower.behavior.tick(id, owner.getVelocity().horizontalLengthSquared() > .003, owner.isTouchingWater(),
                        client.world.hasRain(owner.getBlockPos()), music, Double.isFinite(home), home <= 64);
            } else follower.behavior.reset();
            follower.ownerEntityId = owner.getId();
            if(follower.position.squaredDistanceTo(desired)>144)follower.position=desired;
            follower.previous=follower.position;Vec3d change=desired.subtract(follower.position);double step=Math.min(.24,change.length());
            Vec3d next=change.lengthSquared()>.0001?follower.position.add(change.normalize().multiply(step)):follower.position;
            var ground=client.world.raycast(new net.minecraft.world.RaycastContext(next.add(0,2.5,0),next.add(0,-4,0),net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,owner));
            if(ground.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)next=new Vec3d(next.x,follower.position.y+(ground.getPos().y-follower.position.y)*.3,next.z);
            if(client.world.isSpaceEmpty(owner,new Box(next.x-.15,next.y+.1,next.z-.15,next.x+.15,next.y+.55,next.z+.15)))follower.position=next;
        }
        followers.keySet().retainAll(present);
    }
    private static net.minecraft.client.render.Frustum petFrustum;

    public static void setPetFrustum(net.minecraft.client.render.Frustum frustum) {
        petFrustum = frustum;
    }

    public static void renderPets(WorldRenderContext context) {
        if (followers.isEmpty() || !net.aero.visuals.guard.AeroGuard.allowed()
                || !net.aero.visuals.regulator.ServerRestrictions.allowed("pet")) return;
        var client = MinecraftClient.getInstance();
        var matrices = context.matrices();
        if (client.player == null || client.world == null || followerWorld == null || followerWorld.get() != client.world || matrices == null || context.consumers() == null) return;

        float delta = client.getRenderTickCounter().getTickProgress(true);
        var camera = client.gameRenderer.getCamera().getCameraPos();
        var module = ImportedFeatures.get(ImportedFeatures.Pet.class);
        for (var entry : followers.entrySet()) {
            var follower = entry.getValue();
            if (!(client.world.getEntityById(follower.ownerEntityId) instanceof net.minecraft.client.network.AbstractClientPlayerEntity owner)
                    || !entry.getKey().equals(owner.getUuid()) || owner.isInvisible()
                    || petId(owner, client) != follower.id) continue;

            var model = model(byId("pet", follower.id));
            if (model == null) continue;
            var position = follower.previous.lerp(follower.position, delta);
            double height = owner == client.player && module != null ? module.size.get() : .6;
            double radius = model.radius * height / model.height + .05;
            if (petFrustum != null && !petFrustum.isVisible(new Box(position.x - radius, position.y - .05,
                    position.z - radius, position.x + radius, position.y + height + .05, position.z + radius))) continue;

            matrices.push();
            try {
                matrices.translate(position.x - camera.x, position.y - camera.y + follower.behavior.bob(delta), position.z - camera.z);
                float yaw = (float) Math.toDegrees(Math.atan2(position.x - owner.getX(), position.z - owner.getZ()));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(follower.behavior.pitch(delta)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(follower.behavior.roll(delta)));
                matrices.scale(-1, -follower.behavior.height(delta), 1);
                draw(context, matrices, model, height);
            } finally {
                matrices.pop();
            }
        }
    }
}
