package net.aero.visuals.gui;

import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Same face and effective text size as ClickGUI; all measurements use the displayed font. */
final class AccountPanelStyle {
    private AccountPanelStyle() {}
    static double size(){return Math.max(4,14*client.gui.screen.ClickGuiScreen.getValue235()*client.util.InterfaceSizing.getTextScale());}
    static double width(String text){return UiRenderer.fontWidth(FontFace.GOLOS,text,size());}
    static String trim(String text,int max){
        if(text==null||max<=0)return "";
        if(width(text)<=max)return text;
        String suffix="…";double room=max-width(suffix);if(room<=0)return "";
        int end=0;
        while(end<text.length()){int next=end+Character.charCount(text.codePointAt(end));if(width(text.substring(0,next))>room)break;end=next;}
        return text.substring(0,end)+suffix;
    }
    static void text(GuiGraphicsExtractor d,Object ignored,String value,int x,int y,int color,boolean shadow){
        UiRenderer.font(d,FontFace.GOLOS,value,x,y,size(),color);
    }
    static double centeredY(double y,double height){return y+(height-size()*1.2)/2;}
    static void center(GuiGraphicsExtractor d,Object ignored,String value,int centerX,int y,int color){
        UiRenderer.font(d,FontFace.GOLOS,value,centerX-width(value)/2,y,size(),color);
    }
    static void buttonText(GuiGraphicsExtractor d,String value,int x,int y,int w,int h,int color){
        String label=trim(value,w-12);UiRenderer.font(d,FontFace.GOLOS,label,x+(w-width(label))/2,centeredY(y,h),size(),color);
    }
}
