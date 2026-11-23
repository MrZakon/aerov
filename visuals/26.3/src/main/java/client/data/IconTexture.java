package client.data;

import net.minecraft.resources.Identifier;

public record IconTexture(Identifier texture, float size) {
   public float getSize() {
      return this.size;
   }

   public Identifier getTexture() {
      return this.texture;
   }
}
