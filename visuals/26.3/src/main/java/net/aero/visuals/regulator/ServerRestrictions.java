package net.aero.visuals.regulator;

import net.aero.regulator.protocol.RegulatorProtocol;
import net.aero.regulator.protocol.RegulatorProtocol.*;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.localization.LocalText;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Connection-scoped, deny-only server policy. It never changes saved module selections or site loadouts. */
public final class ServerRestrictions {
    private record View(boolean pending,Map<String,String> blocked){}
    private static volatile View view=new View(false,Map.of());
    private static volatile long visibilityVersion;
    private static ServerRestrictions instance;
    private final ClientRuntime runtime;
    private final List<String> features;
    private final AtomicInteger queued=new AtomicInteger();
    private Object connection,lastWorld;
    private String nonce="",session="";
    private long revision,nextHello,deadline,receiveWindow,lastWarning;
    private int received;
    private boolean serverSeen,awaiting,warned,channelSeen;

    private ServerRestrictions(ClientRuntime runtime){
        this.runtime=runtime;features=runtime.modules().all().stream().map(net.aero.visuals.module.Module::id).sorted().toList();
        if(features.isEmpty()||features.size()>RegulatorProtocol.MAX_FEATURES)throw new IllegalStateException("Regulator feature catalog size is unsupported");
        PayloadTypeRegistry.serverboundPlay().register(RegulatorPayload.ID,RegulatorPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RegulatorPayload.ID,RegulatorPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LitePayload.ID,LitePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LitePayload.ID,LitePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(LitePayload.ID,(payload,context)->{
            Object source = context.player().connection;
            context.client().execute(() -> {
                if (source == connection) receiveLite(payload.data());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(RegulatorPayload.ID,(payload,context)->{
            if(queued.incrementAndGet()>16){queued.decrementAndGet();return;}
            Object source=context.player().connection;byte[] bytes=payload.data();
            try{context.client().execute(()->{try{receive(source,bytes);}finally{queued.decrementAndGet();}});}
            catch(RuntimeException shuttingDown){queued.decrementAndGet();}
        });
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{
            resetLite(); connection=handler;lastWorld=client.level;nonce=UUID.randomUUID().toString();session="";revision=0;serverSeen=false;awaiting=false;warned=false;channelSeen=false;
            receiveWindow=System.nanoTime();received=0;lastWarning=receiveWindow-10_000_000_001L;
            update(new View(false,Map.of()));tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{
            if(connection==handler){resetLite(); connection=null;lastWorld=null;serverSeen=false;awaiting=false;nonce="";session="";revision=0;update(new View(false,Map.of()));}
        });
    }
    public static void initialize(ClientRuntime runtime){if(instance==null)instance=new ServerRestrictions(runtime);}
    public static boolean allowed(String id){View current=view;return !current.pending&&!current.blocked.containsKey(id);}
    public static boolean blocked(String id){return !allowed(id);}
    public static boolean hidesModule(String id) { return view.blocked.containsKey(id); }
    public static long visibilityVersion() { return visibilityVersion; }
    public static String reason(String id){
        View current=view;
        if(current.pending)return LocalText.pick("Ожидается политика функций от сервера","Waiting for the server feature policy","Очікується політика функцій сервера");
        return current.blocked.getOrDefault(id,"");
    }
    /** Filter drawing only. CosmeticLoadout.capture() remains the account's configured loadout for website sync. */
    public static Map<String,Integer> visibleCosmetics(Map<String,Integer> equipped){
        boolean clothes=allowed("cosmetics"),pets=allowed("pet");
        if(clothes&&pets)return equipped;
        var filtered=new HashMap<>(equipped);
        if(!clothes)filtered.keySet().removeIf(type->!type.equals("pet"));
        if(!pets)filtered.remove("pet");
        return filtered;
    }
    public static void clientTick(Minecraft client){if(instance!=null)instance.tick(client);}
    private void tick(Minecraft client){
        if(connection==null||client.getConnection()!=connection)return;
        tickLite();
        boolean supported=ClientPlayNetworking.canSend(RegulatorPayload.ID);
        if(supported)channelSeen=true;
        else if(channelSeen){
            channelSeen=false;serverSeen=false;awaiting=false;session="";revision=0;nonce=UUID.randomUUID().toString();
            update(new View(false,Map.of()));
        }
        if(!serverSeen&&supported)begin(session,false);
        if(!serverSeen)return;
        if(client.level!=null&&client.level!=lastWorld){lastWorld=client.level;begin(session,true);}
        long now=System.nanoTime();
        if(awaiting&&now>=nextHello){sendHello();nextHello=now+(warned?5_000_000_000L:2_000_000_000L);}
        if(awaiting&&!warned&&now>deadline){warned=true;AeroVisuals.LOGGER.warn("Regulator did not confirm a policy; server-controlled features remain suspended until a valid policy or disconnect");}
    }
    private void begin(String expectedSession,boolean newNonce){
        if(newNonce||!serverSeen)nonce=UUID.randomUUID().toString();
        session=expectedSession;revision=0;serverSeen=true;awaiting=true;warned=false;
        nextHello=0;deadline=System.nanoTime()+8_000_000_000L;
        update(new View(true,regulatorView.blocked));
    }
    private void sendHello(){
        String clientVersion=FabricLoader.getInstance().getModContainer("aerovisuals").orElseThrow().getMetadata().getVersion().getFriendlyString();
        String minecraft=FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        send(new Hello(nonce,clientVersion,minecraft,features));
    }
    private void send(Packet packet){
        if(connection==null||Minecraft.getInstance().getConnection()!=connection)return;
        try {
            // A server request also proves support when a plugin was enabled after the initial channel advertisement.
            ClientPlayNetworking.getSender().sendPacket(new RegulatorPayload(RegulatorProtocol.encode(packet)));
        }catch(RuntimeException failure){warn("Could not send regulator packet",failure);}
    }
    private void receive(Object source,byte[] bytes){
        if(source!=connection||connection==null||bytes.length==0)return;
        long now=System.nanoTime();if(now-receiveWindow>1_000_000_000L){receiveWindow=now;received=0;}if(++received>32)return;
        try {
            Packet packet=RegulatorProtocol.decode(bytes);
            if(packet instanceof Request request){
                if(!request.session().equals(session)){begin(request.session(),true);}
                else if(!serverSeen)begin(request.session(),false);
                if(now>=nextHello){sendHello();nextHello=now+500_000_000L;}
                return;
            }
            if(packet instanceof Failure failure){warn("Regulator response: "+failure.code(),null);return;}
            if(!(packet instanceof Policy policy)||!serverSeen||!policy.nonce().equals(nonce)||!session.isEmpty()&&!session.equals(policy.session()))return;
            if(policy.revision()<revision)return;
            if(policy.revision()==revision&&!policy.blocked().equals(regulatorView.blocked))return;
            Map<String,String> previous=regulatorView.blocked;
            session=policy.session();revision=policy.revision();awaiting=false;warned=false;
            update(new View(false,policy.blocked()));
            send(new Ack(session,nonce,revision));
            if(!previous.equals(policy.blocked())&&!policy.blocked().isEmpty()){
                var player=Minecraft.getInstance().player;
                if(player!=null)player.sendSystemMessage(Component.literal(LocalText.pick("Сервер ограничил функций AeroVisuals: ","Server-restricted AeroVisuals features: ","Сервер обмежив функцій AeroVisuals: ")+policy.blocked().size()+LocalText.pick(". Эти функции скрыты в ClickGUI.",". These features are hidden in ClickGUI.",". Ці функції приховано в ClickGUI.")));
            }
        }catch(IllegalArgumentException invalid){warn("Ignored malformed regulator payload",invalid);}
    }
    private View regulatorView = new View(false,Map.of());
    private Map<String,String> liteBlocked = Map.of();
    private String requestId;
    private boolean answered;
    private long retryAt;

    private void resetLite() {
        requestId = null;
        answered = false;
        liteBlocked = Map.of();
    }

    private void tickLite() {
        if (answered || !ClientPlayNetworking.canSend(LitePayload.ID)) return;
        long now = System.nanoTime();
        if (requestId != null && now - retryAt < 0) return;
        requestId = UUID.randomUUID().toString();
        retryAt = now + 10_000_000_000L;
        var names = new com.google.gson.JsonArray();
        features.forEach(names::add);
        var data = new com.google.gson.JsonObject();
        data.addProperty("client","aerovisuals");
        data.add("features",names);
        var request = new com.google.gson.JsonObject();
        request.addProperty("id",requestId);
        request.addProperty("method","checkFeatures");
        request.add("payload",data);
        ClientPlayNetworking.send(new LitePayload(request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void receiveLite(byte[] bytes) {
        if (requestId == null) return;
        try {
            var root = com.google.gson.JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!requestId.equals(root.get("id").getAsString()) || !root.get("ok").getAsBoolean()) return;
            var denied = new HashMap<String,String>();
            for (var item : root.getAsJsonObject("payload").getAsJsonArray("blocklist")) {
                String id = item.getAsString();
                if (features.contains(id)) denied.put(id,"HolyWorld Lite API");
            }
            liteBlocked = Map.copyOf(denied);
            answered = true;
            requestId = null;
            update(regulatorView);
        } catch (RuntimeException malformed) {
            warn("Invalid Lite API response",malformed);
        }
    }

    private void update(View next){
        regulatorView = next;
        if (!liteBlocked.isEmpty()) {
            var blocked = new HashMap<>(liteBlocked);
            blocked.putAll(next.blocked);
            next = new View(next.pending,Map.copyOf(blocked));
        }
        View previous = view;
        if (previous.equals(next)) return;
        view = next;
        if (!previous.blocked.keySet().equals(next.blocked.keySet())) visibilityVersion++;
        for(var module:runtime.modules().all()){
            try{module.refreshAccess();}catch(RuntimeException|LinkageError failure){AeroVisuals.LOGGER.error("Could not refresh module {} after a server policy change",module.id(),failure);}
        }
        net.aero.visuals.hud.HudDragController.finish();
        var legacy=client.concurrent.SystemClient.INSTANCE;
        if(legacy!=null&&legacy.getModuleRegistry()!=null)legacy.getModuleRegistry().update2();
    }
    private void warn(String message,Throwable error){long now=System.nanoTime();if(now-lastWarning<10_000_000_000L)return;lastWarning=now;if(error==null)AeroVisuals.LOGGER.warn(message);else AeroVisuals.LOGGER.warn(message,error);}
}
