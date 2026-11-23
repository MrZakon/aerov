package net.aero.visuals.feature.showcase;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.*;
/** All layers retain depth testing. Effects cannot reveal objects behind walls. */
public final class SceneGeometry {
    private SceneGeometry(){}
    public static final RenderPipeline COLOR_PIPELINE=RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
        .withLocation(Identifier.of("aerovisuals","pipeline/showcase_color")).withVertexShader("core/position_color").withFragmentShader("core/position_color")
        .withVertexFormat(VertexFormats.POSITION_COLOR,VertexFormat.DrawMode.QUADS).withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withCull(false).withBlend(BlendFunction.TRANSLUCENT).build();
    public static final RenderLayer COLOR=RenderLayer.of("aero_showcase_color",RenderSetup.builder(COLOR_PIPELINE).translucent().expectedBufferSize(65536).build());
    private static final RenderPipeline MATERIAL_PIPELINE=RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
        .withLocation(Identifier.of("aerovisuals","pipeline/showcase_material")).withVertexShader(Identifier.of("aerovisuals","core/showcase_material")).withFragmentShader(Identifier.of("aerovisuals","core/showcase_material"))
        .withSampler("Sampler0").withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,VertexFormat.DrawMode.QUADS)
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withCull(false).withBlend(BlendFunction.TRANSLUCENT).build();
    private static final Map<Identifier,RenderLayer> materials=new LinkedHashMap<>();
    public static RenderLayer material(Identifier texture){var found=materials.get(texture);if(found!=null)return found;if(materials.size()>16)materials.clear();var layer=RenderLayer.of("aero_showcase_material",RenderSetup.builder(MATERIAL_PIPELINE).texture("Sampler0",texture).translucent().expectedBufferSize(65536).build());materials.put(texture,layer);return layer;}
    public static void vertex(MatrixStack.Entry matrix,VertexConsumer v,Vec3d p,int color){v.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).color(color);}
    public static void quad(MatrixStack.Entry m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d c,Vec3d d,int color){vertex(m,v,a,color);vertex(m,v,b,color);vertex(m,v,c,color);vertex(m,v,d,color);}
    public static void box(MatrixStack.Entry m,VertexConsumer v,double x,double y,double z,double w,double h,double d,int color){Vec3d a=new Vec3d(x-w/2,y-h/2,z-d/2),b=new Vec3d(x+w/2,y-h/2,z-d/2),c=new Vec3d(x+w/2,y+h/2,z-d/2),e=new Vec3d(x-w/2,y+h/2,z-d/2),f=a.add(0,0,d),g=b.add(0,0,d),i=c.add(0,0,d),j=e.add(0,0,d);quad(m,v,a,b,c,e,color);quad(m,v,g,f,j,i,color);quad(m,v,f,a,e,j,color);quad(m,v,b,g,i,c,color);quad(m,v,e,c,i,j,color);quad(m,v,f,g,b,a,color);}
    public static void beam(MatrixStack.Entry m,VertexConsumer v,Vec3d from,Vec3d to,double width,int color){Vec3d direction=to.subtract(from);if(direction.lengthSquared()<1e-8)return;Vec3d cross=direction.crossProduct(Math.abs(direction.normalize().y)>.9?new Vec3d(1,0,0):new Vec3d(0,1,0)).normalize().multiply(width),other=direction.crossProduct(cross).normalize().multiply(width);Vec3d[] a={from.add(cross),from.add(other),from.subtract(cross),from.subtract(other)},b={to.add(cross),to.add(other),to.subtract(cross),to.subtract(other)};for(int i=0;i<4;i++)quad(m,v,a[i],a[(i+1)%4],b[(i+1)%4],b[i],color);}
    public static void ring(MatrixStack.Entry m,VertexConsumer v,Vec3d center,double radius,double width,int color){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;quad(m,v,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),center.add(Math.cos(b)*radius,0,Math.sin(b)*radius),center.add(Math.cos(b)*(radius+width),0,Math.sin(b)*(radius+width)),center.add(Math.cos(a)*(radius+width),0,Math.sin(a)*(radius+width)),color);}}
}
