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
        root.add("visuals",runtime.config().portableSnapshot());
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
    public static void apply(ClientRuntime runtime, JsonObject root) {
        validate(root);
        var visuals = runtime.config();
        var interfaceConfig = legacy();
        JsonObject previousInterface = interfaceConfig.cloudSnapshot();
        try {
            visuals.commitGlobalChange(() -> {
                interfaceConfig.restoreCloudSnapshot(root.getAsJsonObject("interface"));
                visuals.restorePortable(root.getAsJsonObject("visuals"));
            }, () -> writeInterface(runtime));
        } catch (RuntimeException | java.io.IOException failure) {
            try {
                interfaceConfig.restoreCloudSnapshot(previousInterface);
            } catch (RuntimeException rollback) {
                if (rollback != failure) failure.addSuppressed(rollback);
            }
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            throw new IllegalStateException("Не удалось записать настройки на ПК", failure);
        }
    }
    private static Path local(ClientRuntime runtime){return runtime.config().getConfigPath().resolveSibling("aerovisuals-interface.json");}
    public static void saveInterface(ClientRuntime runtime) {
        try {
            writeInterface(runtime);
        } catch (RuntimeException | java.io.IOException failure) {
            net.aero.visuals.AeroVisuals.LOGGER.warn("Could not save interface settings", failure);
        }
    }

    private static void writeInterface(ClientRuntime runtime) throws java.io.IOException {
        Path file=local(runtime),temporary=null;
        try{Files.createDirectories(file.getParent());temporary=Files.createTempFile(file.getParent(),"aero-interface-",".tmp");Files.writeString(temporary,legacy().cloudSnapshot().toString(),StandardCharsets.UTF_8);
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(java.io.IOException ignored){}}
    }
    public static void loadInterface(ClientRuntime runtime){
        Path file=local(runtime);try{if(Files.isRegularFile(file)&&Files.size(file)<=MAX_BYTES)legacy().restoreCloudSnapshot(JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject());}
        catch(Exception failure){net.aero.visuals.AeroVisuals.LOGGER.warn("Could not restore interface settings",failure);}
    }
}
