package net.aero.visuals.cosmetic;

import com.google.gson.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.user.AeroUserService;
import net.aero.visuals.guard.AeroGuard;
import net.aero.visuals.guard.HardwareId;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated website loadouts and public player presence; IO never runs on the game thread. */
public final class CosmeticSyncService implements AutoCloseable {
    private static final Map<String,String> CATEGORY=Map.of("cape","ПЛАЩИ","wings","КРЫЛЬЯ","bodywear","РЮКЗАКИ","hat","ГОЛОВНЫЕ_УБОРЫ","pet","ПИТОМЦЫ","graffiti","ГРАФФИТИ");
    private record Snapshot(UUID uuid,String name,String server,Map<String,Integer> equipped,long context){}
    private record Reply(int status,JsonObject root,long epoch){}
    private record RequestScope(WebsiteAccess owner, Snapshot player) {}
    private record LoadoutRequest(Snapshot player, WebsiteAccess session, Map<String,Integer> baseline, String revision) {
        RequestScope scope() { return new RequestScope(session, player); }
    }
    private record PresenceRegistration(Snapshot player, WebsiteAccess owner) {}
    private final ClientRuntime runtime;
    private volatile HttpClient http=newHttpClient(),rosterHttp=newHttpClient(),presenceHttp=newHttpClient(),deviceHttp=newHttpClient();
    private volatile CompletableFuture<HttpResponse<String>> accountRead, rosterRead;
    private static HttpClient newHttpClient(){return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();}
    private final Path sessionFile=FabricLoader.getInstance().getConfigDir().resolve("aerovisuals-free/aerovisuals-web-session.json");
    private final URI base;
    private final WebsiteSessionStore sessions;
    private final LauncherSessionStore launcherSessions;
    private final ScheduledExecutorService worker=Executors.newScheduledThreadPool(4,task->{var t=new Thread(task,"Aero-Website-Cosmetics");t.setDaemon(true);return t;});
    private volatile Snapshot snapshot=new Snapshot(null,"","",Map.of(),0);
    private volatile boolean closed,applying,loginRequested;
    private volatile String status="Сайт: войти",rosterProblem="";
    private volatile long nextWarning;
    private volatile long rosterAt;
    private volatile int rosterCount;
    private String rosterSignature="";
    private boolean staleCosmetics;
    private boolean watchSupported=true;
    private String rosterRevision="";
    private final Set<String> rejectedTokens=ConcurrentHashMap.newKeySet();
    private volatile String token="";
    private String webSession="",revision="";
    private long webExpires,nextWebPoll,nextPush;
    private volatile long nextPull;
    private volatile long nextHeartbeat;
    private volatile long nextDeviceCheck;
    private volatile boolean forceLogin;
    private volatile boolean deviceVerified,deviceChecking;
    private String launcherHwid="";
    private volatile long localChangeAt;
    private volatile boolean refreshRequested;
    private volatile long nextRoster;
    private volatile long sessionEpoch,applyTicket;
    private volatile Map<String,Integer> acknowledged;
    private UUID loadoutOwner;
    private volatile PresenceRegistration advertised;
    private long context;
    private Object world;
    private Snapshot rosterContext;

    public CosmeticSyncService(ClientRuntime runtime){
        this.runtime=runtime;
        String configured=System.getProperty("aero.apiBaseUrl",System.getenv("AERO_API_BASE_URL"));
        if(configured==null||configured.isBlank())configured=System.getProperty("lunacy.apiBaseUrl",System.getenv("LUNACY_API_BASE_URL"));
        base=validateBase(configured==null||configured.isBlank()?"https://aeroproject.space":configured);
        sessions=new WebsiteSessionStore(base,sessionFile);
        launcherSessions=new LauncherSessionStore(base);
    }
    private static URI validateBase(String value){
        URI uri=URI.create(value.replaceAll("/+$","")+"/");
        boolean local=Set.of("localhost","127.0.0.1","::1","[::1]").contains(String.valueOf(uri.getHost()).toLowerCase(Locale.ROOT));
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!("https".equals(uri.getScheme())||"http".equals(uri.getScheme())&&local))throw new IllegalArgumentException("Cosmetics API requires HTTPS (HTTP is allowed on localhost)");
        return uri;
    }
    public void start(){worker.scheduleWithFixedDelay(this::cycle,0,25,TimeUnit.MILLISECONDS);worker.scheduleWithFixedDelay(this::updateRoster,0,50,TimeUnit.MILLISECONDS);worker.scheduleWithFixedDelay(this::updatePresence,0,50,TimeUnit.MILLISECONDS);worker.execute(this::checkStartupAccess);}
    private void checkStartupAccess(){
        synchronized(this){if(closed||deviceChecking)return;deviceChecking=true;deviceVerified=false;}
        try {
            rejectedTokens.clear();
            while (!closed && !deviceVerified) {
                readSession();
                if (token.isBlank()) break;
                String attempted = token;
                nextDeviceCheck = 0;
                verifyDevice();
                if (!rejectedTokens.contains(attempted)) break;
            }
        }
        finally{synchronized(this){deviceChecking=false;}}
    }
    public record WebsiteAccess(URI base,String token,long generation){}
    public synchronized WebsiteAccess friendAccess(){return new WebsiteAccess(base,deviceVerified?token:"",sessionEpoch);}
    public String status(){return status+" · "+(rosterProblem.isEmpty()?"Игроки: "+rosterCount:rosterProblem);}
    public void connect(){retryDevice();}
    public void loginAgain(){openLauncherPage();}
    public void openLauncherPage(){MinecraftClient.getInstance().execute(()->Util.getOperatingSystem().open(URI.create("https://aeroproject.space/visual")));}
    public synchronized void retryDevice(){if(closed||deviceChecking)return;worker.execute(this::checkStartupAccess);}
    private void verifyDevice(){
        if(closed||deviceVerified||token.isBlank())return;
        long epoch=sessionEpoch;
        try{
            String detected=HardwareId.get();
            if(!launcherHwid.isBlank()&&!launcherHwid.equalsIgnoreCase(detected)){
                rejectToken(token);status="HWID файла лаунчера не совпадает с ПК. Повторите вход в Aero Launcher.";return;
            }
            Reply reply=request("api/auth/me",null,true);
            synchronized(this){
                if(closed||epoch!=sessionEpoch||reply.epoch!=epoch)return;
                if(reply.status==200&&reply.root.has("success")&&reply.root.get("success").getAsBoolean()&&reply.root.has("data")&&reply.root.get("data").isJsonObject()){
                    deviceVerified=true;nextPull=nextHeartbeat=nextRoster=0;status="AeroVisuals Free · аккаунт подключён";
                }else{rejectToken(token);status="Войдите в Aero Launcher для синхронизации";}
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(Exception|LinkageError failure){status="Free работает локально · повторите подключение к Aero ID";}
    }

    /** Client-thread snapshot: network callbacks never inspect entities or modify settings directly. */
    public synchronized void tick(MinecraftClient client){
        UUID uuid=client.player==null?null:client.player.getUuid();
        String name=client.player==null?"":client.player.getName().getString();
        String address=serverAddress(client,uuid);
        Map<String,Integer> equipped=CosmeticLoadout.capture();Snapshot old=snapshot;
        boolean contextChanged=world!=client.world||!Objects.equals(old.uuid,uuid)||!old.server.equals(address);
        if(contextChanged){
            world=client.world;context++;rosterAt=0;rosterCount=0;rosterSignature="";rosterProblem="";staleCosmetics=false;AeroUserService.replacePresence(Map.of());
            nextPull=nextPush=0;
            if (!Objects.equals(old.uuid, uuid) || !old.server.equals(address)) nextHeartbeat = 0;
            applyTicket++;
            applying=false;
            var watching = rosterRead;
            if (watching != null) watching.cancel(true);
        }
        if(old.context!=context||!old.name.equals(name)||!old.equipped.equals(equipped)){
            localChangeAt=System.nanoTime();snapshot=new Snapshot(uuid,name,address,equipped,context);
        }
        var baseline=acknowledged;var pending=accountRead;
        if(pending!=null&&(contextChanged||baseline!=null&&!equipped.equals(baseline)))pending.cancel(true);
        if(rosterAt!=0&&!staleCosmetics&&System.nanoTime()-rosterAt>10_000_000_000L){
            // Keep the online badge TTL, but never render a stale loadout for 45 seconds.
            staleCosmetics=true;AeroUserService.clearCosmetics();
        }
        if(rosterAt!=0&&System.nanoTime()-rosterAt>45_000_000_000L){rosterAt=0;rosterCount=0;AeroUserService.replacePresence(Map.of());}
    }
    private static String serverAddress(MinecraftClient client, UUID uuid) {
        if (uuid == null) return "";
        var server = client.getCurrentServerEntry();
        return server == null ? "singleplayer/" + uuid : normalizeServer(server.address);
    }
    private boolean matchesClient(Snapshot player, MinecraftClient client) {
        return client.world == world && client.player != null && player.uuid.equals(client.player.getUuid())
                && player.server.equals(serverAddress(client, player.uuid));
    }
    private static String normalizeServer(String server){String s=server.trim().toLowerCase(Locale.ROOT);return s.endsWith(":25565")?s.substring(0,s.length()-6):s;}
    private void safely(CheckedTask task){safely(0,task);}
    private void safely(int channel,CheckedTask task){safely(channel,null,task);}
    private void safely(int channel,RequestScope scope,CheckedTask task){
        try{task.run();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(CancellationException superseded){/* A local edit supersedes a background read. */}
        catch (Exception error) {
            String message = error.getMessage();
            String detail = message != null && message.matches("Website HTTP [0-9]{3}") ? message.substring(8) : "нет связи";
            synchronized (this) {
                if (!currentRequest(scope)) return;
                if (channel == 1) rosterProblem = "игроки: " + detail;
                else if (channel == 0 && !token.isBlank()) status = "Сайт: " + detail;
                long now = System.nanoTime();
                if (nextWarning != 0 && now - nextWarning < 0) return;
                nextWarning = now + 30_000_000_000L;
            }
            AeroVisuals.LOGGER.warn("Website cosmetics {} at {} unavailable ({}, {}); retrying in background",
                    channel==1?"presence":channel==2?"heartbeat":"account",base.getHost(),error.getClass().getSimpleName(),detail);
        }
    }
    @FunctionalInterface private interface CheckedTask{void run()throws Exception;}
    private void cycle(){
        if(closed)return;
        long now=System.nanoTime();
        safely(()->{
            if(loginRequested){loginRequested=false;if(forceLogin){forceLogin=false;if(webSession.isBlank())startWebLogin();}else if(!token.isBlank())nextPull=0;else if(webSession.isBlank())startWebLogin();}
            if(!webSession.isBlank()&&now>=nextWebPoll){nextWebPoll=now+1_000_000_000L;pollWebLogin();}
        });
        LoadoutRequest loadout;
        boolean save;
        synchronized (this) {
            if (closed || !AeroGuard.allowed()) return;
            Snapshot s = snapshot;
            if (!Objects.equals(loadoutOwner, s.uuid)) {
                loadoutOwner=s.uuid;acknowledged=null;revision="";nextPull=nextPush=0;applyTicket++;applying=false;refreshRequested=false;
            }
            if (s.uuid == null || token.isBlank() || applying) return;
            if(acknowledged!=null&&!s.equipped.equals(acknowledged)){
                // Coalesce a short burst, but never wait for the next periodic pull.
                if ((nextPush != 0 && now - nextPush < 0) || now - localChangeAt < 40_000_000L) return;
                nextPush = now + 500_000_000L;
                status = "Сайт: сохранение…";
                save = true;
            } else {
                if (!refreshRequested && nextPull != 0 && now - nextPull < 0) return;
                refreshRequested = false;
                nextPull = now + 5_000_000_000L;
                save = false;
            }
            loadout = new LoadoutRequest(s, friendAccess(), acknowledged, revision);
        }
        safely(0, loadout.scope(), () -> {
            if (!currentLoadout(loadout)) return;
            if (save) {
                push(loadout);
            } else {
                pull(loadout);
                synchronized (this) {
                    if (currentLoadout(loadout)) nextPull = System.nanoTime() + 5_000_000_000L;
                }
            }
        });
    }
    /** Presence cannot queue a local edit behind a slow heartbeat or leave request. */
    private void updatePresence() {
        PresenceRegistration current;
        PresenceRegistration departed = null;
        synchronized (this) {
            if (closed) return;
            Snapshot player = snapshot;
            WebsiteAccess owner = friendAccess();
            current = player.uuid != null && !owner.token().isBlank() && AeroGuard.allowed()
                    ? new PresenceRegistration(player, owner) : null;
            PresenceRegistration old = advertised;
            if (old != null && !samePresence(old, current)) {
                departed = old;
                nextHeartbeat = 0;
            }
        }
        if (departed != null) {
            PresenceRegistration old = departed;
            safely(2, () -> leave(old));
            synchronized (this) {
                if (advertised == old) advertised = null;
            }
        }
        if (current == null) return;
        var scope = new RequestScope(current.owner(), current.player());
        synchronized (this) {
            if (!currentRequest(scope)) return;
            long now = System.nanoTime();
            if (nextHeartbeat != 0 && now - nextHeartbeat < 0) return;
            nextHeartbeat = now + 5_000_000_000L;
        }
        safely(2, scope, () -> heartbeat(current));
    }

    private static boolean samePresence(PresenceRegistration old, PresenceRegistration current) {
        return current != null && old.owner().equals(current.owner())
                && old.player().uuid.equals(current.player().uuid) && old.player().server.equals(current.player().server);
    }
    /** Public presence must keep refreshing even while the personal API or login is failing. */
    private void updateRoster(){
        if(closed||!AeroGuard.allowed())return;Snapshot s=snapshot;if(s.uuid==null){rosterContext=null;return;}
        if(rosterContext==null||rosterContext.context!=s.context){rosterContext=s;nextRoster=0;rosterRevision="";}
        long now=System.nanoTime();if(nextRoster!=0&&now-nextRoster<0)return;
        var scope = new RequestScope(null, s);
        safely(1, scope, () -> {
            try {
                roster(s, scope);
                if (currentRequest(scope)) nextRoster = watchSupported ? 0 : System.nanoTime() + 1_000_000_000L;
            } catch (Exception failure) {
                if (!(failure instanceof CancellationException) && currentRequest(scope)) {
                    nextRoster = System.nanoTime() + 2_000_000_000L;
                }
                throw failure;
            }
        });
    }
    private Reply request(String path,JsonObject body,boolean authenticated)throws Exception{
        return request(path,body,authenticated,null);
    }
    private Reply request(String path,JsonObject body,boolean authenticated,RequestScope scope)throws Exception{
        if (!currentRequest(scope)) throw new CancellationException();
        WebsiteAccess owner = scope == null || scope.owner() == null ? friendAccess() : scope.owner();
        boolean watch=path.startsWith("api/presence/watch?"),publicRoster=watch||path.startsWith("api/presence/online?");
        boolean presence=path.equals("api/presence/heartbeat")||path.equals("api/presence/leave"),read=authenticated&&body==null&&!presence;
        boolean device=path.equals("api/client/verify-hwid");long requestEpoch=owner.generation();
        long timeout = path.equals("api/presence/leave") ? 2 : watch ? 12 : publicRoster ? 6 : 15;
        var b=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(timeout)).header("Accept","application/json").header("Cache-Control","no-cache");
        String requestToken=owner.token();if(authenticated&&!requestToken.isBlank())b.header("Authorization","Bearer "+requestToken);
        if(authenticated&&!HardwareId.cached().isBlank())b.header("X-HWID",HardwareId.cached());
        if(body==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8));
        HttpRequest request=b.build();HttpClient connection=device?deviceHttp:publicRoster?rosterHttp:presence?presenceHttp:http;
        HttpResponse<String> response;
        try{response=exchange(connection,request,read,publicRoster,scope);}
        catch(java.io.IOException failure){
            if (!currentRequest(scope)) throw new CancellationException();
            HttpClient replacement=newHttpClient();
            if(device)deviceHttp=replacement;else if(publicRoster)rosterHttp=replacement;else if(presence)presenceHttp=replacement;else http=replacement;
            connection.shutdownNow();
            // The proxy may keep an unusable pooled socket after a timeout/restart.
            // Retry only reads. POST may already have committed on the server.
            if(body!=null)throw failure;
            response=exchange(replacement,request,read,publicRoster,scope);
        }
        if (!currentRequest(scope)) throw new CancellationException();
        JsonObject root=new JsonObject();try{root=JsonParser.parseString(response.body()).getAsJsonObject();}catch(RuntimeException ignored){}
        if(device&&response.statusCode()==403)return new Reply(403,root,requestEpoch);
        if(authenticated&&(response.statusCode()==401||response.statusCode()==403)){
            if (!presence) {
                synchronized (this) {
                    if (requestEpoch == sessionEpoch && currentRequest(scope)) rejectToken(requestToken);
                }
            }
            throw new IllegalStateException("Website session expired");
        }
        if(response.statusCode()/100!=2&&response.statusCode()!=404&&response.statusCode()!=409)throw new java.io.IOException("Website HTTP "+response.statusCode());
        return new Reply(response.statusCode(),root,requestEpoch);
    }
    private HttpResponse<String> exchange(HttpClient connection,HttpRequest request,boolean account,boolean roster,
                                          RequestScope scope)throws java.io.IOException,InterruptedException{
        if (!account && !roster) return connection.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var pending=connection.sendAsync(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        synchronized (this) {
            if (!currentRequest(scope)) {
                pending.cancel(true);
                throw new CancellationException();
            }
            if (roster) rosterRead = pending;
            else accountRead = pending;
        }
        try{return pending.get();}
        catch(ExecutionException failure){if(failure.getCause() instanceof java.io.IOException io)throw io;throw new java.io.IOException("Website read failed",failure.getCause());}
        finally {
            if (roster) {
                if (rosterRead == pending) rosterRead = null;
            } else if (accountRead == pending) accountRead = null;
        }
    }
    private static JsonObject data(Reply r){return r.root.has("data")&&r.root.get("data").isJsonObject()?r.root.getAsJsonObject("data"):new JsonObject();}
    private static JsonObject object(Map<String,Integer> values){JsonObject json=new JsonObject();values.forEach(json::addProperty);return json;}
    private synchronized boolean currentRequest(RequestScope scope) {
        if (closed) return false;
        if (scope == null || scope.player() == null) return true;
        if (!AeroGuard.allowed() || snapshot.context != scope.player().context) return false;
        WebsiteAccess owner = scope.owner();
        return owner == null || owner.generation() == sessionEpoch && owner.token().equals(token);
    }

    private boolean currentLoadout(LoadoutRequest loadout) {
        return currentRequest(loadout.scope());
    }

    private synchronized void loadoutStatus(LoadoutRequest loadout, String message) {
        if (currentLoadout(loadout)) status = message;
    }

    private void pull(LoadoutRequest loadout) throws Exception {
        Snapshot player = loadout.player();
        Reply reply = request("api/client/cosmetics/equipped?uuid=" + player.uuid, null, true, loadout.scope());
        if (!currentLoadout(loadout) || reply.status != 200) return;
        JsonObject data = data(reply);
        if (!data.has("equipped") || !data.get("equipped").isJsonObject()) return;
        if (!scoped(data, player)) {
            loadoutStatus(loadout, "Сайт: обновить API");
            return;
        }
        var remote = CosmeticLoadout.decode(data.getAsJsonObject("equipped"));
        var desired = loadout.baseline() == null ? remote : CosmeticLoadout.merge(loadout.baseline(), player.equipped, remote);
        accept(loadout, remote, desired, responseRevision(data), false);
    }

    private void accept(LoadoutRequest loadout, Map<String,Integer> server, Map<String,Integer> desired,
                        String serverRevision, boolean saved) {
        long ticket;
        synchronized (this) {
            if (!currentLoadout(loadout)) return;
            applying = true;
            ticket = ++applyTicket;
        }
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            synchronized (this) {
                try {
                    Snapshot expected = loadout.player();
                    if (applyTicket != ticket || !currentLoadout(loadout) || !matchesClient(expected, client)) return;
                    var current = CosmeticLoadout.capture();
                    var merged = CosmeticLoadout.merge(expected.equipped, current, desired);
                    if (!current.equals(merged)) CosmeticLoadout.apply(merged);
                    if (saved || !Objects.equals(acknowledged, server)) nextHeartbeat = 0;
                    acknowledged = server;
                    revision = serverRevision;
                    status = "Сайт: подключён";
                    if (saved) nextPull = System.nanoTime() + 3_000_000_000L;
                    tick(client);
                } finally {
                    if (applyTicket == ticket) applying = false;
                }
            }
        });
        rememberConfirmedSession(loadout.session());
    }

    private void push(LoadoutRequest loadout) throws Exception {
        Snapshot player = loadout.player();
        JsonObject body = new JsonObject();
        body.addProperty("uuid", player.uuid.toString());
        body.addProperty("name", player.name);
        body.add("equipped", object(player.equipped));
        if (!loadout.revision().isBlank()) body.addProperty("baseRevision", loadout.revision());
        Reply reply = request("api/client/cosmetics/equipped", body, true, loadout.scope());
        if (!currentLoadout(loadout)) return;
        if (reply.status != 409 && reply.status / 100 != 2) {
            if (reply.status == 404) loadoutStatus(loadout, "Сайт: обновить API");
            return;
        }
        JsonObject data = data(reply);
        if (!scoped(data, player)) {
            loadoutStatus(loadout, "Сайт: обновить API");
            return;
        }
        if (reply.status == 409) {
            if (!data.has("equipped") || !data.get("equipped").isJsonObject()) return;
            var remote = CosmeticLoadout.decode(data.getAsJsonObject("equipped"));
            var desired = CosmeticLoadout.merge(loadout.baseline(), player.equipped, remote);
            accept(loadout, remote, desired, responseRevision(data), false);
        } else {
            var saved = data.has("equipped") ? CosmeticLoadout.decode(data.getAsJsonObject("equipped")) : player.equipped;
            accept(loadout, saved, saved, responseRevision(data), true);
        }
    }

    private static String responseRevision(JsonObject data) {
        return data.has("revision") && data.get("revision").isJsonPrimitive() ? data.get("revision").getAsString() : "";
    }

    private static boolean scoped(JsonObject data, Snapshot expected) {
        return expected.uuid != null && data.has("uuid") && data.get("uuid").isJsonPrimitive()
                && expected.uuid.toString().equalsIgnoreCase(data.get("uuid").getAsString());
    }
    private JsonObject identity(Snapshot s){JsonObject b=new JsonObject();b.addProperty("uuid",s.uuid.toString());b.addProperty("name",s.name);b.addProperty("server",s.server);return b;}
    private void heartbeat(PresenceRegistration registration)throws Exception{
        JsonObject b=identity(registration.player());b.addProperty("version",net.minecraft.SharedConstants.getGameVersion().id());b.addProperty("cosmeticsEnabled",true);
        Reply reply=request("api/presence/heartbeat",b,true,new RequestScope(registration.owner(),null));
        // An in-flight heartbeat can succeed after a switch. Keep its original owner for cleanup.
        if(reply.status/100==2) {
            synchronized (this) {
                if (!closed) advertised = registration;
            }
        }
        else if(reply.status==409)AeroVisuals.LOGGER.debug("Website presence identity is already registered by another account");
    }
    private void leave(PresenceRegistration registration)throws Exception {
        request("api/presence/leave", identity(registration.player()), true, new RequestScope(registration.owner(), null));
    }
    private void roster(Snapshot s, RequestScope scope)throws Exception{
        String query="server="+URLEncoder.encode(s.server,StandardCharsets.UTF_8);
        Reply r=request(watchSupported?"api/presence/watch?"+query+"&revision="+rosterRevision:"api/presence/online?"+query,null,false,scope);
        if(watchSupported&&r.status==404){watchSupported=false;r=request("api/presence/online?"+query,null,false,scope);}
        if(r.status!=200||!r.root.has("data")||!r.root.get("data").isJsonArray())throw new java.io.IOException("Invalid presence response");
        if(r.root.has("revision")){String value=r.root.get("revision").getAsString();if(value.matches("[a-f0-9]{64}"))rosterRevision=value;else watchSupported=false;}
        else watchSupported=false;
        Map<UUID,AeroUserService.PublicPlayer> players=new HashMap<>();
        for(JsonElement row:r.root.getAsJsonArray("data"))try{
            JsonObject e=row.getAsJsonObject();String raw=e.get("uuid").getAsString();if(raw.matches("[a-fA-F0-9]{32}"))raw=raw.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})","$1-$2-$3-$4-$5");
            UUID uuid=UUID.fromString(raw);Map<String,Integer> ids=e.has("cosmetics")&&e.get("cosmetics").isJsonObject()?CosmeticLoadout.decode(e.getAsJsonObject("cosmetics")):Map.of();
            players.put(uuid,new AeroUserService.PublicPlayer(e.get("name").getAsString(),ids));
        }catch(RuntimeException ignored){}
        String signature=players.entrySet().stream().map(e->e.getKey()+"/"+e.getValue().name()+"/"+new TreeMap<>(e.getValue().cosmetics())).sorted().collect(java.util.stream.Collectors.joining(", "));
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            synchronized (this) {
                if (!currentRequest(scope) || !matchesClient(s, client)) return;
                AeroUserService.replacePresence(players);
                rosterAt = System.nanoTime();
                rosterCount = players.size();
                rosterProblem = "";
                staleCosmetics = false;
                var own = players.get(s.uuid);
                var baseline = acknowledged;
                if (own != null && baseline != null && !own.cosmetics().equals(baseline)) refreshRequested = true;
                if (signature.equals(rosterSignature)) return;
                rosterSignature = signature;
            }
            AeroVisuals.LOGGER.info("Website presence on {}: {} players [{}]", s.server, players.size(), signature);
        });
    }
    private void startWebLogin()throws Exception{
        Reply r=request("api/auth/web-session/start",new JsonObject(),false);if(r.status!=200){status="Сайт: недоступен";return;}
        String id=r.root.get("sessionId").getAsString();if(!id.matches("[a-f0-9]{64}"))return;
        URI link=base.resolve(r.root.get("authUrl").getAsString());
        if(!Objects.equals(base.getHost(),link.getHost())||!Objects.equals(base.getScheme(),link.getScheme())||base.getPort()!=link.getPort())throw new IllegalArgumentException("Website login origin mismatch");
        webSession=id;webExpires=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);status="Подтверди на сайте";
        MinecraftClient.getInstance().execute(()->Util.getOperatingSystem().open(link));
    }
    private void pollWebLogin()throws Exception{
        if(System.nanoTime()>webExpires){webSession="";status="Сайт: подключить";return;}
        Reply r=request("api/auth/web-session/status?sessionId="+webSession,null,false);if(r.status!=200)return;
        String state=r.root.has("status")?r.root.get("status").getAsString():"";
        if(state.equals("cancelled")){webSession="";status="Сайт: подключить";return;}
        if(!state.equals("confirmed")||!r.root.has("token"))return;
        String value=r.root.get("token").getAsString();if(!value.matches("[a-f0-9]{64}"))return;
        setToken(value);webSession="";sessions.save(value);
    }
    private synchronized void setToken(String value){if(!value.equals(token)){token=value;deviceVerified=false;acknowledged=null;revision="";nextPull=nextHeartbeat=nextDeviceCheck=0;sessionEpoch++;applyTicket++;applying=false;if(!deviceVerified)AeroGuard.reset();status="Сайт: подключение…";var pending=accountRead;if(pending!=null)pending.cancel(true);}}
    private synchronized void rejectToken(String value){if(value.equals(token)){rejectedTokens.add(value);token="";deviceVerified=false;acknowledged=null;sessionEpoch++;applyTicket++;applying=false;if(!deviceVerified)AeroGuard.reset();status="Сайт: войти заново";}}
    private void rememberConfirmedSession(WebsiteAccess owner){if(launcherSessions.managed()||!owner.equals(friendAccess()))return;try{sessions.publish(owner.token());}catch(java.io.IOException error){AeroVisuals.LOGGER.debug("Could not save shared website session ({})",error.getClass().getSimpleName());}}
    private void readSession(){
        var launcher=launcherSessions.read(rejectedTokens);
        launcherHwid=launcher.hwid();
        String value=launcher.token();
        if(value.isBlank()){setToken("");status="Free · войдите в Aero Launcher для друзей и синхронизации";}
        else if(rejectedTokens.contains(value)){setToken("");AeroGuard.denied("Сессия лаунчера истекла. Повторите вход в Aero Launcher.");}
        else setToken(value);
    }
    @Override public void close(){
        PresenceRegistration old;
        synchronized (this) {
            if (closed) return;
            closed = true;
            sessionEpoch++;
            applyTicket++;
            old = advertised;
            advertised = null;
        }
        if(old!=null){
            var request=HttpRequest.newBuilder(base.resolve("api/presence/leave")).timeout(Duration.ofSeconds(2))
                    .header("Authorization","Bearer "+old.owner().token()).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(identity(old.player()).toString(),StandardCharsets.UTF_8));
            if (!HardwareId.cached().isBlank()) request.header("X-HWID", HardwareId.cached());
            http.sendAsync(request.build(),HttpResponse.BodyHandlers.discarding()).exceptionally(error->null);
        }
        var pending=accountRead;if(pending!=null)pending.cancel(true);
        var watching=rosterRead;if(watching!=null)watching.cancel(true);
        worker.shutdownNow();rosterHttp.shutdownNow();presenceHttp.shutdownNow();deviceHttp.shutdownNow();http.shutdown();HardwareId.close();AeroGuard.reset();AeroUserService.replacePresence(Map.of());
    }
}
