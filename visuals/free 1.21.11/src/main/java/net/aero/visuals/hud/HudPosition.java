package net.aero.visuals.hud;

public record HudPosition(HudAnchor anchor, double x, double y, double scale) {
    public HudPosition {
        anchor = anchor == null ? HudAnchor.TOP_LEFT : anchor;
        x = Double.isFinite(x) ? Math.clamp(x, -8192.0, 8192.0) : 8.0;
        y = Double.isFinite(y) ? Math.clamp(y, -8192.0, 8192.0) : 8.0;
        scale = Double.isFinite(scale) ? Math.clamp(scale, 0.25, 4.0) : 1.0;
    }
}
