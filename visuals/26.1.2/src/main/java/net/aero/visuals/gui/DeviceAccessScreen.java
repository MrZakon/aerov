package net.aero.visuals.gui;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.guard.AeroGuard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** The Minecraft window stays usable while AeroVisuals awaits device authorization. */
public final class DeviceAccessScreen extends Screen {
    public DeviceAccessScreen(){super(Component.literal("AeroVisuals · Проверка устройства"));}
    @Override protected void init(){
        int x=width/2-110,y=height/2+12;
        addRenderableWidget(Button.builder(Component.literal("Обновить вход из лаунчера"),button->{var sync=ClientRuntime.get().cosmeticSync();if(sync!=null)sync.openLauncherPage();}).bounds(x,y,220,20).build());
        addRenderableWidget(Button.builder(Component.literal("Повторить проверку HWID"),button->{var sync=ClientRuntime.get().cosmeticSync();if(sync!=null)sync.retryDevice();}).bounds(x,y+25,220,20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"),button->onClose()).bounds(x,y+50,220,20).build());
    }
    @Override public void tick(){if(AeroGuard.allowed()&&minecraft!=null)minecraft.setScreen(ClickGuiScreen.getOrInstance());}
    @Override public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta){
        context.fill(0,0,width,height,0xF0101218);
        context.centeredText(font,title,width/2,height/2-73,0xFFFFFFFF);
        var lines=font.split(Component.literal(AeroGuard.message()),Math.max(100,width-40));
        for(int i=0;i<lines.size();i++)context.text(font,lines.get(i),(width-font.width(lines.get(i)))/2,height/2-48+i*11,0xFFD1D5DB,false);
        super.extractRenderState(context,mouseX,mouseY,delta);
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
