package net.aero.visuals.feature.enhanced;

import client.api.Theme;
import net.aero.visuals.hud.*;
import net.aero.visuals.module.impl.hud.HudWidgetModule;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.mixin.ExpansionHotbarAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.lang.ref.WeakReference;

/** Custom hotbar rendering using Aero's existing drag layout. */
public final class VisualHud {
    private VisualHud(){}
    private abstract static class Widget extends HudWidgetModule {
        Widget(String id,String name,String desc,HudPosition fallback){super(id,name,desc,fallback);scale.set(1.0);}
        public boolean replacesVanilla(){return canDraw();}
    }
    /** Rival HotbarHud reference: rounded slots, eased selection and optional vital bars. */
    public static final class Hotbar extends Widget {
        private final BooleanSetting vitalBars=add(new BooleanSetting("vitals","Полосы здоровья, еды и брони","Заменяет стандартные индикаторы в обычном режиме выживания.",false));
        private final DoubleSetting smooth=add(new DoubleSetting("smooth","Плавность выбора слота","Скорость перемещения выделения слота.",18,5,35,1));
        private double selection;private long last;private WeakReference<Object> world;private boolean groupedRender;
        public Hotbar(){super("custom_hotbar","CustomHotbar","Скруглённый хотбар с плавным выбором слота, предметом второй руки и индикаторами состояния.",new HudPosition(HudAnchor.BOTTOM_CENTER,0,3,1));}
        @Override public boolean replacesVanilla(){var p=Minecraft.getInstance().player;return super.replacesVanilla()&&p!=null&&!p.isSpectator();}
        public boolean replacesVitals(){var p=Minecraft.getInstance().player;return replacesVanilla()&&vitalBars.get()&&!p.isCreative()&&!p.isPassenger();}
        public boolean drawingGroup(){return groupedRender;}
        @Override protected boolean usePanel(){return false;}
        @Override protected void onDisable(){last=0;world=null;}
        @Override protected void refresh(Minecraft mc,float delta){content=!mc.player.isSpectator();width=mc.player.getOffhandItem().isEmpty()?208:236;double rows=Math.ceil((mc.player.getMaxHealth()+mc.player.getAbsorptionAmount())/20.0);height=84+Math.max(0,rows-1)*10;int slot=mc.player.getInventory().getSelectedSlot();long now=System.nanoTime();if(world==null||world.get()!=mc.level||last==0){world=new WeakReference<>(mc.level);selection=slot;last=now;}double dt=Math.clamp((now-last)/1e9,0,.05);last=now;selection+=(slot-selection)*(1-Math.exp(-smooth.get()*dt));}
        @Override protected void renderHud(GuiGraphicsExtractor d,Minecraft mc){
            if(mc.player.isSpectator())return;boolean off=!mc.player.getOffhandItem().isEmpty(),left=mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT;double y=height-28,ox=off&&left?28:0;
            // Replay the vanilla lower HUD inside this widget's single transform.
            // This includes XP/level, hearts, food, armor, air, mount/jump bars and item names.
            var pose=d.pose();pose.pushMatrix();groupedRender=true;
            try{pose.translate((float)(ox+104-d.guiWidth()/2),(float)(height-6-d.guiHeight()));((ExpansionHotbarAccess)mc.gui).aero$drawGroup(d,mc.getDeltaTracker());}
            finally{groupedRender=false;pose.popMatrix();}
            int bg=((int)(opacity.get()*2.55)<<24)|(Theme.background()&0xFFFFFF);UiRenderer.roundedRect(d,ox,y,208,28,8,bg);
            UiRenderer.roundedRect(d,ox+5+selection*22,y+3,22,22,6,0x35FFFFFF);UiRenderer.roundedBorder(d,ox+5+selection*22,y+3,22,22,6,1,Theme.primary());
            for(int i=0;i<9;i++){var stack=mc.player.getInventory().getItem(i);int x=(int)ox+8+i*22;if(!stack.isEmpty()){d.item(stack,x,(int)y+6);d.itemDecorations(mc.font,stack,x,(int)y+6);}}
            if(off){int x=left?0:212;UiRenderer.roundedRect(d,x,y,24,28,7,bg);d.item(mc.player.getOffhandItem(),x+4,(int)y+6);d.itemDecorations(mc.font,mc.player.getOffhandItem(),x+4,(int)y+6);}
            if(replacesVitals()){
                meter(d,ox+6,0,62,mc.player.getHealth()/Math.max(1,mc.player.getMaxHealth()),Math.round(mc.player.getHealth())+" +"+Math.round(mc.player.getAbsorptionAmount()),0xFFFF7D98);
                meter(d,ox+73,0,62,mc.player.getFoodData().getFoodLevel()/20.0,mc.player.getFoodData().getFoodLevel()+" / 20",0xFFF6C47D);
                meter(d,ox+140,0,62,mc.player.getArmorValue()/20.0,mc.player.getArmorValue()+" / 20",0xFF91BDFF);
                if(mc.player.getAirSupply()<mc.player.getMaxAirSupply()){double air=Math.clamp((double)mc.player.getAirSupply()/Math.max(1,mc.player.getMaxAirSupply()),0,1);UiRenderer.roundedRect(d,ox+6,23,196,3,1.5,0x40255C8D);UiRenderer.roundedRect(d,ox+6,23,196*air,3,1.5,0xFF70D8FF);}
            }
        }
        private void meter(GuiGraphicsExtractor d,double x,double y,double w,double value,String label,int color){text(d,label,x+(w-textWidth(label,8))/2,y,8,color);UiRenderer.roundedRect(d,x,y+11,w,4,2,0x303E4048);UiRenderer.roundedRect(d,x,y+11,w*Math.clamp(value,0,1),4,2,color);}
    }
}
