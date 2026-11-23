package client.util;

public final class ThemeState {
   private static boolean blur = true;
   private ThemeState() {}
   public static boolean check() { return blur; }
   public static void update() { blur = !blur; }
   public static void setBoolean(boolean value) { blur = value; }
}
