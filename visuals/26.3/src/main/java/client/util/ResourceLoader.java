package client.util;

import client.module.Feature;
import java.io.ByteArrayInputStream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public class ResourceLoader {
   private final Identifier identifier;
   private final byte[] byteArray;

   public ResourceLoader(String text, byte[] valueArray) {
      this.identifier = Identifier.withDefaultNamespace(text);
      this.byteArray = valueArray;
   }

   public Identifier getIdentifier() {
      return this.identifier;
   }

   public static ResourceLoader getResourceLoaderByByteArrayString(byte[] valueArray, String text) {
      return new ResourceLoader(text, valueArray);
   }

   public Resource getResource() {
      return new Resource(Feature.mc.getVanillaPackResources().fullResources(), () -> new ByteArrayInputStream(this.byteArray));
   }
}
