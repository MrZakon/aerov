package net.aero.visuals.hud;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

/** Hit targets from the latest completed pass of the chat HUD editor. */
public final class HudLayout {
    private final SequencedMap<DraggableHud, HudBounds> targets = new LinkedHashMap<>();
    private Object screen;
    private Object player;
    private Object world;
    private int width, height;
    private boolean collecting;

    void begin(Object screen, Object player, Object world, int width, int height) {
        targets.clear();
        this.screen = screen;
        this.player = player;
        this.world = world;
        this.width = width;
        this.height = height;
        collecting = true;
    }

    void finish(boolean complete) {
        collecting = false;
        if (!complete) clear();
    }

    void clear() {
        collecting = false;
        targets.clear();
        screen = null;
        player = null;
        world = null;
    }

    boolean matches(Object screen, Object player, Object world, int width, int height) {
        return !collecting && this.screen == screen && this.player == player && this.world == world
                && this.width == width && this.height == height;
    }

    public void put(DraggableHud widget, HudBounds bounds) {
        if (collecting) targets.putLast(widget, bounds);
    }

    DraggableHud hit(double x, double y) {
        for (var entry : targets.reversed().entrySet()) {
            if (entry.getValue().contains(x, y) && entry.getKey().isVisible()) return entry.getKey();
        }
        return null;
    }

    HudBounds bounds(DraggableHud widget) {
        return targets.get(widget);
    }
}
