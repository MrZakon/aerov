package net.aero.visuals.feature.enhanced;

import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;

/** Smoothed third-person distance; the vanilla camera still performs wall clipping. */
public final class BetterF5 extends VisualExpansion.Feature {
    public final DoubleSetting height=number("height","Высота камеры",.1,-1,2,.05);
    private final DoubleSetting distance=number("distance","Расстояние камеры",5,1,12,.25),smoothing=number("smoothing","Плавность",10,3,25,1);
    private long last;private double currentDistance,currentHeight;
    public BetterF5(){super("better_f5","BetterF5","Плавная дистанция и высота камеры от третьего лица с сохранением столкновений со стенами.", ModuleCategory.RENDER);}
    public void reset(){last=0;currentHeight=0;currentDistance=4;}
    @Override protected void onEnable(){reset();}
    @Override protected void onDisable(){reset();}
    public float distance(float vanilla){long now=System.nanoTime();double dt=last==0?0:Math.clamp((now-last)/1e9,0,.05);if(last==0)currentDistance=vanilla;last=now;double a=1-Math.exp(-smoothing.get()*dt);currentDistance+=(distance.get()-currentDistance)*a;currentHeight+=(height.get()-currentHeight)*a;return (float)currentDistance;}
    public double height(){return currentHeight;}
}
