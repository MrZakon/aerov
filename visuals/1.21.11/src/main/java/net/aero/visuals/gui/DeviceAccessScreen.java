package net.aero.visuals.gui;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.guard.AeroGuard;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** The Minecraft window stays usable while AeroVisuals awaits device authorization. */
public final class DeviceAccessScreen extends Screen {
    public DeviceAccessScreen(){super(Text.literal("AeroVisuals · Проверка устройства"));}
    @Override protected void init(){
        int x=width/2-110,y=height/2+12;
        addDrawableChild(ButtonWidget.builder(Text.literal("Обновить вход из лаунчера"),button->{var sync=ClientRuntime.get().cosmeticSync();if(sync!=null)sync.openLauncherPage();}).dimensions(x,y,220,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Повторить проверку HWID"),button->{var sync=ClientRuntime.get().cosmeticSync();if(sync!=null)sync.retryDevice();}).dimensions(x,y+25,220,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"),button->close()).dimensions(x,y+50,220,20).build());
    }
    @Override public void tick(){if(AeroGuard.allowed()&&client!=null)client.setScreen(ClickGuiScreen.getOrInstance());}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        context.fill(0,0,width,height,0xF0101218);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-73,0xFFFFFFFF);
        var lines=textRenderer.wrapLines(Text.literal(AeroGuard.message()),Math.max(100,width-40));
        for(int i=0;i<lines.size();i++)context.drawText(textRenderer,lines.get(i),(width-textRenderer.getWidth(lines.get(i)))/2,height/2-48+i*11,0xFFD1D5DB,false);
        super.render(context,mouseX,mouseY,delta);
    }
    @Override public void close(){if(client!=null)client.setScreen(null);}
    @Override public boolean shouldPause(){return false;}
}
