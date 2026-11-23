package net.aero.visuals.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class FeatureScreens extends Screen {
    private static String tr(String text){return net.aero.visuals.localization.LocalText.get(text);}
    private final Screen parent;
    private final int type;
    private int page;
    private String status="";
    private EditBox name;
    private boolean refreshPending, editorFocusPending;

    private FeatureScreens(Screen parent,int type){
        super(Component.literal(tr(type==0?"История уведомлений":type==1?"Скриншоты":"Профили оформления")));
        this.parent=parent;this.type=type;
    }
    public static void history(Screen parent){Minecraft.getInstance().gui.setScreen(new FeatureScreens(parent,0));}
    public static void screenshot(Screen parent){Minecraft.getInstance().gui.setScreen(new FeatureScreens(parent,1));}
    public static void profiles(Screen parent){Minecraft.getInstance().gui.setScreen(new FeatureScreens(parent,2));}
    private void button(String text,int x,int y,int w,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(tr(text)),b->action.run()).bounds(x,y,w,20).build());
    }

    @Override protected void init(){
        int left=width/2-150;
        button("Назад",width/2-50,height-28,100,this::onClose);
        if(type==0){
            button("Очистить",left,35,100,()->{FeatureJournal.clear();page=0;});
            button("◀",left+190,35,50,()->page=Math.max(0,page-1));
            button("▶",left+245,35,50,()->page=Math.min(lastPage(FeatureJournal.entries().size(),historyRows()),page+1));
        }
        if(type==1){
            button("Открыть папку",left,35,145,()->openFolder(ScreenshotPreview.directory()));
            button("Открыть снимок",left+150,35,150,this::openScreenshot);
        }
        if(type==2){
            // Reuse the editor through resize/rebuild: keep text, cursor and selection.
            if(name==null){
                name=new EditBox(font,left,35,300,20,Component.literal(tr("Имя профиля")));
                name.setMaxLength(48);
            }else{name.setX(left);name.setY(35);}
            name.setHint(Component.literal(tr("Имя набора оформления")));
            addRenderableWidget(name);
            button("Сохранить",left,60,145,()->operation(true));
            button("Загрузить",left+150,60,150,()->operation(false));
            button("Папка профилей",left,85,180,()->openFolder(AppearanceProfiles.directory()));
            button("◀",left+190,85,50,()->{page=Math.max(0,page-1);refreshProfiles();});
            button("▶",left+245,85,50,()->{page++;refreshProfiles();});
            addProfiles();
        }
    }

    @Override protected void setInitialFocus(){
        if(type==2&&name!=null)setInitialFocus(name);else super.setInitialFocus();
    }

    private void error(Exception failure){
        String reason=failure.getMessage();
        status=tr("Ошибка:")+" "+(reason==null||reason.isBlank()?failure.getClass().getSimpleName():reason);
        net.aero.visuals.AeroVisuals.LOGGER.debug("Feature screen operation failed",failure);
    }
    private void openFolder(Path folder){
        try{Files.createDirectories(folder);net.aero.visuals.render.PlatformLinks.openFile(folder.toFile());status="";}
        catch(Exception failure){error(failure);}
    }
    private void openScreenshot(){
        try{
            Path p=ScreenshotPreview.latest();
            if(p==null){status=tr("Новых снимков пока нет");return;}
            if(!Files.isRegularFile(p)){status=tr("Снимок больше не существует");return;}
            net.aero.visuals.render.PlatformLinks.openFile(p.toFile());status="";
        }catch(Exception failure){error(failure);}
    }
    private void operation(boolean save){
        try{
            if(save)AppearanceProfiles.save(name.getValue());else AppearanceProfiles.load(name.getValue());
            status=tr(save?"Сохранено:":"Загружено:")+" "+name.getValue();
            refreshProfiles();
        }catch(Exception failure){error(failure);}
    }
    private void refreshProfiles(){refreshPending=true;}
    @Override public void tick(){
        super.tick();
        // The parent assigns focus after a button callback returns. Rebuild afterward.
        if(refreshPending){
            refreshPending=false;editorFocusPending=false;
            setDragging(false);setFocused(null);clearWidgets();init();setInitialFocus();
        }else if(editorFocusPending){
            editorFocusPending=false;setFocused(null);setInitialFocus(name);
        }
    }
    private int historyRows(){return Math.max(1,(height-105)/22);}
    private static int lastPage(int size,int rows){return size==0?0:(size-1)/rows;}
    private void addProfiles(){
        try{
            var files=AppearanceProfiles.list();
            int count=Math.max(1,(height-175)/24);
            page=Math.max(0,Math.min(page,lastPage(files.size(),count)));
            for(int i=page*count;i<Math.min(files.size(),(page+1)*count);i++){
                String label=files.get(i);
                button(label,width/2-150,112+(i-page*count)*24,300,()->{name.setValue(label);editorFocusPending=true;});
            }
        }catch(Exception failure){error(failure);}
    }

    @Override public void extractRenderState(GuiGraphicsExtractor d,int mouseX,int mouseY,float delta){
        super.extractRenderState(d,mouseX,mouseY,delta);
        d.centeredText(font,title,width/2,12,0xFFFFFFFF);
        if(type==0){
            var entries=FeatureJournal.entries();
            int count=historyRows();
            // A resize changes the number of rows; the old page may no longer exist.
            page=Math.max(0,Math.min(page,lastPage(entries.size(),count)));
            int start=page*count;
            var format=DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
            for(int i=start;i<Math.min(entries.size(),start+count);i++){
                var entry=entries.get(i);
                String line=format.format(Instant.ofEpochMilli(entry.time()))+"  "+entry.text();
                d.text(font,font.plainSubstrByWidth(line,Math.max(1,width-36)),18,65+(i-start)*22,0xFFE5DEFF,true);
            }
            if(entries.isEmpty())d.centeredText(font,tr("Уведомлений пока нет"),width/2,78,0xFFBBBBCC);
        }
        if(type==1){
            if(ScreenshotPreview.ready()){
                int w=Math.max(1,Math.min(width-40,500)),h=Math.max(1,(int)(w*ScreenshotPreview.ratio()));
                if(h>Math.max(1,height-120)){h=Math.max(1,height-120);w=Math.max(1,(int)(h/ScreenshotPreview.ratio()));}
                ScreenshotPreview.draw(d,(width-w)/2,65,w,h);
                var p=ScreenshotPreview.latest();
                if(p!=null)d.centeredText(font,font.plainSubstrByWidth(p.getFileName().toString(),Math.max(1,width-20)),width/2,height-57,0xFFE5DEFF);
            }else d.centeredText(font,tr("Сделай скриншот клавишей F2"),width/2,85,0xFFBBBBCC);
        }
        if(!status.isEmpty())d.centeredText(font,font.plainSubstrByWidth(status,Math.max(1,width-20)),width/2,height-42,0xFFE5DEFF);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
