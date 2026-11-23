package client.util;

import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;

public final class SlotSyncAccess {
   private static final Method method = getMethod();

   private SlotSyncAccess() {
   }

   public static void onClientPlayerInteractionManager(MultiPlayerGameMode clientPlayerInteractionManager) {
      if (method != null && clientPlayerInteractionManager != null) {
         try {
            method.invoke(clientPlayerInteractionManager);
         } catch (Throwable throwable) {
         }
      }
   }

   private static Method getMethod() {
      for (String s : new String[]{"ensureHasSentCarriedItem"}) {
         try {
            Method methodx = MultiPlayerGameMode.class.getDeclaredMethod(s);
            methodx.setAccessible(true);
            return methodx;
         } catch (Throwable throwable) {
         }
      }

      return null;
   }
}
