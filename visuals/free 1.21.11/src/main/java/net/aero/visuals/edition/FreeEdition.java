package net.aero.visuals.edition;
import java.util.*;
import net.aero.visuals.cosmetic.CosmeticCatalog;
/** Free's local equip rules never filter the catalog used to draw other players. */
public final class FreeEdition {
    private FreeEdition() {}
    private static final Set<String> MODULES=Set.of("wm_island","inventory_display","shulker_preview","armor_hud","potion_hud","crosshair","hit_sounds","jump_circles","item_physics","cosmetics","pet",
            "keystrokes_hud","totems_hud","saturation_hud","keybinds_hud",
            "world_particles","footprints","death_coordinates","armor_notifier","experience_effects");
    private static final Map<String,String> COSMETICS=Map.ofEntries(Map.entry("cape:100", "Плащ - Броя V2"),
            Map.entry("cape:101", "Плащ - Броя V3"),
            Map.entry("wings:123", "Purple monster"),
            Map.entry("bodywear:137", "Катана - ila_studio"),
            Map.entry("hat:19", "Новогодняя шапка"),
            Map.entry("pet:126", "Питомец - 1 Год"),
            Map.entry("graffiti:127", "Граффити - Петух"));
    public static boolean moduleAllowed(String id){return MODULES.contains(id);}
    public static boolean cosmeticAllowed(String type,int id){return COSMETICS.containsKey(type.toLowerCase(Locale.ROOT)+":"+id);}
    public static List<CosmeticCatalog.Entry> selectable(List<CosmeticCatalog.Entry> entries){return entries.stream().filter(e->cosmeticAllowed(e.type(),e.id())).toList();}
    public static Map<String,Integer> loadout(Map<String,Integer> ids){Map<String,Integer> result=new HashMap<>();ids.forEach((type,id)->{if(id!=null&&cosmeticAllowed(type,id))result.put(type,id);});return Map.copyOf(result);}
}
