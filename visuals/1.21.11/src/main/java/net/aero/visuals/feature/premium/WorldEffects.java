package net.aero.visuals.feature.premium;

import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.feature.showcase.SceneGeometry;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.*;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.registry.tag.BlockTags;
import java.lang.ref.WeakReference;
import java.util.*;

/** Bounded, depth-tested scene effects. No position prediction or hidden-player rendering. */
public final class WorldEffects {
    private WorldEffects(){}
    static int fade(int color,double alpha){return ColorUtil.withAlpha(color,(int)((color>>>24)*Math.clamp(alpha,0,1)));}
    abstract static class Effect extends VisualExpansion.Feature implements WorldRenderable {
        protected final ColorSetting tint;
        protected int ticks;
        private WeakReference<Object> effectWorld;
        Effect(String id,String name,String description,ModuleCategory category){this(id,name,description,category,true);}
        Effect(String id,String name,String description,ModuleCategory category,boolean colored){super(id,name,description,category);tint=colored?color("color","Цвет",0xFFA995FF):null;}
        protected net.minecraft.client.render.RenderLayer layer(){return SceneGeometry.COLOR;}
        @Subscribe
        private void advance(TickEvent.Client event) {
            var mc = event.client();
            updateWorld(mc.world);
            if (mc.player == null || mc.world == null) return;
            ticks++;
            tick(mc);
        }

        protected final void updateWorld(Object currentWorld) {
            if (effectWorld == null ? currentWorld == null : currentWorld != null && effectWorld.get() == currentWorld) return;
            clear();
            ticks = 0;
            effectWorld = currentWorld == null ? null : new WeakReference<>(currentWorld);
        }
        protected void tick(MinecraftClient mc){}
        protected void clear(){}
        @Override protected void onEnable() { updateWorld(MinecraftClient.getInstance().world); }
        @Override protected void onDisable() { updateWorld(null); }
        @Override public final void renderWorld(WorldRenderContext c){var mc=MinecraftClient.getInstance();if(mc.player==null||effectWorld==null||mc.world==null||mc.world!=effectWorld.get()||c.matrices()==null||c.consumers()==null)return;var m=c.matrices();var camera=mc.gameRenderer.getCamera().getCameraPos();m.push();try{m.translate(-camera.x,-camera.y,-camera.z);draw(mc,m,c.consumers().getBuffer(layer()),ticks+mc.getRenderTickCounter().getTickProgress(false));}finally{m.pop();}}
        protected abstract void draw(MinecraftClient mc,MatrixStack m,VertexConsumer v,float time);
    }
    static final class Mote {
        Vec3d position,previous,velocity;final int born,color;final double size;final boolean bounce;
        Mote(Vec3d p,Vec3d velocity,int born,int color,double size,boolean bounce){position=previous=p;this.velocity=velocity;this.born=born;this.color=color;this.size=size;this.bounce=bounce;}
        void tick(MinecraftClient mc){
            previous=position;Vec3d next=position.add(velocity);
            if(bounce){
                var hit=mc.world.raycast(new RaycastContext(position,next,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
                if(hit.getType()!=HitResult.Type.MISS){
                    var side=hit.getSide().getVector();var normal=new Vec3d(side.getX(),side.getY(),side.getZ());
                    next=hit.getPos().add(normal.multiply(.003));
                    velocity=velocity.subtract(normal.multiply(2*velocity.dotProduct(normal))).multiply(.45);
                }
            }
            position=next;velocity=velocity.multiply(.97).add(0,bounce?-.012:-.0008,0);
        }
    }
    abstract static class Motes extends Effect {
        final ArrayDeque<Mote> motes=new ArrayDeque<>();
        final DoubleSetting count=number("count","Количество",18,4,48,1),life=number("life","Длительность (сек.)",1,.3,3,.1),size=number("size","Размер",.055,.02,.15,.005);
        Motes(String id,String name,String desc){this(id,name,desc,true);}
        Motes(String id,String name,String desc,boolean colored){super(id,name,desc,ModuleCategory.RENDER,colored);}
        void emit(Vec3d point, int color, boolean bounce, int amount) {
            if (!isEnabled()) return;
            var mc = MinecraftClient.getInstance();
            var player = mc.player;
            if (player == null || mc.world == null) return;
            updateWorld(mc.world);
            var random = player.getRandom();
            double moteSize = size.get();
            amount = net.aero.visuals.module.impl.misc.SmartEffectsModule.particleCount(amount, id());
            for (int i = 0; i < amount; i++) {
                if (motes.size() >= 256) motes.removeFirst();
                var velocity = new Vec3d((random.nextDouble() - .5) * .16,
                        .04 + random.nextDouble() * .17, (random.nextDouble() - .5) * .16);
                motes.add(new Mote(point, velocity, ticks, color, moteSize, bounce));
            }
        }
        @Override protected void clear(){motes.clear();}
        @Override protected void tick(MinecraftClient mc){motes.removeIf(p->ticks-p.born>life.get()*20);for(var p:motes)p.tick(mc);}
        @Override
        protected void draw(MinecraftClient mc, MatrixStack matrices, VertexConsumer vertices, float time) {
            if (motes.isEmpty()) return;
            double lifetime = life.get() * 20;
            float partialTick = time - ticks;
            var matrix = matrices.peek();
            for (var mote : motes) {
                int color = fade(mote.color, 1 - (time - mote.born) / lifetime);
                if ((color >>> 24) == 0) continue;
                var point = mote.previous.lerp(mote.position, partialTick);
                SceneGeometry.box(matrix, vertices, point.x, point.y, point.z,
                        mote.size, mote.size, mote.size, color);
            }
        }
    }
    public static final class PickBlowup extends Motes {
        public PickBlowup(){super("pick_blowup","PickBlowup","Энергетический всплеск при своём подтверждённом подборе предмета.");}
        public void pickup(Vec3d point){emit(point,tint.get().eval(),false,count.get().intValue());}
    }
    public static final class BlockParticles extends Motes {
        public BlockParticles(){super("block_particles","BlockParticles","Осколки в цвет разрушенного блока: гравитация, отскок и плавное затухание.",false);}
        public void broken(Vec3d point,int blockColor){emit(point,0xFF000000|(blockColor&0xFFFFFF),true,count.get().intValue());}
    }
    public static final class Particular extends Motes {
        private final BooleanSetting leaves=flag("leaves","Листья",true),water=flag("water","Брызги воды",true),dust=flag("dust","Пещерная пыль",true);
        private final ArrayDeque<Crown> crowns=new ArrayDeque<>();private boolean submerged;private Vec3d previous;
        private record Crown(Vec3d point,int born,double size){}
        public Particular() {
            super("particular", "Particular", "Водные короны и брызги при входе в воду, падающие листья и пещерная пыль.", false);
            count.visibleWhen(water::get);
            size.visibleWhen(water::get);
        }
        @Override protected void clear(){super.clear();crowns.clear();previous=null;submerged=false;}
        @Override protected void tick(MinecraftClient mc){super.tick(mc);boolean wet=mc.player.isTouchingWater();if(previous!=null&&water.get()&&wet&&!submerged){Vec3d p=mc.player.getEntityPos().add(0,.15,0);emit(p,0xFFA9DAFF,false,count.get().intValue());if(crowns.size()>=8)crowns.removeFirst();crowns.add(new Crown(p,ticks,Math.clamp(Math.abs(mc.player.getVelocity().y)*2+.5,.5,1.5)));}previous=mc.player.getEntityPos();submerged=wet;crowns.removeIf(c->ticks-c.born>20);
            if(ticks%4==0){var r=mc.player.getRandom();var pos=mc.player.getBlockPos().add(r.nextInt(13)-6,r.nextInt(7)+1,r.nextInt(13)-6);if(!mc.world.isChunkLoaded(pos))return;var state=mc.world.getBlockState(pos);if(leaves.get()&&state.isIn(BlockTags.LEAVES)&&mc.world.getBlockState(pos.down()).isAir()){if(motes.size()<256)motes.add(new Mote(Vec3d.ofCenter(pos).add(0,-.5,0),new Vec3d(.01,-.018,.005),ticks,0xBD79B468,.07,false));}else if(dust.get()&&mc.player.getY()<55&&mc.world.getBlockState(pos).isAir()&&motes.size()<192){motes.add(new Mote(Vec3d.ofCenter(pos),new Vec3d(.002,-.001,.001),ticks,0x669CC8D2,.018,false));}}
        }
        @Override protected void draw(MinecraftClient mc,MatrixStack m,VertexConsumer v,float time){super.draw(mc,m,v,time);for(var c:crowns){double t=(time-c.born)/20;SceneGeometry.ring(m.peek(),v,c.point,c.size*(.3+t),.035,fade(0xFFABDFFF,1-t));for(int i=0;i<16;i++){double angle=i*Math.PI/8;Vec3d p=c.point.add(Math.cos(angle)*c.size*(.3+t),0,Math.sin(angle)*c.size*(.3+t));SceneGeometry.beam(m.peek(),v,p,p.add(0,Math.sin(Math.PI*t)*.3,0),.012,fade(0xFFB3DFFF,1-t));}}}
    }
    public static final class Constellation extends Effect {
        private final DoubleSetting stars=number("stars","Число звёзд",24,8,40,1),radius=number("radius","Радиус",3,1,6,.25),links=number("links","Длина связей",1.6,.5,3,.1);
        private final DoubleSetting starSize=number("star_size","Размер звёзд",.035,.015,.08,.005),drift=number("star_drift","Движение звёзд",.35,0,1,.05);
        public Constellation(){super("constellation","Constellation","Мягкие четырёхлучевые звёзды с мерцанием и тонкими связями вокруг своего персонажа.", ModuleCategory.RENDER);}
        @Override protected net.minecraft.client.render.RenderLayer layer(){return StarSprites.LAYER;}
        private final Vec3d[] points = new Vec3d[(int) stars.maximum()];

        private Vec3d point(int index, float time, Vec3d center, double drift, double radius) {
            double angle = index * 2.399963 + time * .0006 * drift;
            double spread = Math.sin(index * 7.17);
            double distance = radius * (.5 + .5 * spread * spread);
            double height = 1 + Math.sin(index * 1.7 + time * .0015 * drift) * 1.05;
            return center.add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
        }
        @Override protected void draw(MinecraftClient mc,MatrixStack matrices,VertexConsumer vertices,float time){
            var center=mc.player.getLerpedPos(time-ticks);var rotation=mc.gameRenderer.getCamera().getRotation();
            var r=new org.joml.Vector3f(1,0,0).rotate(rotation);var u=new org.joml.Vector3f(0,1,0).rotate(rotation);
            var right=new Vec3d(r.x,r.y,r.z);var up=new Vec3d(u.x,u.y,u.z);var eye=mc.gameRenderer.getCamera().getCameraPos();
            int n = stars.get().intValue();
            int raw = tint.get().eval();
            double orbitRadius = radius.get();
            double driftSpeed = drift.get();
            double spriteSize = starSize.get();
            double linkLength = links.get();
            double linkLengthSquared = linkLength * linkLength;
            var matrix = matrices.peek();
            for (int i = 0; i < n; i++) {
                points[i] = point(i, time, center, driftSpeed, orbitRadius);
                double phase = .5 + .5 * Math.sin(time * .035 + i * 2.37);
                double twinkle = .72 + .28 * phase * phase;
                double extent = spriteSize * (.9 + .1 * twinkle);
                StarSprites.star(matrix, vertices, points[i], right.multiply(extent), up.multiply(extent),
                        fade(raw, twinkle));
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double distanceSquared = points[i].squaredDistanceTo(points[j]);
                    if (distanceSquared >= linkLengthSquared) continue;
                    double falloff = 1 - Math.sqrt(distanceSquared) / linkLength;
                    int color = fade(raw, .22 * falloff * falloff);
                    if ((color >>> 24) != 0) {
                        StarSprites.link(matrix, vertices, points[i], points[j], eye, .002, color);
                    }
                }
            }
        }
    }

    public static final class ProjectileTrails extends Effect {
        private final DoubleSetting life=number("life","Длина следа (сек.)",1,.2,3,.1),width=number("width","Толщина",.025,.005,.1,.005);
        private record Segment(Vec3d from,Vec3d to,int born){}
        private final ArrayDeque<Segment> segments=new ArrayDeque<>();private final Map<Integer,Vec3d> positions=new HashMap<>();
        public ProjectileTrails(){super("projectile_trails","ProjectileTrails","След реального движения видимых снарядов без прогнозирования траектории.", ModuleCategory.RENDER);}
        @Override protected void clear(){segments.clear();positions.clear();}
        @Override protected void tick(MinecraftClient mc){segments.removeIf(s->ticks-s.born>life.get()*20);Set<Integer> present=new HashSet<>();int count=0;for (var e : mc.world.getEntitiesByClass(ProjectileEntity.class, mc.player.getBoundingBox().expand(64), projectile -> mc.player.squaredDistanceTo(projectile) <= 4096)) { if (count == 64) break; if (!mc.player.canSee(e)) continue; count++;present.add(e.getId());var next=e.getEntityPos();var old=positions.put(e.getId(),next);if(old!=null&&old.squaredDistanceTo(next)>.0001&&old.squaredDistanceTo(next)<256){if(segments.size()>=768)segments.removeFirst();segments.add(new Segment(old,next,ticks));}}positions.keySet().retainAll(present);}
        @Override protected void draw(MinecraftClient mc,MatrixStack m,VertexConsumer v,float time){for(var s:segments)SceneGeometry.beam(m.peek(),v,s.from,s.to,width.get(),fade(tint.get().eval(),1-(time-s.born)/(life.get()*20)));}
    }
    public static final class WaveEffect extends Effect {
        private final DoubleSetting radius=number("radius","Радиус",8,3,14,1),duration=number("wave_duration","Длительность волны (сек.)",1.4,.5,3,.1),width=number("wave_width","Ширина волны",1,.4,2,.1);
        private record Surface(Vec3d center,double halfX,double halfZ,double distance){}
        private record Wave(List<Surface> surfaces,int born,double radius,double duration){}
        private final ArrayDeque<Wave> waves=new ArrayDeque<>();
        public WaveEffect(){super("wave_effect","WaveEffect","Плавная волна по поверхности блоков только при прыжке своего игрока.", ModuleCategory.RENDER);}
        @Override protected void clear(){waves.clear();}
        @Override protected void tick(MinecraftClient mc){waves.removeIf(w->ticks-w.born>w.duration*20+1);}
        public void jump(){
            if(!isEnabled())return;var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null)return;
            updateWorld(mc.world);
            Vec3d origin=mc.player.getEntityPos();var base=mc.player.getBlockPos();int r=radius.get().intValue();var surfaces=new ArrayList<Surface>();
            var block = new BlockPos.Mutable();
            var above = new BlockPos.Mutable();
            for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
                if(x*x+z*z>r*r)continue;
                int blockX = base.getX() + x;
                int blockZ = base.getZ() + z;
                if (!mc.world.getChunkManager().isChunkLoaded(blockX >> 4, blockZ >> 4)) continue;
                for (int y = base.getY() + 2; y >= base.getY() - 3; y--) {
                    block.set(blockX, y, blockZ);
                    var shape=mc.world.getBlockState(block).getCollisionShape(mc.world,block);
                    if(shape.isEmpty())continue;
                    above.set(blockX, y + 1, blockZ);
                    if (mc.world.getBlockState(above).isSolidBlock(mc.world, above)) continue;
                    var box=shape.getBoundingBox();
                    Vec3d center=new Vec3d(block.getX()+(box.minX+box.maxX)*.5,block.getY()+box.maxY+.004,block.getZ()+(box.minZ+box.maxZ)*.5);
                    surfaces.add(new Surface(center,(box.maxX-box.minX)*.495,(box.maxZ-box.minZ)*.495,Math.hypot(center.x-origin.x,center.z-origin.z)));break;
                }
            }
            if (surfaces.isEmpty()) return;
            surfaces.sort(Comparator.comparingDouble(Surface::distance));
            if (waves.size() >= 4) waves.removeFirst();
            waves.add(new Wave(List.copyOf(surfaces),ticks,r,duration.get()));
        }
        private static int firstSurfaceInRange(List<Surface> surfaces, double minimumDistance) {
            int low = 0;
            int high = surfaces.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (surfaces.get(middle).distance < minimumDistance) low = middle + 1;
                else high = middle;
            }
            return low;
        }

        @Override
        protected void draw(MinecraftClient mc, MatrixStack matrices, VertexConsumer vertices, float time) {
            if (waves.isEmpty()) return;
            double waveWidth = width.get();
            double extent = 1.2 * waveWidth;
            int tintColor = tint.get().eval();
            var matrix = matrices.peek();
            for (var wave : waves) {
                double progress = Math.clamp((time - wave.born) / (wave.duration * 20), 0, 1);
                double envelope = Math.sin(Math.PI * progress) * .42;
                if (envelope < .006) continue;
                double front = wave.radius * progress * progress * (3 - 2 * progress);
                double outer = front + extent;
                var surfaces = wave.surfaces;
                for (int i = firstSurfaceInRange(surfaces, front - extent); i < surfaces.size(); i++) {
                    var surface = surfaces.get(i);
                    if (surface.distance > outer) break;
                    double delta = (surface.distance - front) / waveWidth;
                    double alpha = Math.exp(-delta * delta * 3) * envelope;
                    if (alpha < .006) continue;
                    int color = fade(tintColor, alpha);
                    if ((color >>> 24) == 0) continue;
                    var center = surface.center;
                    float minX = (float) (center.x - surface.halfX);
                    float maxX = (float) (center.x + surface.halfX);
                    float minZ = (float) (center.z - surface.halfZ);
                    float maxZ = (float) (center.z + surface.halfZ);
                    float y = (float) center.y;
                    vertices.vertex(matrix, minX, y, minZ).color(color);
                    vertices.vertex(matrix, maxX, y, minZ).color(color);
                    vertices.vertex(matrix, maxX, y, maxZ).color(color);
                    vertices.vertex(matrix, minX, y, maxZ).color(color);
                }
            }
        }
    }


    public static final class HitMarker extends Effect {
        private record Mark(Vec3d point,int born){}private final ArrayDeque<Mark> marks=new ArrayDeque<>();
        private final DoubleSetting life=number("life","Длительность",.6,.2,1.5,.1),size=number("size","Размер",.15,.05,.4,.025);
        public HitMarker(){super("hit_marker","HitMarker","Короткий пространственный маркер в точке подтверждённого попадания.",ModuleCategory.PVP);}
        public void hit(Vec3d point) {
            if (!isEnabled()) return;
            var level = MinecraftClient.getInstance().world;
            if (level == null) return;
            updateWorld(level);
            if (marks.size() >= 16) marks.removeFirst();
            marks.add(new Mark(point, ticks));
        }
        @Override protected void clear(){marks.clear();}
        @Override protected void tick(MinecraftClient mc){marks.removeIf(p->ticks-p.born>life.get()*20);}
        @Override protected void draw(MinecraftClient mc,MatrixStack m,VertexConsumer v,float time){var q=mc.gameRenderer.getCamera().getRotation();for(var p:marks){double t=(time-p.born)/(life.get()*20),r=size.get()*(.65+t*.4);var a=new org.joml.Vector3f((float)r,0,0).rotate(q);var b=new org.joml.Vector3f(0,(float)r,0).rotate(q);var right=new Vec3d(a.x,a.y,a.z);var up=new Vec3d(b.x,b.y,b.z);for(int side=-1;side<=1;side+=2){Vec3d diagonal=right.add(up.multiply(side));for(int sign=-1;sign<=1;sign+=2)SceneGeometry.beam(m.peek(),v,p.point.add(diagonal.multiply(.35*sign)),p.point.add(diagonal.multiply(sign)),.009,fade(tint.get().eval(),Math.sin(Math.PI*Math.min(1,t))));}}}
    }
    public static final class DamageNumbers extends VisualExpansion.Feature {
        private record Number(Vec3d point,String value,long born){}private final ArrayDeque<Number> numbers=new ArrayDeque<>();
        private final DoubleSetting life=number("life","Длительность",1.1,.4,2,.1),size=number("size","Размер текста",12,8,72,1);private final ColorSetting tint=color("color","Цвет урона",0xFFFF987F);
        private WeakReference<Object> world;
        private final org.joml.Vector2f screen = new org.joml.Vector2f();
        public DamageNumbers(){super("damage_numbers","DamageNumbers","Числа по сообщённому сервером изменению здоровья атакованной цели. Не придумывает неизвестный урон.",ModuleCategory.PVP);}
        public void damage(Vec3d point, float amount) {
            if (!isEnabled() || !Float.isFinite(amount) || amount <= 0) return;
            var level = MinecraftClient.getInstance().world;
            updateWorld(level);
            if (level == null) return;
            if (numbers.size() >= 24) numbers.removeFirst();
            numbers.add(new Number(point, String.format(Locale.ROOT, "−%.1f", amount), System.nanoTime()));
        }

        private void updateWorld(Object level) {
            if (world == null ? level == null : level != null && world.get() == level) return;
            numbers.clear();
            world = level == null ? null : new WeakReference<>(level);
        }
        @Override protected void onDisable(){numbers.clear();world=null;}
        @Subscribe private void draw(Render2DEvent event) {
            var mc = MinecraftClient.getInstance();
            var level = mc.world;
            updateWorld(level);
            if (mc.player == null || level == null || mc.options.hudHidden) return;
            long now = System.nanoTime();
            double lifetime = life.get() * 1e9;
            while (!numbers.isEmpty() && now - numbers.getFirst().born > lifetime) numbers.removeFirst();
            if (numbers.isEmpty() || !TargetMarkerProjection.hasCurrentCapture()) return;

            var gui = event.context();
            int width = gui.getScaledWindowWidth(), height = gui.getScaledWindowHeight();
            var eye = mc.gameRenderer.getCamera().getCameraPos();
            var font = net.aero.visuals.render.font.FontFace.GOLOS_SEMI;
            double fontSize = size.get();
            int color = tint.get().eval();
            for (var number : numbers) {
                double age = (now - number.born) / lifetime;
                double y = number.point.y + age * .6;
                if (!TargetMarkerProjection.projectCaptured(number.point.x, y, number.point.z, width, height, screen)) continue;
                Vec3d point = number.point.add(0, age * .6, 0);
                if (level.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
                double x = screen.x - UiRenderer.fontWidth(font, number.value, fontSize) / 2;
                UiRenderer.font(gui, font, number.value, x, screen.y, fontSize, fade(color, Math.min(1, (1 - age) * 4)));
            }
        }
    }
}
