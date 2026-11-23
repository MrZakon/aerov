package net.aero.visuals.feature.showcase;
import com.mojang.blaze3d.pipeline.*;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** All layers retain depth testing. Effects cannot reveal objects behind walls. */
public final class SceneGeometry {
    private SceneGeometry(){}
    public static final RenderPipeline COLOR_PIPELINE=RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/showcase_color")).withVertexShader("core/position_color").withFragmentShader("core/position_color")
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS).withDepthStencilState(new DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL,false)).withCull(false).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    public static final RenderType COLOR=RenderType.create("aero_showcase_color",RenderSetup.builder(COLOR_PIPELINE).sortOnUpload().bufferSize(65536).createRenderSetup());
    private static final RenderPipeline MATERIAL_PIPELINE=RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("aerovisuals","pipeline/showcase_material")).withVertexShader(Identifier.fromNamespaceAndPath("aerovisuals","core/showcase_material")).withFragmentShader(Identifier.fromNamespaceAndPath("aerovisuals","core/showcase_material"))
        .withSampler("Sampler0").withVertexFormat(DefaultVertexFormat.ENTITY,VertexFormat.Mode.QUADS)
        .withDepthStencilState(new DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL,false)).withCull(false).withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
    private static final Map<Identifier,RenderType> materials=new LinkedHashMap<>();
    public static RenderType material(Identifier texture){var found=materials.get(texture);if(found!=null)return found;if(materials.size()>16)materials.clear();var layer=RenderType.create("aero_showcase_material",RenderSetup.builder(MATERIAL_PIPELINE).withTexture("Sampler0",texture).sortOnUpload().bufferSize(65536).createRenderSetup());materials.put(texture,layer);return layer;}
    public static void vertex(PoseStack.Pose matrix,VertexConsumer v,Vec3 p,int color){v.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setColor(color);}
    public static void quad(PoseStack.Pose m,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color){vertex(m,v,a,color);vertex(m,v,b,color);vertex(m,v,c,color);vertex(m,v,d,color);}
    public static void box(PoseStack.Pose m,VertexConsumer v,double x,double y,double z,double w,double h,double d,int color){Vec3 a=new Vec3(x-w/2,y-h/2,z-d/2),b=new Vec3(x+w/2,y-h/2,z-d/2),c=new Vec3(x+w/2,y+h/2,z-d/2),e=new Vec3(x-w/2,y+h/2,z-d/2),f=a.add(0,0,d),g=b.add(0,0,d),i=c.add(0,0,d),j=e.add(0,0,d);quad(m,v,a,b,c,e,color);quad(m,v,g,f,j,i,color);quad(m,v,f,a,e,j,color);quad(m,v,b,g,i,c,color);quad(m,v,e,c,i,j,color);quad(m,v,f,g,b,a,color);}
    public static void beam(PoseStack.Pose m,VertexConsumer v,Vec3 from,Vec3 to,double width,int color){Vec3 direction=to.subtract(from);if(direction.lengthSqr()<1e-8)return;Vec3 cross=direction.cross(Math.abs(direction.normalize().y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(width),other=direction.cross(cross).normalize().scale(width);Vec3[] a={from.add(cross),from.add(other),from.subtract(cross),from.subtract(other)},b={to.add(cross),to.add(other),to.subtract(cross),to.subtract(other)};for(int i=0;i<4;i++)quad(m,v,a[i],a[(i+1)%4],b[(i+1)%4],b[i],color);}
    public static void ring(PoseStack.Pose m,VertexConsumer v,Vec3 center,double radius,double width,int color){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;quad(m,v,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),center.add(Math.cos(b)*radius,0,Math.sin(b)*radius),center.add(Math.cos(b)*(radius+width),0,Math.sin(b)*(radius+width)),center.add(Math.cos(a)*(radius+width),0,Math.sin(a)*(radius+width)),color);}}
}
