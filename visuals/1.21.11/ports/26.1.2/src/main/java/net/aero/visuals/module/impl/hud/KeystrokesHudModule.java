package net.aero.visuals.module.impl.hud;
import net.aero.visuals.hud.*;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.MouseEvent;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayDeque;
public final class KeystrokesHudModule extends HudWidgetModule {
    private final ArrayDeque<Long> left=new ArrayDeque<>(),right=new ArrayDeque<>();
    public KeystrokesHudModule(){super("keystrokes_hud","Keystrokes","WASD, пробел, кнопки мыши и реальное число нажатий в секунду.",new HudPosition(HudAnchor.BOTTOM_LEFT,10,120,1));}
    @Subscribe private void mouse(MouseEvent e){var c=Minecraft.getInstance();if(e.type()==MouseEvent.Type.BUTTON&&e.action()==1&&c.screen==null){var q=e.button()==0?left:e.button()==1?right:null;if(q!=null){if(q.size()<200)q.add(System.nanoTime());}}}
    @Override protected void refresh(Minecraft c,float delta){long now=System.nanoTime();left.removeIf(t->now-t>1_000_000_000L);right.removeIf(t->now-t>1_000_000_000L);width=98;height=132;}
    @Override protected void renderHud(GuiGraphicsExtractor d,Minecraft c){title(d,"Нажатия");key(d,"W",36,27,26,c.options.keyUp.isDown());key(d,"A",6,57,26,c.options.keyLeft.isDown());key(d,"S",36,57,26,c.options.keyDown.isDown());key(d,"D",66,57,26,c.options.keyRight.isDown());key(d,"SPACE",6,87,86,c.options.keyJump.isDown());text(d,"LMB "+left.size()+" · RMB "+right.size()+" CPS",6,118,7,0xFFCAD0DF);}
    private void key(GuiGraphicsExtractor d,String label,int x,int y,int w,boolean pressed){UiRenderer.roundedRect(d,x,y,w,25,5,pressed?client.api.Theme.primary():client.api.Theme.elevated());text(d,label,x+(w-textWidth(label,8))/2,y+8,8,client.api.Theme.foreground());}
    @Override protected void onDisable(){left.clear();right.clear();}
}
