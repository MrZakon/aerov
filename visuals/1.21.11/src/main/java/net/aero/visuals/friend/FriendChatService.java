package net.aero.visuals.friend;

import com.google.gson.*;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.cosmetic.CosmeticSyncService;
import net.aero.visuals.guard.AeroGuard;
import net.aero.visuals.guard.HardwareId;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Only a bounded view is cached in memory. The server owns history and friendship authorization. */
public final class FriendChatService implements AutoCloseable {
    public record Message(String id,String clientId,String body,long createdAt,boolean own){}
    public record Snapshot(String friendId,String name,List<Message> messages,boolean hasOlder,boolean olderPage,String status,String sentId,long revision,long scrollRevision) {
        public Snapshot {messages=List.copyOf(messages);}
        static Snapshot empty(){return new Snapshot("","",List.of(),false,false,"","",0,0);}
    }
    private record Pending(String friendId,String body,String id,long generation,String token){}
    private final ClientRuntime runtime;
    private final ScheduledExecutorService workers=Executors.newScheduledThreadPool(2,r->{var t=new Thread(r,"Aero-Friend-Chat");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    // Conversation state and request ownership are guarded by this monitor.
    private Snapshot state = Snapshot.empty();
    private String token = "", serverCursor = "";
    private long generation = -1, lastTouch, nextPoll, epoch;
    private boolean closed, sending;
    private Future<?> readTask;
    private Pending pending;
    public FriendChatService(ClientRuntime runtime){this.runtime=runtime;}
    public void start(){workers.scheduleWithFixedDelay(this::cycle,0,250,TimeUnit.MILLISECONDS);}
    private CosmeticSyncService.WebsiteAccess access(){return runtime.cosmeticSync().friendAccess();}
    private boolean same(CosmeticSyncService.WebsiteAccess a){return !closed&&AeroGuard.allowed()&&a.generation()==generation&&a.token().equals(token);}
    public synchronized Snapshot snapshot() { return same(access()) ? state : Snapshot.empty(); }
    public synchronized boolean reading() { return readTask != null; }
    public synchronized boolean sending() { return sending; }
    public synchronized void touch() { lastTouch = System.nanoTime(); }

    public synchronized void open(FriendsService.Friend friend) {
        if (closed) return;
        invalidateRead();
        var a = access();
        generation = a.generation();
        token = a.token();
        serverCursor = "";
        state = new Snapshot(friend.id(), friend.name(), List.of(), false, false,
                "Загрузка переписки…", "", state.revision() + 1, 0);
        touch();
        nextPoll = 0;
    }

    public synchronized void leave() {
        invalidateRead();
        state = Snapshot.empty();
        lastTouch = 0;
        nextPoll = 0;
        serverCursor = "";
    }

    private void invalidateRead() {
        epoch++;
        if (readTask != null) {
            readTask.cancel(true);
            readTask = null;
        }
    }

    private void status(String message) {
        state = new Snapshot(state.friendId(), state.name(), state.messages(), state.hasOlder(),
                state.olderPage(), message, state.sentId(), state.revision(), state.scrollRevision());
    }

    private boolean current(CosmeticSyncService.WebsiteAccess a, String friend, long viewEpoch) {
        return viewEpoch == epoch && state.friendId().equals(friend) && same(a) && a.equals(access());
    }

    private synchronized void cycle() {
        if (closed) return;
        var a = access();
        if (!same(a)) {
            leave();
            generation = a.generation();
            token = a.token();
            return;
        }
        long now = System.nanoTime();
        if (token.isBlank() || state.friendId().isBlank() || state.olderPage()
                || now - lastTouch > 2_000_000_000L) return;
        if (nextPoll != 0 && now - nextPoll < 0) return;
        fetch(a, state.friendId(), serverCursor, "", false);
    }

    public synchronized void older() {
        var a = access();
        if (!same(a) || state.messages().isEmpty() || !state.hasOlder()) return;
        fetch(a, state.friendId(), "", state.messages().getFirst().id(), true);
    }

    public synchronized void latest() {
        var a = access();
        if (same(a) && !state.friendId().isBlank()) fetch(a, state.friendId(), "", "", true);
    }

    private void fetch(CosmeticSyncService.WebsiteAccess a, String friend, String after, String before, boolean pageRequest) {
        if (readTask != null) return;
        boolean append = !after.isEmpty();
        long viewEpoch = epoch;
        nextPoll = System.nanoTime() + 900_000_000L;
        readTask = workers.submit(() -> {
            try {
                synchronized (this) {
                    if (!current(a, friend, viewEpoch)) return;
                }
                String query = after.isBlank() ? (before.isBlank() ? "" : "?before=" + before) : "?after=" + after;
                var data = exchange(a, "GET", "api/client/friends/" + friend + "/messages" + query, null);
                var messages = new ArrayList<Message>();
                if (data.has("messages")) {
                    for (var e : data.getAsJsonArray("messages")) messages.add(message(e.getAsJsonObject()));
                }
                boolean hasMore = bool(data, "hasMore");
                synchronized (this) {
                    if (!current(a, friend, viewEpoch)) return;
                    var old = state;
                    var merged = new TreeMap<Long, Message>();
                    if (append) {
                        for (var m : old.messages()) merged.put(Long.parseLong(m.id()), m);
                    }
                    for (var m : messages) merged.put(Long.parseLong(m.id()), m);
                    if (!append && before.isBlank() && !old.olderPage()) {
                        long newest = messages.isEmpty() ? 0 : Long.parseLong(messages.getLast().id());
                        for (var m : old.messages()) {
                            if (m.own() && Long.parseLong(m.id()) > newest) merged.put(Long.parseLong(m.id()), m);
                        }
                    }
                    boolean older = append ? old.hasOlder() : bool(data, "hasOlder");
                    while (merged.size() > 200) {
                        merged.pollFirstEntry();
                        older = true;
                    }
                    if (before.isBlank() && !messages.isEmpty()) serverCursor = messages.getLast().id();
                    state = new Snapshot(friend, string(data, "name"), new ArrayList<>(merged.values()), older,
                            !before.isBlank(), "Переписка хранится на сервере", old.sentId(), old.revision() + 1,
                            old.scrollRevision() + (pageRequest ? 1 : 0));
                    nextPoll = System.nanoTime() + (hasMore ? 100_000_000L : 900_000_000L);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                synchronized (this) {
                    if (current(a, friend, viewEpoch)) {
                        String reason = failure.getMessage();
                        status(reason == null || reason.isBlank() ? "Не удалось загрузить переписку" : reason);
                        nextPoll = System.nanoTime() + 2_000_000_000L;
                    }
                }
            } finally {
                synchronized (this) {
                    if (epoch == viewEpoch) readTask = null;
                }
            }
        });
    }
    /** The same uncertain message is retried with its original UUID to avoid duplicate server writes. */
    public synchronized String send(String value){
        var a=access();var s=state;String body=value.strip();if(!same(a)||token.isBlank()||s.friendId().isBlank()||body.isEmpty()||body.length()>1000||sending)return "";
        if(pending==null||!pending.friendId().equals(s.friendId())||!pending.body().equals(body)||pending.generation()!=generation||!pending.token().equals(token))pending=new Pending(s.friendId(),body,UUID.randomUUID().toString(),generation,token);
        var request=pending;final long viewEpoch=epoch;sending=true;status("Отправка…");
        workers.execute(()->{try{
            synchronized (this) {
                if (!same(a) || !a.equals(access())) return;
            }
            var data=new JsonObject();data.addProperty("body",request.body());data.addProperty("clientId",request.id());
            var response=exchange(a,"POST","api/client/friends/"+request.friendId()+"/messages",data);var sent=message(response.getAsJsonObject("message"));
            synchronized (this) {
                if (pending == request) pending = null;
                if (!current(a, request.friendId(), viewEpoch)) return;
                var old = state;
                var list = old.olderPage() ? new ArrayList<Message>() : new ArrayList<>(old.messages());
                if (list.stream().noneMatch(m -> m.id().equals(sent.id()))) list.add(sent);
                list.sort(Comparator.comparingLong(m -> Long.parseLong(m.id())));
                boolean older = old.hasOlder() || old.olderPage();
                while (list.size() > 200) {
                    list.removeFirst();
                    older = true;
                }
                // The acknowledged send returns to the live view and supersedes any pending history read.
                invalidateRead();
                if (old.olderPage()) serverCursor = "";
                state = new Snapshot(old.friendId(), old.name(), list, older, false,
                        "Сообщение сохранено на сервере", request.id(), old.revision() + 1, old.scrollRevision() + 1);
                nextPoll = 0;
            }
        }catch(Exception failure){
            synchronized (this) {
                if (current(a, request.friendId(), viewEpoch)) {
                    status(failure.getMessage()==null ? "Не удалось подтвердить отправку. Повторите её." : failure.getMessage());
                }
            }
        }finally{
            synchronized (this) { sending=false; }
        }});
        return request.id();
    }
    private JsonObject exchange(CosmeticSyncService.WebsiteAccess a,String method,String path,JsonObject body)throws Exception{
        if(a.token().isBlank())throw new IllegalStateException("Войдите в Aero Launcher");
        var b=HttpRequest.newBuilder(a.base().resolve(path)).timeout(Duration.ofSeconds(6)).header("Accept","application/json").header("Authorization","Bearer "+a.token()).header("Cache-Control","no-cache");
        if(!HardwareId.cached().isBlank())b.header("X-HWID",HardwareId.cached());
        if(method.equals("GET"))b.GET();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8));
        var response=http.send(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject root;try{root=JsonParser.parseString(response.body()).getAsJsonObject();}catch(RuntimeException failure){throw new IllegalStateException("Сервер чата недоступен или не обновлён");}
        if(response.statusCode()/100!=2)throw new IllegalStateException(root.has("error")?root.get("error").getAsString():"Ошибка чата: "+response.statusCode());
        if(!root.has("data")||!root.get("data").isJsonObject())throw new IllegalStateException("Обновите API чата на сайте");return root.getAsJsonObject("data");
    }
    private static Message message(JsonObject o){return new Message(string(o,"id"),string(o,"clientId"),string(o,"body"),o.get("createdAt").getAsLong(),bool(o,"own"));}
    private static String string(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    private static boolean bool(JsonObject o,String k){return o.has(k)&&o.get(k).getAsBoolean();}
    @Override public synchronized void close(){closed=true;leave();pending=null;workers.shutdownNow();http.shutdownNow();}
}
