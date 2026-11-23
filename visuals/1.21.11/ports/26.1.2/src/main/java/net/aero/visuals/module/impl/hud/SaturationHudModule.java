package net.aero.visuals.module.impl.hud;
import net.aero.visuals.hud.*;import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
public final class SaturationHudModule extends HudWidgetModule {
    private int labelHunger;
    private int labelSaturationBits;
    private boolean labelShowsHunger;
    private String label;

    private String foodLabel(int hunger, float saturation, boolean includeHunger) {
        int bits = Float.floatToIntBits(saturation);
        if (label == null || labelShowsHunger != includeHunger || labelSaturationBits != bits
                || (includeHunger && labelHunger != hunger)) {
            String value = String.format(java.util.Locale.ROOT, "%.1f", saturation);
            label = includeHunger ? hunger + " / 20  ·  " + value : value;
            labelHunger = hunger;
            labelSaturationBits = bits;
            labelShowsHunger = includeHunger;
        }
        return label;
    }

    public SaturationHudModule(){super("saturation_hud","Saturation","Голод и насыщение своего персонажа по данным Minecraft.",new HudPosition(HudAnchor.BOTTOM_RIGHT,10,75,1));}
    @Override protected void refresh(Minecraft c,float delta){width=150;height=57;content=!c.player.isSpectator()&&!c.player.isCreative();}
    @Override
    protected void renderHud(GuiGraphicsExtractor draw, Minecraft client) {
        title(draw, "Насыщение");
        var food = client.player.getFoodData();
        int hunger = food.getFoodLevel();
        float saturation = food.getSaturationLevel();
        text(draw, foodLabel(hunger, saturation, true), 9, 29, 9, 0xFFF1F4FF);
        UiRenderer.roundedRect(draw, 9, 44, width - 18, 4, 2, 0x30FFFFFF);
        UiRenderer.roundedRect(draw, 9, 44, (width - 18) * Math.clamp(hunger / 20.0, 0, 1), 4, 2, 0xFFDFAC70);
        UiRenderer.roundedRect(draw, 9, 51, (width - 18) * Math.clamp(saturation / 20.0, 0, 1), 2, 1, 0xFF83D8A7);
    }
}
