package net.aero.visuals.feature.premium;

import com.mojang.blaze3d.pipeline.*;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Camera-facing, depth-tested stars with shader-softened rays and feathered constellation links. */
final class StarSprites {
    private StarSprites(){}
    private static final RenderPipeline PIPELINE=RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/constellation_stars"))
        .withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/constellation_stars"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/constellation_stars"))
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS)
        .withDepthStencilState(new DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL,false)).withCull(false).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    static final RenderType LAYER=RenderType.create("aero_constellation_stars",RenderSetup.builder(PIPELINE).sortOnUpload().bufferSize(32768).createRenderSetup());
    private static void vertex(PoseStack.Pose matrix,VertexConsumer consumer,Vec3 p,float u,float v,int color){consumer.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(color);}
    static void star(PoseStack.Pose matrix,VertexConsumer consumer,Vec3 center,Vec3 right,Vec3 up,int color){
        vertex(matrix,consumer,center.subtract(right).subtract(up),0,0,color);
        vertex(matrix,consumer,center.add(right).subtract(up),1,0,color);
        vertex(matrix,consumer,center.add(right).add(up),1,1,color);
        vertex(matrix,consumer,center.subtract(right).add(up),0,1,color);
    }
    static void link(PoseStack.Pose matrix,VertexConsumer consumer,Vec3 from,Vec3 to,Vec3 eye,double width,int color){
        Vec3 normal=to.subtract(from).cross(eye.subtract(from.add(to).scale(.5)));
        if(normal.lengthSqr()<1e-10)return;normal=normal.normalize().scale(width);
        vertex(matrix,consumer,from.subtract(normal),-1,0,color);
        vertex(matrix,consumer,to.subtract(normal),-1,0,color);
        vertex(matrix,consumer,to.add(normal),-1,1,color);
        vertex(matrix,consumer,from.add(normal),-1,1,color);
    }
}
