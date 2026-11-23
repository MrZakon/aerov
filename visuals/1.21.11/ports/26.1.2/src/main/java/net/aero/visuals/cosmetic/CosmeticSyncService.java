package net.aero.visuals.cosmetic;

import com.google.gson.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.user.AeroUserService;
import net.aero.visuals.guard.AeroGuard;
import net.aero.visuals.guard.HardwareId;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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
    private final ClientRuntime runtime;
    private volatile HttpClient http=newHttpClient(),rosterHttp=newHttpClient(),presenceHttp=newHttpClient(),deviceHttp=newHttpClient();
    private volatile CompletableFuture<HttpResponse<String>> accountRead;
    private static HttpClient newHttpClient(){return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();}
    private final Path sessionFile=FabricLoader.getInstance().getConfigDir().resolve("aerovisuals-web-session.json");
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
    private volatile Snapshot advertised;
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
        synchronized(this){if(closed||deviceVerified||deviceChecking)return;deviceChecking=true;}
        try{launcherSessions.rescan();rejectedTokens.clear();readSession();nextDeviceCheck=0;verifyDevice();}
        finally{synchronized(this){deviceChecking=false;}}
    }
    public record WebsiteAccess(URI base,String token,long generation){}
    public synchronized WebsiteAccess friendAccess(){return new WebsiteAccess(base,token,sessionEpoch);}
    public String status(){return !AeroGuard.allowed()?AeroGuard.message():status+" · "+(rosterProblem.isEmpty()?"Игроки: "+rosterCount:rosterProblem);}
    public void connect(){retryDevice();}
    public void loginAgain(){openLauncherPage();}
    public void openLauncherPage(){Minecraft.getInstance().execute(()->Util.getPlatform().openUri(URI.create("https://aeroproject.space/visual")));}
    public synchronized void retryDevice(){if(closed||deviceVerified||deviceChecking)return;worker.execute(this::checkStartupAccess);}
    private void verifyDevice(){
        if(closed||deviceVerified||token.isBlank())return;long now=System.nanoTime();if(now<nextDeviceCheck)return;
        nextDeviceCheck=now+30_000_000_000L;long epoch=sessionEpoch;
        try{
            AeroGuard.checking();String detectedHwid=HardwareId.get();
            if(!launcherHwid.isBlank()&&!launcherHwid.equalsIgnoreCase(detectedHwid)){AeroGuard.denied("HWID в файле лаунчера не совпадает с этим компьютером. Повторите вход в Aero Launcher.");return;}
            Reply result=request("api/client/verify-hwid",new JsonObject(),true);
            synchronized(this){
                if(closed||epoch!=sessionEpoch||result.epoch!=epoch)return;
                if(result.status==403){String code=result.root.has("code")?result.root.get("code").getAsString():"";AeroGuard.denied(code.equals("SUBSCRIPTION_REQUIRED")?"Нет активной подписки AeroVisuals. Оформите или продлите её на сайте.":"HWID не совпадает с привязкой аккаунта. Требуется сброс на сайте.");return;}
                JsonObject d=data(result);
                if(result.status==200&&result.root.has("success")&&result.root.get("success").getAsBoolean()&&d.has("hwidOk")&&d.get("hwidOk").getAsBoolean()){
                    boolean subscribed=d.has("hasSubscription")&&d.get("hasSubscription").getAsBoolean();
                    if(!subscribed){AeroGuard.denied("Нет активной подписки AeroVisuals. Оформите или продлите её на сайте.");return;}
                    long leaseMillis=120_000L;
                    if(d.has("endsAt")&&!d.get("endsAt").isJsonNull()){
                        try{leaseMillis=Math.min(leaseMillis,java.time.Instant.parse(d.get("endsAt").getAsString()).toEpochMilli()-System.currentTimeMillis());}
                        catch(RuntimeException invalid){AeroGuard.denied("Сайт не подтвердил срок действия подписки.");return;}
                    }
                    if(leaseMillis<=0){AeroGuard.denied("Подписка AeroVisuals истекла. Продлите её на сайте.");return;}
                    deviceVerified=true;AeroGuard.verified();nextDeviceCheck=Long.MAX_VALUE;nextPull=nextHeartbeat=nextRoster=0;
                }else AeroGuard.unavailable();
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(Exception|LinkageError failure){synchronized(this){if(!closed&&epoch==sessionEpoch&&!token.isBlank())AeroGuard.unavailable();}}
    }

    /** Client-thread snapshot: network callbacks never inspect entities or modify settings directly. */
    public void tick(Minecraft client){
        UUID uuid=client.player==null?null:client.player.getUUID();
        String name=client.player==null?"":client.player.getName().getString();
        var server=client.getCurrentServer();
        String address=uuid==null?"":server==null?"singleplayer/"+uuid:normalizeServer(server.ip);
        Map<String,Integer> equipped=CosmeticLoadout.capture();Snapshot old=snapshot;
        if(world!=client.level||!Objects.equals(old.uuid,uuid)||!old.server.equals(address)){
            world=client.level;context++;rosterAt=0;rosterCount=0;rosterSignature="";staleCosmetics=false;AeroUserService.replacePresence(Map.of());
        }
        if(old.context!=context||!old.name.equals(name)||!old.equipped.equals(equipped)){
            localChangeAt=System.nanoTime();snapshot=new Snapshot(uuid,name,address,equipped,context);
        }
        var baseline=acknowledged;var pending=accountRead;
        if(pending!=null&&(!Objects.equals(old.uuid,uuid)||baseline!=null&&!equipped.equals(baseline)))pending.cancel(true);
        if(rosterAt!=0&&!staleCosmetics&&System.nanoTime()-rosterAt>10_000_000_000L){
            // Keep the online badge TTL, but never render a stale loadout for 45 seconds.
            staleCosmetics=true;AeroUserService.clearCosmetics();
        }
        if(rosterAt!=0&&System.nanoTime()-rosterAt>45_000_000_000L){rosterAt=0;rosterCount=0;AeroUserService.replacePresence(Map.of());}
    }
    private static String normalizeServer(String server){String s=server.trim().toLowerCase(Locale.ROOT);return s.endsWith(":25565")?s.substring(0,s.length()-6):s;}
    private void safely(CheckedTask task){safely(0,task);}
    private void safely(boolean roster,CheckedTask task){safely(roster?1:0,task);}
    private void safely(int channel,CheckedTask task){
        try{task.run();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(CancellationException superseded){/* A local edit supersedes a background read. */}
        catch(Exception error){if(!closed){
            String detail=error.getMessage()!=null&&error.getMessage().matches("Website HTTP [0-9]{3}")?error.getMessage().substring(8):"нет связи";
            if(channel==1)rosterProblem="игроки: "+detail;else if(channel==0&&!token.isBlank())status="Сайт: "+detail;
            long now=System.nanoTime();if(now>=nextWarning){nextWarning=now+30_000_000_000L;
                AeroVisuals.LOGGER.warn("Website cosmetics {} at {} unavailable ({}, {}); retrying in background",channel==1?"presence":channel==2?"heartbeat":"account",base.getHost(),error.getClass().getSimpleName(),detail);}
        }}
    }
    @FunctionalInterface private interface CheckedTask{void run()throws Exception;}
    private void cycle(){
        if(closed)return;
        long now=System.nanoTime();
        safely(()->{
            if(loginRequested){loginRequested=false;if(forceLogin){forceLogin=false;if(webSession.isBlank())startWebLogin();}else if(!token.isBlank())nextPull=0;else if(webSession.isBlank())startWebLogin();}
            if(!webSession.isBlank()&&now>=nextWebPoll){nextWebPoll=now+1_000_000_000L;pollWebLogin();}
        });
        if(!AeroGuard.allowed())return;
        Snapshot s=snapshot;
        if(!Objects.equals(loadoutOwner,s.uuid)){
            loadoutOwner=s.uuid;acknowledged=null;revision="";nextPull=nextPush=0;applyTicket++;applying=false;refreshRequested=false;
        }
        if(s.uuid==null)return;
        if(!token.isBlank()&&!applying)safely(()->{
            if(acknowledged!=null&&!s.equipped.equals(acknowledged)){
                // Coalesce a short burst, but never wait for the next periodic pull.
                if(now>=nextPush&&now-localChangeAt>=40_000_000L){
                    nextPush=now+500_000_000L;status="Сайт: сохранение…";push(s);nextHeartbeat=0;
                }
            }else if(refreshRequested||now>=nextPull){refreshRequested=false;nextPull=now+5_000_000_000L;pull(s);nextPull=System.nanoTime()+5_000_000_000L;}
        });
    }
    /** Presence cannot queue a local edit behind a slow heartbeat or leave request. */
    private void updatePresence(){
        if(closed||token.isBlank()||!AeroGuard.allowed())return;Snapshot current=snapshot;long now=System.nanoTime();
        safely(2,()->{
            if(advertised!=null&&(!Objects.equals(current.uuid,advertised.uuid)||!current.server.equals(advertised.server))){
                Snapshot old=advertised;advertised=null;nextHeartbeat=0;leave(old);
            }
            if(current.uuid!=null&&now>=nextHeartbeat){nextHeartbeat=now+5_000_000_000L;heartbeat(current);}
        });
    }
    /** Public presence must keep refreshing even while the personal API or login is failing. */
    private void updateRoster(){
        if(closed||!AeroGuard.allowed())return;Snapshot s=snapshot;if(s.uuid==null){rosterContext=null;return;}
        if(rosterContext==null||rosterContext.context!=s.context){rosterContext=s;nextRoster=0;rosterRevision="";}
        long now=System.nanoTime();if(now<nextRoster)return;
        safely(true,()->{
            try{roster(s);nextRoster=watchSupported?0:System.nanoTime()+1_000_000_000L;}
            catch(Exception failure){nextRoster=System.nanoTime()+2_000_000_000L;throw failure;}
        });
    }
    private Reply request(String path,JsonObject body,boolean authenticated)throws Exception{
        boolean watch=path.startsWith("api/presence/watch?"),publicRoster=watch||path.startsWith("api/presence/online?");
        boolean presence=path.equals("api/presence/heartbeat")||path.equals("api/presence/leave"),read=authenticated&&body==null&&!presence;
        boolean device=path.equals("api/client/verify-hwid");long requestEpoch=sessionEpoch;
        var b=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(watch?12:publicRoster?6:15)).header("Accept","application/json").header("Cache-Control","no-cache");
        String requestToken=token;if(authenticated&&!requestToken.isBlank())b.header("Authorization","Bearer "+requestToken);
        if(authenticated&&!HardwareId.cached().isBlank())b.header("X-HWID",HardwareId.cached());
        if(body==null)b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8));
        HttpRequest request=b.build();HttpClient connection=device?deviceHttp:publicRoster?rosterHttp:presence?presenceHttp:http;
        HttpResponse<String> response;
        try{response=exchange(connection,request,read);}
        catch(java.io.IOException failure){
            HttpClient replacement=newHttpClient();
            if(device)deviceHttp=replacement;else if(publicRoster)rosterHttp=replacement;else if(presence)presenceHttp=replacement;else http=replacement;
            connection.shutdownNow();
            // The proxy may keep an unusable pooled socket after a timeout/restart.
            // Retry only reads. POST may already have committed on the server.
            if(body!=null)throw failure;
            response=exchange(replacement,request,read);
        }
        JsonObject root=new JsonObject();try{root=JsonParser.parseString(response.body()).getAsJsonObject();}catch(RuntimeException ignored){}
        if(device&&response.statusCode()==403)return new Reply(403,root,requestEpoch);
        if(authenticated&&(response.statusCode()==401||response.statusCode()==403)){
            if(!presence)rejectToken(requestToken);
            throw new IllegalStateException("Website session expired");
        }
        if(response.statusCode()/100!=2&&response.statusCode()!=404&&response.statusCode()!=409)throw new java.io.IOException("Website HTTP "+response.statusCode());
        return new Reply(response.statusCode(),root,requestEpoch);
    }
    private HttpResponse<String> exchange(HttpClient connection,HttpRequest request,boolean interruptible)throws java.io.IOException,InterruptedException{
        if(!interruptible)return connection.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var pending=connection.sendAsync(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));accountRead=pending;
        try{return pending.get();}
        catch(ExecutionException failure){if(failure.getCause() instanceof java.io.IOException io)throw io;throw new java.io.IOException("Website read failed",failure.getCause());}
        finally{if(accountRead==pending)accountRead=null;}
    }
    private static JsonObject data(Reply r){return r.root.has("data")&&r.root.get("data").isJsonObject()?r.root.getAsJsonObject("data"):new JsonObject();}
    private static JsonObject object(Map<String,Integer> values){JsonObject json=new JsonObject();values.forEach(json::addProperty);return json;}
    private void pull(Snapshot s)throws Exception{
        Reply r=request("api/client/cosmetics/equipped?uuid="+s.uuid,null,true);
        if(r.epoch!=sessionEpoch)return;
        if(r.status!=200)return;JsonObject d=data(r);if(!d.has("equipped")||!d.get("equipped").isJsonObject())return;
        if(!scoped(d,s)){status="Сайт: обновить API";return;}
        Map<String,Integer> remote=CosmeticLoadout.decode(d.getAsJsonObject("equipped"));revision=d.has("revision")?d.get("revision").getAsString():"";
        accept(s,remote,acknowledged==null?remote:CosmeticLoadout.merge(acknowledged,s.equipped,remote));
    }
    private void accept(Snapshot expected,Map<String,Integer> server,Map<String,Integer> desired){
        if(!AeroGuard.allowed()||!Objects.equals(snapshot.uuid,expected.uuid))return;
        if(!Objects.equals(acknowledged,server))nextHeartbeat=0;
        acknowledged=server;status="Сайт: подключён";rememberConfirmedSession();if(expected.equipped.equals(desired))return;
        applying=true;long epoch=sessionEpoch,ticket=++applyTicket;
        Minecraft.getInstance().execute(()->{try{
            if(!closed&&AeroGuard.allowed()&&sessionEpoch==epoch&&applyTicket==ticket&&Objects.equals(snapshot.uuid,expected.uuid)){
                Map<String,Integer> current=CosmeticLoadout.capture();
                Map<String,Integer> merged=CosmeticLoadout.merge(expected.equipped,current,desired);
                if(!current.equals(merged))CosmeticLoadout.apply(merged);
                tick(Minecraft.getInstance());
            }
        }finally{if(applyTicket==ticket)applying=false;}});
    }
    private void push(Snapshot s)throws Exception{
        JsonObject body=new JsonObject();body.addProperty("uuid",s.uuid.toString());body.addProperty("name",s.name);body.add("equipped",object(s.equipped));if(!revision.isBlank())body.addProperty("baseRevision",revision);
        Reply r=request("api/client/cosmetics/equipped",body,true);
        if(r.epoch!=sessionEpoch)return;
        if(r.status==404){
            status="Сайт: обновить API";return;
        }
        if(r.status==409){JsonObject d=data(r);if(!scoped(d,s))return;revision=d.has("revision")?d.get("revision").getAsString():"";if(d.has("equipped")){var remote=CosmeticLoadout.decode(d.getAsJsonObject("equipped"));accept(s,remote,CosmeticLoadout.merge(acknowledged,s.equipped,remote));}return;}
        if(r.status/100!=2)return;JsonObject d=data(r);revision=d.has("revision")?d.get("revision").getAsString():"";
        if(!scoped(d,s)){status="Сайт: обновить API";return;}
        var saved=d.has("equipped")?CosmeticLoadout.decode(d.getAsJsonObject("equipped")):s.equipped;accept(s,saved,saved);nextPull=System.nanoTime()+3_000_000_000L;
    }
    private static boolean scoped(JsonObject data,Snapshot expected){return expected.uuid!=null&&data.has("uuid")&&expected.uuid.toString().equalsIgnoreCase(data.get("uuid").getAsString());}
    private JsonObject identity(Snapshot s){JsonObject b=new JsonObject();b.addProperty("uuid",s.uuid.toString());b.addProperty("name",s.name);b.addProperty("server",s.server);return b;}
    private void heartbeat(Snapshot s)throws Exception{
        JsonObject b=identity(s);b.addProperty("version",net.minecraft.SharedConstants.getCurrentVersion().id());b.addProperty("cosmeticsEnabled",true);
        Reply reply=request("api/presence/heartbeat",b,true);
        if(reply.status/100==2)advertised=s;
        else if(reply.status==409)AeroVisuals.LOGGER.debug("Website presence identity is already registered by another account");
    }
    private void leave(Snapshot s)throws Exception{if(!token.isBlank())request("api/presence/leave",identity(s),true);}
    private void roster(Snapshot s)throws Exception{
        String query="server="+URLEncoder.encode(s.server,StandardCharsets.UTF_8);
        Reply r=request(watchSupported?"api/presence/watch?"+query+"&revision="+rosterRevision:"api/presence/online?"+query,null,false);
        if(watchSupported&&r.status==404){watchSupported=false;r=request("api/presence/online?"+query,null,false);}
        if(r.status!=200||!r.root.has("data")||!r.root.get("data").isJsonArray())throw new java.io.IOException("Invalid presence response");
        if(r.root.has("revision")){String value=r.root.get("revision").getAsString();if(value.matches("[a-f0-9]{64}"))rosterRevision=value;else watchSupported=false;}
        else watchSupported=false;
        Map<UUID,AeroUserService.PublicPlayer> players=new HashMap<>();
        for(JsonElement row:r.root.getAsJsonArray("data"))try{
            JsonObject e=row.getAsJsonObject();String raw=e.get("uuid").getAsString();if(raw.matches("[a-fA-F0-9]{32}"))raw=raw.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})","$1-$2-$3-$4-$5");
            UUID uuid=UUID.fromString(raw);Map<String,Integer> ids=e.has("cosmetics")&&e.get("cosmetics").isJsonObject()?CosmeticLoadout.decode(e.getAsJsonObject("cosmetics")):Map.of();
            players.put(uuid,new AeroUserService.PublicPlayer(e.get("name").getAsString(),ids));
        }catch(RuntimeException ignored){}
        Minecraft.getInstance().execute(()->{if(!closed&&snapshot.context==s.context){
            AeroUserService.replacePresence(players);rosterAt=System.nanoTime();rosterCount=players.size();rosterProblem="";staleCosmetics=false;
            var own=players.get(s.uuid);var baseline=acknowledged;
            if(own!=null&&baseline!=null&&!own.cosmetics().equals(baseline))refreshRequested=true;
            String signature=players.entrySet().stream().map(e->e.getKey()+"/"+e.getValue().name()+"/"+new TreeMap<>(e.getValue().cosmetics())).sorted().collect(java.util.stream.Collectors.joining(", "));
            if(!signature.equals(rosterSignature)){rosterSignature=signature;
                AeroVisuals.LOGGER.info("Website presence on {}: {} players [{}]",s.server,players.size(),signature);}
        }});
    }
    private void startWebLogin()throws Exception{
        Reply r=request("api/auth/web-session/start",new JsonObject(),false);if(r.status!=200){status="Сайт: недоступен";return;}
        String id=r.root.get("sessionId").getAsString();if(!id.matches("[a-f0-9]{64}"))return;
        URI link=base.resolve(r.root.get("authUrl").getAsString());
        if(!Objects.equals(base.getHost(),link.getHost())||!Objects.equals(base.getScheme(),link.getScheme())||base.getPort()!=link.getPort())throw new IllegalArgumentException("Website login origin mismatch");
        webSession=id;webExpires=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);status="Подтверди на сайте";
        Minecraft.getInstance().execute(()->Util.getPlatform().openUri(link));
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
    private synchronized void setToken(String value){if(!value.equals(token)){token=value;acknowledged=null;revision="";nextPull=nextHeartbeat=nextDeviceCheck=0;sessionEpoch++;applyTicket++;applying=false;if(!deviceVerified)AeroGuard.reset();status="Сайт: подключение…";var pending=accountRead;if(pending!=null)pending.cancel(true);}}
    private synchronized void rejectToken(String value){if(value.equals(token)){rejectedTokens.add(value);token="";acknowledged=null;sessionEpoch++;applyTicket++;applying=false;if(!deviceVerified)AeroGuard.reset();status="Сайт: войти заново";}}
    private void rememberConfirmedSession(){if(launcherSessions.managed())return;try{sessions.publish(token);}catch(java.io.IOException error){AeroVisuals.LOGGER.debug("Could not save shared website session ({})",error.getClass().getSimpleName());}}
    private void readSession(){
        var launcher=launcherSessions.read();
        launcherHwid=launcher.hwid();
        String value=launcher.token();
        if(value.isBlank()){setToken("");AeroGuard.denied(launcher.message());}
        else if(rejectedTokens.contains(value)){setToken("");AeroGuard.denied("Сессия лаунчера истекла. Повторите вход в Aero Launcher.");}
        else setToken(value);
    }
    @Override public void close(){
        closed=true;sessionEpoch++;applyTicket++;Snapshot s=advertised;
        if(s!=null&&!token.isBlank()){
            var request=HttpRequest.newBuilder(base.resolve("api/presence/leave")).timeout(Duration.ofSeconds(2)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(identity(s).toString())).build();
            http.sendAsync(request,HttpResponse.BodyHandlers.discarding()).exceptionally(error->null);
        }
        var pending=accountRead;if(pending!=null)pending.cancel(true);
        worker.shutdownNow();rosterHttp.shutdownNow();presenceHttp.shutdownNow();deviceHttp.shutdownNow();http.shutdown();HardwareId.close();AeroGuard.reset();AeroUserService.replacePresence(Map.of());
    }
}
