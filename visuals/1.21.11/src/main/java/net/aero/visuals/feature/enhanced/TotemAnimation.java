package net.aero.visuals.feature.enhanced;

import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.*;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Item activation overlay with independent timing. Reference: Rival TotemAnimation. */
public final class TotemAnimation extends VisualExpansion.Feature {
    public enum Mode { CIRCULAR, SPIRAL, BOUNCE, FLIP, PORTAL }
    private final EnumSetting<Mode> mode=add(new EnumSetting<>("mode","Анимация","Движение тотема на экране.",Mode.SPIRAL,Mode.class));
    private final DoubleSetting duration=number("duration","Длительность (сек.)",1.2,.3,3,.1),size=number("size","Размер",5,2,10,.25);
    private final BooleanSetting preview=flag("preview","Показать предпросмотр",false);
    private ItemStack stack=ItemStack.EMPTY;private int age;private Object world;
    public TotemAnimation(){super("totem_animation","TotemAnimation","Плавная анимация срабатывания тотема: спираль, отскок, вращение и портал.", ModuleCategory.RENDER);}
    public void start(ItemStack item){if(isEnabled()&&item.isOf(Items.TOTEM_OF_UNDYING)){stack=item.copy();age=0;world=MinecraftClient.getInstance().world;}}
    @Override protected void onDisable() {
        stack = ItemStack.EMPTY;
        age = 0;
        world = null;
        preview.set(false);
    }
    @Subscribe private void tick(TickEvent.Client e){if(e.client().world!=world){stack=ItemStack.EMPTY;world=e.client().world;}if(preview.get()){preview.set(false);if(e.client().player!=null)start(new ItemStack(Items.TOTEM_OF_UNDYING));}if(!stack.isEmpty()&&++age>duration.get()*20)stack=ItemStack.EMPTY;}
    @Subscribe private void render(Render2DEvent e){if(MinecraftClient.getInstance().currentScreen==null)draw(e.context(),e.tickDelta());}
    public void draw(net.minecraft.client.gui.DrawContext d,float delta){var mc=MinecraftClient.getInstance();if(!isEnabled()||stack.isEmpty()||mc.player==null||world!=mc.world||mc.options.hudHidden)return;double t=Math.clamp((age+delta)/(duration.get()*20),0,1),ease=Math.sin(Math.PI*t),x=0,y=0,angle=0,s=size.get()*Math.min(1,Math.min(t/.12,(1-t)/.18));
        switch(mode.get()){
            case CIRCULAR -> {double radius=36*Math.sin(Math.PI*t);angle=t*360;x=Math.cos(t*6.28)*radius;y=Math.sin(t*6.28)*radius;}
            case SPIRAL -> {double radius=80*Math.pow(1-t,2);angle=t*720;x=Math.cos(t*12.56)*radius;y=Math.sin(t*12.56)*radius;}
            case BOUNCE -> {y=-55*Math.abs(Math.sin(t*Math.PI*3))*(1-t);angle=Math.sin(t*6.28)*12;}
            case FLIP -> {angle=t*360;s*=.35+.65*Math.abs(Math.cos(t*Math.PI*3));}
            case PORTAL -> {angle=t*540;s*=.6+.6*ease;y=(1-t)*20;}
        }
        var pose=d.getMatrices();pose.pushMatrix();try{pose.translate((float)(d.getScaledWindowWidth()/2.0+x),(float)(d.getScaledWindowHeight()/2.0+y));pose.rotate((float)Math.toRadians(angle));pose.scale((float)Math.max(.001,s),(float)Math.max(.001,s));d.drawItem(stack,-8,-8);}finally{pose.popMatrix();}
    }
}
