package net.aero.visuals.feature.showcase;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.minecraft.util.Identifier;
/** Procedural GPU materials, inspired by Rival ShaderPlayer/ShaderCrystals. */
public final class Materials {
    private Materials(){}
    public enum Style { GLOW, HOLOGRAM, ENERGY, PULSE, SMOKE }
    public abstract static class Material extends VisualExpansion.Feature {
        public final EnumSetting<Style> style=add(new EnumSetting<>("style","Материал","Процедурный шейдер поверхности модели.",Style.ENERGY,Style.class));
        public final DoubleSetting intensity=number("intensity","Яркость",1,.2,2,.1),speed=number("speed","Скорость",1,.1,3,.1),opacity=number("opacity","Непрозрачность",.55,.1,1,.05);
        public final ColorSetting tint=add(new ColorSetting("color","Цвет материала","",ColorValue.solid(0xFF9A82FF),false));
        public final BooleanSetting rim=flag("rim","Свечение краёв",true);
        protected Material(String id,String name,String description){super(id,name,description,ModuleCategory.RENDER);}
        /** UV2 carries animation/style; UV1 carries RGBA. This shader does not sample the lightmap. */
        public int animation(){return ((style.get().ordinal()|(rim.get()?256:0))<<16)|((int)(System.nanoTime()/1e6*speed.get())%60000);}
        public int color(){int c=tint.get().eval();double power=intensity.get();return ((int)(opacity.get()*255)<<24)|((int)Math.min(255,(c>>16&255)*power)<<16)|((int)Math.min(255,(c>>8&255)*power)<<8)|(int)Math.min(255,(c&255)*power);}
    }
    public static final class Player extends Material {
        public Player(){super("shader_player","ShaderPlayer","Шейдер своей модели: свечение, голограмма, энергия, пульсация и дым. Повторяет настоящую геометрию и анимацию игрока.");}
    }
    public static final class Crystals extends Material {
        public static final Identifier TEXTURE=Identifier.ofVanilla("textures/entity/end_crystal/end_crystal.png");
        public Crystals(){super("shader_crystals","ShaderCrystals","Процедурный материал видимых кристаллов Края. Не подсвечивает сквозь стены и не меняет взаимодействие.");}
    }
}
