package net.aero.visuals.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Confirmed website logins can be reused by either Minecraft version on this OS account. */
final class WebsiteSessionStore {
    private final URI base;
    private final Path local,shared;
    private String published="";
    WebsiteSessionStore(URI base,Path local){
        this.base=base;this.local=local;
        String appdata=System.getenv("APPDATA"),home=System.getProperty("user.home");
        Path sharedPath=null;
        try{
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(base.toString().getBytes(StandardCharsets.UTF_8)));
            Path directory=appdata!=null&&!appdata.isBlank()?Path.of(appdata,"AeroVisuals"):home==null?null:Path.of(home,".aerovisuals");
            if(directory!=null)sharedPath=directory.resolve("web-sessions").resolve(hash+".json");
        }catch(Exception ignored){}
        shared=sharedPath;
    }
    Path sharedFile(){return shared;}
    void save(String token)throws IOException{write(local,token);publish(token);}
    void publish(String token)throws IOException{
        if(shared!=null&&!token.equals(published)){write(shared,token);published=token;}
    }
    private void write(Path path,String token)throws IOException{
        if(!token.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid website session");
        if(Files.isRegularFile(path))try{
            var old=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if(old.has("token")&&old.has("apiBaseUrl")&&token.equals(old.get("token").getAsString())&&base.toString().equals(old.get("apiBaseUrl").getAsString()))return;
        }catch(RuntimeException ignored){}
        Files.createDirectories(path.getParent());
        Path temporary=Files.createTempFile(path.getParent(),"website-session-",".tmp");
        try{
            if(Files.getFileStore(temporary).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(temporary,PosixFilePermissions.fromString("rw-------"));
            JsonObject value=new JsonObject();value.addProperty("token",token);value.addProperty("apiBaseUrl",base.toString());
            Files.writeString(temporary,value.toString(),StandardCharsets.UTF_8);
            try{Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
}
