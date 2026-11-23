package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
public final class AspectRatio extends VisualExpansion.Feature {
    public enum Preset { CUSTOM, NORMAL_16_9, CLASSIC_4_3, ULTRAWIDE_21_9 }
    private final EnumSetting<Preset> preset=add(new EnumSetting<>("preset","Пропорции","Соотношение сторон проекции мира.",Preset.CUSTOM,Preset.class));
    private final DoubleSetting ratio=number("ratio","Свои пропорции",1.75,.75,3,.05);
    public AspectRatio(){super("aspect_ratio","AspectRatio","Меняет пропорции изображения мира, сохраняя размеры интерфейса и поведение окна.", ModuleCategory.RENDER);}
    public float ratio(){return (float)switch(preset.get()){case CUSTOM->ratio.get();case NORMAL_16_9->16./9;case CLASSIC_4_3->4./3;case ULTRAWIDE_21_9->21./9;};}
    @Override public String displayValue(){return String.format(java.util.Locale.ROOT,"%.2f",ratio());}
}
