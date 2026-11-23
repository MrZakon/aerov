package net.aero.visuals.feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.time.*;
import java.time.format.DateTimeFormatter;
public final class FeatureScreens extends Screen {
    private final Screen parent;private final int type;private int page;private String status="";private EditBox name;
    private FeatureScreens(Screen parent,int type){super(Component.literal(type==0?"История уведомлений":type==1?"Скриншоты":"Профили оформления"));this.parent=parent;this.type=type;}
    public static void history(Screen parent){Minecraft.getInstance().setScreen(new FeatureScreens(parent,0));}
    public static void screenshot(Screen parent){Minecraft.getInstance().setScreen(new FeatureScreens(parent,1));}
    public static void profiles(Screen parent){Minecraft.getInstance().setScreen(new FeatureScreens(parent,2));}
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){int left=width/2-150;button("Назад",width/2-50,height-28,100,this::onClose);
        if(type==0){button("Очистить",left,35,100,()->{FeatureJournal.clear();page=0;});button("◀",left+190,35,50,()->page=Math.max(0,page-1));button("▶",left+245,35,50,()->{int rows=Math.max(1,(height-105)/22);page=Math.min(Math.max(0,(FeatureJournal.entries().size()-1)/rows),page+1);});}
        if(type==1){button("Открыть папку",left,35,145,()->Util.getPlatform().openFile(ScreenshotPreview.directory().toFile()));button("Открыть снимок",left+150,35,150,()->{var p=ScreenshotPreview.latest();if(p!=null)Util.getPlatform().openFile(p.toFile());else status="Новых снимков пока нет";});}
        if(type==2){name=addRenderableWidget(new EditBox(font,left,35,300,20,Component.literal("Имя профиля")));name.setMaxLength(48);name.setHint(Component.literal("Имя набора оформления"));button("Сохранить",left,60,145,()->operation(true));button("Загрузить",left+150,60,150,()->operation(false));button("Папка профилей",left,85,180,()->{try{AppearanceProfiles.list();Util.getPlatform().openFile(AppearanceProfiles.directory().toFile());}catch(Exception e){status=e.getMessage();}});button("◀",left+190,85,50,()->{page=Math.max(0,page-1);refreshProfiles();});button("▶",left+245,85,50,()->{page++;refreshProfiles();});addProfiles();}}
    private void operation(boolean save){try{if(save)AppearanceProfiles.save(name.getValue());else AppearanceProfiles.load(name.getValue());status=(save?"Сохранено: ":"Загружено: ")+name.getValue();refreshProfiles();}catch(Exception e){status="Ошибка: "+e.getMessage();}}
    private void refreshProfiles(){String value=name==null?"":name.getValue();clearWidgets();init();if(name!=null)name.setValue(value);}
    private void addProfiles(){try{var files=AppearanceProfiles.list();int count=Math.max(1,(height-175)/24);page=Math.min(page,Math.max(0,(files.size()-1)/count));for(int i=page*count;i<Math.min(files.size(),(page+1)*count);i++){String label=files.get(i);button(label,width/2-150,112+(i-page*count)*24,300,()->name.setValue(label));}}catch(Exception e){status=e.getMessage();}}
    @Override public void extractRenderState(GuiGraphicsExtractor d,int mouseX,int mouseY,float delta){super.extractRenderState(d,mouseX,mouseY,delta);d.centeredText(font,title,width/2,12,0xFFFFFFFF);
        if(type==0){var entries=FeatureJournal.entries();int count=Math.max(1,(height-105)/22),start=Math.min(page*count,entries.size());var format=DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());for(int i=start;i<Math.min(entries.size(),start+count);i++){var entry=entries.get(i);int y=65+(i-start)*22;String line=format.format(Instant.ofEpochMilli(entry.time()))+"  "+entry.text();d.text(font,font.plainSubstrByWidth(line,Math.max(50,width-36)),18,y,0xFFE5DEFF,true);}if(entries.isEmpty())d.centeredText(font,"Уведомлений пока нет",width/2,78,0xFFBBBBCC);}
        if(type==1){if(ScreenshotPreview.ready()){int w=Math.min(width-40,500),h=(int)(w*ScreenshotPreview.ratio());if(h>height-120){h=Math.max(1,height-120);w=(int)(h/ScreenshotPreview.ratio());}ScreenshotPreview.draw(d,(width-w)/2,65,w,h);var p=ScreenshotPreview.latest();if(p!=null)d.centeredText(font,font.plainSubstrByWidth(p.getFileName().toString(),width-20),width/2,height-57,0xFFE5DEFF);}else d.centeredText(font,"Сделай скриншот клавишей F2",width/2,85,0xFFBBBBCC);}
        if(!status.isEmpty())d.centeredText(font,font.plainSubstrByWidth(status,width-20),width/2,height-42,0xFFE5DEFF);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
