package net.aero.visuals.feature.premium;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Camera-facing, depth-tested stars with shader-softened rays and feathered constellation links. */
final class StarSprites {
    private StarSprites(){}
    private static final RenderPipeline PIPELINE=RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
        .withLocation(Identifier.of("aerovisuals","pipeline/constellation_stars"))
        .withVertexShader(Identifier.of("aerovisuals","core/constellation_stars"))
        .withFragmentShader(Identifier.of("aerovisuals","core/constellation_stars"))
        .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR,VertexFormat.DrawMode.QUADS)
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withCull(false).withBlend(BlendFunction.TRANSLUCENT).build();
    static final RenderLayer LAYER=RenderLayer.of("aero_constellation_stars",RenderSetup.builder(PIPELINE).translucent().expectedBufferSize(32768).build());
    private static void vertex(MatrixStack.Entry matrix,VertexConsumer consumer,Vec3d p,float u,float v,int color){consumer.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).texture(u,v).color(color);}
    static void star(MatrixStack.Entry matrix,VertexConsumer consumer,Vec3d center,Vec3d right,Vec3d up,int color){
        vertex(matrix,consumer,center.subtract(right).subtract(up),0,0,color);
        vertex(matrix,consumer,center.add(right).subtract(up),1,0,color);
        vertex(matrix,consumer,center.add(right).add(up),1,1,color);
        vertex(matrix,consumer,center.subtract(right).add(up),0,1,color);
    }
    static void link(MatrixStack.Entry matrix,VertexConsumer consumer,Vec3d from,Vec3d to,Vec3d eye,double width,int color){
        Vec3d normal=to.subtract(from).crossProduct(eye.subtract(from.add(to).multiply(.5)));
        if(normal.lengthSquared()<1e-10)return;normal=normal.normalize().multiply(width);
        vertex(matrix,consumer,from.subtract(normal),-1,0,color);
        vertex(matrix,consumer,to.subtract(normal),-1,0,color);
        vertex(matrix,consumer,to.add(normal),-1,1,color);
        vertex(matrix,consumer,from.add(normal),-1,1,color);
    }
}
