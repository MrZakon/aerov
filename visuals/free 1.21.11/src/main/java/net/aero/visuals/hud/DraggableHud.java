package net.aero.visuals.hud;

import net.minecraft.client.gui.DrawContext;

public interface DraggableHud {
    String id();
    boolean isEnabled();
    boolean isVisible();
    default boolean isDraggable() { return true; }
    void draw(DrawContext context);
    default boolean onHudClick(double screenX, double screenY) { return false; }
}
