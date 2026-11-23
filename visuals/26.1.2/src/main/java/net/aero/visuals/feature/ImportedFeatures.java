package net.aero.visuals.feature;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.*;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Native settings/lifecycle bindings for the sixteen selected functions. */
public final class ImportedFeatures {
    private ImportedFeatures() {}
    public static void install(ModuleManager modules) {
        modules.register(new Cosmetics()); modules.register(new Pet()); modules.register(new Emotes());
        modules.register(new WorldParticles()); modules.register(new Weather()); modules.register(new Hands());
        modules.register(new JumpCircles()); modules.register(new ItemPhysics()); modules.register(new SpatialAudio());
        modules.register(new Fog()); modules.register(new Zoom());
        modules.register(new Streamer()); modules.register(new DeathCoordinates()); modules.register(new ArmorNotifier());
        modules.register(new SoundController());
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
        @Subscribe private void tick(TickEvent.Client e){if(trackedWorld!=e.client().level){simulation=new CapeSimulation();trackedWorld=e.client().level;}if(e.client().player!=null && cape.get()){simulation.b(e.client().player);simulation.c(e.client().player);}}
        @Override protected void onDisable(){simulation=new CapeSimulation();trackedWorld=null;}
    }
    public static final class Pet extends Feature {
        public final DoubleSetting variant=number("variant","Модель питомца",0,0,Math.max(0,ImportedModels.count("pet")-1),1),size=number("size","Размер",.6,.2,1.5,.05),distance=number("distance","Дистанция",1.5,.8,4,.1);
        public final BooleanSetting living = flag("living", "Живой питомец", true);
        public final BooleanSetting danceToMusic = flag("dance_to_music", "Реагировать на музыку", true);
        public Pet(){super("pet","Pet","Модельный питомец: плавное следование по поверхности. Видим другим игрокам через синхронизацию сайта.", ModuleCategory.COSMETICS);}
    }
    public static final class Emotes extends Feature {
        public enum Pose{WAVE,DANCE,BOW,CHEER}
        public final EnumSetting<Pose> pose=add(new EnumSetting<>("pose","Эмоция","Локальная анимация модели",Pose.WAVE,Pose.class));
        public final DoubleSetting speed=number("speed","Скорость",1,.4,2,.1),key=number("key","Клавиша воспроизведения (B=66)",66,32,348,1);
        public final BooleanSetting stopOnMove=flag("stop_move","Останавливать при движении",true);
        long started; boolean playing;
        public Emotes(){super("emotes","Emotes","Взмах, танец, поклон и радость. B запускает/останавливает. Только анимация своего персонажа.", ModuleCategory.RENDER);}
        @Subscribe private void key(KeyEvent e){var mc=Minecraft.getInstance();if(e.action()==1&&e.key()==key.get().intValue()&&mc.screen==null&&mc.player!=null){playing=!playing;started=System.nanoTime();}}
        @Subscribe private void tick(TickEvent.Client e){if(e.client().player==null||(stopOnMove.get()&&e.client().player.getDeltaMovement().horizontalDistanceSqr()>.003))playing=false;}
        public float time(){return (float)((System.nanoTime()-started)/1e9*speed.get());}
        public boolean playing(){return isEnabled()&&playing;}
        @Override protected void onDisable(){playing=false;}
    }
    public static final class WorldParticles extends Feature {
        public enum Style{FIREFLIES,LEAVES,SNOW,SPARKS}
        final EnumSetting<Style> style=add(new EnumSetting<>("style","Частицы","Вид окружения",Style.FIREFLIES,Style.class));
        final DoubleSetting count=number("count","Количество",4,1,15,1),radius=number("radius","Радиус",8,3,18,1);int ticks;
        public WorldParticles(){super("world_particles","World Particles","Локальные атмосферные частицы с ограничением количества и радиуса.", ModuleCategory.RENDER);}
        @Subscribe private void tick(TickEvent.Client e){var mc=e.client();if(mc.player==null||mc.level==null||++ticks%3!=0)return;var r=mc.player.getRandom();
            var weather = get(Weather.class);
            boolean snowCovered = style.get() == Style.SNOW && weather != null && weather.isEnabled();
            for(int i=0;i<net.aero.visuals.module.impl.misc.SmartEffectsModule.particleCount(count.get().intValue(), id());i++){double x=mc.player.getX()+(r.nextDouble()-.5)*radius.get()*2,y=mc.player.getY()+r.nextDouble()*5,z=mc.player.getZ()+(r.nextDouble()-.5)*radius.get()*2;
                if(!mc.level.isEmptyBlock(BlockPos.containing(x,y,z)))continue;
                if (snowCovered && weather.coversSnowAt(mc, x, z)) continue;
                var type=switch(style.get()){case FIREFLIES->ParticleTypes.GLOW;case LEAVES->ParticleTypes.CHERRY_LEAVES;case SNOW->ParticleTypes.SNOWFLAKE;case SPARKS->ParticleTypes.ELECTRIC_SPARK;};
                mc.particleEngine.createParticle(type,x,y,z,.006,.001,.003);
            }}
    }
    public static final class Weather extends Feature {
        final DoubleSetting density=number("density","Плотность",6,1,20,1),radius=number("radius","Радиус",8,3,16,1);int ticks;
        public Weather(){super("weather_effects","Weather Effects","Брызги дождя на поверхности и снег в холодных биомах; только реальная погода.", ModuleCategory.RENDER);}
        @Subscribe private void tick(TickEvent.Client e){var mc=e.client();if(mc.player==null||mc.level==null||!mc.level.isRaining()||++ticks%2!=0)return;var r=mc.player.getRandom();
            for(int i=0;i<net.aero.visuals.module.impl.misc.SmartEffectsModule.particleCount(density.get().intValue(), id());i++){
                int x=(int)Math.floor(mc.player.getX()+(r.nextDouble()-.5)*radius.get()*2),z=(int)Math.floor(mc.player.getZ()+(r.nextDouble()-.5)*radius.get()*2);
                BlockPos p=mc.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,new BlockPos(x,0,z));if(Math.abs(p.getY()-mc.player.getY())>12||!mc.level.canSeeSky(p))continue;
                String biome=mc.level.getBiome(p).unwrapKey().map(k->k.identifier().toString()).orElse("");boolean snow=snowy(biome);
                if(snow)mc.particleEngine.createParticle(ParticleTypes.SNOWFLAKE,x+r.nextDouble(),Math.max(p.getY(),mc.player.getY())+3+r.nextDouble()*4,z+r.nextDouble(),.01,-.03,.005);
                else mc.particleEngine.createParticle(ParticleTypes.SPLASH,x+r.nextDouble(),p.getY()+.02,z+r.nextDouble(),0,.04,0);
            }}
        boolean coversSnowAt(Minecraft mc, double x, double z) {
            if (!mc.level.isRaining() || Math.abs(x - mc.player.getX()) > radius.get()
                    || Math.abs(z - mc.player.getZ()) > radius.get()) return false;
            var p = mc.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    new BlockPos((int)Math.floor(x), 0, (int)Math.floor(z)));
            return Math.abs(p.getY() - mc.player.getY()) <= 12 && mc.level.canSeeSky(p)
                    && snowy(mc.level.getBiome(p).unwrapKey().map(k -> k.identifier().toString()).orElse(""));
        }
        private static boolean snowy(String biome) {
            return biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("grove");
        }
    }
    public static final class Hands extends Feature {
        public enum Style{PLASMA,GALAXY,GLASS}
        public final EnumSetting<Style> style=add(new EnumSetting<>("style","Шейдер","Процедурное оформление рук",Style.PLASMA,Style.class));
        public final ColorSetting color=add(new ColorSetting("color","Цвет","",ColorValue.solid(0xFF8D63FF),false));
        public final DoubleSetting opacity=number("opacity","Непрозрачность",.65,.05,1,.05),speed=number("speed","Скорость",1,.1,3,.1),scale=number("scale","Масштаб",1,.3,3,.1);
        public Hands(){super("hand_shaders","Hand Shaders","Маска первого лица по depth-buffer: плазма, галактика и преломляющее стекло.", ModuleCategory.RENDER);}
    }
    public static final class JumpCircles extends Feature implements WorldRenderable {
        private static final int SEGMENTS = 48;
        private static final double[] COS = new double[SEGMENTS + 1];
        private static final double[] SIN = new double[SEGMENTS + 1];

        static {
            for (int i = 0; i <= SEGMENTS; i++) {
                double angle = i * 2 * Math.PI / SEGMENTS;
                COS[i] = Math.cos(angle);
                SIN[i] = Math.sin(angle);
            }
        }

        public final DoubleSetting radius = number("radius", "Радиус", 1.5, .3, 4, .1);
        public final DoubleSetting life = number("life", "Длительность (сек.)", 1.1, .3, 3, .1);
        public final DoubleSetting brightness = number("brightness", "Яркость", 1, .1, 3, .1);
        public final DoubleSetting thickness = number("thickness", "Толщина кольца", .09, .02, .4, .01);
        public final ColorSetting color = tint("color", "Цвет", 0xFF9D7AFF);

        private record Ring(Vec3 position, long born) {}

        private final Deque<Ring> rings = new ArrayDeque<>();
        private Object world;

        public JumpCircles() {
            super("jump_circles", "Jump Circles", "Кольца с мягкими краями и плавным затуханием на месте прыжка.", ModuleCategory.RENDER);
        }

        public void jump() {
            var mc = Minecraft.getInstance();
            if (!isEnabled() || mc.player == null || mc.level == null) return;
            updateWorld(mc.level);
            if (rings.size() >= 12) rings.removeFirst();
            rings.addLast(new Ring(mc.player.position().add(0, .032, 0), System.nanoTime()));
        }

        @Subscribe
        private void tick(TickEvent.Client event) {
            var mc = event.client();
            updateWorld(mc.player == null ? null : mc.level);
            long cutoff = System.nanoTime() - (long) (life.get() * 1_000_000_000);
            while (!rings.isEmpty() && rings.peekFirst().born() <= cutoff) rings.removeFirst();
        }

        private void updateWorld(Object currentWorld) {
            if (world == currentWorld) return;
            rings.clear();
            world = currentWorld;
        }

        @Override
        protected void onDisable() {
            updateWorld(null);
        }

        @Override
        public void renderWorld(LevelRenderContext context) {
            if (rings.isEmpty()) return;
            var matrices = context.poseStack();
            var buffers = context.bufferSource();
            if (matrices == null || buffers == null) return;
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            matrices.pushPose();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            var matrix = matrices.last();
            var vertices = buffers.getBuffer(net.aero.visuals.feature.showcase.SceneGeometry.COLOR);
            long now = System.nanoTime();
            double duration = life.get() * 1_000_000_000;
            double maxRadius = radius.get();
            double halfWidth = thickness.get() * .5;
            int raw = color.get().eval();
            double light = brightness.get();
            int rgb = (int) Math.min(255, (raw >> 16 & 255) * light) << 16
                    | (int) Math.min(255, (raw >> 8 & 255) * light) << 8
                    | (int) Math.min(255, (raw & 255) * light);
            double maxAlpha = Math.min(255, (raw >>> 24) * light);
            for (Ring ring : rings) {
                double progress = Math.clamp((now - ring.born()) / duration, 0, 1);
                double remaining = 1 - progress;
                double radius = maxRadius * (1 - remaining * remaining * remaining);
                double appear = Math.min(1, progress / .08);
                double fade = remaining * remaining * (3 - 2 * remaining);
                int alpha = (int) (maxAlpha * appear * appear * (3 - 2 * appear) * fade);
                if (alpha == 0) continue;
                int center = ColorUtil.withAlpha(rgb, alpha);
                band(matrix, vertices, ring.position(), Math.max(0, radius - halfWidth), radius, rgb, center);
                band(matrix, vertices, ring.position(), radius, radius + halfWidth, center, rgb);
            }
            matrices.popPose();
        }

        private static void band(com.mojang.blaze3d.vertex.PoseStack.Pose matrix, com.mojang.blaze3d.vertex.VertexConsumer vertices, Vec3 point,
                                 double inner, double outer, int inside, int outside) {
            float y = (float) point.y;
            for (int i = 0; i < SEGMENTS; i++) {
                vertices.addVertex(matrix, (float) (point.x + COS[i] * inner), y, (float) (point.z + SIN[i] * inner)).setColor(inside);
                vertices.addVertex(matrix, (float) (point.x + COS[i + 1] * inner), y, (float) (point.z + SIN[i + 1] * inner)).setColor(inside);
                vertices.addVertex(matrix, (float) (point.x + COS[i + 1] * outer), y, (float) (point.z + SIN[i + 1] * outer)).setColor(outside);
                vertices.addVertex(matrix, (float) (point.x + COS[i] * outer), y, (float) (point.z + SIN[i] * outer)).setColor(outside);
            }
        }
    }
    public static final class ItemPhysics extends Feature {
        public final BooleanSetting flat=flag("flat","Лежать на поверхности",true);
        public final DoubleSetting speed=number("speed","Вращение в воздухе",1,0,3,.1);
        public ItemPhysics(){super("item_physics","Item Physics","Поворот выпавших предметов в воздухе и горизонтальное положение на земле.", ModuleCategory.RENDER);}
    }
    public static final class SpatialAudio extends Feature {
        final BooleanSetting quality=flag("quality","Подробное окружение",false),reverb=flag("reverb","Реверберация",true),occlusion=flag("occlusion","Приглушение",true);
        final DoubleSetting strength=number("strength","Интенсивность",.8,0,1.5,.05);
        public SpatialAudio(){super("spatial_audio","Spatial Audio","OpenAL EFX: акустика помещения и фильтрация звука по окружению.", ModuleCategory.MISC);}
        @Subscribe private void tick(TickEvent.Client e){if(e.client().player!=null)SpatialAudioManager.getInstance().update(e.client(),quality.get(),reverb.get(),occlusion.get(),false,strength.get().floatValue());else SpatialAudioManager.getInstance().disable();}
        @Override protected void onDisable(){SpatialAudioManager.getInstance().disable();}
    }
    public static final class Fog extends Feature {
        public final DoubleSetting start=number("start","Начало",24,4,192,1),range=number("range","Рассеивание",90,16,256,1),strength=number("strength","Плотность",.65,.05,1,.05),blur=number("blur","Размытие вдали",3,0,10,.5);
        public final ColorSetting color=tint("color","Цвет тумана",0xFF8396B4);
        public Fog(){super("custom_fog","Custom Fog","Туман и размытие по восстановленной глубине сцены. Штатные ограничения видимости сохраняются.", ModuleCategory.RENDER);}
    }
    public static final class Zoom extends Feature {
        public final net.aero.visuals.module.setting.KeybindSetting key = add(new net.aero.visuals.module.setting.KeybindSetting("key", "Клавиша", "Клавиша приближения.", 67));
        public final DoubleSetting factor=number("factor","Приближение",3,1.2,12,.2),smoothing=number("smoothing","Плавность",12,3,25,1);
        float current=1,scroll=1;long frame;boolean held;
        public Zoom(){super("zoom","Zoom","Удержание C, плавный FOV, колесо и снижение чувствительности без изменения настроек.", ModuleCategory.RENDER);}
        public float multiplier(){held=isHeld();long now=System.nanoTime();double dt=frame==0?0:Math.min(.1,(now-frame)/1e9);frame=now;float goal=held?(float)(1/(factor.get()*scroll)):1;current+=(goal-current)*(float)(1-Math.exp(-smoothing.get()*dt));return Math.max(.04f,current);}
        private boolean isHeld() {
            var mc = Minecraft.getInstance();
            return isEnabled()&&mc.player!=null&&mc.screen==null&&key.get().isHeld(mc.getWindow().handle());
        }
        @Subscribe private void scroll(MouseEvent e){if(e.type()==MouseEvent.Type.SCROLL&&isHeld()){scroll=(float)Math.clamp(scroll*Math.pow(1.15,e.vertical()),.5,4);e.setCancelled(true);}}
        @Override protected void onDisable(){held=false;current=scroll=1;frame=0;}
    }
    public static final class Streamer extends Feature {
        public final BooleanSetting nickname=flag("nickname","Скрывать свой ник",true),passwords=flag("passwords","Скрывать пароли в командах",true),server=flag("server","Скрывать адрес сервера",true);
        public Streamer(){super("streamer_mode","Streamer Mode","Локальная маскировка ника, адреса сервера и паролей. Отправляемые сообщения не изменяются.", ModuleCategory.MISC);}
        public static String display(String text) {
            var streamer = ImportedFeatures.get(Streamer.class);
            return streamer == null ? text : streamer.filter(text);
        }
        public String filter(String text){if(text==null||!isEnabled())return text;var mc=Minecraft.getInstance();if(nickname.get()){String name=mc.getUser().getName();if(!name.isEmpty())text=text.replace(name,"Player");if(mc.player!=null)text=text.replace(mc.player.getName().getString(),"Player");}if(server.get()&&mc.getCurrentServer()!=null)text=text.replace(mc.getCurrentServer().ip,"Server");return text;}
        public String input(String text){if(!isEnabled()||!passwords.get()||text==null)return text;return text.replaceAll("(?i)^(/(?:login|l|register|reg|changepassword)\\s+)(.+)$","$1"+"*".repeat(Math.max(1,text.length()-text.indexOf(' ')-1)));}
    }
    public static final class DeathCoordinates extends Feature {
        public final BooleanSetting message=flag("message","Сообщение в чат",true);boolean dead;
        private java.lang.ref.WeakReference<Object> world = new java.lang.ref.WeakReference<>(null);
        public DeathCoordinates(){super("death_coordinates","Death Coordinates","Сохраняет координаты собственной смерти в журнал и отдельный локальный файл.", ModuleCategory.MISC);}
        @Subscribe private void tick(TickEvent.Client e){var mc=e.client();if(mc.player==null||mc.level==null){dead=false;return;}if (world.get() != mc.level) {
                world = new java.lang.ref.WeakReference<>(mc.level);
                dead = false;
            }boolean now=mc.player.isDeadOrDying()||mc.player.getHealth()<=0;
            if(now&&!dead){String value=mc.level.dimension().identifier()+" "+mc.player.getBlockX()+" "+mc.player.getBlockY()+" "+mc.player.getBlockZ();if(message.get())mc.gui.getChat().addClientSystemMessage(Component.literal("Координаты смерти: "+value));try{var file=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("aerovisuals-deaths.log");java.nio.file.Files.writeString(file,java.time.Instant.now()+" "+value+System.lineSeparator(),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}catch(java.io.IOException err){net.aero.visuals.AeroVisuals.LOGGER.warn("Could not save death coordinates",err);}}dead=now;}
    }
    public static final class ArmorNotifier extends Feature {
        final DoubleSetting threshold=number("threshold","Порог (%)",15,1,50,1),cooldown=number("cooldown","Интервал (сек.)",10,3,60,1);long next;
        public ArmorNotifier(){super("armor_notifier","Armor Notifier","Сообщение и звуковой сигнал при низкой прочности своей брони.", ModuleCategory.INVENTORY);}
        @Subscribe private void tick(TickEvent.Client e){var mc=e.client();if(mc.player==null||System.nanoTime()<next)return;for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){var item=mc.player.getItemBySlot(slot);if(!item.isEmpty()&&item.isDamageableItem()&&100.0*(item.getMaxDamage()-item.getDamageValue())/item.getMaxDamage()<=threshold.get()){next=System.nanoTime()+(long)(cooldown.get()*1e9);mc.gui.getChat().addClientSystemMessage(Component.literal("Мало прочности: ").append(item.getHoverName()));mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),1.2f,.5f));break;}}}
    }
    public static final class SoundController extends Feature {
        public final DoubleSetting weather=number("weather","Погода",.5,0,1,.05),blocks=number("blocks","Блоки",1,0,1,.05),ambient=number("ambient","Окружение",.7,0,1,.05),players=number("players","Игроки",1,0,1,.05),hostile=number("hostile","Враждебные мобы",1,0,1,.05);
        public SoundController(){super("sound_controller","Sound Controller","Множители громкости категорий без перезаписи ванильных настроек.", ModuleCategory.MISC);for(var setting:settings())setting.onChanged(value->refresh());}
        private void refresh(){var mc=Minecraft.getInstance();if(mc.options!=null)mc.getSoundManager().refreshCategoryVolume(net.minecraft.sounds.SoundSource.MASTER);}
        @Override protected void onEnable(){refresh();}
        @Override protected void onDisable(){refresh();}
        public float multiplier(net.minecraft.sounds.SoundSource category){if(!isEnabled())return 1;return switch(category){case WEATHER->weather.get().floatValue();case BLOCKS->blocks.get().floatValue();case AMBIENT->ambient.get().floatValue();case PLAYERS->players.get().floatValue();case HOSTILE->hostile.get().floatValue();default->1;};}
    }
}
