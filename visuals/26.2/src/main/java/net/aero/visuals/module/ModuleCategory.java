package net.aero.visuals.module;

public enum ModuleCategory {
    INVENTORY("Inventory"),
    HUD("HUD"),
    RENDER("Рендер"),
    PVP("PvP"),
    MISC("Misc"),
    COSMETICS("Косметика");

    private final String displayName;
    ModuleCategory(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
