package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.DoubleSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

public final class ViewModelModule extends Module {
    private final DoubleSetting x = add(new DoubleSetting("x", "Сдвиг X", "Сдвиг предмета по горизонтали.", 0, -1, 1, 0.05));
    private final DoubleSetting y = add(new DoubleSetting("y", "Сдвиг Y", "Сдвиг предмета по вертикали.", 0, -1, 1, 0.05));
    private final DoubleSetting z = add(new DoubleSetting("z", "Сдвиг Z", "Приближение или удаление предмета.", 0, -1, 1, 0.05));
    private final DoubleSetting scale = add(new DoubleSetting("scale", "Масштаб", "Размер предметов в руках.", 1, 0.5, 1.5, 0.05));
    private final DoubleSetting rotate = add(new DoubleSetting("rotate", "Поворот", "Поворот предмета вокруг оси взгляда.", 0, -90, 90, 5));

    public ViewModelModule() {
        super("view_model", "ViewModel", "Меняет положение, масштаб и поворот предметов в руках от первого лица.", ModuleCategory.RENDER);
    }

    public void transform(MatrixStack matrices, Arm arm) {
        if (!isEnabled()) return;
        float side = arm == Arm.RIGHT ? 1 : -1;
        matrices.translate(x.get() * side, y.get(), z.get());
        matrices.scale(scale.get().floatValue(), scale.get().floatValue(), scale.get().floatValue());
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate.get().floatValue() * side));
    }
}
