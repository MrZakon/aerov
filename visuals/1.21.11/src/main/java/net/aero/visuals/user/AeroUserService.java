package net.aero.visuals.user;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Atlases;
import net.aero.visuals.AeroVisuals;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks players using AeroVisuals and provides the Aero logo badge.
 */
public final class AeroUserService {
    private static final String BADGE_GLYPH = "\uE000";
    private static final StyleSpriteSource BADGE_SPRITE = new StyleSpriteSource.Sprite(
            Atlases.GUI, Identifier.of(AeroVisuals.MOD_ID, "badges/aero"));
    private static volatile Map<UUID, Map<String, Integer>> COSMETICS = Map.of();
    private static volatile Set<String> PRESENCE_NAMES=Set.of();
    private static volatile Map<String,Map<String,Integer>> COSMETICS_BY_NAME=Map.of();
    public record PublicPlayer(String name,Map<String,Integer> cosmetics){public PublicPlayer{cosmetics=Map.copyOf(cosmetics);}}

    private AeroUserService() {}

    /** Stores the public cosmetic selection received from the presence service. */
    public static void registerCosmetics(UUID uuid, Map<String, Integer> cosmetics) {
        if (uuid == null || cosmetics == null) return;
        var copy=new java.util.HashMap<>(COSMETICS);copy.put(uuid,Map.copyOf(cosmetics));COSMETICS=Map.copyOf(copy);
    }

    /** An empty selection and expired presence actively remove old remote cosmetics. */
    public static void replacePresence(Map<UUID,PublicPlayer> players){
        Map<UUID,Map<String,Integer>> next=new java.util.HashMap<>();
        Set<String> names=new java.util.HashSet<>();
        Map<String,Map<String,Integer>> named=new java.util.HashMap<>();Set<String> ambiguous=new java.util.HashSet<>();
        for(var entry:players.entrySet()){
            String name=entry.getValue().name().toLowerCase(Locale.ROOT).trim();
            if(!names.add(name))ambiguous.add(name);
            named.put(name,entry.getValue().cosmetics());next.put(entry.getKey(),entry.getValue().cosmetics());
        }
        ambiguous.forEach(named::remove);COSMETICS_BY_NAME=Map.copyOf(named);
        PRESENCE_NAMES=Set.copyOf(names);
        COSMETICS=Map.copyOf(next);
    }
    public static Map<String,Integer> cosmetics(UUID uuid){return COSMETICS.getOrDefault(uuid,Map.of());}
    /** A failed connection must not leave a removed cape/backpack rendered indefinitely. */
    public static void clearCosmetics(){
        Map<UUID,Map<String,Integer>> empty=new java.util.HashMap<>();
        COSMETICS.keySet().forEach(uuid->empty.put(uuid,Map.of()));COSMETICS=Map.copyOf(empty);
        COSMETICS_BY_NAME=Map.of();
    }
    /** Some offline/proxy servers rewrite player UUIDs. Only an unambiguous exact
     * profile name from the same server's current roster may serve as fallback. */
    public static Map<String,Integer> cosmetics(UUID uuid,String profileName){
        Map<String,Integer> exact=COSMETICS.get(uuid);if(exact!=null)return exact;
        return profileName==null?Map.of():COSMETICS_BY_NAME.getOrDefault(profileName.toLowerCase(Locale.ROOT).trim(),Map.of());
    }

    public static int cosmetic(UUID uuid, String type) {
        if (uuid == null || type == null) return -1;
        return COSMETICS.getOrDefault(uuid, Map.of()).getOrDefault(type, -1);
    }

    public static boolean isAeroUser(UUID uuid, String name) {
        if(!net.aero.visuals.guard.AeroGuard.allowed())return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            if (uuid != null && uuid.equals(client.player.getUuid())) return true;
            if (name != null && name.equalsIgnoreCase(client.player.getName().getString())) return true;
        }
        if (uuid != null && COSMETICS.containsKey(uuid)) return true;
        if (name != null) {
            String clean = name.toLowerCase(Locale.ROOT).trim();
            if (PRESENCE_NAMES.contains(clean)) return true;
        }
        return false;
    }

    public static MutableText badge() {
        return Text.empty()
                .append(Text.literal(BADGE_GLYPH).setStyle(Style.EMPTY
                        .withFont(BADGE_SPRITE)
                        .withColor(0xFFFFFF)
                        .withBold(false)
                        .withItalic(false)))
                .append(Text.literal(" "));
    }

    public static boolean hasBadge(Text text) {
        return text != null && text.visit((style, content) ->
                BADGE_SPRITE.equals(style.getFont()) && content.contains(BADGE_GLYPH)
                        ? Optional.of(true) : Optional.<Boolean>empty(), Style.EMPTY).orElse(false);
    }
}
