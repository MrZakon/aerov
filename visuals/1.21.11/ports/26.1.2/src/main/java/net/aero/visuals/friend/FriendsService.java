package net.aero.visuals.friend;

import com.google.gson.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.cosmetic.CosmeticSyncService;
import net.aero.visuals.guard.AeroGuard;
import net.aero.visuals.guard.HardwareId;

/** The server is authoritative. This service only holds an in-memory view; it never writes config files. */
public final class FriendsService implements AutoCloseable {
    public record Player(String name,String server,String version) {}
    public record Friend(String id,String accountId,String name,boolean pinned,boolean online,String createdAt,List<String> minecraftNames,List<Player> players) {
        public Friend { minecraftNames=List.copyOf(minecraftNames);players=List.copyOf(players); }
    }
    public record Request(String id,String name,String accountId,boolean incoming) {}
    public record Snapshot(List<Friend> friends,List<Request> incoming,List<Request> outgoing,String revision) {
        public Snapshot {friends=List.copyOf(friends);incoming=List.copyOf(incoming);outgoing=List.copyOf(outgoing);}
        static Snapshot empty(){return new Snapshot(List.of(),List.of(),List.of(),"");}
    }
    private final ClientRuntime runtime;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(task->{var t=new Thread(task,"Aero-Account-Friends");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(4)).build();
    private final AtomicBoolean busy=new AtomicBoolean();
    private volatile Snapshot state=Snapshot.empty();
    private volatile String token="",status="Друзья: подключение…";
    private volatile long generation=-1,nextPoll,lastSuccess;
    private volatile boolean closed;
    public FriendsService(ClientRuntime runtime){this.runtime=runtime;}
    public void start(){worker.scheduleWithFixedDelay(this::cycle,0,500,TimeUnit.MILLISECONDS);}
    private CosmeticSyncService.WebsiteAccess access(){return runtime.cosmeticSync().friendAccess();}
    public Snapshot snapshot(){var a=access();return a.generation()==generation&&a.token().equals(token)&&AeroGuard.allowed()?state:Snapshot.empty();}
    public String status(){return !AeroGuard.allowed()?AeroGuard.message():status;}
    public boolean busy(){return busy.get();}
    public boolean fresh(){return System.nanoTime()-lastSuccess<10_000_000_000L;}
    public void refresh(){nextPoll=0;}
    private void clear(CosmeticSyncService.WebsiteAccess a){token=a.token();generation=a.generation();state=Snapshot.empty();lastSuccess=0;client.data.SystemFriend.getInstance().replaceServerFriends(state);nextPoll=0;}
    private void cycle(){
        if(closed)return;
        try{
            var a=access();if(a.generation()!=generation||!a.token().equals(token))clear(a);
            if(!AeroGuard.allowed()||a.token().isBlank()){status="Войдите в Aero Launcher";return;}
            if(System.nanoTime()<nextPoll)return;
            nextPoll=System.nanoTime()+2_000_000_000L;
            apply(exchange(a,"GET","api/client/friends",null),a);status="Друзья: синхронизированы";
        }catch(Exception failure){if(!closed)status=message(failure);}
    }
    public void request(String name){JsonObject body=new JsonObject();body.addProperty("name",name.strip());perform("POST","api/client/friends/request",body);}
    public void answer(String id,String action){JsonObject body=new JsonObject();body.addProperty("action",action);perform("POST","api/client/friends/requests/"+id,body);}
    public void remove(String id){perform("DELETE","api/client/friends/"+id,null);}
    public void pin(String id,boolean value){JsonObject body=new JsonObject();body.addProperty("pinned",value);perform("POST","api/client/friends/"+id+"/pin",body);}
    public void removeByName(String name){snapshot().friends().stream().filter(f->matches(f,name)).findFirst().ifPresent(f->remove(f.id()));}
    public boolean isFriend(String name){return name!=null&&snapshot().friends().stream().anyMatch(f->matches(f,name));}
    private static boolean matches(Friend f,String name){return f.name().equalsIgnoreCase(name)||f.minecraftNames().stream().anyMatch(n->n.equalsIgnoreCase(name));}
    private void perform(String method,String path,JsonObject body){
        if(closed||!AeroGuard.allowed()||!busy.compareAndSet(false,true))return;
        var requested=access();
        worker.execute(()->{
            try{
                var current=access();if(current.generation()!=requested.generation()||!current.token().equals(requested.token()))throw new IllegalStateException("Аккаунт изменился. Повторите действие.");
                status="Друзья: сохранение…";apply(exchange(current,method,path,body),current);status="Друзья: сохранено на сервере";
            }catch(Exception failure){if(!closed)status=message(failure);}
            finally{busy.set(false);nextPoll=System.nanoTime()+1_500_000_000L;}
        });
    }
    private JsonObject exchange(CosmeticSyncService.WebsiteAccess a,String method,String path,JsonObject body)throws Exception{
        if(a.token().isBlank())throw new IllegalStateException("Войдите в Aero Launcher");
        var builder=HttpRequest.newBuilder(a.base().resolve(path)).timeout(Duration.ofSeconds(6)).header("Accept","application/json").header("Authorization","Bearer "+a.token()).header("Cache-Control","no-cache");
        if(!HardwareId.cached().isBlank())builder.header("X-HWID",HardwareId.cached());
        if(method.equals("GET"))builder.GET();else if(method.equals("DELETE"))builder.DELETE();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8));
        // Never retry mutations: a timed-out request may already have committed.
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject root;try{root=JsonParser.parseString(response.body()).getAsJsonObject();}catch(RuntimeException bad){throw new IllegalStateException("Сайт временно недоступен");}
        if(response.statusCode()/100!=2)throw new IllegalStateException(root.has("error")?root.get("error").getAsString():"Друзья: ошибка сайта "+response.statusCode());
        if(!root.has("data")||!root.get("data").isJsonObject())throw new IllegalStateException("Обновите API друзей на сайте");
        return root.getAsJsonObject("data");
    }
    private void apply(JsonObject data,CosmeticSyncService.WebsiteAccess expected){
        var current=access();if(closed||current.generation()!=expected.generation()||!current.token().equals(expected.token()))return;
        List<Friend> friends=new ArrayList<>();
        for(var value:array(data,"friends")){var f=value.getAsJsonObject();List<String> names=new ArrayList<>();for(var n:array(f,"minecraftNames"))names.add(n.getAsString());List<Player> players=new ArrayList<>();for(var p:array(f,"players")){var v=p.getAsJsonObject();players.add(new Player(string(v,"name"),string(v,"server"),string(v,"version")));}friends.add(new Friend(string(f,"id"),string(f,"accountId"),string(f,"name"),bool(f,"pinned"),bool(f,"online"),string(f,"createdAt"),names,players));}
        token=expected.token();generation=expected.generation();
        state=new Snapshot(friends,requests(data,"incoming",true),requests(data,"outgoing",false),string(data,"revision"));lastSuccess=System.nanoTime();
        client.data.SystemFriend.getInstance().replaceServerFriends(state);
    }
    private static List<Request> requests(JsonObject data,String key,boolean incoming){List<Request> list=new ArrayList<>();for(var value:array(data,key)){var r=value.getAsJsonObject();list.add(new Request(string(r,"id"),string(r,"name"),string(r,"accountId"),incoming));}return list;}
    private static JsonArray array(JsonObject obj,String key){return obj.has(key)&&obj.get(key).isJsonArray()?obj.getAsJsonArray(key):new JsonArray();}
    private static String string(JsonObject obj,String key){return obj.has(key)&&!obj.get(key).isJsonNull()?obj.get(key).getAsString():"";}
    private static boolean bool(JsonObject obj,String key){return obj.has(key)&&!obj.get(key).isJsonNull()&&obj.get(key).getAsBoolean();}
    private static String message(Exception failure){return failure instanceof IllegalStateException?failure.getMessage():"Не удалось подтвердить действие. Список обновится с сервера.";}
    @Override public void close(){closed=true;worker.shutdownNow();http.shutdownNow();state=Snapshot.empty();client.data.SystemFriend.getInstance().replaceServerFriends(state);}
}
