package net.aero.visuals.feature;

import com.google.gson.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.cosmetic.CosmeticLoadout;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class AppearanceProfiles {
    private static final Path directory=FabricLoader.getInstance().getConfigDir().resolve("aerovisuals-appearance");
    private static final Set<String> ids=Set.of("cosmetics","pet","emotes","world_particles","weather_effects","hand_shaders","jump_circles","item_physics","spatial_audio","custom_fog","footprints","player_trail","inventory_animation","block_highlight","biome_ambience","food_effects","experience_effects","crosshair","hit_sounds","hit_color","view_model","swing_animation");
    private static final Map<String,String> variantKeys=Map.of("cape","variant","wings","wings_variant","bodywear","bodywear_variant","hat","hat_variant","graffiti","graffiti_variant");
    private static Path path(String name){return directory.resolve(net.aero.visuals.config.StudioProfiles.safeName(name)+".json");}

    public static List<String> list() throws java.io.IOException {
        Files.createDirectories(directory);
        try(var files=Files.list(directory)){
            return files.filter(Files::isRegularFile).filter(p->p.getFileName().toString().endsWith(".json"))
                    .map(p->p.getFileName().toString().replaceFirst("\\.json$","")).sorted().toList();
        }
    }

    public static void save(String name) throws java.io.IOException {
        Path output=path(name);
        var runtime=ClientRuntime.get();
        JsonObject modules=new JsonObject();
        var all=runtime.config().snapshot().getAsJsonObject("modules");
        for(String id:ids)if(all.has(id))modules.add(id,all.get(id).deepCopy());
        JsonObject root=new JsonObject();
        root.addProperty("version",2);
        root.add("modules",modules);
        // Capture the actual configured loadout; the auxiliary selection file may be stale.
        root.add("cosmetics",new Gson().toJsonTree(CosmeticLoadout.capture()));
        Files.createDirectories(directory);
        Path temporary=Files.createTempFile(directory,"aero-appearance-",".tmp");
        try{
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(root),StandardCharsets.UTF_8);
            try{Files.move(temporary,output,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
        FeatureJournal.record("Сохранено оформление: "+name);
    }

    private static int index(String type,int id){
        var entries=ImportedModels.catalog(type);
        for(int i=0;i<entries.size();i++)if(entries.get(i).id()==id)return i;
        return -1;
    }

    private static Map<String,Integer> cosmeticIds(JsonObject root){
        if(!root.has("cosmetics")||!root.get("cosmetics").isJsonObject())throw new IllegalArgumentException("Нет косметики");
        Map<String,Integer> result=new HashMap<>();
        for(var entry:root.getAsJsonObject("cosmetics").entrySet()){
            if(!CosmeticLoadout.TYPES.contains(entry.getKey()))continue;
            int id=Integer.parseInt(entry.getValue().getAsString());
            if(id<0)throw new IllegalArgumentException("Некорректный ID косметики");
            result.put(entry.getKey(),id);
        }
        return result;
    }

    private static void remapVariants(JsonObject modules,Map<String,Integer> cosmetics){
        if(modules.has("cosmetics")){
            var saved=modules.getAsJsonObject("cosmetics");
            variantKeys.forEach((type,key)->{
                Integer id=cosmetics.get(type);
                if(id==null)return; // Keep disabled categories and their settings intact.
                int variant=index(type,id);
                if(variant>=0)saved.addProperty(key,variant);
                else saved.addProperty(type,false); // A removed model must not select an unrelated entry.
            });
        }
        Integer pet=cosmetics.get("pet");
        if(pet!=null&&modules.has("pet")){
            var saved=modules.getAsJsonObject("pet");
            int variant=index("pet",pet);
            if(variant>=0)saved.addProperty("variant",variant);else saved.addProperty("enabled",false);
        }
    }

    public static void load(String name) throws java.io.IOException {
        Path p=path(name);
        if(Files.size(p)>2_000_000)throw new java.io.IOException("Слишком большой профиль");
        try{
            var root=JsonParser.parseString(Files.readString(p,StandardCharsets.UTF_8)).getAsJsonObject();
            int version=root.has("version")?Integer.parseInt(root.get("version").getAsString()):1;
            if(version<1||version>2)throw new IllegalArgumentException("Неподдерживаемая версия профиля");
            if(!root.has("modules")||!root.get("modules").isJsonObject())throw new IllegalArgumentException("Нет модулей");
            JsonObject saved=new JsonObject();
            for(var entry:root.getAsJsonObject("modules").entrySet())if(ids.contains(entry.getKey())){
                if(!entry.getValue().isJsonObject())throw new IllegalArgumentException("Некорректный модуль: "+entry.getKey());
                saved.add(entry.getKey(),entry.getValue().deepCopy());
            }
            // Legacy profiles used cached selection IDs, so retain their module-based behavior.
            if(version==2)remapVariants(saved,cosmeticIds(root));
            var runtime=ClientRuntime.get();
            var merged=runtime.config().snapshot();
            for(var entry:saved.entrySet())merged.getAsJsonObject("modules").add(entry.getKey(),entry.getValue());
            runtime.config().restore(merged);
            // One write, with no intermediate empty/partially restored selection on disk.
            runtime.cosmeticSelection().replaceRemote(CosmeticLoadout.capture());
            runtime.config().flush();
            FeatureJournal.record("Загружено оформление: "+name);
        }catch(RuntimeException failure){throw new java.io.IOException("Некорректный профиль",failure);}
    }

    public static Path directory(){return directory;}
}
