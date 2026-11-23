package net.aero.visuals.cosmetic;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import com.google.gson.*;
import java.util.*;

/** Bakes Bedrock axes, cube pivots, parent transforms and UVs once per model. */
public final class BedrockMesh {
    private static final Map<BedrockGeometryLoader.Geometry,float[]> meshes=new IdentityHashMap<>();
    private static final ArrayDeque<BedrockGeometryLoader.Geometry> order=new ArrayDeque<>();
    private static int cachedFloats;
    public record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ){}
    private static synchronized float[] mesh(BedrockGeometryLoader.Geometry geometry){
        var known=meshes.get(geometry);if(known!=null)return known;float[] baked=bake(geometry);
        if(baked.length<=2_000_000){
            while(!order.isEmpty()&&(order.size()>=32||cachedFloats+baked.length>2_000_000))cachedFloats-=meshes.remove(order.removeFirst()).length;
            meshes.put(geometry,baked);order.addLast(geometry);cachedFloats+=baked.length;
        }
        return baked;
    }
    public static synchronized void clearCache(){meshes.clear();order.clear();cachedFloats=0;WingAnimator.clearCache();}
    public static Bounds bounds(BedrockGeometryLoader.Geometry geometry){
        float[] vertices=mesh(geometry);float x=Float.POSITIVE_INFINITY,y=x,z=x,X=-x,Y=-x,Z=-x;
        for(int i=0;i<vertices.length;i+=8){x=Math.min(x,vertices[i]);y=Math.min(y,vertices[i+1]);z=Math.min(z,vertices[i+2]);X=Math.max(X,vertices[i]);Y=Math.max(Y,vertices[i+1]);Z=Math.max(Z,vertices[i+2]);}
        if(!Float.isFinite(y))throw new IllegalArgumentException("Model contains no visible faces");return new Bounds(x,y,z,X,Y,Z);
    }
    public static void draw(BedrockGeometryLoader.Geometry geometry,VertexConsumer out,MatrixStack.Entry matrix){
        float[] vertices=mesh(geometry);
        for(int i=0;i<vertices.length;i+=8)BedrockCosmeticRenderer.vertex(out,matrix,vertices[i],vertices[i+1],vertices[i+2],0xFFFFFFFF,vertices[i+3],vertices[i+4],vertices[i+5],vertices[i+6],vertices[i+7]);
    }
    private static float[] bake(BedrockGeometryLoader.Geometry g){
        return bake(g,g.bones(),true);
    }
    /** Cube pivots and UVs are baked once; bone hinges remain available to the animator. */
    static float[] localMesh(BedrockGeometryLoader.Geometry geometry,BedrockGeometryLoader.Bone bone){
        return bake(geometry,List.of(bone),false);
    }
    private static float[] bake(BedrockGeometryLoader.Geometry g,List<BedrockGeometryLoader.Bone> selected,boolean bindPose){
        ArrayList<float[]> vertices=new ArrayList<>();Map<String,BedrockGeometryLoader.Bone> bones=new HashMap<>();
        for(var b:g.bones())bones.put(b.name(),b);
        String[] names={"north","south","east","west","up","down"};
        for(var b:selected)for(var c:b.cubes()){
            float a=c.inflate(),x=c.x()-a,y=c.y()-a,z=c.z()-a,X=c.x()+c.sx()+a,Y=c.y()+c.sy()+a,Z=c.z()+c.sz()+a;
            float[][][] faces={{{x,Y,z},{X,Y,z},{X,y,z},{x,y,z}},{{X,Y,Z},{x,Y,Z},{x,y,Z},{X,y,Z}},
                    {{x,Y,Z},{x,Y,z},{x,y,z},{x,y,Z}},{{X,Y,z},{X,Y,Z},{X,y,Z},{X,y,z}},
                    {{X,Y,z},{X,Y,Z},{x,Y,Z},{x,Y,z}},{{x,y,z},{x,y,Z},{X,y,Z},{X,y,z}}};
            float d=c.sz(),w=c.sx(),h=c.sy();
            float[][] defaults={{c.u()+d,c.v()+d,w,h},{c.u()+2*d+w,c.v()+d,w,h},{c.u(),c.v()+d,d,h},{c.u()+d+w,c.v()+d,d,h},{c.u()+d,c.v(),w,d},{c.u()+d+w,c.v(),w,d}};
            for(int f=0;f<6;f++){
                int source=c.mirror()?(f==2?3:f==3?2:f):f,uvRotation=0;float[] uv=defaults[source];
                if(!c.faces().isEmpty()){
                    if(!c.faces().has(names[source]))continue;JsonObject face=c.faces().getAsJsonObject(names[source]);
                    if(!face.has("uv"))continue;var origin=face.getAsJsonArray("uv");var size=face.has("uv_size")?face.getAsJsonArray("uv_size"):null;
                    uv=new float[]{origin.get(0).getAsFloat(),origin.get(1).getAsFloat(),size==null?uv[2]:size.get(0).getAsFloat(),size==null?uv[3]:size.get(1).getAsFloat()};
                    if(face.has("uv_rotation"))uvRotation=Math.floorMod(face.get("uv_rotation").getAsInt()/90,4);
                }
                Vector3f[] points=new Vector3f[4];
                for(int i=0;i<4;i++){float[] q=faces[f][i];points[i]=transform(new Vector3f(q[0],-q[1],q[2]),c,bindPose?b:null,bones);}
                Vector3f normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
                if(normal.lengthSquared()<1e-12f)continue;normal.normalize();
                Vector3f center=transform(new Vector3f(c.x()+c.sx()/2,-c.y()-c.sy()/2,c.z()+c.sz()/2),c,bindPose?b:null,bones);
                Vector3f outward=new Vector3f();for(var point:points)outward.add(point);outward.mul(.25f).sub(center);
                boolean reverse=normal.dot(outward)<0;if(reverse)normal.negate();
                for(int index=0;index<4;index++){
                    int i=reverse?3-index:index,uvIndex=(i+uvRotation)%4;
                    float u=f<4?(uvIndex==1||uvIndex==2?1:0):(uvIndex>=2?1:0);
                    float v=f<4?(uvIndex>=2?1:0):(uvIndex==1||uvIndex==2?1:0);if(c.mirror())u=1-u;
                    var p=points[i];vertices.add(new float[]{p.x,p.y,p.z,(uv[0]+u*uv[2])/g.width(),(uv[1]+v*uv[3])/g.height(),normal.x,normal.y,normal.z});
                }
            }
        }
        float[] packed=new float[vertices.size()*8];int offset=0;
        for(float[] vertex:vertices){System.arraycopy(vertex,0,packed,offset,8);offset+=8;}return packed;
    }
    private static Vector3f transform(Vector3f point,BedrockGeometryLoader.Cube cube,BedrockGeometryLoader.Bone bone,Map<String,BedrockGeometryLoader.Bone> bones){
        rotate(point,cube.px(),-cube.py(),cube.pz(),cube.rx(),cube.ry(),cube.rz());
        int depth=0;while(bone!=null&&depth++<64){rotate(point,bone.px(),-bone.py(),bone.pz(),bone.rx(),bone.ry(),bone.rz());bone=bones.get(bone.parent());}
        if(bone!=null)throw new IllegalArgumentException("Cyclic cosmetic bone hierarchy");return point;
    }
    private static void rotate(Vector3f p,float x,float y,float z,float rx,float ry,float rz){
        p.sub(x,y,z).rotateX((float)Math.toRadians(rx)).rotateY((float)Math.toRadians(ry)).rotateZ((float)Math.toRadians(rz)).add(x,y,z);
    }
}
