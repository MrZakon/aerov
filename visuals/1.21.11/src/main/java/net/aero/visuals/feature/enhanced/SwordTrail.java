package net.aero.visuals.feature.enhanced;

import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.ArrayDeque;

/** Time-limited blade ribbon, inspired by Arbuz SwordTrail. */
public final class SwordTrail extends VisualExpansion.Feature implements WorldRenderable {
    public enum Filter { SWORDS, TOOLS, ANY }
    private record Sample(Vec3d base,Vec3d tip,long born){}
    private final EnumSetting<Filter> filter=add(new EnumSetting<>("filter","Предметы","Какие предметы оставляют след.",Filter.SWORDS,Filter.class));
    private final DoubleSetting duration=number("duration","Длительность (сек.)",.28,.08,.8,.02);
    private final DoubleSetting width=number("width","Длина следа на клинке",.8,.2,1.3,.05);
    private final ColorSetting start=color("start","Цвет у оружия",0xCF88DFFF),end=color("end","Цвет хвоста",0x587E69FF);
    private final BooleanSetting first=flag("first_person","От первого лица",true),third=flag("third_person","От третьего лица",true);
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    private Object world;private boolean wasFirst;private long sampled;
    public SwordTrail(){super("sword_trail","SwordTrail","Плавный цветной след за клинком во время взмаха. Настройка градиента и затухания.", ModuleCategory.RENDER);}
    private boolean accepts(ItemStack stack){return !stack.isEmpty()&&(filter.get()==Filter.ANY||stack.isIn(ItemTags.SWORDS)||filter.get()==Filter.TOOLS&&(stack.isIn(ItemTags.AXES)||stack.isIn(ItemTags.PICKAXES)||stack.isIn(ItemTags.SHOVELS)||stack.isIn(ItemTags.HOES)));}
    private void reset(){samples.clear();sampled=0;world=null;}
    @Override protected void onDisable(){reset();}
    @Subscribe private void tick(TickEvent.Client event){var mc=event.client();if(mc.world!=world){reset();world=mc.world;}if(mc.player==null)samples.clear();}
    /** Called from the actual first-person item transform, after ViewModel/SwingAnimation. */
    public void capture(ItemStack stack,MatrixStack matrices){
        var mc=MinecraftClient.getInstance();if(!isEnabled()||mc.player==null||!mc.options.getPerspective().isFirstPerson()||!first.get()||!mc.player.handSwinging||mc.player.isUsingItem()||!accepts(stack))return;
        if(System.nanoTime()-sampled<3_000_000L)return;
        var camera=mc.gameRenderer.getCamera();var pos=camera.getCameraPos();
        // The held-item stack already contains the inverse world view rotation.
        // Restore camera space before converting to world space; otherwise yaw is applied twice.
        var handPose=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(matrices.peek().getPositionMatrix());
        Vector3f a=handPose.transformPosition(new Vector3f(0,-.12f,0));
        Vector3f b=handPose.transformPosition(new Vector3f(0,width.get().floatValue(),0));
        a.rotate(camera.getRotation());b.rotate(camera.getRotation());
        append(pos.add(a.x,a.y,a.z),pos.add(b.x,b.y,b.z));
    }
    private void append(Vec3d base,Vec3d tip){long now=System.nanoTime();if(now-sampled<3_000_000L)return;sampled=now;var previous=samples.peekLast();if(previous!=null&&previous.base.squaredDistanceTo(base)>9)samples.clear();samples.addLast(new Sample(base,tip,now));while(samples.size()>100)samples.removeFirst();}
    @Override public void renderWorld(WorldRenderContext context){
        var mc=MinecraftClient.getInstance();if(mc.player==null||context.matrices()==null||context.consumers()==null)return;
        boolean fp=mc.options.getPerspective().isFirstPerson();if(fp!=wasFirst){samples.clear();wasFirst=fp;}
        if(!fp&&third.get()&&mc.player.handSwinging&&!mc.player.isUsingItem()&&accepts(mc.player.getMainHandStack())){
            float progress=mc.player.getHandSwingProgress(mc.getRenderTickCounter().getTickProgress(false));
            double yaw=Math.toRadians(-mc.player.getYaw()),side=mc.player.getMainArm()==net.minecraft.util.Arm.RIGHT?1:-1;
            double angle=yaw+side*(.7-2.6*progress);Vec3d shoulder=mc.player.getLerpedPos(mc.getRenderTickCounter().getTickProgress(false)).add(Math.cos(yaw)*side*.3,1.25,Math.sin(yaw)*side*.3);
            Vec3d direction=new Vec3d(Math.sin(angle)*.8,-.35+Math.sin(progress*Math.PI)*.3,Math.cos(angle)*.8);
            append(shoulder.add(direction.multiply(.3)),shoulder.add(direction.multiply(.3+width.get())));
        }
        long now=System.nanoTime();samples.removeIf(s->(now-s.born)/1e9>duration.get());if((fp&&!first.get())||(!fp&&!third.get()))return;
        var matrices=context.matrices();var cam=mc.gameRenderer.getCamera().getCameraPos();matrices.push();
        try{matrices.translate(-cam.x,-cam.y,-cam.z);var vertices=context.consumers().getBuffer(RenderLayers.debugQuads());Sample previous=null;for(var sample:samples){if(previous!=null){float age=(float)Math.clamp((now-sample.born)/1e9/duration.get(),0,1);int col=ColorUtil.mix(start.get().eval(),end.get().eval(),age);col=ColorUtil.withAlpha(col,(int)((col>>>24)*Math.pow(1-age,1.6)));quad(matrices,vertices,previous.base,previous.tip,sample.tip,sample.base,col);}previous=sample;}}
        finally{matrices.pop();}
    }
    static void quad(MatrixStack matrices,net.minecraft.client.render.VertexConsumer v,Vec3d a,Vec3d b,Vec3d c,Vec3d d,int color){vertex(matrices,v,a,color);vertex(matrices,v,b,color);vertex(matrices,v,c,color);vertex(matrices,v,d,color);vertex(matrices,v,d,color);vertex(matrices,v,c,color);vertex(matrices,v,b,color);vertex(matrices,v,a,color);}
    static void vertex(MatrixStack matrices,net.minecraft.client.render.VertexConsumer v,Vec3d p,int color){v.vertex(matrices.peek(),(float)p.x,(float)p.y,(float)p.z).color(color);}
}
