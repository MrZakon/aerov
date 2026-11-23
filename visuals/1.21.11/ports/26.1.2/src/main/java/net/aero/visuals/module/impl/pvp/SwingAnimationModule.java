package net.aero.visuals.module.impl.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.setting.EnumSetting;
import net.minecraft.world.entity.HumanoidArm;

public final class SwingAnimationModule extends Module {
    public enum Style { SLASH, STAB, SPIN, CLASSIC }
    private final EnumSetting<Style> style = add(new EnumSetting<>("style", "Анимация", "Рубящий взмах, выпад или вращение.", Style.SLASH, Style.class));
    private final DoubleSetting strength = add(new DoubleSetting("strength", "Размах", "Сила анимации взмаха.", 0.75, 0.2, 1.5, 0.05));

    public SwingAnimationModule() {
        super("swing_animation", "SwingAnimation", "Меняет анимацию взмаха предметом в руке.", ModuleCategory.PVP);
    }

    public void transform(PoseStack matrices, HumanoidArm arm, float progress) {
        if (!isEnabled() || progress <= 0) return;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float pulse = (float) Math.sin(Math.PI * Math.clamp(progress, 0, 1));
        float power = pulse * strength.get().floatValue();
        switch (style.get()) {
            case SLASH -> {
                matrices.mulPose(Axis.ZP.rotationDegrees(side * power * -42));
                matrices.mulPose(Axis.XP.rotationDegrees(power * -24));
            }
            case STAB -> {
                matrices.translate(0, 0, -power * 0.38);
                matrices.mulPose(Axis.XP.rotationDegrees(power * 30));
            }
            case SPIN -> {matrices.translate(0,0,-power*.12);matrices.mulPose(Axis.ZP.rotationDegrees(side*(float)(Math.sin(Math.PI*progress)*180)*strength.get().floatValue()));}
            case CLASSIC -> {float root=(float)Math.sin(Math.sqrt(progress)*Math.PI),squared=(float)Math.sin(progress*progress*Math.PI),p=strength.get().floatValue();matrices.translate(-side*.15f*root*p,.08f*root*p,-.1f*root*p);matrices.mulPose(Axis.YP.rotationDegrees(side*(45-squared*20*p)));matrices.mulPose(Axis.ZP.rotationDegrees(-side*root*20*p));matrices.mulPose(Axis.XP.rotationDegrees(-root*70*p));matrices.mulPose(Axis.YP.rotationDegrees(-side*45));}
        }
    }
}
