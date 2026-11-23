package client.concurrent;

import client.audio.SoundEngine;
import client.util.HashUtil;
import client.util.InputCallbacks;
import client.util.KeyboardState;
import client.util.ModuleDispatcher;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemClient {
   public static volatile SystemClient INSTANCE;
   private ModuleRegistry moduleRegistry;
   private KeyboardState keyboardState;
   private ModuleDispatcher moduleDispatcher;
   private InputCallbacks inputCallbacks;
   private final ScheduledExecutorService scheduledExecutorService = new GuardedScheduler(Executors.newSingleThreadScheduledExecutor());
   private ConfigManager configManager;
   private HashUtil hashUtil;
   private final boolean flag = false;

   public SystemClient() {
      if (INSTANCE == null) {
         this.update3();
      }
   }

   private void update() {
      INSTANCE = this;
      try { SoundEngine.getInstance().update5(); } catch (Throwable t) { System.err.println("[SystemDLC] SoundEngine init failed: " + t); }
      try { SoundEngine.getInstance().setFlag3(true); } catch (Throwable t) {}
      try { this.moduleRegistry = new ModuleRegistry(); } catch (Throwable t) { System.err.println("[SystemDLC] ModuleRegistry init failed: " + t); }
      try { this.configManager = new ConfigManager(this.moduleRegistry); } catch (Throwable t) { System.err.println("[SystemDLC] ConfigManager init failed: " + t); }
      try { this.hashUtil = new HashUtil(this.configManager, this.scheduledExecutorService); } catch (Throwable t) { System.err.println("[SystemDLC] HashUtil init failed: " + t); }
      try { this.moduleDispatcher = new ModuleDispatcher(); } catch (Throwable t) { System.err.println("[SystemDLC] ModuleDispatcher init failed: " + t); }
      try { this.moduleDispatcher.setModuleRegistry2(this.moduleRegistry); } catch (Throwable t) {}
      try { this.keyboardState = KeyboardState.getKeyboardState(); } catch (Throwable t) { System.err.println("[SystemDLC] KeyboardState init failed: " + t); }
      try { this.inputCallbacks = new InputCallbacks(); } catch (Throwable t) { System.err.println("[SystemDLC] InputCallbacks init failed: " + t); }
      try { new ClientTicker(this.scheduledExecutorService); } catch (Throwable t) { System.err.println("[SystemDLC] ClientTicker init failed: " + t); }
      try { this.keyboardState.update3(); } catch (Throwable t) { System.err.println("[SystemDLC] KeyboardState update3 failed: " + t); }
      try { this.moduleRegistry.update7(); } catch (Throwable t) { System.err.println("[SystemDLC] ModuleRegistry.update7 failed: " + t); }
      try { SoundEngine.getInstance().setFlag3(false); } catch (Throwable t) {}
   }

   public ScheduledExecutorService getScheduledExecutorService() {
      return this.scheduledExecutorService;
   }

   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   public HashUtil getHashUtil() {
      return this.hashUtil;
   }

   public Object getAssetLoader() {
      return null;
   }

   public boolean check() {
      return false;
   }

   public InputCallbacks getInputCallbacks() {
      return this.inputCallbacks;
   }

   public static SystemClient getInstance() {
      if (INSTANCE == null) {
         synchronized (SystemClient.class) {
            if (INSTANCE == null) {
               new SystemClient();
            }
         }
      }
      return INSTANCE;
   }

   public ModuleRegistry getModuleRegistry() {
      if (this.moduleRegistry == null) {
         this.moduleRegistry = new ModuleRegistry();
      }
      return this.moduleRegistry;
   }

   public KeyboardState getKeyboardState() {
      return this.keyboardState;
   }

   public ModuleDispatcher getModuleDispatcher() {
      return this.moduleDispatcher;
   }

   private void update3() {
      try {
         this.update();
      } catch (Throwable throwable) {
         System.err.println("[SystemDLC] FATAL system initialization error: " + throwable);
         throwable.printStackTrace();
      }
   }
}
