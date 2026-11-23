package net.aero.visuals.render;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
/** Uses the native GUI texture pipeline, retaining the supplied PNG's transparency. */
public final class NursultanMarker {
    private static final Identifier TEXTURE=Identifier.of("aerovisuals","textures/target/nursultan.png");
    public static void draw(DrawContext context,float x,float y,float size,double time,int color,float reveal){
        double angle=Math.clamp(Math.sin(((time/150+1)/2)*30),0,30);
        double pulse=Math.clamp(Math.sin((time/500+1)/2),.8,1);
        double extraRotation=(Math.sin(time/1000)+1)*.5*360;
        float rotation=(float)Math.toRadians((time/5)%360+45-(angle-15)+extraRotation);
        float scale=(float)(pulse*(pulse+1)*.5)*reveal;
        var matrices=context.getMatrices();matrices.pushMatrix();try{matrices.translate(x,y);matrices.rotate(rotation);matrices.scale(scale,scale);int edge=Math.max(1,Math.round(size));context.drawTexture(RenderPipelines.GUI_TEXTURED,TEXTURE,-edge/2,-edge/2,0,0,edge,edge,3100,3100,3100,3100,color);}finally{matrices.popMatrix();}
    }
}
