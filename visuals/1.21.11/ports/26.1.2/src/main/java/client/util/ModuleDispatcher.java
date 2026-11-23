package client.util;

import client.concurrent.ModuleRegistry;
import client.concurrent.SystemClient;
import client.module.Module;
import client.render.HudRenderContext;
import client.render.ItemIconCache;
import client.render.LevelRenderContext;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModuleDispatcher {
   private static final ModuleRegistry fallbackRegistry = new ModuleRegistry();
   private ModuleRegistry moduleRegistry;
   private final HudRenderContext hudRenderContext = new HudRenderContext();

   public void setModuleRegistry2(ModuleRegistry registry) {
      this.moduleRegistry = registry;
   }

   public ModuleRegistry getModuleRegistry() {
      if (this.moduleRegistry != null) {
         return this.moduleRegistry;
      }
      SystemClient sc = SystemClient.getInstance();
      if (sc != null && sc.getModuleRegistry() != null) {
         this.moduleRegistry = sc.getModuleRegistry();
         return this.moduleRegistry;
      }
      return fallbackRegistry;
   }

   public void update2() {
      try {
         SystemClient systemclient = SystemClient.getInstance();
         if (systemclient != null) {
            KeyboardState keyboardstate = systemclient.getKeyboardState();
            if (keyboardstate != null) {
               keyboardstate.update5();
            }
         }
      } catch (Exception ignored) {
      }

      List<Module> list = this.getModuleRegistry().getList22();
      for (int i = 0, j = list.size(); i < j; i++) {
         try {
            list.get(i).onTick();
         } catch (Exception ignored) {
         }
      }
   }

   public void update6() {
      List<Module> list = this.getModuleRegistry().getList22();
      for (int i = 0, j = list.size(); i < j; i++) {
         try {
            list.get(i).update8();
         } catch (Exception ignored) {
         }
      }
   }

   public void render(GuiGraphicsExtractor drawContext, DeltaTracker renderTickCounter) {
      try {
         ItemIconCache.render(drawContext);
      } catch (Exception ignored) {
      }

      HudRenderContext hudrendercontext = this.hudRenderContext.getHudRenderContextByDrawContextRenderTickCounter(drawContext, renderTickCounter);
      List<Module> list = this.getModuleRegistry().getList22();
      for (int i = 0, j = list.size(); i < j; i++) {
         try {
            list.get(i).onHudRenderContext(hudrendercontext);
         } catch (Exception ignored) {
         }
      }
   }

   public void render(LevelRenderContext worldRenderContext) {
      if (worldRenderContext != null) {
         List<Module> list = this.getModuleRegistry().getList22();
         for (int i = 0, j = list.size(); i < j; i++) {
            try {
               list.get(i).render5(worldRenderContext);
            } catch (Exception ignored) {
            }
         }
      }
   }

   public void render2(LevelRenderContext worldRenderContext) {
      if (worldRenderContext != null) {
         List<Module> list = this.getModuleRegistry().getList22();
         for (int i = 0, j = list.size(); i < j; i++) {
            try {
               list.get(i).render2(worldRenderContext);
            } catch (Exception ignored) {
            }
         }
      }
   }

   public void render3(LevelRenderContext worldRenderContext) {
      if (worldRenderContext != null) {
         List<Module> list = this.getModuleRegistry().getList22();
         for (int i = 0, j = list.size(); i < j; i++) {
            try {
               list.get(i).render6(worldRenderContext);
            } catch (Exception ignored) {
            }
         }
      }
   }

   public void render4(LevelRenderContext worldRenderContext) {
      if (worldRenderContext != null) {
         List<Module> list = this.getModuleRegistry().getList22();
         for (int i = 0, j = list.size(); i < j; i++) {
            try {
               list.get(i).render7(worldRenderContext);
            } catch (Exception ignored) {
            }
         }
      }
   }

   public void render5(LevelRenderContext worldRenderContext) {
      if (worldRenderContext != null) {
         List<Module> list = this.getModuleRegistry().getList22();
         for (int i = 0, j = list.size(); i < j; i++) {
            try {
               list.get(i).render(worldRenderContext);
            } catch (Exception ignored) {
            }
         }
      }
   }

   public void render6(LevelRenderContext worldRenderContext) {
      if (worldRenderContext != null) {
         List<Module> list = this.getModuleRegistry().getList22();
         for (int i = 0, j = list.size(); i < j; i++) {
            try {
               list.get(i).render3(worldRenderContext);
            } catch (Exception ignored) {
            }
         }
      }
   }
}
