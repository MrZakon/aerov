package net.aero.visuals.config;

import com.google.gson.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.config.LocalConfigStore.Document;
import net.aero.visuals.cosmetic.CosmeticSyncService.WebsiteAccess;
import net.aero.visuals.guard.AeroGuard;
import net.aero.visuals.guard.HardwareId;

/** Atomic local files and a durable outbox. HTTP never blocks local saves or the game thread. */
public final class CloudConfigsService implements AutoCloseable {
    public record Entry(String id,String name,String description,boolean privateConfig,String code,String revision,String updatedAt,long size,boolean compatible,String syncState) {}
    private record Reply(int status,JsonObject data,String error) {}
    private final ClientRuntime runtime;
    private final Executor main;
    private final Path root;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(task->{var t=new Thread(task,"Aero-Local-Configs");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(4)).build();
    private final AtomicBoolean localBusy = new AtomicBoolean();
    private volatile boolean networkBusy;
    private final Map<String,Document> documents=new LinkedHashMap<>();
    private final Map<String,Consumer<String>> shareCallbacks=new HashMap<>();
    private final Deque<Runnable> jobs=new ArrayDeque<>();
    private volatile List<Entry> entries=List.of();
    private volatile WebsiteAccess owner;
    private volatile String status="Конфиги: подключение…",active="",localDirectory="";
    private volatile boolean closed, closing;
    private volatile long nextPoll,loadGeneration;
    private LocalConfigStore store;
    private String account="";
    private boolean confirmed,inFlight;
    private long flight;
    public CloudConfigsService(ClientRuntime runtime,Executor main){this.runtime=runtime;this.main=main;root=runtime.config().getConfigPath().resolveSibling("aerovisuals-configs");}
    private WebsiteAccess access(){return runtime.cosmeticSync().friendAccess();}
    private static boolean same(WebsiteAccess a,WebsiteAccess b){return a!=null&&b!=null&&a.generation()==b.generation()&&a.token().equals(b.token())&&a.base().equals(b.base());}
    private boolean current(WebsiteAccess a){return !closed&&AeroGuard.allowed()&&same(a,access());}
    public void start(){worker.scheduleWithFixedDelay(this::cycle,0,250,TimeUnit.MILLISECONDS);}
    public List<Entry> entries(){return current(owner)?entries:List.of();}
    public String status(){return AeroGuard.allowed()?status:AeroGuard.message();}
    public String active(){return current(owner)?active:"";}
    public String localDirectory(){return localDirectory;}
    public boolean busy(){return localBusy.get() || networkBusy;}
    public void refresh(){nextPoll=0;}
    private String sessionKey(WebsiteAccess a){return LocalConfigStore.hash(a.base()+"/"+a.token());}
    private void open(String id)throws Exception{
        var nextStore = new LocalConfigStore(root, owner.base().toString(), id);
        var loaded = nextStore.read();
        account = id;
        store = nextStore;
        documents.clear();
        for (Document d : loaded) documents.put(d.key, d);
        localDirectory = store.directory().toString();
        publish();
    }
    private void reset(WebsiteAccess a)throws Exception{
        undo = null;
        loadGeneration++;
        owner=a;confirmed=false;inFlight=false;flight++;store=null;documents.clear();jobs.clear();shareCallbacks.clear();entries=List.of();active="";localDirectory="";networkBusy=false;nextPoll=0;account="";
        String known=LocalConfigStore.knownAccount(root,sessionKey(a));if(!known.isBlank())open(known);
    }
    private void cycle(){
        if(closed)return;
        try{
            var a=access();if(!same(owner,a))reset(a);
            if(!AeroGuard.allowed()||a.token().isBlank()){status="Войдите в Aero Launcher";return;}
            if(inFlight)return;
            if(!confirmed){if(System.nanoTime()>=nextPoll)poll();return;}
            if(!jobs.isEmpty()){jobs.removeFirst().run();return;}
            var pending=documents.values().stream().filter(d->!d.intent.isEmpty()&&!d.paused&&System.nanoTime()>=d.retryAt).sorted(Comparator.comparing(d->!d.deleted)).findFirst();
            if(pending.isPresent()){upload(pending.get());return;}
            if(System.nanoTime()>=nextPoll)poll();
        }catch(Exception failure){status=message(failure);nextPoll=System.nanoTime()+3_000_000_000L;}
    }
    private void poll(){
        nextPoll=System.nanoTime()+15_000_000_000L;
        send("GET","api/client/configs",null,reply->{
            if(reply.status!=200){status=reply.error;nextPoll=System.nanoTime()+3_000_000_000L;return;}
            try{
                String id=string(reply.data,"accountId");if(!id.matches("[0-9]{1,20}"))throw new IllegalStateException("Обновите API конфигов на сайте");
                if(store==null||!account.equals(id))open(id);LocalConfigStore.rememberAccount(root,sessionKey(owner),id);confirmed=true;merge(reply.data);
                status=documents.values().stream().anyMatch(d->!d.intent.isEmpty())?"Сохранено на ПК · синхронизация…":"Конфиги: синхронизированы";
            }catch(Exception failure){status=message(failure);nextPoll=System.nanoTime()+3_000_000_000L;}
        });
    }
    private void publish(){
        entries=documents.values().stream().filter(d->!d.deleted).map(d->new Entry(d.key,d.name,d.description,d.privateConfig,d.code,d.revision,d.updatedAt,d.remoteSize,d.compatible,d.paused?"Ошибка синхронизации":!d.intent.isEmpty()?"Сохранено на ПК · синхронизация…":!d.hasCurrentPayload()?"На сервере":"На ПК и сервере")).sorted(Comparator.comparing(Entry::updatedAt).reversed().thenComparing(Entry::name)).toList();
        if(!active.isBlank()&&entries.stream().noneMatch(e->e.id().equals(active)))active="";
    }
    private Document find(Entry e){Document d=documents.get(e.id());if(d==null||d.deleted)throw new IllegalStateException("Конфиг уже удалён. Обновите список.");return d;}
    private void local(Runnable action) {
        if (closing || closed || !AeroGuard.allowed() || networkBusy || !localBusy.compareAndSet(false, true)) return;
        WebsiteAccess expected = access();
        try {
            worker.execute(() -> {
                try {
                    if (!current(expected) || !same(owner, expected)) return;
                    if (store == null) throw new IllegalStateException("Дождитесь подключения к Aero ID");
                    action.run();
                } catch (Exception failure) {
                    status = message(failure);
                } finally {
                    localBusy.set(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            localBusy.set(false);
            if (!closing && !closed) throw rejected;
        }
    }
    private static String name(String value){String n=value.strip();if(n.isEmpty()||n.length()>60)throw new IllegalArgumentException("Название: от 1 до 60 символов");return n;}
    private void unique(String name,String key){if(documents.values().stream().anyMatch(d->!d.deleted&&!d.key.equals(key)&&d.name.equalsIgnoreCase(name)))throw new IllegalArgumentException("Конфиг с таким названием уже есть");}
    private void save(Document d) {
        try {
            store.save(d);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Не удалось сохранить конфиг на ПК", failure);
        }
        documents.put(d.key, d);
        publish();
        status = "Сохранено на ПК · синхронизация…";
    }
    private void changed(Document d){if(d.intent.isEmpty()||d.paused)d.baseRevision=d.revision;d.change(d.serverId.isBlank()?"NEW":"PATCH");}
    public void create(String value){
        try{String name=name(value);JsonObject data=ConfigSnapshots.capture(runtime);ConfigSnapshots.validate(data);local(()->{
            unique(name,"");if(documents.values().stream().filter(d->!d.deleted).count()>=100)throw new IllegalArgumentException("Можно сохранить до 100 конфигов");
            Document d=new Document();d.name=name;d.data=data;d.remoteSize=data.toString().getBytes(StandardCharsets.UTF_8).length;d.patch.addProperty("name",name);d.patch.add("data",data);d.change("NEW");
            try{store.save(d);}catch(Exception failure){throw new IllegalStateException("Не удалось сохранить конфиг на ПК",failure);}documents.put(d.key,d);publish();status="Сохранено на ПК · синхронизация…";
        });}catch(RuntimeException failure){status=message(failure);}
    }
    public void overwrite(Entry e){try{JsonObject data=ConfigSnapshots.capture(runtime);ConfigSnapshots.validate(data);local(()->{Document d=find(e).copyForEdit();requireRevision(e,d);changed(d);d.data=data;d.remoteSize=data.toString().getBytes(StandardCharsets.UTF_8).length;d.compatible=true;d.patch.add("data",data);save(d);loadGeneration++;});}catch(RuntimeException failure){status=message(failure);}}
    private static void requireRevision(Entry expected,Document current){if(!Objects.equals(expected.revision(),current.revision))throw new IllegalStateException("Конфиг изменён на другом клиенте. Обновите выбор и повторите действие.");}
    public void rename(Entry e,String value){try{String name=name(value);local(()->{Document d=find(e).copyForEdit();unique(name,d.key);changed(d);d.name=name;d.patch.addProperty("name",name);save(d);});}catch(RuntimeException failure){status=message(failure);}}
    public void share(Entry e,Consumer<String> copy){local(()->{Document d=find(e);if(!d.privateConfig&&!d.code.isBlank()&&d.intent.isEmpty()){String code=d.code;WebsiteAccess expected=owner;main.execute(()->{if(current(expected))copy.accept(code);});return;}d=d.copyForEdit();changed(d);d.privateConfig=false;d.patch.addProperty("private",false);save(d);shareCallbacks.put(d.key,copy);});}
    public void makePrivate(Entry e){local(()->{Document d=find(e).copyForEdit();changed(d);d.privateConfig=true;d.code="";d.patch.addProperty("private",true);save(d);shareCallbacks.remove(d.key);});}
    public void remove(Entry e){local(()->{Document d=find(e).copyForEdit();requireRevision(e,d);d.baseRevision=d.revision;d.deleted=true;d.patch=new JsonObject();d.change("DELETE");save(d);loadGeneration++;shareCallbacks.remove(d.key);status="Конфиг удалён с ПК · синхронизация…";});}

    public final class Preview {
        private final WebsiteAccess access;
        private final String id;
        private final String revision;
        private final long selection;
        private final JsonObject data;
        private JsonObject baseline;

        private Preview(WebsiteAccess access, Document d, JsonObject data, long selection) {
            this.access = access;
            this.id = d.key;
            this.revision = d.revision;
            this.data = data;
            this.selection = selection;
        }

        private boolean valid() {
            return current(access) && selection == loadGeneration
                    && entries.stream().anyMatch(e -> e.id().equals(id) && e.revision().equals(revision));
        }

        public List<String> changes() {
            if (!valid()) return List.of("Конфиг изменился. Откройте предпросмотр заново.");
            try {
                baseline = ConfigSnapshots.capture(runtime);
                return ConfigChanges.describe(runtime, baseline, data);
            }
            catch (RuntimeException failure) { return List.of(message(failure)); }
        }

        public boolean apply() {
            if (!valid()) {
                status = "Конфиг изменился. Откройте предпросмотр заново.";
                return false;
            }
            try {
                var previous = ConfigSnapshots.capture(runtime);
                if (baseline == null || !baseline.equals(previous)) {
                    status = "Настройки изменились. Проверьте обновлённый предпросмотр.";
                    return false;
                }
                ConfigChanges.describe(runtime, previous, data);
                ConfigSnapshots.apply(runtime, data);
                undo = new Undo(access, active, previous);
                active = id;
                loadGeneration++;
                status = "Конфиг применён с ПК";
                return true;
            } catch (Exception failure) {
                status = message(failure);
                return false;
            }
        }
    }

    private record Undo(WebsiteAccess access, String active, JsonObject data) {}
    private volatile Undo undo;

    public boolean canUndo() {
        var checkpoint = undo;
        return checkpoint != null && current(checkpoint.access());
    }

    public void undo() {
        var checkpoint = undo;
        if (checkpoint == null || !current(checkpoint.access()) || busy()) return;
        try {
            ConfigSnapshots.apply(runtime, checkpoint.data());
            active = entries.stream().anyMatch(e -> e.id().equals(checkpoint.active())) ? checkpoint.active() : "";
            undo = null;
            loadGeneration++;
            status = "Предыдущие настройки восстановлены";
        } catch (Exception failure) {
            status = message(failure);
        }
    }

    public void preview(Entry entry, Consumer<Preview> show) {
        local(() -> {
            Document d = find(entry);
            requireRevision(entry, d);
            if (!d.compatible) throw new IllegalStateException("Конфиг создан в другом клиенте");
            long selection = ++loadGeneration;
            if (d.hasCurrentPayload()) {
                showPreview(d, d.data.deepCopy(), selection, show);
                return;
            }
            jobs.add(() -> {
                var latest = documents.get(d.key);
                if (selection == loadGeneration && latest != null && d.revision.equals(latest.revision)
                        && d.sequence == latest.sequence) download(latest, selection, show);
            });
            status = "Скачивание конфига на ПК…";
        });
    }

    private void showPreview(Document d, JsonObject data, long selection, Consumer<Preview> show) {
        var preview = new Preview(owner, d, data, selection);
        main.execute(() -> { if (preview.valid()) show.accept(preview); });
    }

    private void cache(Document d) {
        download(d, loadGeneration, null);
    }

    private void download(Document d, long selection, Consumer<Preview> show) {
        if (documents.get(d.key) != d || d.deleted || d.serverId.isBlank() || d.hasLocalPayload()) {
            networkBusy = false;
            return;
        }
        long sequence = d.sequence;
        String requestedRevision = d.revision;
        networkBusy = true;
        send("GET", "api/client/configs/" + d.serverId, null, reply -> {
            try {
                if (reply.status != 200) throw new IllegalStateException(reply.error);
                if (documents.get(d.key) != d || d.deleted || d.sequence != sequence) return;
                JsonObject data = reply.data.getAsJsonObject("data");
                ConfigSnapshots.validate(data);
                Document downloaded = d.copyForEdit();
                downloaded.data = data;
                metadata(downloaded, reply.data);
                downloaded.cachedRevision = downloaded.revision;
                store.save(downloaded);
                documents.put(downloaded.key, downloaded);
                publish();
                status = "Конфиг скачан на ПК";
                if (show == null || selection != loadGeneration) return;
                if (requestedRevision.equals(downloaded.revision)) showPreview(downloaded, data.deepCopy(), selection, show);
                else status = "Конфиг изменился. Откройте предпросмотр заново.";
            } catch (Exception failure) {
                status = message(failure);
            } finally {
                networkBusy = false;
            }
        });
    }

    public void importCode(String value){local(()->{JsonObject body=new JsonObject();body.addProperty("code",value.strip());body.addProperty("operationId",UUID.randomUUID().toString());jobs.add(()->{networkBusy = true;send("POST","api/client/configs/import",body,reply->{try{
        if(reply.status!=200)throw new IllegalStateException(reply.error);merge(reply.data);String id=string(reply.data.getAsJsonObject("changed"),"id");Document d=documents.values().stream().filter(v->v.serverId.equals(id)).findFirst().orElseThrow();cache(d);
    }catch(Exception failure){status=message(failure);networkBusy = false;}});});});}
    private void metadata(Document d,JsonObject remote){d.serverId=string(remote,"id");if(d.data==null||d.intent.isEmpty())d.remoteSize=remote.has("size")?remote.get("size").getAsLong():0;d.revision=string(remote,"revision");if(d.intent.isEmpty()){d.name=string(remote,"name");d.description=string(remote,"description");d.privateConfig=remote.get("private").getAsBoolean();d.updatedAt=string(remote,"updatedAt");}d.code=d.privateConfig?"":string(remote,"shareCode");d.compatible=d.hasLocalPayload() || remote.get("compatible").getAsBoolean();}
    private static String metadataKey(Document d){return Arrays.asList(d.serverId,d.name,d.description,d.code,d.revision,d.cachedRevision,d.updatedAt,d.intent,d.operationId,d.error,d.privateConfig,d.compatible,d.deleted,d.paused,d.remoteSize).toString();}
    private void merge(JsonObject result)throws Exception{
        Set<String> found=new HashSet<>();
        for(var value:result.getAsJsonArray("configs")){
            JsonObject remote=value.getAsJsonObject();String id=string(remote,"id"),clientId=string(remote,"clientId");found.add(id);
            Document d=documents.values().stream().filter(v->v.serverId.equals(id)||!clientId.isEmpty()&&v.clientId.equals(clientId)).findFirst().orElse(null);
            boolean added = d == null;
            String before = added ? "" : metadataKey(d);
            if (added) {
                d = new Document();
                d.clientId = clientId;
            } else {
                d = d.copyForEdit();
            }
            String oldRevision = d.revision;
            metadata(d, remote);
            if(!d.deleted&&!d.intent.isEmpty()&&d.operationId.equals(string(remote,"clientOperationId"))){boolean contentSaved=d.hasLocalPayload();d.intent="";d.patch=new JsonObject();d.paused=false;d.error="";if(d.data!=null && (contentSaved || d.baseRevision.equals(d.cachedRevision))) d.cachedRevision=d.revision;metadata(d,remote);}
            else if(d.intent.equals("NEW")){d.intent="PATCH";d.baseRevision=d.revision;}
            else if(d.intent.isEmpty()&&oldRevision.equals(d.cachedRevision)&&!oldRevision.equals(d.revision))d.cachedRevision="";
            if (added || !before.equals(metadataKey(d))) {
                store.save(d);
                documents.put(d.key, d);
            }
        }
        for(Document d:new ArrayList<>(documents.values()))if(!d.serverId.isEmpty()&&!found.contains(d.serverId)){
            if(d.intent.isEmpty()){store.remove(d);documents.remove(d.key);}else if (!d.deleted && !d.paused) {
                Document paused = d.copyForEdit();
                paused.paused = true;
                paused.error = "Конфиг удалён на сервере. Сохраните его под новым названием.";
                store.save(paused);
                documents.put(paused.key, paused);
            }
        }publish();
    }
    private void upload(Document sent){
        long sequence=sent.sequence;String op=sent.operationId,intent=sent.intent;JsonObject body=sent.patch.deepCopy();String path="api/client/configs",method="POST";body.addProperty("operationId",op);
        if(intent.equals("DELETE")){if(sent.serverId.isBlank()){path+="/delete";body=new JsonObject();body.addProperty("clientId",sent.clientId);}else{path+="/"+sent.serverId+"?revision="+java.net.URLEncoder.encode(sent.baseRevision,StandardCharsets.UTF_8);method="DELETE";body=null;}}
        else if(intent.equals("NEW")){body.addProperty("clientId",sent.clientId);body.addProperty("name",sent.name);body.addProperty("description",sent.description);body.addProperty("private",sent.privateConfig);body.add("data",sent.data);}
        else{path+="/"+sent.serverId;body.addProperty("revision",sent.baseRevision);}
        boolean contentSent=body!=null&&body.has("data");String previous=sent.baseRevision;
        send(method, path, body, reply -> {
            Document live = documents.get(sent.key);
            if (live == null) return;
            try {
                if (reply.status < 200 || reply.status >= 300) {
                    if (live.sequence != sequence) {
                        live.retryAt = 0;
                        return;
                    }
                    if (reply.status == 0 || reply.status >= 500 || reply.status == 429) {
                        live.failures++;
                        live.retryAt = System.nanoTime() + Math.min(30, 1L << Math.min(5, live.failures)) * 1_000_000_000L;
                        status = "Сохранено на ПК · сервер недоступен, повторим позже";
                        return;
                    }

                    Document failed = live.copyForEdit();
                    if (reply.status == 409 && intent.equals("DELETE")) {
                        failed.deleted = false;
                        failed.intent = "";
                        failed.patch = new JsonObject();
                        failed.paused = false;
                        failed.error = "";
                        failed.cachedRevision = "";
                    } else {
                        failed.paused = true;
                        failed.error = reply.error;
                    }
                    store.save(failed);
                    documents.put(failed.key, failed);
                    publish();
                    nextPoll = 0;
                    status = failed.paused ? reply.error
                            : "Конфиг изменён на другом клиенте. Удаление отменено; обновите список.";
                    return;
                }
                if (intent.equals("DELETE")) {
                    store.remove(live);
                    documents.remove(live.key);
                    publish();
                    status = "Конфиг удалён с ПК и сервера";
                    return;
                }

                Document d = live.copyForEdit();
                JsonObject remote = reply.data.getAsJsonObject("changed");
                metadata(d, remote);
                d.failures = 0;
                d.retryAt = 0;
                boolean acknowledged = op.equals(string(remote, "clientOperationId"));
                if (d.sequence == sequence && acknowledged) {
                    d.intent = "";
                    d.patch = new JsonObject();
                    d.paused = false;
                    d.error = "";
                    if (d.data != null && (contentSent || previous.equals(d.cachedRevision))) {
                        d.cachedRevision = d.revision;
                    }
                    metadata(d, remote);
                } else if (d.deleted) {
                    if (acknowledged) d.baseRevision = d.revision;
                } else {
                    d.intent = "PATCH";
                    d.baseRevision = d.revision;
                    if (!acknowledged && intent.equals("NEW")) {
                        d.patch.addProperty("name", d.name);
                        d.patch.add("data", d.data);
                    }
                }
                store.save(d);
                documents.put(d.key, d);
                publish();

                var callback = shareCallbacks.get(d.key);
                if (callback != null && !d.deleted && !d.privateConfig && !d.code.isBlank()) {
                    String code = d.code;
                    WebsiteAccess expected = owner;
                    shareCallbacks.remove(d.key);
                    main.execute(() -> {
                        if (current(expected)) callback.accept(code);
                    });
                }
                status = d.intent.isEmpty() ? "Сохранено на ПК и сервере" : "Сохранено на ПК · синхронизация…";
            } catch (Exception failure) {
                Document latest = documents.get(sent.key);
                if (latest != null) latest.retryAt = System.nanoTime() + 3_000_000_000L;
                status = message(failure);
            }
        });
    }
    private void send(String method,String path,JsonObject body,Consumer<Reply> done){
        WebsiteAccess expected=owner;long ticket=++flight;inFlight=true;
        try{
            var b=HttpRequest.newBuilder(expected.base().resolve(path)).timeout(Duration.ofSeconds(10)).header("Accept","application/json").header("Authorization","Bearer "+expected.token()).header("Cache-Control","no-cache");if(!HardwareId.cached().isBlank())b.header("X-HWID",HardwareId.cached());
            if(method.equals("GET"))b.GET();else if(method.equals("DELETE"))b.DELETE();else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8));
            http.sendAsync(b.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response,failure)->{try{worker.execute(()->{
                if(ticket!=flight)return;inFlight=false;if(!current(expected)){networkBusy=false;return;}Reply reply;
                try{if(failure!=null)throw new IllegalStateException("Сайт временно недоступен");if(response.body().length()>ConfigSnapshots.MAX_BYTES*3)throw new IllegalStateException("Ответ сайта слишком большой");JsonObject json=JsonParser.parseString(response.body()).getAsJsonObject();reply=new Reply(response.statusCode(),json.has("data")&&json.get("data").isJsonObject()?json.getAsJsonObject("data"):new JsonObject(),json.has("error")?json.get("error").getAsString():"Ошибка сайта: "+response.statusCode());}catch(Exception error){reply=new Reply(0,new JsonObject(),message(error));}done.accept(reply);
            });}catch(RejectedExecutionException ignored){}});
        }catch(Exception failure){inFlight=false;done.accept(new Reply(0,new JsonObject(),message(failure)));}
    }
    private static String string(JsonObject o,String key){return o!=null&&o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static String message(Exception failure){return failure instanceof IllegalStateException||failure instanceof IllegalArgumentException?failure.getMessage():"Не удалось сохранить конфиг. Проверьте доступ к папке и соединение.";}
    @Override public void close() {
        closing = true;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(2, TimeUnit.SECONDS)) worker.shutdownNow();
        } catch (InterruptedException interrupted) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            closed = true;
            http.shutdownNow();
            networkBusy = false;
            localBusy.set(false);
            entries = List.of();
        }
    }
}
