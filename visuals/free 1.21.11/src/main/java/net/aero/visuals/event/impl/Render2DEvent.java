package net.aero.visuals.event.impl;

import net.aero.visuals.event.Event;
import net.minecraft.client.gui.DrawContext;

/** Переиспользуемое событие отрисовки HUD без аллокации на каждый кадр. */
public final class Render2DEvent implements Event {
    private DrawContext context;
    private float tickDelta;

    public Render2DEvent reset(DrawContext context, float tickDelta) {
        this.context = context;
        this.tickDelta = tickDelta;
        return this;
    }

    public DrawContext context() {
        return context;
    }

    public float tickDelta() {
        return tickDelta;
    }
}
