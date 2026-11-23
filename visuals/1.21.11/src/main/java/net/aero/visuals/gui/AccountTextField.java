package net.aero.visuals.gui;

import client.api.Theme;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Keeps native editing/clipboard/keyboard behavior, with ClickGUI rendering and mouse metrics. */
final class AccountTextField extends TextFieldWidget {
    private String hint="";
    private double offset;
    private int anchor;
    AccountTextField(TextRenderer renderer,int x,int y,int w,int h,Text title){super(renderer,x,y,w,h,title);setDrawsBackground(false);setTextShadow(false);}
    @Override public void setPlaceholder(Text value){super.setPlaceholder(value);hint=value==null?"":value.getString();}
    @Override public void setSelectionEnd(int position){super.setSelectionEnd(position);anchor=Math.clamp(position,0,getText().length());}
    private double measure(String value){return AccountPanelStyle.width(value);}
    private void updateOffset(){
        String value=getText();int cursor=Math.clamp(getCursor(),0,value.length());double caret=measure(value.substring(0,cursor)),available=Math.max(1,getWidth()-20);
        if(caret-offset>available-1)offset=caret-available+1;if(caret<offset)offset=caret;
        offset=Math.clamp(offset,0,Math.max(0,measure(value)-available+1));
    }
    private int position(double mouseX){
        updateOffset();String value=getText();double at=mouseX-getX()-10+offset;int index=0;double previous=0;
        while(index<value.length()){int next=index+Character.charCount(value.codePointAt(index));double advance=measure(value.substring(0,next));if(at<(previous+advance)/2)return index;previous=advance;index=next;}
        return value.length();
    }
    @Override public void onClick(Click click,boolean twice){
        int index=position(click.x());
        if(twice){String value=getText();int start=index,end=index;while(start>0&&!Character.isWhitespace(value.charAt(start-1)))start--;while(end<value.length()&&!Character.isWhitespace(value.charAt(end)))end++;setCursor(start,false);setCursor(end,true);}
        else setCursor(index,click.hasShift());
    }
    @Override protected void onDrag(Click click,double dx,double dy){setCursor(position(click.x()),true);}
    @Override public void renderWidget(DrawContext d,int mx,int my,float delta){
        if(!visible)return;updateOffset();int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        boolean focused=isFocused()&&active;
        UiRenderer.roundedRect(d,x,y,w,h,5,focused?Theme.primary():Theme.border());
        UiRenderer.roundedRect(d,x+.6,y+.6,w-1.2,h-1.2,4.4,Theme.surface());
        String value=getText();double size=AccountPanelStyle.size(),top=AccountPanelStyle.centeredY(y,h);
        d.enableScissor(x+7,y+2,x+w-7,y+h-2);
        try{
            if(focused&&anchor!=getCursor()){
                int left=Math.clamp(Math.min(anchor,getCursor()),0,value.length()),right=Math.clamp(Math.max(anchor,getCursor()),0,value.length());
                double begin=x+10+measure(value.substring(0,left))-offset,end=x+10+measure(value.substring(0,right))-offset;
                UiRenderer.roundedRect(d,begin,top,end-begin,size*1.2,1,(Theme.primary()&0xffffff)|0x55000000);
            }
            UiRenderer.font(d,FontFace.GOLOS,value.isEmpty()?AccountPanelStyle.trim(hint,w-20):value,x+10-(value.isEmpty()?0:offset),top,size,active&&!value.isEmpty()?Theme.foreground():Theme.mutedFg());
            if(focused&&(System.nanoTime()/500_000_000L)%2==0){double caret=x+10+measure(value.substring(0,getCursor()))-offset;UiRenderer.rect(d,caret,top+.5,.7,Math.max(4,size*1.1),Theme.foreground());}
        }finally{d.disableScissor();}
    }
}
