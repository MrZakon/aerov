package net.aero.visuals.gui;

import client.api.Theme;
import java.util.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.config.CloudConfigsService;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** Account presets, embedded into the same category transition as ordinary modules. */
public final class ConfigsPanelScreen extends Screen {
    private record Action(int x,int y,int w,int h,Runnable run,boolean enabled,boolean row) {}
    private final List<Action> actions=new ArrayList<>();
    private AccountTextField name,code,search,rename;
    private String newName="",shareCode="",query="",selected="",editedName="",confirm="",confirmRevision="";
    private int originX,originY,split,listBottom;
    private List<CloudConfigsService.Entry> rows=List.of();
    private double scroll,targetScroll,detailScroll;
    private boolean details;
    private long frame;
    public ConfigsPanelScreen(){super(Text.literal("Конфиги"));}
    private CloudConfigsService service(){return ClientRuntime.get().cloudConfigs();}
    private String tr(String ru,String en,String uk){return LocalText.pick(ru,en,uk);}
    private AccountTextField field(int x,int y,int w,String value,String hint,java.util.function.Consumer<String> changed){
        var f=addDrawableChild(new AccountTextField(textRenderer,x,y,w,20,Text.literal(hint)));f.setMaxLength(60);f.setText(value);f.setPlaceholder(Text.literal(hint));f.setChangedListener(changed);return f;
    }
    @Override protected void init(){
        split=Math.max(150,width/2);int inputWidth=Math.max(60,width-126);
        name=field(12,35,inputWidth,newName,tr("Название нового конфига","New config name","Назва нового конфіга"),v->newName=v);
        code=field(12,63,inputWidth,shareCode,tr("Код общего конфига","Shared config code","Код спільного конфіга"),v->shareCode=v);code.setMaxLength(16);
        search=field(12,91,inputWidth,query,tr("Поиск конфигов…","Search configs…","Пошук конфігів…"),v->{query=v;targetScroll=scroll=0;});
        rename=field(split+16,159,Math.max(60,width-split-32),editedName,tr("Новое название","New name","Нова назва"),v->editedName=v);
        service().refresh();
    }
    public void renderEmbedded(DrawContext d,int x,int y,int w,int h,int mx,int my,float delta,float progress){
        originX=x;originY=y+Math.round((1-progress)*10);if(width!=w||height!=h)init(w,h);
        d.getMatrices().pushMatrix();d.getMatrices().translate(originX,originY);
        try{render(d,mx-originX,my-originY,delta);}finally{d.getMatrices().popMatrix();}
    }
    @Override public void render(DrawContext d,int mouseX,int mouseY,float delta){
        actions.clear();UiRenderer.roundedRect(d,0,0,width,height,8,Theme.background());
        AccountPanelStyle.text(d,textRenderer,tr("Конфиги · Aero Cloud","Configs · Aero Cloud","Конфіги · Aero Cloud"),12,13,Theme.foreground(),false);
        var all=service().entries();var entry=all.stream().filter(e->e.id().equals(selected)).findFirst().orElse(null);
        boolean available=!service().busy();rename.visible=false;rename.active=available&&confirm.isEmpty();
        super.render(d,mouseX,mouseY,delta);
        button(d,width-104,35,92,20,tr("Создать","Create","Створити"),available&&!newName.isBlank(),()->service().create(newName),false,mouseX,mouseY);
        button(d,width-104,63,92,20,tr("Импорт кода","Import code","Імпорт коду"),available&&shareCode.matches("(?i)[a-f0-9]{16}"),()->service().importCode(shareCode),false,mouseX,mouseY);
        button(d,width-104,91,92,20,tr("Обновить","Refresh","Оновити"),available,()->service().refresh(),false,mouseX,mouseY);
        rows=all.stream().filter(e->e.name().toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))).toList();
        int top=122;listBottom=height-32;
        targetScroll=Math.clamp(targetScroll,0,maxScroll());long now=System.nanoTime();double dt=frame==0?0:Math.min(.1,(now-frame)/1e9);frame=now;scroll+=(targetScroll-scroll)*(1-Math.exp(-18*dt));
        if(listBottom>top){
            d.enableScissor(10,top,split-3,listBottom);
            for(int i=0;i<rows.size();i++){
                var item=rows.get(i);int y=top+i*49-(int)scroll;if(y+45<top||y>=listBottom)continue;
                button(d,12,y,split-22,43,"",true,()->select(item),true,mouseX,mouseY);
                if(item.id().equals(selected))UiRenderer.roundedRect(d,13,y+4,2,35,1,Theme.primary());
                text(d,(item.id().equals(service().active())?"✓ ":"")+item.name(),23,y+8,split-45,Theme.foreground());
                text(d,LocalText.get(item.syncState())+(item.size()>0?" · "+Math.max(1,(item.size()+1023)/1024)+" KB":""),23,y+26,split-45,Theme.mutedFg());
            }
            d.disableScissor();
            if(rows.isEmpty())text(d,tr("Конфигов пока нет","No configs yet","Конфігів ще немає"),18,top+18,split-30,Theme.mutedFg());
            if(maxScroll()>0){double track=listBottom-top,handle=Math.max(16,track*track/(rows.size()*49));UiRenderer.roundedRect(d,split-6,top+(track-handle)*scroll/maxScroll(),3,handle,1.5,Theme.primary());}
        }
        int x=split+16,w=Math.max(60,width-split-32);
        detailScroll=Math.clamp(detailScroll,0,Math.max(0,405-(height-68)));
        d.enableScissor(split+4,122,width-8,Math.max(123,height-68));details=true;
        rename.setY(159-(int)detailScroll);rename.visible=entry!=null&&rename.getY()>=122&&rename.getY()+20<=height-68;
        if(rename.visible)rename.render(d,mouseX,mouseY,delta);
        if(entry!=null){
            text(d,entry.name(),x,127,w,Theme.foreground());
            text(d,entry.compatible()?tr("1.21.11 ↔ 26.1.2","1.21.11 ↔ 26.1.2","1.21.11 ↔ 26.1.2"):tr("Другой формат","Different format","Інший формат"),x,143,w,Theme.mutedFg());
            button(d,x,187,w,24,tr("Переименовать","Rename","Перейменувати"),available&&!editedName.isBlank()&&confirm.isEmpty(),()->service().rename(entry,editedName),false,mouseX,mouseY);
            button(d,x,219,w,26,tr("Применить","Apply","Застосувати"),available&&entry.compatible()&&confirm.isEmpty(),()->service().load(entry),false,mouseX,mouseY);
            button(d,x,253,w,24,tr("Сохранить в этот конфиг","Overwrite with current","Перезаписати поточним"),available&&confirm.isEmpty(),()->confirm(entry,"save"),false,mouseX,mouseY);
            button(d,x,285,w,24,tr("Поделиться · скопировать код","Share · copy code","Поділитися · копіювати код"),available&&entry.compatible()&&confirm.isEmpty(),()->service().share(entry,c->client.keyboard.setClipboard(c)),false,mouseX,mouseY);
            button(d,x,317,w,24,tr("Закрыть доступ по коду","Disable sharing","Закрити доступ за кодом"),available&&!entry.privateConfig()&&confirm.isEmpty(),()->service().makePrivate(entry),false,mouseX,mouseY);

            if(!entry.code().isBlank())text(d,entry.code(),x,385,w,Theme.primary());

        }else{
            text(d,tr("Выберите конфиг из списка","Select a preset","Виберіть конфіг зі списку"),x,134,w,Theme.mutedFg());
            text(d,tr("Конфиги хранятся в Aero ID","Presets are saved to Aero ID","Конфіги зберігаються в Aero ID"),x,154,w,Theme.mutedFg());
        }
        details=false;d.disableScissor();
        if(entry!=null){button(d,x,height-60,w,24,tr("Удалить конфиг","Delete config","Видалити конфіг"),available&&confirm.isEmpty(),()->confirm(entry,"delete"),false,mouseX,mouseY);}
        if(entry!=null&&!confirm.isEmpty()){
            actions.clear();UiRenderer.rect(d,0,0,width,height,0xA0000000);
            int mw=Math.min(300,width-24),mh=116,ml=(width-mw)/2,mt=Math.max(8,(height-mh)/2);
            UiRenderer.roundedRect(d,ml,mt,mw,mh,8,Theme.border());UiRenderer.roundedRect(d,ml+1,mt+1,mw-2,mh-2,7,Theme.background());
            text(d,confirm.equals("save")?tr("Перезаписать конфиг?","Overwrite preset?","Перезаписати конфіг?"):tr("Удалить конфиг?","Delete preset?","Видалити конфіг?"),ml+14,mt+14,mw-28,Theme.foreground());
            text(d,entry.name(),ml+14,mt+35,mw-28,Theme.mutedFg());
            text(d,tr("На ПК и на сервере","On this PC and server","На ПК та на сервері"),ml+14,mt+51,mw-28,Theme.mutedFg());
            button(d,ml+14,mt+76,(mw-34)/2,25,confirm.equals("save")?tr("Сохранить","Save","Зберегти"):tr("Удалить","Delete","Видалити"),available,()->{if(confirm.equals("save"))service().overwrite(entry);else service().remove(entry);confirm="";},false,mouseX,mouseY);
            button(d,ml+20+(mw-34)/2,mt+76,(mw-34)/2,25,tr("Отмена","Cancel","Скасувати"),true,()->confirm="",false,mouseX,mouseY);
        }else if(entry==null)confirm="";
        text(d,LocalText.get(service().status()),12,height-19,width-24,Theme.mutedFg());
    }
    private void select(CloudConfigsService.Entry entry){selected=entry.id();editedName=entry.name();rename.setText(editedName);confirm="";detailScroll=0;}
    private void confirm(CloudConfigsService.Entry entry,String action){confirm=action;confirmRevision=entry.revision();}
    private double maxScroll(){return Math.max(0,rows.size()*49-Math.max(1,height-154));}
    private void text(DrawContext d,String text,int x,int y,int w,int color){if(details)y-=(int)detailScroll;AccountPanelStyle.text(d,textRenderer,AccountPanelStyle.trim(text,Math.max(1,w)),x,y,color,false);}
    private void button(DrawContext d,int x,int y,int w,int h,String label,boolean enabled,Runnable run,boolean row,int mx,int my){
        if(details)y-=(int)detailScroll;row|=details;
        UiRenderer.roundedRect(d,x,y,w,h,5,enabled&&mx>=x&&mx<x+w&&my>=y&&my<y+h?Theme.elevated():Theme.surface());
        AccountPanelStyle.buttonText(d,label,x,y,w,h,enabled?Theme.foreground():Theme.mutedFg());
        actions.add(new Action(x,y,w,h,run,enabled,row));
    }
    private Click local(Click c){return new Click(c.x()-originX,c.y()-originY,c.buttonInfo());}
    public boolean clickEmbedded(Click click,boolean twice){
        Click c=local(click);if(c.x()<0||c.y()<0||c.x()>width||c.y()>height)return false;
        if(c.button()==0)for(var a:List.copyOf(actions))if(c.x()>=a.x&&c.x()<a.x+a.w&&c.y()>=a.y&&c.y()<a.y+a.h){if(a.row&&(c.y()<122||c.y()>=(a.x>=split?height-68:listBottom)))continue;if(a.enabled)a.run.run();return true;}
        if(!confirm.isEmpty())return true;super.mouseClicked(c,twice);return true;
    }
    public boolean scrollEmbedded(double x,double y,double horizontal,double vertical){if(x>=originX+split&&x<originX+width&&y>=originY+122&&y<originY+listBottom){detailScroll=Math.clamp(detailScroll-vertical*35,0,Math.max(0,405-(height-68)));return true;}if(x>=originX&&x<originX+split&&y>=originY+122&&y<originY+listBottom){targetScroll=Math.clamp(targetScroll-vertical*40,0,maxScroll());return true;}return false;}
    public boolean releaseEmbedded(Click c){return super.mouseReleased(local(c));}
    public boolean dragEmbedded(Click c,double dx,double dy){return super.mouseDragged(local(c),dx,dy);}
    @Override public boolean keyPressed(KeyInput key){if(!confirm.isEmpty())return true;if(key.key()==257&&!service().busy()){if(name.isFocused()&&!newName.isBlank()){service().create(newName);return true;}if(code.isFocused()&&shareCode.matches("(?i)[a-f0-9]{16}")){service().importCode(shareCode);return true;}}return super.keyPressed(key);}
    @Override public boolean shouldPause(){return false;}
}
