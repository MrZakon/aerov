package net.aero.visuals.hud;

public record HudPosition(HudAnchor anchor, double x, double y, double scale) {
    public HudPosition {
        anchor = anchor == null ? HudAnchor.TOP_LEFT : anchor;
        x = Double.isFinite(x) ? Math.clamp(x, -8192.0, 8192.0) : 8.0;
        y = Double.isFinite(y) ? Math.clamp(y, -8192.0, 8192.0) : 8.0;
        scale = Double.isFinite(scale) ? Math.clamp(scale, 0.25, 4.0) : 1.0;
    }

    public double screenX(double width, int screenWidth) {
        double free = screenWidth - width;
        return switch (anchor) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> free / 2 + x;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> free - x;
            default -> x;
        };
    }

    public double screenY(double height, int screenHeight) {
        double free = screenHeight - height;
        return switch (anchor) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> free / 2 + y;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> free - y;
            default -> y;
        };
    }

    public static HudPosition fromScreen(HudAnchor anchor, double x, double y,
                                         double width, double height, int screenWidth, int screenHeight, double scale) {
        double freeX = screenWidth - width;
        double freeY = screenHeight - height;
        double offsetX = switch (anchor) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> x - freeX / 2;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> freeX - x;
            default -> x;
        };
        double offsetY = switch (anchor) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> y - freeY / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> freeY - y;
            default -> y;
        };
        return new HudPosition(anchor, offsetX, offsetY, scale);
    }
}
