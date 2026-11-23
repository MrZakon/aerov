package net.aero.visuals.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface DraggableHud {
    String id();
    boolean isEnabled();
    boolean isVisible();
    default boolean isDraggable() { return true; }
    default HudAnchor defaultHudAnchor() { return HudAnchor.TOP_LEFT; }
    void draw(GuiGraphicsExtractor context);
    default boolean onHudClick(double screenX, double screenY) { return false; }
}
