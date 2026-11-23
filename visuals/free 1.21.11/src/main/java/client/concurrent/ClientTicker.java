package client.concurrent;

import client.audio.SoundEngine;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClientTicker {
   private final SystemClient systemClient = SystemClient.getInstance();
   private final ScheduledExecutorService scheduledExecutorService;
   private boolean flag = false;

   public ClientTicker(ScheduledExecutorService scheduledExecutorService2) {
      this.scheduledExecutorService = scheduledExecutorService2;
      Thread thread = Thread.ofPlatform().name("e").unstarted(this::update);
      Runtime.getRuntime().addShutdownHook(thread);
      net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.update());
      this.update3();
   }

   private synchronized void update() {
      if (!this.flag) {
         this.flag = true;
         try {
            if (this.systemClient.getHashUtil() != null) {
               this.systemClient.getHashUtil().update4();
            }
         } catch (Exception exception1) {
         }

         try {
            this.systemClient.getScheduledExecutorService().shutdown();
            if (!this.systemClient.getScheduledExecutorService().awaitTermination(5L, TimeUnit.SECONDS)) {
               this.systemClient.getScheduledExecutorService().shutdownNow();
            }
         } catch (InterruptedException interruptedexception) {
            this.systemClient.getScheduledExecutorService().shutdownNow();
         }

         SoundEngine.getInstance().update4();
      }
   }

   public void update3() {
      this.scheduledExecutorService.scheduleAtFixedRate(() -> {
         try {
            if (this.systemClient.getHashUtil() != null) {
               this.systemClient.getHashUtil().update4();
            }
         } catch (Exception exception) {
         }
      }, 3L, 3L, TimeUnit.MINUTES);
   }
}
