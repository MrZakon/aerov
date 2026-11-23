package net.aero.visuals.feature;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.*;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import net.minecraft.resources.Identifier;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.*;


import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;

public final class ExtraFeatures {
    private ExtraFeatures() {}
    public static void install(ModuleManager m) {
        m.register(new Footprints());m.register(new Trail());m.register(new InventoryAnimation());
        m.register(new BlockHighlight());m.register(new Ambience());m.register(new FoodEffects());
        m.register(new ExperienceEffects());m.register(new History());m.register(new Screenshots());m.register(new StyleProfiles());
        for(var module:m.all()) module.onStateChanged(on->FeatureJournal.record(module.name()+(on?" включён":" выключен")));
    }
    private static Minecraft mc(){return Minecraft.getInstance();}
    private record Mark(Vec3 p,float yaw,long born){}
    private abstract static class PathEffect extends ImportedFeatures.Feature implements WorldRenderable {
        final Deque<Mark> marks=new ArrayDeque<>();Object world;Vec3 last;boolean left;
        final DoubleSetting life,spacing;final ColorSetting color;
        PathEffect(String id,String name,String desc,double duration,double step){super(id,name,desc);life=number("duration","Длительность (сек.)",duration,.3,12,.1);spacing=number("spacing","Расстояние",step,.05,1,.05);color=tint("color","Цвет",0xFFAF95FF);}
        abstract boolean allowed();abstract Vec3 position();
        @Subscribe private void tick(TickEvent.Client e){var c=e.client();if(c.level!=world||c.player==null){clear();world=c.level;}if(c.player==null)return;
            long now=System.nanoTime();marks.removeIf(p->(now-p.born)/1e9>life.get());if(!allowed()){last=null;return;}Vec3 p=position();
            if(last!=null&&p.distanceToSqr(last)>16){marks.clear();last=null;}
            if(last==null||p.distanceToSqr(last)>=spacing.get()*spacing.get()){last=p;left=!left;add(p,c.player.getYRot(),now);while(marks.size()>240)marks.removeFirst();}}
        void add(Vec3 p,float yaw,long now){marks.add(new Mark(p,yaw,now));}
        void clear(){marks.clear();last=null;world=null;}
        @Override protected void onDisable(){clear();}
        int faded(Mark p){return ColorUtil.withAlpha(color.get().eval(),(int)((color.get().eval()>>>24)*Math.clamp(1-(System.nanoTime()-p.born)/1e9/life.get(),0,1)));}
        @Override public final void renderWorld(LevelRenderContext c){if(c.poseStack()==null||c.bufferSource()==null)return;var cam=mc().gameRenderer.getMainCamera().position();c.poseStack().pushPose();c.poseStack().translate(-cam.x,-cam.y,-cam.z);draw(c);c.poseStack().popPose();}
        abstract void draw(LevelRenderContext c);
    }
    public static final class Footprints extends PathEffect {
        public Footprints(){super("footprints","Footprints","Чередующиеся отпечатки подошв на твёрдой поверхности с затуханием.",4,.38);}
        boolean allowed(){return mc().player.onGround()&&!mc().player.isInWater()&&mc().player.getDeltaMovement().horizontalDistanceSqr()>.002;}
        Vec3 position(){var p=mc().player;return p.position().add(0,.015,0);}
        @Override void add(Vec3 p,float yaw,long now){double a=Math.toRadians(yaw);double side=left?.13:-.13;marks.add(new Mark(p.add(Math.cos(a)*side,0,Math.sin(a)*side),yaw,now));}
        void draw(LevelRenderContext c){var v=c.bufferSource().getBuffer(RenderTypes.linesTranslucent());for(var p:marks){double a=Math.toRadians(p.yaw),s=Math.sin(a),co=Math.cos(a);int col=faded(p);for(int i=0;i<5;i++){double z=-.12+i*.055;Vec3 l=p.p.add(-.065*co-z*s,0,-.065*s+z*co),r=p.p.add(.065*co-z*s,0,.065*s+z*co);LineRenderer.renderLine(c.poseStack(),v,l.x,l.y,l.z,r.x,r.y,r.z,col);}}}
    }
    public static final class Trail extends PathEffect {
        final DoubleSetting width=number("width","Ширина ленты",.2,.03,.8,.01);final BooleanSetting firstPerson=flag("first_person","Видно от первого лица",false);
        public Trail(){super("player_trail","Player Trail","Лента за своим персонажем с ограничением длины и плавным исчезновением.",1.8,.08);}
        boolean allowed(){return mc().player.getDeltaMovement().lengthSqr()>.003;}
        Vec3 position(){return mc().player.position().add(0,.7,0);}
        void draw(LevelRenderContext c){if(mc().options.getCameraType().isFirstPerson()&&!firstPerson.get())return;var v=c.bufferSource().getBuffer(RenderTypes.debugQuads());Mark before=null;for(var p:marks){if(before!=null&&p.p.distanceToSqr(before.p)<4){double w=width.get()/2;int a=faded(before),b=faded(p);vertex(c,v,before.p.add(0,w,0),a);vertex(c,v,before.p.add(0,-w,0),a);vertex(c,v,p.p.add(0,-w,0),b);vertex(c,v,p.p.add(0,w,0),b);vertex(c,v,p.p.add(0,w,0),b);vertex(c,v,p.p.add(0,-w,0),b);vertex(c,v,before.p.add(0,-w,0),a);vertex(c,v,before.p.add(0,w,0),a);}before=p;}}
    }
    static void vertex(LevelRenderContext c,VertexConsumer v,Vec3 p,int color){v.addVertex(c.poseStack().last(),(float)p.x,(float)p.y,(float)p.z).setColor(color);}
    public static final class InventoryAnimation extends ImportedFeatures.Feature {
        public final DoubleSetting duration=number("duration","Длительность (мс)",230,80,600,10);
        public final ColorSetting color=tint("color","Выделение слота",0x659C80FF);
        public InventoryAnimation(){super("inventory_animation","Inventory Animation","Плавное появление и изменение предметов, анимированное выделение слотов. Область нажатия сохраняется.");}
    }
    public static final class BlockHighlight extends ImportedFeatures.Feature implements WorldRenderable {
        final ColorSetting color=tint("color","Цвет",0xFFA88AFF);final DoubleSetting rounding=number("rounding","Скругление",.08,0,.24,.01),fill=number("fill","Заливка",.12,0,.5,.01);
        public BlockHighlight(){super("block_highlight","Block Highlight","Контур и заливка выбранного блока с учётом формы. Без отображения сквозь стены.");}
        @Override public void renderWorld(LevelRenderContext c){if(!(mc().hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||c.poseStack()==null||c.bufferSource()==null||mc().level==null)return;var pos=hit.getBlockPos();var shape=mc().level.getBlockState(pos).getShape(mc().level,pos);if(shape.isEmpty())return;
            var cam=mc().gameRenderer.getMainCamera().position();var m=c.poseStack();m.pushPose();m.translate(pos.getX()-cam.x,pos.getY()-cam.y,pos.getZ()-cam.z);
            for(AABB raw:shape.toAabbs()){AABB b=raw.inflate(.002);double r=Math.min(rounding.get(),Math.min(b.getXsize(),Math.min(b.getYsize(),b.getZsize()))*.24);
                for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++){double fixed=axis==0?(side==0?b.minX:b.maxX):axis==1?(side==0?b.minY:b.maxY):(side==0?b.minZ:b.maxZ);double lo=axis==0?b.minY:b.minX,hi=axis==0?b.maxY:b.maxX,bottom=axis==2?b.minY:b.minZ,top=axis==2?b.maxY:b.maxZ;List<Vec3> points=new ArrayList<>();double[][] centers={{hi-r,top-r},{lo+r,top-r},{lo+r,bottom+r},{hi-r,bottom+r}};
                    for(int corner=0;corner<4;corner++)for(int j=0;j<=5;j++){double a=(corner*90+j*18)*Math.PI/180,u=centers[corner][0]+Math.cos(a)*r,v=centers[corner][1]+Math.sin(a)*r;points.add(axis==0?new Vec3(fixed,u,v):axis==1?new Vec3(u,fixed,v):new Vec3(u,v,fixed));}
                    var lines=c.bufferSource().getBuffer(RenderTypes.linesTranslucent());for(int i=0;i<points.size();i++){var a=points.get(i);var d=points.get((i+1)%points.size());LineRenderer.renderLine(m,lines,a.x,a.y,a.z,d.x,d.y,d.z,color.get().eval());}
                    if(fill.get()>0){var quads=c.bufferSource().getBuffer(RenderTypes.debugQuads());Vec3 center=axis==0?new Vec3(fixed,(lo+hi)/2,(bottom+top)/2):axis==1?new Vec3((lo+hi)/2,fixed,(bottom+top)/2):new Vec3((lo+hi)/2,(bottom+top)/2,fixed);int col=ColorUtil.withAlpha(color.get().eval(),(int)(fill.get()*255));for(int i=0;i<points.size();i++){var a=points.get(i);var d=points.get((i+1)%points.size());vertex(c,quads,center,col);vertex(c,quads,a,col);vertex(c,quads,d,col);vertex(c,quads,center,col);vertex(c,quads,center,col);vertex(c,quads,d,col);vertex(c,quads,a,col);vertex(c,quads,center,col);}}
                }}m.popPose();}
    }
    public static final class Ambience extends ImportedFeatures.Feature {
        final DoubleSetting volume=number("volume","Громкость",.25,0,1,.05);final BooleanSetting wind=flag("wind","Ветер",true),forest=flag("forest","Лес",true),cave=flag("cave","Пещеры",true);
        SoundInstance playing;String current="";long next;Object world;
        private float playingVolume;
        public Ambience(){super("biome_ambience","Biome Ambience","Локальная атмосфера: ветер, лес и пещеры; биом, высота, время суток. Без смены ванильных настроек.");}
        @Subscribe private void tick(TickEvent.Client e){var c=e.client();if(c.level==null||c.player==null){stop();world=null;return;}if(world!=c.level){stop();world=c.level;}if(c.isPaused()){stop();return;}String biome=c.level.getBiome(c.player.blockPosition()).unwrapKey().map(k->k.identifier().getPath()).orElse("");boolean underground=!c.level.canSeeSky(c.player.blockPosition())&&c.player.getY()<c.level.getSeaLevel();String type=underground?(cave.get()?"cave":""):(forest.get()&&(biome.contains("forest")||biome.contains("taiga")||biome.contains("jungle"))?"forest":wind.get()?"wind":"");
            String variant=type.equals("forest")&&c.level.isDarkOutside()?"forest_night":type;long now=System.nanoTime();if(!variant.equals(current)){stop();current=variant;}float gain = volume.get().floatValue();
            if (variant.isEmpty() || gain == 0) { stop(); return; }
            if (playing == null || gain != playingVolume || now - next >= 0) {
                if (playing != null) c.getSoundManager().stop(playing);
                playingVolume = gain;playing=new SimpleSoundInstance(Identifier.fromNamespaceAndPath("aerovisuals","ambience."+variant),SoundSource.AMBIENT,gain,1,c.player.getRandom(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true);c.getSoundManager().play(playing);next=now+8_000_000_000L;}}
        void stop(){if(playing!=null)mc().getSoundManager().stop(playing);playing=null;current="";next=0;}
        @Override protected void onDisable(){stop();world=null;}
    }
    public static final class FoodEffects extends ImportedFeatures.Feature {
        final DoubleSetting count=number("count","Частицы",4,1,12,1);final BooleanSetting sound=flag("sound","Звук завершения",true);boolean eating;int remaining;long ticks;Object world;
        public FoodEffects(){super("food_effects","Food Effects","Локальные частицы еды и напитков; звук только при завершении употребления.");}
        @Override protected void onDisable() {
            eating = false;
            remaining = 0;
            ticks = 0;
            world = null;
        }
        @Subscribe private void tick(TickEvent.Client event) {
            var client = event.client();
            var player = client.player;
            if (player == null || client.level == null) {
                eating = false;
                remaining = 0;
                ticks = 0;
                world = null;
                return;
            }
            if (world != client.level) {
                eating = false;
                remaining = 0;
                ticks = 0;
                world = client.level;
            }
            var item = player.getUseItem();
            var action = item.getUseAnimation();
            boolean food = action == net.minecraft.world.item.ItemUseAnimation.EAT;
            boolean using = player.isUsingItem() && !item.isEmpty()
                    && (food || action == net.minecraft.world.item.ItemUseAnimation.DRINK);
            if (eating && !using && remaining <= 2 && sound.get()) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, .2f));
            }
            eating = using;
            remaining = using ? player.getUseItemRemainingTicks() : 0;
            if (!using) {
                ticks = 0;
                return;
            }
            if (++ticks < 3) return;
            ticks = 0;
            var center = player.getEyePosition().add(player.getViewVector(1).scale(.35));
            var random = player.getRandom();
            ParticleOptions particle = food
                    ? new ItemParticleOption(ParticleTypes.ITEM, net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(item))
                    : ParticleTypes.SPLASH;
            int particles = count.get().intValue();
            for (int index = 0; index < particles; index++) {
                client.particleEngine.createParticle(particle, center.x + (random.nextDouble() - .5) * .15,
                        center.y - .15, center.z + (random.nextDouble() - .5) * .15,
                        (random.nextDouble() - .5) * .03, .025, (random.nextDouble() - .5) * .03);
            }
        }
    }
    public static final class ExperienceEffects extends ImportedFeatures.Feature {
        final DoubleSetting count=number("count","Частицы",12,3,30,1);final BooleanSetting sound=flag("sound","Дополнительный звук",false);int xp = -1;
        private java.lang.ref.WeakReference<Object> xpPlayer;
        private java.lang.ref.WeakReference<Object> xpWorld;
        public ExperienceEffects(){super("experience_effects","Experience Effects","Кольцо искр при увеличении собственного опыта. Не создаёт и не собирает сферы.");}
        @Override protected void onDisable() {
            xp = -1;
            xpPlayer = null;
            xpWorld = null;
        }

        @Subscribe private void tick(TickEvent.Client event) {
            var client = event.client();
            var player = client.player;
            var world = client.level;
            if (player == null || world == null) {
                onDisable();
                return;
            }
            int next = player.totalExperience;
            if (xpPlayer == null || xpPlayer.get() != player || xpWorld == null || xpWorld.get() != world) {
                xpPlayer = new java.lang.ref.WeakReference<>(player);
                xpWorld = new java.lang.ref.WeakReference<>(world);
                xp = next;
                return;
            }
            int previous = xp;
            xp = next;
            if (previous < 0 || next <= previous) return;
            var position = player.position().add(0, .25, 0);
            int particles = count.get().intValue();
            double step = Math.PI * 2 / particles;
            for (int index = 0; index < particles; index++) {
                double x = Math.cos(index * step);
                double z = Math.sin(index * step);
                client.particleEngine.createParticle(ParticleTypes.END_ROD, position.x + x * .45, position.y,
                        position.z + z * .45, x * .035, .04, z * .035);
            }
            if (sound.get()) client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8f, .15f));
        }
    }
    public static final class History extends ImportedFeatures.Feature {
        final DoubleSetting key=number("key","Клавиша (H=72)",72,32,348,1);final BooleanSetting open=flag("open","Открыть историю",false);
        public History(){super("notification_history","Notification History","История уведомлений клиента и переключений модулей; последние 200 записей сохраняются.");}
        @Subscribe private void tick(TickEvent.Client e){if(open.get()){open.set(false);FeatureScreens.history(e.client().screen);}}
        @Subscribe private void key(KeyEvent e){if(e.action()==1&&e.key()==key.get().intValue()&&mc().screen==null)FeatureScreens.history(null);}
    }
    public static final class Screenshots extends ImportedFeatures.Feature {
        final BooleanSetting open=flag("open","Открыть последний снимок",false);final DoubleSetting key=number("key","Клавиша (F8=297)",297,32,348,1),duration=number("duration","Показ миниатюры (сек.)",6,2,20,1);
        public Screenshots(){super("screenshot_preview","Screenshot Preview","Миниатюра новых PNG из screenshots, просмотр и кнопка открытия папки. F8 — последний снимок.");}
        @Subscribe private void tick(TickEvent.Client e){ScreenshotPreview.tick();if(open.get()){open.set(false);FeatureScreens.screenshot(e.client().screen);}}
        @Subscribe private void key(KeyEvent e){if(e.action()==1&&e.key()==key.get().intValue()&&mc().screen==null)FeatureScreens.screenshot(null);}
        @Subscribe private void draw(Render2DEvent e){ScreenshotPreview.overlay(e.context(),duration.get());}
        @Override protected void onEnable(){ScreenshotPreview.start();}
        @Override protected void onDisable(){ScreenshotPreview.close();}
    }
    public static final class StyleProfiles extends ImportedFeatures.Feature {
        final BooleanSetting open=flag("open","Открыть профили оформления",false);final DoubleSetting key=number("key","Клавиша (P=80)",80,32,348,1);
        public StyleProfiles(){super("style_profiles","Style Profiles","Именованные наборы косметики, цветов и визуальных эффектов. Главное меню и HUD не сохраняются.");}
        @Subscribe private void tick(TickEvent.Client e){if(open.get()){open.set(false);FeatureScreens.profiles(e.client().screen);}}
        @Subscribe private void key(KeyEvent e){if(e.action()==1&&e.key()==key.get().intValue()&&mc().screen==null)FeatureScreens.profiles(null);}
    }
}
