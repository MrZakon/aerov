package net.aero.visuals.feature;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.*;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import java.util.*;

/** The Free cosmetic and world modules. */
public final class ImportedFeatures {
    private ImportedFeatures() {}
    public static void install(ModuleManager modules) {
        modules.register(new Cosmetics()); modules.register(new Pet());
        modules.register(new JumpCircles()); modules.register(new ItemPhysics());
        modules.register(new WorldParticles());
        modules.register(new Footprints());
        modules.register(new DeathCoordinates());
        modules.register(new ArmorNotifier());
        modules.register(new ExperienceEffects());
    }
    public static <T extends Module> T get(Class<T> type) {
        var runtime = ClientRuntime.getNullable(); return runtime == null ? null : runtime.module(type);
    }
    public static boolean on(Class<? extends Module> type) { var m=get(type); return m!=null && m.isEnabled(); }
    public abstract static class Feature extends Module {
        protected Feature(String id, String name, String description, ModuleCategory category) {
            super(id, name, description, category);
        }
        protected ColorSetting tint(String key,String name,int color){return color(key,name,color);}
    }
    public static final class Cosmetics extends Feature {
        public final BooleanSetting cape=flag("cape","Плащ с физикой",true),wings=flag("wings","Крылья",true),hat=flag("hat","Головное украшение",false),bodywear=flag("bodywear","Рюкзак",false),graffiti=flag("graffiti","Граффити",false);
        public final DoubleSetting variant=number("variant","Модель плаща",0,0,Math.max(0,ImportedModels.count("cape")-1),1),wingsVariant=number("wings_variant","Модель крыльев",0,0,Math.max(0,ImportedModels.count("wings")-1),1),hatVariant=number("hat_variant","Модель украшения",0,0,Math.max(0,ImportedModels.count("hat")-1),1),size=number("size","Размер",1,.5,1.8,.05);
        public final ColorSetting color=tint("color","Цвет плаща",0xFF9675EA);
        public final DoubleSetting bodywearVariant=number("bodywear_variant","Модель рюкзака",0,0,Math.max(0,ImportedModels.count("bodywear")-1),1),graffitiVariant=number("graffiti_variant","Граффити",0,0,Math.max(0,ImportedModels.count("graffiti")-1),1);
        CapeSimulation simulation=new CapeSimulation();Object trackedWorld;
        public Cosmetics(){super("cosmetics","Cosmetics","Плащи, крылья, украшения и рюкзаки. Синхронизация экипировки через сайт AeroProject.", ModuleCategory.COSMETICS);}
        @Subscribe private void tick(TickEvent.Client e){if(trackedWorld!=e.client().world){simulation=new CapeSimulation();trackedWorld=e.client().world;}if(e.client().player!=null && cape.get()){simulation.b(e.client().player);simulation.c(e.client().player);}}
        @Override protected void onDisable(){simulation=new CapeSimulation();trackedWorld=null;}
    }
    public static final class Pet extends Feature {
        public final DoubleSetting variant=number("variant","Модель питомца",0,0,Math.max(0,ImportedModels.count("pet")-1),1),size=number("size","Размер",.6,.2,1.5,.05),distance=number("distance","Дистанция",1.5,.8,4,.1);
        public Pet(){super("pet","Pet","Модельный питомец: плавное следование по поверхности. Видим другим игрокам через синхронизацию сайта.", ModuleCategory.COSMETICS);}
    }

    public static final class WorldParticles extends Feature {
        public enum Style{FIREFLIES,LEAVES,SNOW,SPARKS}
        final EnumSetting<Style> style=add(new EnumSetting<>("style","Частицы","Вид окружения",Style.FIREFLIES,Style.class));
        final DoubleSetting count=number("count","Количество",4,1,15,1),radius=number("radius","Радиус",8,3,18,1);int ticks;
        public WorldParticles(){super("world_particles","World Particles","Локальные атмосферные частицы с ограничением количества и радиуса.", ModuleCategory.RENDER);}
        @Subscribe private void tick(TickEvent.Client e){var mc=e.client();if(mc.player==null||mc.world==null||++ticks%3!=0)return;var r=mc.player.getRandom();
            for(int i=0;i<count.get().intValue();i++){double x=mc.player.getX()+(r.nextDouble()-.5)*radius.get()*2,y=mc.player.getY()+r.nextDouble()*5,z=mc.player.getZ()+(r.nextDouble()-.5)*radius.get()*2;
                if(!mc.world.isAir(BlockPos.ofFloored(x,y,z)))continue;
                var type=switch(style.get()){case FIREFLIES->ParticleTypes.GLOW;case LEAVES->ParticleTypes.CHERRY_LEAVES;case SNOW->ParticleTypes.SNOWFLAKE;case SPARKS->ParticleTypes.ELECTRIC_SPARK;};
                mc.particleManager.addParticle(type,x,y,z,.006,.001,.003);
            }}
    }
    public static final class DeathCoordinates extends Feature {
        public final BooleanSetting message=flag("message","Сообщение в чат",true);boolean dead;
        private java.lang.ref.WeakReference<Object> world = new java.lang.ref.WeakReference<>(null);
        public DeathCoordinates(){super("death_coordinates","Death Coordinates","Сохраняет координаты собственной смерти в журнал и отдельный локальный файл.", ModuleCategory.MISC);}
        @Subscribe private void tick(TickEvent.Client e){var mc=e.client();if(mc.player==null||mc.world==null){dead=false;return;}if (world.get() != mc.world) {
                world = new java.lang.ref.WeakReference<>(mc.world);
                dead = false;
            }boolean now=mc.player.isDead()||mc.player.getHealth()<=0;
            if(now&&!dead){String value=mc.world.getRegistryKey().getValue()+" "+mc.player.getBlockX()+" "+mc.player.getBlockY()+" "+mc.player.getBlockZ();if(message.get())mc.inGameHud.getChatHud().addMessage(Text.literal("Координаты смерти: "+value));try{var folder=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("aerovisuals-free");var file=java.nio.file.Files.createDirectories(folder).resolve("deaths.log");java.nio.file.Files.writeString(file,java.time.Instant.now()+" "+value+System.lineSeparator(),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}catch(java.io.IOException err){net.aero.visuals.AeroVisuals.LOGGER.warn("Could not save death coordinates",err);}}dead=now;}
    }
    public static final class ArmorNotifier extends Feature {
        final DoubleSetting threshold=number("threshold","Порог (%)",15,1,50,1),cooldown=number("cooldown","Интервал (сек.)",10,3,60,1);long next;
        public ArmorNotifier(){super("armor_notifier","Armor Notifier","Сообщение и звуковой сигнал при низкой прочности своей брони.", ModuleCategory.INVENTORY);}
        @Subscribe private void tick(TickEvent.Client e){var mc=e.client();if(mc.player==null||System.nanoTime()<next)return;for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){var item=mc.player.getEquippedStack(slot);if(!item.isEmpty()&&item.isDamageable()&&100.0*(item.getMaxDamage()-item.getDamage())/item.getMaxDamage()<=threshold.get()){next=System.nanoTime()+(long)(cooldown.get()*1e9);mc.inGameHud.getChatHud().addMessage(Text.literal("Мало прочности: ").append(item.getName()));mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),1.2f,.5f));break;}}}
    }
    public static final class ExperienceEffects extends Feature {
        final DoubleSetting count=number("count","Частицы",12,3,30,1);final BooleanSetting sound=flag("sound","Дополнительный звук",false);int xp=-1;Object world;
        public ExperienceEffects(){super("experience_effects","Experience Effects","Кольцо искр при увеличении собственного опыта. Не создаёт и не собирает сферы.", ModuleCategory.RENDER);}
        @Override protected void onDisable(){xp=-1;world=null;}
        @Subscribe private void tick(TickEvent.Client e){var c=e.client();if(c.player==null||c.world==null){xp=-1;world=null;return;}int next=c.player.totalExperience;if(world!=c.world){xp=next;world=c.world;}if(xp>=0&&next>xp){Vec3d p=c.player.getEntityPos().add(0,.25,0);for(int i=0;i<count.get().intValue();i++){double a=i*Math.PI*2/count.get();c.particleManager.addParticle(ParticleTypes.END_ROD,p.x+Math.cos(a)*.45,p.y,p.z+Math.sin(a)*.45,Math.cos(a)*.035,.04,Math.sin(a)*.035);}if(sound.get())c.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,1.8f,.15f));}xp=next;}
    }
    public static final class Footprints extends Feature implements WorldRenderable {
        private record Mark(Vec3d position, float yaw, long born) {}

        private final Deque<Mark> marks = new ArrayDeque<>();
        private final DoubleSetting life = number("duration", "Длительность (сек.)", 4, .3, 12, .1);
        private final DoubleSetting spacing = number("spacing", "Расстояние", .38, .05, 1, .05);
        private final ColorSetting color = tint("color", "Цвет", 0xFFAF95FF);
        private Object world;
        private Vec3d last;
        private boolean left;

        public Footprints() {
            super("footprints", "Footprints", "Чередующиеся следы подошв на земле с плавным затуханием.", ModuleCategory.RENDER);
        }

        @Subscribe
        private void tick(TickEvent.Client event) {
            var client = event.client();
            var player = client.player;
            if (client.world != world || player == null) {
                clear();
                world = client.world;
            }
            if (client.world == null || player == null) return;

            long now = System.nanoTime();
            long cutoff = now - (long) (life.get() * 1_000_000_000);
            while (!marks.isEmpty() && marks.peekFirst().born() < cutoff) marks.removeFirst();
            if (!player.isOnGround() || player.isTouchingWater() || player.getVelocity().horizontalLengthSquared() <= .002) {
                last = null;
                return;
            }
            var point = player.getEntityPos().add(0, .018, 0);
            if (last != null && point.squaredDistanceTo(last) > 16) {
                marks.clear();
                last = null;
            }
            if (last != null && point.squaredDistanceTo(last) < spacing.get() * spacing.get()) return;
            last = point;
            left = !left;
            double yaw = Math.toRadians(player.getYaw());
            double side = left ? .13 : -.13;
            if (marks.size() >= 240) marks.removeFirst();
            marks.addLast(new Mark(point.add(Math.cos(yaw) * side, 0, Math.sin(yaw) * side), player.getYaw(), now));
        }

        private void clear() {
            marks.clear();
            last = null;
            world = null;
            left = false;
        }

        @Override
        protected void onDisable() {
            clear();
        }

        @Override
        public void renderWorld(WorldRenderContext context) {
            var client = MinecraftClient.getInstance();
            var matrices = context.matrices();
            var buffers = context.consumers();
            if (marks.isEmpty() || world != client.world || matrices == null || buffers == null) return;
            var camera = client.gameRenderer.getCamera().getCameraPos();
            var vertices = buffers.getBuffer(RenderLayers.linesTranslucent());
            long now = System.nanoTime();
            double duration = life.get() * 1_000_000_000;
            int tint = color.get().eval();
            matrices.push();
            try {
                matrices.translate(-camera.x, -camera.y, -camera.z);
                for (var mark : marks) {
                    double remaining = Math.clamp(1 - (now - mark.born()) / duration, 0, 1);
                    int argb = ColorUtil.withAlpha(tint, (int) ((tint >>> 24) * remaining));
                    var p = mark.position();
                    double angle = Math.toRadians(mark.yaw());
                    double sin = Math.sin(angle), cos = Math.cos(angle);
                    for (int i = 0; i < 5; i++) {
                        double offset = -.12 + i * .055;
                        double x = p.x - offset * sin;
                        double z = p.z + offset * cos;
                        LineRenderer.renderLine(matrices, vertices,
                                x - .065 * cos, p.y, z - .065 * sin,
                                x + .065 * cos, p.y, z + .065 * sin, argb);
                    }
                }
            } finally {
                matrices.pop();
            }
        }
    }

    public static final class JumpCircles extends Feature implements WorldRenderable {
        private static final double[] COS=new double[97],SIN=new double[97];static{for(int i=0;i<=96;i++){COS[i]=Math.cos(i*Math.PI/48);SIN[i]=Math.sin(i*Math.PI/48);}}
        public final DoubleSetting radius=number("radius","Радиус",1.5,.3,4,.1),life=number("life","Длительность (сек.)",1.1,.3,3,.1),brightness=number("brightness","Яркость",1,.1,3,.1),thickness=number("thickness","Толщина кольца",.09,.02,.4,.01);
        public final ColorSetting color=tint("color","Цвет",0xFF9D7AFF);
        private record Ring(Vec3d position,long born){} final Deque<Ring> rings=new ArrayDeque<>();boolean grounded;Object world;
        public JumpCircles(){super("jump_circles","Jump Circles","Кольца на месте прыжка с расширением и плавным затуханием; не следуют за игроком.", ModuleCategory.RENDER);}
        @Subscribe private void tick(TickEvent.Client e){var mc=e.client();if(mc.player==null||mc.world==null){clear();return;}if(world!=mc.world){clear();world=mc.world;grounded=mc.player.isOnGround();}
            if(grounded&&!mc.player.isOnGround()&&mc.player.getVelocity().y>.1){if(rings.size()>=12)rings.removeFirst();rings.add(new Ring(mc.player.getEntityPos().add(0,.025,0),System.nanoTime()));}grounded=mc.player.isOnGround();rings.removeIf(r->(System.nanoTime()-r.born)/1e9>life.get());}
        private void clear(){rings.clear();world=null;}
        @Override protected void onDisable(){clear();}
        @Override public void renderWorld(WorldRenderContext c){if(c.matrices()==null||c.consumers()==null)return;var mc=MinecraftClient.getInstance();var cam=mc.gameRenderer.getCamera().getCameraPos();var m=c.matrices();m.push();m.translate(-cam.x,-cam.y,-cam.z);var lines=c.consumers().getBuffer(net.minecraft.client.render.RenderLayers.debugQuads());
            for(Ring ring:rings){double t=Math.clamp((System.nanoTime()-ring.born)/1e9/life.get(),0,1),r=radius.get()*(1-Math.pow(1-t,3));int raw=color.get().eval();double light=brightness.get();int rgb=0xFF000000|((int)Math.min(255,(raw>>16&255)*light)<<16)|((int)Math.min(255,(raw>>8&255)*light)<<8)|(int)Math.min(255,(raw&255)*light);int col=ColorUtil.withAlpha(rgb,(int)(Math.min(255,210*light)*(1-t)));for(int i=0;i<96;i++){double a=i*Math.PI/48,b=(i+1)*Math.PI/48,inner=Math.max(0,r-thickness.get()/2),outer=r+thickness.get()/2;float y=(float)(ring.position.y+.006);var matrix=m.peek();lines.vertex(matrix,(float)(ring.position.x+COS[i]*inner),y,(float)(ring.position.z+SIN[i]*inner)).color(col);lines.vertex(matrix,(float)(ring.position.x+COS[i+1]*inner),y,(float)(ring.position.z+SIN[i+1]*inner)).color(col);lines.vertex(matrix,(float)(ring.position.x+COS[i+1]*outer),y,(float)(ring.position.z+SIN[i+1]*outer)).color(col);lines.vertex(matrix,(float)(ring.position.x+COS[i]*outer),y,(float)(ring.position.z+SIN[i]*outer)).color(col);}}
            m.pop();}
    }
    public static final class ItemPhysics extends Feature {
        public final BooleanSetting flat=flag("flat","Лежать на поверхности",true);
        public final DoubleSetting speed=number("speed","Вращение в воздухе",1,0,3,.1);
        public ItemPhysics(){super("item_physics","Item Physics","Поворот выпавших предметов в воздухе и горизонтальное положение на земле.", ModuleCategory.RENDER);}
    }

}
