package net.aero.visuals.friend;

import com.google.gson.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
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
    private static final String CONNECTING = "Друзья: подключение…";
    private record View(CosmeticSyncService.WebsiteAccess owner, Snapshot snapshot, String status, long refreshedAt) {}
    private volatile View view = new View(null, Snapshot.empty(), CONNECTING, 0);
    private volatile CosmeticSyncService.WebsiteAccess mutation;
    private volatile boolean closed;
    private long nextPoll;
    public FriendsService(ClientRuntime runtime){this.runtime=runtime;}
    public void start(){worker.scheduleWithFixedDelay(this::cycle,0,500,TimeUnit.MILLISECONDS);}
    private CosmeticSyncService.WebsiteAccess access(){return runtime.cosmeticSync().friendAccess();}
    private boolean active(CosmeticSyncService.WebsiteAccess expected) {
        return expected != null && !closed && AeroGuard.allowed()
                && !expected.token().isBlank() && expected.equals(access());
    }

    private View visibleView() {
        View current = view;
        return active(current.owner()) ? current : null;
    }

    public Snapshot snapshot() {
        View current = visibleView();
        return current == null ? Snapshot.empty() : current.snapshot();
    }

    public String status() {
        if (!AeroGuard.allowed()) return AeroGuard.message();
        var current = visibleView();
        if (current != null) return current.status();
        return access().token().isBlank() ? "Войдите в Aero Launcher" : CONNECTING;
    }

    public boolean busy() { return active(mutation); }

    public boolean fresh() {
        View current = visibleView();
        return current != null && current.refreshedAt() != 0
                && System.nanoTime() - current.refreshedAt() < 10_000_000_000L;
    }

    public synchronized void refresh() { nextPoll = 0; }

    private void switchView(CosmeticSyncService.WebsiteAccess owner) {
        if (owner.equals(view.owner())) return;
        view = new View(owner, Snapshot.empty(), CONNECTING, 0);
        client.data.SystemFriend.getInstance().replaceServerFriends(view.snapshot(), null);
        nextPoll = 0;
    }

    private synchronized CosmeticSyncService.WebsiteAccess beginPoll() {
        if (closed) return null;
        var owner = access();
        switchView(owner);
        if (!active(owner)) return null;
        long now = System.nanoTime();
        if (nextPoll != 0 && now - nextPoll < 0) return null;
        nextPoll = now + 2_000_000_000L;
        return owner;
    }

    private synchronized boolean publishStatus(CosmeticSyncService.WebsiteAccess owner, String status) {
        if (!active(owner)) return false;
        switchView(owner);
        view = new View(owner, view.snapshot(), status, view.refreshedAt());
        return true;
    }

    private void cycle() {
        var owner = beginPoll();
        if (owner == null) return;
        try {
            apply(exchange(owner, "GET", "api/client/friends", null), owner, "Друзья: синхронизированы");
        } catch (Exception failure) {
            publishStatus(owner, message(failure));
        }
    }
    public void request(String name){JsonObject body=new JsonObject();body.addProperty("name",name.strip());perform("POST","api/client/friends/request",body);}
    public void answer(String id,String action){JsonObject body=new JsonObject();body.addProperty("action",action);perform("POST","api/client/friends/requests/"+id,body);}
    public void remove(String id){perform("DELETE","api/client/friends/"+id,null);}
    public void pin(String id,boolean value){JsonObject body=new JsonObject();body.addProperty("pinned",value);perform("POST","api/client/friends/"+id+"/pin",body);}
    public void removeByName(String name) {
        View current = visibleView();
        if (current == null) return;
        current.snapshot().friends().stream().filter(f -> matches(f, name)).findFirst().ifPresent(f ->
                perform(current.owner(), "DELETE", "api/client/friends/" + f.id(), null));
    }
    public boolean isFriend(String name){return name!=null&&snapshot().friends().stream().anyMatch(f->matches(f,name));}
    private static boolean matches(Friend f,String name){return f.name().equalsIgnoreCase(name)||f.minecraftNames().stream().anyMatch(n->n.equalsIgnoreCase(name));}
    private void perform(String method, String path, JsonObject body) {
        perform(access(), method, path, body);
    }

    private synchronized void perform(CosmeticSyncService.WebsiteAccess requested, String method, String path, JsonObject body) {
        if (!active(requested) || requested.equals(mutation)) return;
        mutation = requested;
        worker.execute(() -> {
            try {
                if (!publishStatus(requested, "Друзья: сохранение…")) return;
                apply(exchange(requested, method, path, body), requested, "Друзья: сохранено на сервере");
            } catch (Exception failure) {
                publishStatus(requested, message(failure));
            } finally {
                synchronized (this) {
                    if (requested.equals(mutation)) mutation = null;
                    if (active(requested)) nextPoll = System.nanoTime() + 1_500_000_000L;
                }
            }
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
    private void apply(JsonObject data, CosmeticSyncService.WebsiteAccess expected, String status) {
        if (!active(expected)) return;
        List<Friend> friends = new ArrayList<>();
        for (var value : array(data, "friends")) {
            var f = value.getAsJsonObject();
            List<String> names = new ArrayList<>();
            for (var name : array(f, "minecraftNames")) names.add(name.getAsString());
            List<Player> players = new ArrayList<>();
            for (var p : array(f, "players")) {
                var player = p.getAsJsonObject();
                players.add(new Player(string(player, "name"), string(player, "server"), string(player, "version")));
            }
            friends.add(new Friend(string(f, "id"), string(f, "accountId"), string(f, "name"),
                    bool(f, "pinned"), bool(f, "online"), string(f, "createdAt"), names, players));
        }
        Snapshot next = new Snapshot(friends, requests(data, "incoming", true),
                requests(data, "outgoing", false), string(data, "revision"));
        synchronized (this) {
            if (!active(expected)) return;
            client.data.SystemFriend.getInstance().replaceServerFriends(next, expected);
            view = new View(expected, next, status, System.nanoTime());
        }
    }
    private static List<Request> requests(JsonObject data,String key,boolean incoming){List<Request> list=new ArrayList<>();for(var value:array(data,key)){var r=value.getAsJsonObject();list.add(new Request(string(r,"id"),string(r,"name"),string(r,"accountId"),incoming));}return list;}
    private static JsonArray array(JsonObject obj,String key){return obj.has(key)&&obj.get(key).isJsonArray()?obj.getAsJsonArray(key):new JsonArray();}
    private static String string(JsonObject obj,String key){return obj.has(key)&&!obj.get(key).isJsonNull()?obj.get(key).getAsString():"";}
    private static boolean bool(JsonObject obj,String key){return obj.has(key)&&!obj.get(key).isJsonNull()&&obj.get(key).getAsBoolean();}
    private static String message(Exception failure){return failure instanceof IllegalStateException?failure.getMessage():"Не удалось подтвердить действие. Список обновится с сервера.";}
    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            mutation = null;
            view = new View(null, Snapshot.empty(), CONNECTING, 0);
            client.data.SystemFriend.getInstance().replaceServerFriends(view.snapshot(), null);
            worker.shutdownNow();
        }
        http.shutdownNow();
    }
}
