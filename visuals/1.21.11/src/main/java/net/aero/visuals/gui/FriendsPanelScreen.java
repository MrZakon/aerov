package net.aero.visuals.gui;

import client.api.Theme;
import java.util.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.friend.FriendsService;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/** Account friends embedded in the current ClickGUI. No local lists or settings files. */
public final class FriendsPanelScreen extends Screen {
    private record Action(int x,int y,int width,int height,Runnable run,boolean enabled,boolean row) {}
    private final List<Action> actions=new ArrayList<>();
    private AccountTextField add,search,compose;
    private final FriendConversationView conversation=new FriendConversationView();
    private String chattingId="",chattingName="",draft="",submittedId="",submittedText="";
    private String target="",query="",cache="";
    private int tab,originX,originY;
    private boolean listing;
    private double scroll,targetScroll;
    private long frame;
    private List<Object> rows=List.of();
    public FriendsPanelScreen(){super(Text.literal("Друзья"));}
    private FriendsService service(){return ClientRuntime.get().friends();}
    private net.aero.visuals.friend.FriendChatService chat(){return ClientRuntime.get().friendChat();}
    private String tr(String ru,String en,String uk){return LocalText.pick(ru,en,uk);}
    @Override protected void init(){
        add=addDrawableChild(new AccountTextField(textRenderer,12,35,Math.max(80,width-124),20,Text.literal("Aero ID")));
        add.setMaxLength(64);add.setText(target);add.setPlaceholder(Text.literal(tr("Ник или #Aero ID","Name or #Aero ID","Нік або #Aero ID")));add.setChangedListener(value->target=value);
        search=addDrawableChild(new AccountTextField(textRenderer,12,63,Math.max(80,width-110),20,Text.literal("Search")));
        search.setMaxLength(64);search.setText(query);search.setPlaceholder(Text.literal(tr("Поиск друзей…","Search friends…","Пошук друзів…")));search.setChangedListener(value->{query=value;cache="";scroll=targetScroll=0;});
        compose=addDrawableChild(new AccountTextField(textRenderer,12,height-52,Math.max(80,width-118),24,Text.literal("Message")));
        compose.setMaxLength(1000);compose.setText(draft);compose.setPlaceholder(Text.literal(tr("Сообщение другу…","Message your friend…","Повідомлення другу…")));compose.setChangedListener(value->draft=value);
        fields();
        service().refresh();
    }
    public void renderEmbedded(DrawContext d,int x,int y,int w,int h,int mx,int my,float delta,float progress){
        originX=x;originY=y+Math.round((1-progress)*10);
        if(width!=w||height!=h)init(w,h);
        d.getMatrices().pushMatrix();d.getMatrices().translate(originX,originY);
        try{render(d,mx-originX,my-originY,delta);}finally{d.getMatrices().popMatrix();}
    }
    @Override public void render(DrawContext d,int mouseX,int mouseY,float delta){
        actions.clear();UiRenderer.roundedRect(d,0,0,width,height,8,Theme.background());
        fields();if(!chattingId.isBlank()){renderChat(d,mouseX,mouseY,delta);return;}
        AccountPanelStyle.text(d,textRenderer,tr("Друзья · Aero ID","Friends · Aero ID","Друзі · Aero ID"),12,13,Theme.foreground(),false);
        super.render(d,mouseX,mouseY,delta);
        button(d,width-104,35,92,20,tr("Отправить","Send request","Надіслати"),!service().busy()&&!target.isBlank(),()->service().request(target),mouseX,mouseY);
        button(d,width-90,63,78,20,tr("Обновить","Refresh","Оновити"),!service().busy(),()->service().refresh(),mouseX,mouseY);
        var state=service().snapshot();int cell=Math.max(50,(width-28)/3);
        String[] labels={tr("Друзья","Friends","Друзі")+" ("+state.friends().size()+")",tr("Входящие","Incoming","Вхідні")+" ("+state.incoming().size()+")",tr("Исходящие","Outgoing","Вихідні")+" ("+state.outgoing().size()+")"};
        for(int i=0;i<3;i++){final int next=i;button(d,12+i*(cell+2),92,cell,22,(tab==i?"• ":"")+labels[i],true,()->{tab=next;cache="";scroll=targetScroll=0;},mouseX,mouseY);}
        String nextCache=state.revision()+"/"+tab+"/"+query.toLowerCase(Locale.ROOT);
        if(!cache.equals(nextCache)){
            String filter=query.strip().toLowerCase(Locale.ROOT);List<Object> filtered=new ArrayList<>();
            if(tab==0){for(var f:state.friends())if((f.name()+" "+f.accountId()+" "+String.join(" ",f.minecraftNames())).toLowerCase(Locale.ROOT).contains(filter))filtered.add(f);}
            else for(var r:tab==1?state.incoming():state.outgoing())if((r.name()+" "+r.accountId()).toLowerCase(Locale.ROOT).contains(filter))filtered.add(r);
            rows=List.copyOf(filtered);cache=nextCache;
        }
        int top=122,bottom=height-32;
        targetScroll=Math.clamp(targetScroll,0,maxScroll());long now=System.nanoTime();double dt=frame==0?0:Math.min(.1,(now-frame)/1e9);frame=now;scroll+=(targetScroll-scroll)*(1-Math.exp(-18*dt));
        if(bottom>top){
            d.enableScissor(10,top,width-8,bottom);
            listing=true;
            for(int index=0;index<rows.size();index++){
                int y=top+index*54-(int)scroll;if(y+50<top||y>bottom)continue;
                UiRenderer.roundedRect(d,12,y,width-26,49,6,Theme.surface());
                Object entry=rows.get(index);
                if(entry instanceof FriendsService.Friend f){
                    String name=f.name()+(f.accountId().isBlank()?"":"  #"+f.accountId());
                    AccountPanelStyle.text(d,textRenderer,AccountPanelStyle.trim(name,Math.max(30,width-243)),24,y+9,Theme.foreground(),false);
                    String online=service().fresh()?(f.online()?tr("В игре","In game","У грі"):tr("Не в игре","Not in game","Не у грі")):tr("Статус недоступен","Status unavailable","Статус недоступний");
                    String details=online;
                    if(service().fresh()&&!f.players().isEmpty()){var p=f.players().getFirst();details+=" · "+p.name()+" · "+p.server()+" · "+p.version();if(f.players().size()>1)details+=" +"+(f.players().size()-1);}
                    AccountPanelStyle.text(d,textRenderer,AccountPanelStyle.trim(details,Math.max(30,width-60)),24,y+30,f.online()&&service().fresh()?Theme.primary():Theme.mutedFg(),false);
                    button(d,width-205,y+6,46,20,tr("Чат","Chat","Чат"),true,()->openChat(f),mouseX,mouseY);
                    button(d,width-155,y+6,28,20,f.pinned()?"★":"☆",!service().busy(),()->service().pin(f.id(),!f.pinned()),mouseX,mouseY);
                    button(d,width-123,y+6,40,20,tr("Ник","Copy","Нік"),true,()->client.keyboard.setClipboard(f.minecraftNames().isEmpty()?f.name():f.minecraftNames().getFirst()),mouseX,mouseY);
                    button(d,width-79,y+6,52,20,tr("Удалить","Remove","Видалити"),!service().busy(),()->service().remove(f.id()),mouseX,mouseY);
                }else if(entry instanceof FriendsService.Request r){
                    AccountPanelStyle.text(d,textRenderer,AccountPanelStyle.trim(r.name()+"  #"+r.accountId(),Math.max(30,width-200)),24,y+10,Theme.foreground(),false);
                    AccountPanelStyle.text(d,textRenderer,tr(r.incoming()?"Хочет добавить вас":"Ожидает подтверждения",r.incoming()?"Wants to add you":"Awaiting response",r.incoming()?"Хоче додати вас":"Очікує підтвердження"),24,y+30,Theme.mutedFg(),false);
                    if(r.incoming())button(d,width-145,y+7,58,24,tr("Принять","Accept","Прийняти"),!service().busy(),()->service().answer(r.id(),"accept"),mouseX,mouseY);
                    button(d,width-82,y+7,56,24,r.incoming()?tr("Отклонить","Decline","Відхилити"):tr("Отменить","Cancel","Скасувати"),!service().busy(),()->service().answer(r.id(),r.incoming()?"reject":"cancel"),mouseX,mouseY);
                }
            }
            listing=false;d.disableScissor();
            if(rows.isEmpty())AccountPanelStyle.center(d,textRenderer,tr("Список пуст","Nothing here yet","Список порожній"),width/2,top+35,Theme.mutedFg());
            if(maxScroll()>0){double track=bottom-top,handle=Math.max(16,track*track/(rows.size()*54));UiRenderer.roundedRect(d,width-10,top+(track-handle)*scroll/maxScroll(),3,handle,1.5,Theme.primary());}
        }
        AccountPanelStyle.text(d,textRenderer,AccountPanelStyle.trim(LocalText.get(service().status()),Math.max(40,width-26)),12,height-19,Theme.mutedFg(),false);
    }
    private void fields(){boolean chatting=!chattingId.isBlank();if(add!=null){add.setVisible(!chatting);search.setVisible(!chatting);compose.setVisible(chatting);}}
    private void openChat(FriendsService.Friend friend){chattingId=friend.id();chattingName=friend.name();draft="";submittedId="";compose.setText("");conversation.reset();chat().open(friend);fields();setFocused(compose);}
    private void closeChat(){chat().leave();chattingId="";chattingName="";draft="";compose.setText("");submittedId="";conversation.reset();fields();setFocused(null);}
    private void sendChat(){if(draft.isBlank())return;String id=chat().send(draft);if(!id.isBlank()){submittedId=id;submittedText=draft;}}
    private void renderChat(DrawContext d,int mx,int my,float delta){
        chat().touch();var state=chat().snapshot();
        if(state.friendId().isBlank()){closeChat();return;}
        if(!submittedId.isBlank()&&submittedId.equals(state.sentId())){if(draft.equals(submittedText))compose.setText("");submittedId="";if(state.olderPage())chat().latest();}
        button(d,12,8,90,23,tr("‹ Друзья","‹ Friends","‹ Друзі"),true,this::closeChat,mx,my);
        AccountPanelStyle.text(d,textRenderer,AccountPanelStyle.trim(state.name().isBlank()?chattingName:state.name(),width-130),112,15,Theme.foreground(),false);
        button(d,12,38,125,20,tr("Ранние сообщения","Earlier messages","Попередні повідомлення"),state.hasOlder()&&!chat().reading(),chat()::older,mx,my);
        button(d,width-141,38,129,20,tr("К новым сообщениям","Latest messages","До нових повідомлень"),!chat().reading(),chat()::latest,mx,my);
        int bottom=height-63;conversation.draw(d,state,width,65,bottom);
        if(state.messages().isEmpty())AccountPanelStyle.center(d,textRenderer,tr("Начни переписку с другом","Start a conversation","Почни розмову з другом"),width/2,Math.max(75,(65+bottom)/2),Theme.mutedFg());
        super.render(d,mx,my,delta);
        button(d,width-98,height-52,86,24,tr("Отправить","Send","Надіслати"),!chat().sending()&&!draft.isBlank(),this::sendChat,mx,my);
        AccountPanelStyle.text(d,textRenderer,AccountPanelStyle.trim(LocalText.get(state.status()),width-75),12,height-18,Theme.mutedFg(),false);
        AccountPanelStyle.text(d,textRenderer,draft.length()+"/1000",width-64,height-18,Theme.mutedFg(),false);
    }
    private double maxScroll(){return Math.max(0,rows.size()*54-Math.max(1,height-154));}
    private void button(DrawContext d,int x,int y,int w,int h,String text,boolean enabled,Runnable run,int mx,int my){
        boolean hover=mx>=x&&mx<x+w&&my>=y&&my<y+h;
        UiRenderer.roundedRect(d,x,y,w,h,5,hover&&enabled?Theme.elevated():Theme.surface());
        AccountPanelStyle.buttonText(d,text,x,y,w,h,enabled?Theme.foreground():Theme.mutedFg());
        actions.add(new Action(x,y,w,h,run,enabled,listing));
    }
    private Click local(Click c){return new Click(c.x()-originX,c.y()-originY,c.buttonInfo());}
    public boolean clickEmbedded(Click click,boolean twice){
        Click c=local(click);if(c.x()<0||c.y()<0||c.x()>width||c.y()>height)return false;
        if(c.button()==0){for(var a:List.copyOf(actions)){if(c.x()>=a.x&&c.x()<a.x+a.width&&c.y()>=a.y&&c.y()<a.y+a.height){if(a.row&&(c.y()<122||c.y()>=height-32))continue;if(a.enabled)a.run.run();return true;}}}
        super.mouseClicked(c,twice);return true;
    }
    public boolean scrollEmbedded(double x,double y,double horizontal,double vertical){if(!chattingId.isBlank()&&x>=originX&&x<originX+width&&y>=originY+65&&y<originY+height-63)return conversation.scroll(vertical);if(x>=originX&&x<originX+width&&y>=originY+122&&y<originY+height-32){targetScroll=Math.clamp(targetScroll-vertical*40,0,maxScroll());return true;}return false;}
    public boolean releaseEmbedded(Click c){return super.mouseReleased(local(c));}
    public boolean dragEmbedded(Click c,double dx,double dy){return super.mouseDragged(local(c),dx,dy);}
    @Override public boolean keyPressed(KeyInput input){if(!chattingId.isBlank()&&(input.key()==257||input.key()==335)){sendChat();return true;}if(input.key()==257&&add.isFocused()&&!target.isBlank()){service().request(target);return true;}return super.keyPressed(input);}
    @Override public boolean shouldPause(){return false;}
}
