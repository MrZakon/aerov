package net.aero.visuals.cosmetic;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Continuous hinge animation. The attachment and common body/root bones stay fixed. */
public final class WingAnimator {
    private static final long CLOCK_ORIGIN=System.nanoTime();
    private static final Map<BedrockGeometryLoader.Geometry,Rig> RIGS=new IdentityHashMap<>();
    private static final ArrayDeque<BedrockGeometryLoader.Geometry> ORDER=new ArrayDeque<>();
    private static int cachedFloats;
    private static final class Part {
        final BedrockGeometryLoader.Bone bone;
        final float[] vertices;
        final Matrix4f pose=new Matrix4f();
        final int parent,side,depth;
        final double animationStrength;
        Part(BedrockGeometryLoader.Bone bone,float[] vertices,int parent,int side,int depth){
            this.bone=bone;this.vertices=vertices;this.parent=parent;this.side=side;this.depth=depth;
            String name = bone.name().toLowerCase(Locale.ROOT);
            boolean segment = depth == 0 || name.contains("wing") || name.contains("tip") || name.startsWith("segment_");
            animationStrength = side == 0 || !segment || depth > 4 ? 0 : depth == 0 ? 1 : .18 / (depth * .6 + 1);
        }
    }
    private record Rig(List<Part> parts,int floats){}
    private WingAnimator(){}
    public static double time(){return (System.nanoTime()-CLOCK_ORIGIN)*1e-9;}
    static synchronized void clearCache(){RIGS.clear();ORDER.clear();cachedFloats=0;}
    private static synchronized Rig rig(BedrockGeometryLoader.Geometry geometry){
        Rig known=RIGS.get(geometry);if(known!=null)return known;
        var bones=new LinkedHashMap<String,BedrockGeometryLoader.Bone>();
        geometry.bones().forEach(b->bones.put(b.name(),b));
        List<Part> parts=new ArrayList<>();Map<String,Integer> indices=new HashMap<>();
        boolean namedHinges=bones.values().stream().anyMatch(b->wingSide(b.name())!=0);
        // This archive model stores both halves in each bone. Give each half its own hinge.
        boolean splitHalves=!namedHinges&&bones.values().stream().anyMatch(b->b.name().equals("easter_chick_wings"));
        boolean pairedRoots=!namedHinges&&bones.values().stream().filter(b->b.parent().isBlank()).count()==2;
        for(var bone:bones.values())add(geometry,bone,bones,indices,parts,new HashSet<>(),pairedRoots,splitHalves);
        int count=parts.stream().mapToInt(p->p.vertices.length).sum();Rig result=new Rig(List.copyOf(parts),count);
        if(count<=2_000_000){
            while(!ORDER.isEmpty()&&(ORDER.size()>=32||cachedFloats+count>2_000_000))cachedFloats-=RIGS.remove(ORDER.removeFirst()).floats;
            RIGS.put(geometry,result);ORDER.addLast(geometry);cachedFloats+=count;
        }
        return result;
    }
    private static int add(BedrockGeometryLoader.Geometry geometry,BedrockGeometryLoader.Bone b,
                           Map<String,BedrockGeometryLoader.Bone> bones,Map<String,Integer> indices,
                           List<Part> parts,Set<String> visiting,boolean pairedRoots,boolean splitHalves){
        Integer existing=indices.get(b.name());if(existing!=null)return existing;
        if(visiting.size()>=64||!visiting.add(b.name()))throw new IllegalArgumentException("Cyclic wing hierarchy");
        var parentBone=bones.get(b.parent());
        int parent=parentBone==null?-1:add(geometry,parentBone,bones,indices,parts,visiting,pairedRoots,splitHalves);
        int side=wingSide(b.name()),depth=0;
        if(parent>=0&&parts.get(parent).side!=0){side=parts.get(parent).side;depth=parts.get(parent).depth+1;}
        else if(side==0&&pairedRoots&&parent<0){
            // RIght_1/RIght_2 are oppositely oriented despite their identical name prefix.
            double center=b.cubes().stream().mapToDouble(c->c.x()+c.sx()*.5).average().orElse(b.px());
            side=center>=0?1:-1;
        }
        int index=parts.size();indices.put(b.name(),index);
        parts.add(new Part(b,splitHalves?new float[0]:BedrockMesh.localMesh(geometry,b),parent,side,depth));
        if(splitHalves&&!b.cubes().isEmpty())for(int sign:new int[]{-1,1}){
            var cubes=b.cubes().stream().filter(c->(c.x()+c.sx()*.5>=0?1:-1)==sign).toList();
            if(cubes.isEmpty())continue;
            var half=new BedrockGeometryLoader.Bone(b.name()+"/"+sign,b.name(),sign,b.py(),b.pz(),0,0,0,List.of());
            // Original cube rotations/pivots stay intact. Only the new half hinge moves.
            var local=new BedrockGeometryLoader.Bone(b.name(),b.parent(),b.px(),b.py(),b.pz(),b.rx(),b.ry(),b.rz(),cubes);
            parts.add(new Part(half,BedrockMesh.localMesh(geometry,local),index,sign,0));
        }
        visiting.remove(b.name());return index;
    }
    private static int wingSide(String name){
        String n=name.toLowerCase(Locale.ROOT);if(!n.contains("wing"))return 0;
        return n.contains("left")?1:n.contains("right")?-1:0;
    }
    public static void draw(BedrockGeometryLoader.Geometry geometry,VertexConsumer out,MatrixStack.Entry matrix,double seconds){
        Rig rig=rig(geometry);Vector3f point=new Vector3f(),normal=new Vector3f();
        // Sinusoids share one continuous clock, so opening the preview never resets the pose.
        double phase=seconds*Math.PI*1.15;
        for(Part part:rig.parts){
            var b=part.bone;float yaw=0,roll=0;
            if (part.animationStrength != 0) {
                double segmentPhase = phase - part.depth * .38;
                yaw = (float) (part.side * Math.sin(segmentPhase) * 22 * part.animationStrength);
                roll = (float) (part.side * Math.sin(phase - .55 - part.depth * .38) * 4 * part.animationStrength);
            }
            if(part.parent<0)part.pose.identity();else part.pose.set(rig.parts.get(part.parent).pose);
            part.pose.translate(b.px(),-b.py(),b.pz())
                    .rotateZ((float)Math.toRadians(b.rz()+roll)).rotateY((float)Math.toRadians(b.ry()+yaw))
                    .rotateX((float)Math.toRadians(b.rx())).translate(-b.px(),b.py(),-b.pz());
            float[] vertices=part.vertices;
            for(int i=0;i<vertices.length;i+=8){
                part.pose.transformPosition(point.set(vertices[i],vertices[i+1],vertices[i+2]));
                part.pose.transformDirection(normal.set(vertices[i+5],vertices[i+6],vertices[i+7]));
                BedrockCosmeticRenderer.vertex(out,matrix,point.x,point.y,point.z,0xFFFFFFFF,vertices[i+3],vertices[i+4],normal.x,normal.y,normal.z);
            }
        }
    }
}
