package net.aero.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.aero.visuals.render.font.NameTagTextCache;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.LabelCommandRenderer;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LabelCommandRenderer.class)
public abstract class NameTagTextCacheMixin {
    @Unique private final NameTagTextCache aero$labels = new NameTagTextCache();

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V"))
    private void aero$draw(TextRenderer font, Text text, float x, float y, int color, boolean shadow,
                           Matrix4f pose, VertexConsumerProvider buffers, TextRenderer.TextLayerType layer,
                           int background, int light, Operation<Void> original) {
        var prepared = aero$labels.prepare(font, text, x, y, color, shadow, background);
        if (prepared == null) {
            original.call(font, text, x, y, color, shadow, pose, buffers, layer, background, light);
        } else {
            prepared.draw(TextRenderer.GlyphDrawer.drawing(buffers, pose, layer, light));
        }
    }
}
