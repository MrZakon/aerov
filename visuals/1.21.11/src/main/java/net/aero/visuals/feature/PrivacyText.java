package net.aero.visuals.feature;
import net.minecraft.text.*;
import java.util.*;
/** Replaces only matching spans; retains every surrounding character's style and interaction data. */
public final class PrivacyText {
    private record Glyph(int code,Style style){}
    public static OrderedText mask(OrderedText source,ImportedFeatures.Streamer module){
        if(source==null||module==null||!module.isEnabled())return source;
        var glyphs=new ArrayList<Glyph>();StringBuilder raw=new StringBuilder();var styles=new ArrayList<Style>();
        source.accept((index,style,code)->{raw.appendCodePoint(code);for(int j=0;j<Character.charCount(code);j++)styles.add(style);return true;});
        String text=raw.toString();if(module.filter(text).equals(text))return source;
        var replacements=new LinkedHashMap<String,String>();var mc=net.minecraft.client.MinecraftClient.getInstance();
        if(module.nickname.get()){String nick=mc.getSession().getUsername();if(!nick.isEmpty())replacements.put(nick,"Player");if(mc.player!=null)replacements.put(mc.player.getName().getString(),"Player");}
        if(module.server.get()&&mc.getCurrentServerEntry()!=null)replacements.put(mc.getCurrentServerEntry().address,"Server");
        for(int i=0;i<text.length();){String match=null;for(String candidate:replacements.keySet())if(!candidate.isEmpty()&&text.startsWith(candidate,i)&&(match==null||candidate.length()>match.length()))match=candidate;Style style=styles.get(i);
            if(match!=null){String replacement=replacements.get(match);replacement.codePoints().forEach(c->glyphs.add(new Glyph(c,style)));i+=match.length();}else{int code=text.codePointAt(i);glyphs.add(new Glyph(code,style));i+=Character.charCount(code);}}
        return visitor->{int offset=0;for(var glyph:glyphs){if(!visitor.accept(offset,glyph.style,glyph.code))return false;offset+=Character.charCount(glyph.code);}return true;};
    }
    public static Text mask(Text source){var module=ImportedFeatures.get(ImportedFeatures.Streamer.class);if(module==null||!module.isEnabled())return source;MutableText result=Text.empty();source.visit((style,string)->{String filtered=module.filter(string);result.append(Text.literal(filtered).setStyle(style));return Optional.empty();},Style.EMPTY);return result;}
}
