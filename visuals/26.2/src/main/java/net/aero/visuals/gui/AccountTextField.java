package net.aero.visuals.gui;

import client.api.Theme;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Keeps native editing/clipboard/keyboard behavior, with ClickGUI rendering and mouse metrics. */
final class AccountTextField extends EditBox {
    private String hint="";
    private double offset;
    private int anchor;
    AccountTextField(Font renderer,int x,int y,int w,int h,Component title){super(renderer,x,y,w,h,title);setBordered(false);setTextShadow(false);}
    @Override public void setHint(Component value){super.setHint(value);hint=value==null?"":value.getString();}
    @Override public void setHighlightPos(int position){super.setHighlightPos(position);anchor=Math.clamp(position,0,getValue().length());}
    private double measure(String value){return AccountPanelStyle.width(value);}
    private void updateOffset(){
        String value=getValue();int cursor=Math.clamp(getCursorPosition(),0,value.length());double caret=measure(value.substring(0,cursor)),available=Math.max(1,getWidth()-20);
        if(caret-offset>available-1)offset=caret-available+1;if(caret<offset)offset=caret;
        offset=Math.clamp(offset,0,Math.max(0,measure(value)-available+1));
    }
    private int position(double mouseX){
        updateOffset();String value=getValue();double at=mouseX-getX()-10+offset;int index=0;double previous=0;
        while(index<value.length()){int next=index+Character.charCount(value.codePointAt(index));double advance=measure(value.substring(0,next));if(at<(previous+advance)/2)return index;previous=advance;index=next;}
        return value.length();
    }
    @Override public void onClick(MouseButtonEvent click,boolean twice){
        int index=position(click.x());
        if(twice){String value=getValue();int start=index,end=index;while(start>0&&!Character.isWhitespace(value.charAt(start-1)))start--;while(end<value.length()&&!Character.isWhitespace(value.charAt(end)))end++;moveCursorTo(start,false);moveCursorTo(end,true);}
        else moveCursorTo(index,click.hasShiftDown());
    }
    @Override protected void onDrag(MouseButtonEvent click,double dx,double dy){moveCursorTo(position(click.x()),true);}
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor d,int mx,int my,float delta){
        if(!visible)return;updateOffset();int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        boolean focused=isFocused()&&active;
        UiRenderer.roundedRect(d,x,y,w,h,5,focused?Theme.primary():Theme.border());
        UiRenderer.roundedRect(d,x+.6,y+.6,w-1.2,h-1.2,4.4,Theme.surface());
        String value=getValue();double size=AccountPanelStyle.size(),top=AccountPanelStyle.centeredY(y,h);
        d.enableScissor(x+7,y+2,x+w-7,y+h-2);
        try{
            if(focused&&anchor!=getCursorPosition()){
                int left=Math.clamp(Math.min(anchor,getCursorPosition()),0,value.length()),right=Math.clamp(Math.max(anchor,getCursorPosition()),0,value.length());
                double begin=x+10+measure(value.substring(0,left))-offset,end=x+10+measure(value.substring(0,right))-offset;
                UiRenderer.roundedRect(d,begin,top,end-begin,size*1.2,1,(Theme.primary()&0xffffff)|0x55000000);
            }
            UiRenderer.font(d,FontFace.GOLOS,value.isEmpty()?AccountPanelStyle.trim(hint,w-20):value,x+10-(value.isEmpty()?0:offset),top,size,active&&!value.isEmpty()?Theme.foreground():Theme.mutedFg());
            if(focused&&(System.nanoTime()/500_000_000L)%2==0){double caret=x+10+measure(value.substring(0,getCursorPosition()))-offset;UiRenderer.rect(d,caret,top+.5,.7,Math.max(4,size*1.1),Theme.foreground());}
        }finally{d.disableScissor();}
    }
}
