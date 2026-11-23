package net.aero.visuals.render;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.Minecraft;
public final class GpuFormats {
    public static final VertexFormat EMPTY=VertexFormat.builder(0).build();
    private GpuFormats(){}
    public static GpuFormat sceneFormat(){return Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture().getFormat();}
}
