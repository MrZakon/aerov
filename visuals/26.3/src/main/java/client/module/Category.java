package client.module;

public enum Category {
   INVENTORY("Inventory", CategoryType.RACK),
   HUD("HUD", CategoryType.LOGO),
   RENDER("Рендер", CategoryType.RENDER),
   PVP("PvP", CategoryType.COMBAT),
   MISC("Misc", CategoryType.SETTINGS),
   COSMETICS("Косметика", CategoryType.LOGO),
   FRIENDS("Друзья", CategoryType.FRIENDS),
   CONFIGS("Конфиги", CategoryType.SAVE);

   private final String text;
   private final CategoryType icon;

   Category(String text, CategoryType icon) {
      this.text = text;
      this.icon = icon;
   }

   public String getText() { return net.aero.visuals.localization.LocalText.get(text); }
   public String getString() { return text; }
   public Category getCategory() { return null; }
   public boolean check() { return false; }
   public CategoryType getCategoryType() { return icon; }

   public boolean isEmbeddedPanel() {
      return this == COSMETICS || this == FRIENDS || this == CONFIGS;
   }

   public static Category getCategoryByString(String text) {
      if (text != null) {
         try { return valueOf(text); } catch (IllegalArgumentException ignored) {}
      }
      return HUD;
   }
}
