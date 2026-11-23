package net.aero.visuals.mixin;
import net.aero.visuals.animation.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerListHud.class)
public abstract class AnimatedPlayerListMixin implements AnimatedPlayerList {
    @Unique private boolean aero$target;@Unique private double aero$progress;@Unique private long aero$frame;@Unique private Object aero$world;
    @Unique private Scoreboard aero$board;@Unique private ScoreboardObjective aero$objective;@Unique private int aero$width;
    @Inject(method="setVisible",at=@At("HEAD"))private void aero$visible(boolean visible,CallbackInfo ci){var w=MinecraftClient.getInstance().world;if(w!=aero$world){aero$world=w;aero$progress=0;aero$frame=0;aero$board=null;}aero$target=visible;}
    @Inject(method="render",at=@At("HEAD"))private void aero$start(DrawContext d,int width,Scoreboard board,ScoreboardObjective objective,CallbackInfo ci){aero$board=board;aero$objective=objective;aero$width=width;long now=System.nanoTime();double dt=aero$frame==0?.016:Math.min(.05,(now-aero$frame)/1e9);aero$frame=now;aero$progress=ChatMotion.reduced()?(aero$target?1:0):Math.clamp(aero$progress+(aero$target?1:-1)*dt/(aero$target?.2:.15),0,1);float ease=ChatMotion.ease((float)aero$progress),scale=.94f+.06f*ease;d.getMatrices().pushMatrix();d.getMatrices().translate(width/2f,10-(1-ease)*14);d.getMatrices().scale(scale,scale);d.getMatrices().translate(-width/2f,-10);}
    @Inject(method="render",at=@At("RETURN"))private void aero$end(DrawContext d,int width,Scoreboard board,ScoreboardObjective objective,CallbackInfo ci){d.getMatrices().popMatrix();}
    @Override public void aero$closing(DrawContext d){if(!aero$target&&aero$progress>0&&aero$board!=null&&MinecraftClient.getInstance().world==aero$world)((PlayerListHud)(Object)this).render(d,aero$width,aero$board,aero$objective);}
}
