package net.aero.visuals.config;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.aero.visuals.ClientRuntime;

/** Portable visual settings only. Account credentials, friends and cosmetics never enter a preset. */
public final class ConfigSnapshots {
    public static final int MAX_BYTES=2*1024*1024;
    private static final String FORMAT="aerovisuals-config";
    private ConfigSnapshots() {}
    public static JsonObject capture(ClientRuntime runtime){
        JsonObject root=new JsonObject();root.addProperty("format",FORMAT);root.addProperty("version",1);
        root.add("visuals",runtime.config().snapshot());
        root.add("interface",legacy().cloudSnapshot());
        return root;
    }
    private static client.concurrent.ConfigManager legacy(){return client.concurrent.SystemClient.getInstance().getConfigManager();}
    public static void validate(JsonObject root){
        if(root==null||!root.has("format")||!FORMAT.equals(root.get("format").getAsString())||!root.has("version")||root.get("version").getAsInt()!=1
                ||!root.has("visuals")||!root.get("visuals").isJsonObject()||!root.getAsJsonObject("visuals").has("modules")
                ||!root.getAsJsonObject("visuals").get("modules").isJsonObject()||!root.has("interface")||!root.get("interface").isJsonObject())
            throw new IllegalArgumentException("Это не конфиг AeroVisuals поддерживаемой версии");
        if(root.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Конфиг больше 2 МБ");
    }
    public static void apply(ClientRuntime runtime,JsonObject root){
        validate(root);JsonObject previous=capture(runtime);
        try{
            legacy().restoreCloudSnapshot(root.getAsJsonObject("interface"));
            runtime.config().restore(root.getAsJsonObject("visuals"));
            runtime.config().flush();saveInterface(runtime);
        }catch(RuntimeException failure){
            legacy().restoreCloudSnapshot(previous.getAsJsonObject("interface"));runtime.config().restore(previous.getAsJsonObject("visuals"));throw failure;
        }
    }
    private static Path local(ClientRuntime runtime){return runtime.config().getConfigPath().resolveSibling("aerovisuals-free-interface.json");}
    public static void saveInterface(ClientRuntime runtime){
        Path file=local(runtime),temporary=null;
        try{Files.createDirectories(file.getParent());temporary=Files.createTempFile(file.getParent(),"aero-interface-",".tmp");Files.writeString(temporary,legacy().cloudSnapshot().toString(),StandardCharsets.UTF_8);
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception failure){net.aero.visuals.AeroVisuals.LOGGER.warn("Could not save interface settings",failure);}finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(java.io.IOException ignored){}}
    }
    public static void loadInterface(ClientRuntime runtime){
        Path file=local(runtime);try{if(Files.isRegularFile(file)&&Files.size(file)<=MAX_BYTES)legacy().restoreCloudSnapshot(JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject());}
        catch(Exception failure){net.aero.visuals.AeroVisuals.LOGGER.warn("Could not restore interface settings",failure);}
    }
}
