package net.aero.visuals.feature.premium;

import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.*;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.math.Axis;
import net.minecraft.network.chat.Component;

/** Native implementations of the visual ideas indexed in PREMIUM_FEATURES_30.md. */
public final class PremiumFeatures {
    private PremiumFeatures() {}
    public static <T extends Module> T get(Class<T> type) {return VisualExpansion.get(type);}
    public static boolean on(Class<? extends Module> type) {var m=get(type);return m!=null&&m.isEnabled();}
    public static void install(ModuleManager m) {
        m.register(new CustomGlint());m.register(new SkyShader());
        m.register(new WorldEffects.Particular());m.register(new Shading());m.register(new WorldEffects.Constellation());
        m.register(new ItemInspect());m.register(new WorldEffects.DamageNumbers());
        m.register(new WorldEffects.ProjectileTrails());m.register(new WorldEffects.PickBlowup());m.register(new WorldEffects.BlockParticles());
        m.register(new WorldEffects.WaveEffect());m.register(new DistantAlpha());
        m.register(new WorldEffects.HitMarker());m.register(new ShieldPhysics());m.register(new TabCustomizer());
        
        
        m.register(new SoundReducer());
        
    }

    public static final class CustomGlint extends VisualExpansion.Feature {
        public final DoubleSetting strength=number("strength","Сила свечения",1.2,0,3,.1),radius=number("radius","Радиус свечения",3,1,8,.5);
        public final BooleanSetting items=flag("items","Предметы",true),armor=flag("armor","Броня",true);
        public final ColorSetting tint=color("color","Цвет свечения",0xFFB69CFF);
        public CustomGlint(){super("custom_glint","CustomGlint","Настраиваемое зачарование с проверкой глубины и мягким свечением без пересвета.", ModuleCategory.RENDER);}
    }
    public enum Sky { NEBULA, GALAXY, VORTEX }
    public static final class SkyShader extends VisualExpansion.Feature {
        public final EnumSetting<Sky> style=add(new EnumSetting<>("style","Небо","Процедурное небо. Видно только на фоне мира.",Sky.NEBULA,Sky.class));
        public final DoubleSetting speed=number("speed","Скорость",.3,0,2,.05),strength=number("strength","Яркость",.8,.1,1.5,.05);
        public final ColorSetting tint=color("color","Цвет туманности",0xFF8175FF);
        public SkyShader(){super("sky_shader","SkyShader","Туманности, звёзды и вихрь без кубических облаков и изменения времени сервера.", ModuleCategory.RENDER);}
    }
    public static final class Shading extends VisualExpansion.Feature {
        public final DoubleSetting bloom=number("bloom","Bloom",.3,0,1,.05),clouds=number("clouds","Мягкие облака",.45,0,1,.05),warmth=number("warmth","Теплота света",.15,-1,1,.05);
        public final DoubleSetting bloomRadius=number("shading_bloom_radius","Радиус bloom",10,3,24,1),bloomThreshold=number("shading_bloom_threshold","Порог bloom",.8,.55,.95,.025),cloudHeight=number("shading_cloud_height","Высота мягких облаков",192,128,320,8);
        public Shading(){super("shading","Shading","Мягкие облака, коррекция света и bloom ярких поверхностей.", ModuleCategory.RENDER);}
    }

    public static final class DistantAlpha extends VisualExpansion.Feature {
        public final DoubleSetting near=number("near","Прозрачный вблизи",.4,.1,1,.05),far=number("far","Непрозрачный от",1.6,1.1,4,.1),minimum=number("minimum","Минимальная видимость",.12,.05,.8,.05);
        public DistantAlpha(){super("distant_alpha","DistantAlpha","Модель плавно становится прозрачной при приближении камеры вплотную.", ModuleCategory.RENDER);}
    }
    public enum InspectMode { TURN, FLIP, SPIN, SHOWCASE, RANDOM }
    public static final class ItemInspect extends VisualExpansion.Feature {
        public final DoubleSetting key=number("key","Клавиша осмотра (GLFW)",71,32,348,1),duration=number("duration","Длительность",2.4,1.2,5,.1);
        public final EnumSetting<InspectMode> animation=add(new EnumSetting<>("inspect_animation","Анимация осмотра","Поворот, переворот, вращение, показ с разных сторон или случайный вариант.",InspectMode.SHOWCASE,InspectMode.class));
        public final DoubleSetting amplitude=number("inspect_amplitude","Размах осмотра",1,.5,1.4,.05);
        private long started; private ItemStack inspected=ItemStack.EMPTY; private InspectMode selected=InspectMode.SHOWCASE;
        public ItemInspect(){super("item_inspect","ItemInspect","Осмотр предмета по G: четыре плавные анимации с вращением вокруг самого предмета.",ModuleCategory.INVENTORY);}
        @Subscribe private void key(KeyEvent event){
            var mc=Minecraft.getInstance();
            if(event.action()!=1||event.key()!=key.get().intValue()||mc.gui.screen()!=null||mc.player==null||mc.player.isUsingItem()||mc.player.getMainHandItem().isEmpty())return;
            selected=animation.get()==InspectMode.RANDOM?InspectMode.values()[mc.player.getRandom().nextInt(4)]:animation.get();
            inspected=mc.player.getMainHandItem().copy();started=System.nanoTime();
        }
        @Subscribe private void tick(TickEvent.Client event){
            var mc=event.client();
            if(started!=0&&(mc.player==null||mc.gui.screen()!=null||mc.player.isUsingItem()||!ItemStack.isSameItemSameComponents(inspected,mc.player.getMainHandItem())))reset();
        }
        private void reset(){started=0;inspected=ItemStack.EMPTY;}
        @Override protected void onDisable(){reset();}
        private static float smooth(double x){float f=(float)Math.clamp(x,0,1);return f*f*(3-2*f);}
        public void transform(PoseStack matrices,HumanoidArm arm){
            if(!isEnabled()||started==0)return;
            double t=(System.nanoTime()-started)/(duration.get()*1e9);
            var mc=Minecraft.getInstance();
            if(t>=1||mc.player==null||mc.player.isUsingItem()||!ItemStack.isSameItemSameComponents(inspected,mc.player.getMainHandItem())){reset();return;}
            float envelope=smooth(t/.2)*smooth((1-t)/.2),motion=smooth((t-.18)/.64),side=arm==HumanoidArm.RIGHT?1:-1,amount=amplitude.get().floatValue();
            float x=0,y=0,z=0;
            switch(selected){
                case TURN -> {y=side*envelope*(55+45*(float)Math.sin(motion*Math.PI));x=-15*envelope;z=-side*12*envelope;}
                case FLIP -> {x=360*motion+envelope*12;y=side*25*envelope;z=side*8*envelope;}
                case SPIN -> {z=side*360*motion;y=side*40*envelope;x=10*envelope;}
                case SHOWCASE,RANDOM -> {y=side*envelope*(45+40*(float)Math.sin(motion*Math.PI*2));x=envelope*(-20+16*(float)Math.sin(motion*Math.PI));z=-side*18*envelope;}
            }
            matrices.translate(-side*.06*envelope*amount,.1*envelope*amount,-.035*envelope);
            // Full turns end at identity; amplitude changes only the inspection tilt, not the 360-degree turn.
            matrices.mulPose(Axis.YP.rotationDegrees(y*amount));
            matrices.mulPose(Axis.XP.rotationDegrees(selected==InspectMode.FLIP?360*motion+(x-360*motion)*amount:x*amount));
            matrices.mulPose(Axis.ZP.rotationDegrees(selected==InspectMode.SPIN?side*360*motion:z*amount));
        }
    }

    public static final class ShieldPhysics extends VisualExpansion.Feature {
        public final DoubleSetting strength=number("strength","Сила отдачи",1,.2,2,.1);
        private long impact;
        public ShieldPhysics(){super("shield_physics","ShieldPhysics","Пружинящая отдача щита при подтверждённом сервером блокировании удара.",ModuleCategory.PVP);}
        public void hit(){if(isEnabled())impact=System.nanoTime();}
        public void transform(PoseStack m,ItemStack stack,HumanoidArm arm){if(!isEnabled()||!stack.is(Items.SHIELD))return;double t=(System.nanoTime()-impact)/1e9;if(t<0||t>1)return;float v=(float)(Math.sin(t*25)*Math.exp(-t*7)*strength.get());m.translate(0,-.025*v,.12*v);m.mulPose(Axis.XP.rotationDegrees(-17*v));m.mulPose(Axis.ZP.rotationDegrees((arm==HumanoidArm.RIGHT?1:-1)*8*v));}
    }
    public static final class TabCustomizer extends VisualExpansion.Feature {
        public final BooleanSetting heads=flag("heads","Головы игроков",true),ping=flag("ping","Пинг цифрами",true),header=flag("header","Заголовок",true),footer=flag("footer","Подвал",false);
        public TabCustomizer(){super("tab_customizer","TabCustomizer","Головы, пинг цифрами и оформление списка игроков. Анимация TAB сохраняется.", ModuleCategory.HUD);}
    }
    public static final class SoundReducer extends VisualExpansion.Feature {
        public final BooleanSetting anvil=flag("anvil","Наковальня",true),portal=flag("portal","Портал",true),explosion=flag("explosion","Взрывы",false),totem=flag("totem","Тотем",false);
        public final DoubleSetting volume=number("volume","Громкость (%)",20,0,100,1);
        public SoundReducer(){super("sound_reducer","SoundReducer","Приглушает выбранные звуки, сохраняя громкость остальных звуков категории.",ModuleCategory.MISC);}
        public float multiplier(String id){if(!isEnabled())return 1;return (anvil.get()&&id.contains("anvil")||portal.get()&&id.contains("portal")||explosion.get()&&id.contains("explode")||totem.get()&&id.contains("totem"))?volume.get().floatValue()/100:1;}
    }



}
