package net.aero.visuals.cosmetic;

/** Attachment points shared by all bundled cosmetic categories. */
public final class CosmeticAttachment {
    public enum Point { HEAD, BACK, SHOULDER, FOLLOW, BLOCK }
    private CosmeticAttachment() {}
    public static Point point(String type) {
        if (type == null) return Point.FOLLOW;
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "hat" -> Point.HEAD;
            case "cape", "wings", "bodywear" -> Point.BACK;
            case "pet" -> Point.FOLLOW;
            case "graffiti" -> Point.BLOCK;
            default -> Point.FOLLOW;
        };
    }
}
