package client.util;

public final class InterfaceSizing {
   private InterfaceSizing() {}
   public static float getGuiScale() {
      var runtime = net.aero.visuals.ClientRuntime.getNullable();
      return runtime == null ? 1 : runtime.settings().clickGuiScale().get().floatValue();
   }
   public static float getTextScale() {
      var runtime = net.aero.visuals.ClientRuntime.getNullable();
      return runtime == null ? 1 : runtime.settings().clickGuiTextScale().get().floatValue();
   }
}
