package client.util;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public final class DisplayInfo {
   private static final int value = 60;

   private DisplayInfo() {
   }

   private static boolean isFlag() {
      try {
         Minecraft minecraftclient = Minecraft.getInstance();
         if (minecraftclient == null) {
            return false;
         } else {
            Window window = minecraftclient.getWindow();
            return window != null && window.handle() != 0L;
         }
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static int getInt() {
      if (!isFlag()) {
         return 60;
      } else {
         try {
            long i = GLFW.glfwGetPrimaryMonitor();
            if (i == 0L) {
               return 60;
            } else {
               GLFWVidMode glfwvidmode = GLFW.glfwGetVideoMode(i);
               return glfwvidmode != null && glfwvidmode.refreshRate() > 0 ? glfwvidmode.refreshRate() : 60;
            }
         } catch (Throwable throwable) {
            return 60;
         }
      }
   }
}
