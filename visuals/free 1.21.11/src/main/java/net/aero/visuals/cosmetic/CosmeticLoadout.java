package net.aero.visuals.cosmetic;

import com.google.gson.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.feature.ImportedFeatures;
import net.aero.visuals.feature.ImportedModels;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.DoubleSetting;
import java.util.*;

/** Public cosmetic IDs, never GUI list indices. Shared by local rendering and website sync. */
public final class CosmeticLoadout {
    public static final List<String> TYPES=List.of("cape","wings","bodywear","hat","pet","graffiti");
    private static final Map<String,String> CATEGORIES=Map.of("ПЛАЩИ","cape","КРЫЛЬЯ","wings","РЮКЗАКИ","bodywear","ГОЛОВНЫЕ_УБОРЫ","hat","ПИТОМЦЫ","pet","ГРАФФИТИ","graffiti");
    private CosmeticLoadout(){}
    /** Keep edits made locally since the baseline, including removal of a category. */
    public static Map<String,Integer> merge(Map<String,Integer> baseline,Map<String,Integer> local,Map<String,Integer> remote){
        Map<String,Integer> result=new HashMap<>(remote);
        for(String type:TYPES)if(!Objects.equals(baseline.get(type),local.get(type))){
            if(local.containsKey(type))result.put(type,local.get(type));else result.remove(type);
        }
        return Map.copyOf(result);
    }
    public static Map<String,Integer> decode(JsonObject object){
        Map<String,Integer> result=new HashMap<>();
        if(object==null)return Map.of();
        for(var entry:object.entrySet()){
            String type=CATEGORIES.getOrDefault(entry.getKey().toUpperCase(Locale.ROOT),entry.getKey().toLowerCase(Locale.ROOT));
            if(!TYPES.contains(type)||entry.getValue().isJsonNull())continue;
            try{int id=Integer.parseInt(entry.getValue().getAsString());if(id>=0)result.put(type,id);}catch(RuntimeException ignored){}
        }
        return Map.copyOf(result);
    }
    private static int selectedId(String type, DoubleSetting setting) {
        var entries = ImportedModels.selectableCatalog(type);
        return entries.isEmpty() ? -1 : entries.get(Math.floorMod(setting.get().intValue(), entries.size())).id();
    }

    private static void add(Map<String, Integer> result, String type, boolean enabled, DoubleSetting setting) {
        if (!enabled) return;
        int id = selectedId(type, setting);
        if (id >= 0) result.put(type, id);
    }

    public static int selectedPetId() {
        var pet = ImportedFeatures.get(ImportedFeatures.Pet.class);
        return pet != null && pet.isConfiguredEnabled() ? selectedId("pet", pet.variant) : -1;
    }
    public static Map<String,Integer> capture(){
        var c=ImportedFeatures.get(ImportedFeatures.Cosmetics.class);var p=ImportedFeatures.get(ImportedFeatures.Pet.class);
        boolean clothes=c!=null&&c.isConfiguredEnabled();
        boolean pet=p!=null&&p.isConfiguredEnabled();
        if(!clothes&&!pet)return Map.of();
        Map<String,Integer> result=new HashMap<>();
        if(clothes){
            add(result,"cape",c.cape.get(),c.variant);add(result,"wings",c.wings.get(),c.wingsVariant);
            add(result,"bodywear",c.bodywear.get(),c.bodywearVariant);add(result,"hat",c.hat.get(),c.hatVariant);add(result,"graffiti",c.graffiti.get(),c.graffitiVariant);
        }
        if(pet)add(result,"pet",true,p.variant);
        return net.aero.visuals.edition.FreeEdition.loadout(result);
    }
    private static boolean apply(String type,Map<String,Integer> ids,BooleanSetting enabled,DoubleSetting variant){
        var entries=ImportedModels.selectableCatalog(type);int index=-1;
        for(int i=0;i<entries.size();i++)if(Objects.equals(ids.get(type),entries.get(i).id())){index=i;break;}
        if(index>=0)variant.set((double)index);enabled.set(index>=0);return index>=0;
    }
    public static void apply(Map<String,Integer> ids){
        ids=net.aero.visuals.edition.FreeEdition.loadout(ids);
        var c=ImportedFeatures.get(ImportedFeatures.Cosmetics.class);var p=ImportedFeatures.get(ImportedFeatures.Pet.class);
        if(c!=null){boolean any=false;any|=apply("cape",ids,c.cape,c.variant);any|=apply("wings",ids,c.wings,c.wingsVariant);any|=apply("bodywear",ids,c.bodywear,c.bodywearVariant);any|=apply("hat",ids,c.hat,c.hatVariant);any|=apply("graffiti",ids,c.graffiti,c.graffitiVariant);c.setEnabled(any);}
        if(p!=null){var entries=ImportedModels.selectableCatalog("pet");int index=-1;for(int i=0;i<entries.size();i++)if(Objects.equals(ids.get("pet"),entries.get(i).id())){index=i;break;}if(index>=0)p.variant.set((double)index);p.setEnabled(index>=0);}
        var runtime=ClientRuntime.getNullable();if(runtime!=null)runtime.cosmeticSelection().replaceRemote(ids);
    }
}
