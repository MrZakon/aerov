package net.aero.visuals.gui;

import client.api.Theme;
import net.aero.visuals.friend.FriendChatService;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Reusable conversation drawing in ClickGUI's own font and coordinates. No networking on the render thread. */
final class FriendConversationView {
    private record Bubble(FriendChatService.Message message,List<String> lines,double width,double height){}
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm"),DATE=DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private final Map<String,Bubble> wrapped=new LinkedHashMap<>();
    private double scroll, targetScroll, maxScroll, anchorFromBottom;
    private long lastFrame, revision = -1, scrollRevision;
    private String anchorId = "";

    void reset() {
        scroll = targetScroll = maxScroll = anchorFromBottom = 0;
        lastFrame = scrollRevision = 0;
        revision = -1;
        anchorId = "";
        wrapped.clear();
    }
    boolean scroll(double vertical){targetScroll=Math.clamp(targetScroll+vertical*38,0,maxScroll);return true;}
    void draw(GuiGraphicsExtractor d,FriendChatService.Snapshot state,int width,int top,int bottom){
        if (width <= 20 || bottom <= top) return;
        double font=AccountPanelStyle.size(),lineHeight=font*1.45,maxWidth=Math.max(60,Math.min(420,(width-32)*.76));
        var bubbles=new ArrayList<Bubble>();double total=0;
        for(var message:state.messages()){
            String key=message.id()+":"+maxWidth+":"+font;var bubble=wrapped.get(key);
            if(bubble==null){var lines=wrap(message.body(),maxWidth-22);double w=62;for(var line:lines)w=Math.max(w,AccountPanelStyle.width(line)+22);bubble=new Bubble(message,lines,Math.min(maxWidth,w),lines.size()*lineHeight+font+17);if(wrapped.size()>=256)wrapped.clear();wrapped.put(key,bubble);}
            bubbles.add(bubble);total+=bubble.height()+8;
        }
        maxScroll=Math.max(0,total-(bottom-top));
        if (scrollRevision != state.scrollRevision()) {
            targetScroll = scroll = state.olderPage() ? maxScroll : 0;
            scrollRevision = state.scrollRevision();
        } else if (revision != state.revision() && !state.olderPage() && targetScroll > 1 && !anchorId.isEmpty()) {
            preserveAnchor(bubbles, total);
        }
        revision = state.revision();
        targetScroll=Math.clamp(targetScroll,0,maxScroll);long now=System.nanoTime();double dt=lastFrame==0?0:Math.min(.1,(now-lastFrame)/1e9);lastFrame=now;scroll+=(targetScroll-scroll)*(1-Math.exp(-20*dt));scroll=Math.clamp(scroll,0,maxScroll);
        double y=bottom-total+scroll;if(total<bottom-top)y=top+4;
        anchorId = "";
        double fromBottom = total;
        d.enableScissor(10,top,width-10,bottom);
        try {
            for (var bubble : bubbles) {
                if (y + bubble.height() > top && y < bottom) {
                    var m = bubble.message();
                    if (anchorId.isEmpty()) {
                        anchorId = m.id();
                        anchorFromBottom = fromBottom;
                    }
                    double x = m.own() ? width - 16 - bubble.width() : 16;
                    int color = m.own() ? (Theme.primary() & 0xFFFFFF) | 0x33000000 : Theme.surface();
                    UiRenderer.roundedRect(d, x, y, bubble.width(), bubble.height(), 7, color);
                    int i = 0;
                    for (var line : bubble.lines()) {
                        UiRenderer.font(d, FontFace.GOLOS, line, x + 10, y + 8 + i++ * lineHeight, font, Theme.foreground());
                    }
                    String time = time(m.createdAt());
                    double timeX = x + bubble.width() - 10 - UiRenderer.fontWidth(FontFace.GOLOS, time, font * .8);
                    UiRenderer.font(d, FontFace.GOLOS, time, timeX, y + bubble.height() - font - 4, font * .8, Theme.mutedFg());
                }
                double rowHeight = bubble.height() + 8;
                y += rowHeight;
                fromBottom -= rowHeight;
            }
        } finally {
            d.disableScissor();
        }
        if (maxScroll > 0) {
            double track = bottom - top;
            double handle = Math.min(track, Math.max(18, track * track / total));
            UiRenderer.roundedRect(d, width - 7, top + (track - handle) * (1 - scroll / maxScroll),
                    2, handle, 1, Theme.primary());
        }
    }

    private void preserveAnchor(List<Bubble> bubbles, double fromBottom) {
        for (var bubble : bubbles) {
            if (bubble.message().id().equals(anchorId)) {
                double shift = fromBottom - anchorFromBottom;
                scroll += shift;
                targetScroll += shift;
                return;
            }
            fromBottom -= bubble.height() + 8;
        }
        // The previous top message was evicted from the bounded history.
        scroll = targetScroll = maxScroll;
    }
    private static String time(long value){try{var date=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault());return date.format(date.toLocalDate().equals(LocalDate.now())?CLOCK:DATE);}catch(DateTimeException failure){return "";}}
    private static List<String> wrap(String value,double room){var lines=new ArrayList<String>();for(String paragraph:value.replace("\t","    ").split("\n",-1)){StringBuilder line=new StringBuilder();double used=0;for(String word:paragraph.split("(?<=\\s)|(?=\\s)")){double w=AccountPanelStyle.width(word);if(used+w>room&&line.length()>0){lines.add(line.toString().stripTrailing());line.setLength(0);used=0;}if(w>room){for(int i=0;i<word.length();){int cp=word.codePointAt(i);String c=new String(Character.toChars(cp));double cw=AccountPanelStyle.width(c);if(used+cw>room&&line.length()>0){lines.add(line.toString());line.setLength(0);used=0;}line.append(c);used+=cw;i+=Character.charCount(cp);}}else if(line.length()>0||!word.isBlank()){line.append(word);used+=w;}}lines.add(line.toString().stripTrailing());}return List.copyOf(lines);}
}
