package client.render;

import client.util.ItemKey;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class FramebufferCache extends LinkedHashMap<ItemKey, TextureTarget> {
   FramebufferCache(int count, float value, boolean flag) {
      super(count, value, flag);
   }

   @Override
   protected boolean removeEldestEntry(Entry entry) {
      if (this.size() > 64) {
         try {
            ((TextureTarget)entry.getValue()).destroyBuffers();
         } catch (Throwable throwable) {
         }

         return true;
      } else {
         return false;
      }
   }
}
