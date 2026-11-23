package net.aero.visuals.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** One atomic JSON file per preset, including a durable outbox. Never stores login credentials. */
final class LocalConfigStore {
    static final class Document {
        String key=UUID.randomUUID().toString(),serverId="",clientId=UUID.randomUUID().toString(),name="",description="",code="",revision="",baseRevision="",cachedRevision="",updatedAt="",intent="",operationId="",error="";
        boolean privateConfig=true,compatible=true,deleted,paused;
        long sequence,remoteSize;
        JsonObject data,patch=new JsonObject();
        transient long retryAt;
        transient int failures;
        void change(String intent){this.intent=intent;operationId=UUID.randomUUID().toString();sequence++;paused=false;error="";retryAt=0;failures=0;updatedAt=java.time.Instant.now().toString();}
    }
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final Path directory;
    LocalConfigStore(Path root,String site,String account){
        if(!account.matches("[0-9]{1,20}"))throw new IllegalArgumentException("Некорректный Aero ID");directory=root.resolve(hash(site)).resolve(account);
    }
    Path directory(){return directory;}
    List<Document> read()throws IOException{
        if(!Files.isDirectory(directory))return List.of();List<Document> result=new ArrayList<>();
        try(var files=Files.list(directory)){
            for(Path file:files.filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).limit(250).toList())try{
                if(Files.size(file)>ConfigSnapshots.MAX_BYTES*3L)continue;
                Document d=JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),Document.class);
                if(d==null||!file.getFileName().toString().equals(d.key+".json")||!d.key.matches("[a-f0-9-]{36}")||!d.serverId.matches("[0-9]*"))continue;
                if(d.data!=null)ConfigSnapshots.validate(d.data);result.add(d);
            }catch(Exception failure){net.aero.visuals.AeroVisuals.LOGGER.warn("Ignoring damaged cached config {}",file.getFileName());}
        }return result;
    }
    void save(Document d)throws IOException{write(directory.resolve(d.key+".json"),JSON.toJson(d));}
    void remove(Document d)throws IOException{Files.deleteIfExists(directory.resolve(d.key+".json"));}
    static void write(Path file,String data)throws IOException{
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"config-",".tmp");
        try{Files.writeString(temporary,data,StandardCharsets.UTF_8);try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException failure){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temporary);}
    }
    static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception failure){throw new IllegalStateException(failure);}}
    static String knownAccount(Path root,String session){try{var index=JsonParser.parseString(Files.readString(root.resolve("accounts.json"))).getAsJsonObject();return index.has(session)?index.get(session).getAsString():"";}catch(Exception ignored){return "";}}
    static void rememberAccount(Path root,String session,String account)throws IOException{
        JsonObject index;try{index=JsonParser.parseString(Files.readString(root.resolve("accounts.json"))).getAsJsonObject();}catch(Exception ignored){index=new JsonObject();}
        if(index.has(session)&&account.equals(index.get(session).getAsString()))return;
        if(index.size()>32)index=new JsonObject();index.addProperty(session,account);write(root.resolve("accounts.json"),index.toString());
    }
}
