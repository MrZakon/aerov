package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.Render2DEvent;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CrosshairModule extends Module {
    public enum Shape { CROSS, DOT, CIRCLE }
    private final EnumSetting<Shape> shape = add(new EnumSetting<>("shape", "Форма", "Крест, точка или кольцо.", Shape.CROSS, Shape.class));
    private final DoubleSetting size = add(new DoubleSetting("size", "Размер", "Длина лучей или радиус прицела.", 5, 2, 16, 1));
    private final DoubleSetting gap = add(new DoubleSetting("gap", "Отступ", "Расстояние от центра до лучей.", 3, 0, 10, 1));
    private final DoubleSetting thickness = add(new DoubleSetting("thickness", "Толщина", "Толщина линий прицела.", 2, 1, 5, 1));
    private final ColorSetting color = add(new ColorSetting("color", "Цвет", "Цвет прицела.", ColorValue.solid(0xFFFFFFFF)));

    public CrosshairModule() {
        super("crosshair", "Crosshair", "Настраивает форму, размер, толщину и цвет прицела.", ModuleCategory.PVP);
    }

    @Subscribe
    private void render(Render2DEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null || client.options.hideGui || !client.options.getCameraType().isFirstPerson()) return;
        GuiGraphicsExtractor context = event.context();
        int cx = context.guiWidth() / 2;
        int cy = context.guiHeight() / 2;
        int argb = color.get().eval();
        int s = size.get().intValue();
        int g = gap.get().intValue();
        int t = thickness.get().intValue();
        switch (shape.get()) {
            case DOT -> context.fill(cx - t, cy - t, cx + t + 1, cy + t + 1, argb);
            case CROSS -> {
                context.fill(cx - t / 2, cy - g - s, cx + (t + 1) / 2, cy - g, argb);
                context.fill(cx - t / 2, cy + g + 1, cx + (t + 1) / 2, cy + g + s + 1, argb);
                context.fill(cx - g - s, cy - t / 2, cx - g, cy + (t + 1) / 2, argb);
                context.fill(cx + g + 1, cy - t / 2, cx + g + s + 1, cy + (t + 1) / 2, argb);
            }
            case CIRCLE -> {
                int r = s + g;
                for (int i = 0; i < 32; i++) {
                    double angle = i * Math.PI / 16;
                    int x = cx + (int) Math.round(Math.cos(angle) * r);
                    int y = cy + (int) Math.round(Math.sin(angle) * r);
                    context.fill(x, y, x + t, y + t, argb);
                }
            }
        }
    }
}
