package net.aero.visuals.feature.utility;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.command.ClientCommandHandler;
import net.aero.visuals.cosmetic.CosmeticSyncService.WebsiteAccess;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.feature.enhanced.VisualExpansion;
import net.aero.visuals.module.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

public final class PairedEmotes extends VisualExpansion.Feature {
    public enum Pose { HIGH_FIVE, DANCE, DUEL }
    public record Candidate(UUID player, String friendId, String name) {}
    public record Playback(Pose pose, boolean second, float seconds, float yaw) {
        public float weight() { return Math.clamp(Math.min(seconds, 4 - seconds) * 3, 0, 1); }
        public float sway() { return (float) Math.sin(seconds * 7) * (second ? -1 : 1); }
    }
    private record Invite(UUID id, UUID other, Pose pose, boolean incoming, long start, long expires) {}
    private record Response(Invite invite, String error) {}
    private record Session(WebsiteAccess access, UUID player, Object world) {}
    private record Actor(int entityId, float yaw) {}
    private record Active(Invite invite, Actor own, Actor other) {}
    private static final String HELP = ".pair on · .pair invite <ник> <high_five|dance|duel> · .pair accept · .pair cancel";
    private static volatile PairedEmotes enabled;
    private HttpClient http;
    private CompletableFuture<Response> pending;
    private Session session;
    private Invite invitation;
    private volatile Active active;
    private UUID notified;
    private long nextPoll;
    private boolean announce;
    private String status = "Друзья в радиусе пяти блоков";

    public PairedEmotes() {
        super("paired_emotes", "Парные эмоции", "Дай пять, станцуй или прими дуэльную позу с другом рядом. Приглашения через Aero ID в разделе косметики.", ModuleCategory.COSMETICS);
    }
    @Override protected void onEnable() {
        enabled = this;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }
    @Override protected void onDisable() {
        if (enabled == this) enabled = null;
        reset(null);
        if (http != null) { http.shutdownNow(); http = null; }
    }

    private Session session(Minecraft client) {
        var runtime = ClientRuntime.getNullable();
        if (runtime == null || runtime.cosmeticSync() == null || client.player == null || client.level == null
                || client.getCurrentServer() == null || !isEnabled()) return null;
        var access = runtime.cosmeticSync().friendAccess();
        return access.token().isBlank() ? null : new Session(access, client.player.getUUID(), client.level);
    }
    private void reset(Session next) {
        if (pending != null) pending.cancel(true);
        pending = null; invitation = null; active = null; notified = null; nextPoll = 0; session = next;
    }
    @Subscribe private void tick(TickEvent.Client event) {
        var client = event.client();
        var current = session(client);
        if (!java.util.Objects.equals(current, session)) reset(current);
        if (current == null) return;
        long now = System.nanoTime();
        if (pending != null && pending.isDone()) {
            var response = pending.getNow(null);
            pending = null;
            if (response != null) {
                if (response.error() != null) {
                    if (announce) tell(response.error());
                    else status = response.error();
                    nextPoll = now + 5_000_000_000L;
                } else publish(client, response.invite());
            }
        }
        if (invitation != null && now - invitation.expires() >= 0) { invitation = null; active = null; }
        if (active != null && nearby(client, active.invite().other()) == null) active = null;
        if (pending == null && now - nextPoll >= 0) request(null, false);
    }

    private void publish(Minecraft client, Invite next) {
        var previous = invitation;
        if (next != null && previous != null && next.id().equals(previous.id()) && previous.start() != 0 && next.start() != 0) next = previous;
        invitation = next;
        if (next == null) {
            active = null;
            if (announce) tell("Приглашение отменено.");
            else if (previous != null) status = "Приглашение завершено";
            return;
        }
        var other = nearby(client, next.other());
        if (other == null) { active = null; return; }
        if (next.start() != 0) {
            if (active == null || !active.invite().id().equals(next.id())) {
                float yaw = facing(client.player.getX(), client.player.getZ(), other.getX(), other.getZ());
                active = new Active(next, new Actor(client.player.getId(), yaw), new Actor(other.getId(), yaw + 180));
                if (previous == null || previous.start() == 0) tell("Совместная эмоция начинается. F5 — вид от третьего лица.");
            }
        } else if (next.incoming() && !next.id().equals(notified)) {
            notified = next.id();
            String invitationText = other.getName().getString() + " приглашает: " + poseName(next.pose());
            tell(invitationText + " · Косметика → Парные эмоции");
            status = invitationText;
        } else if (announce && !next.incoming()) tell("Приглашение отправлено. Жду подтверждения друга.");
    }

    private static float facing(double x, double z, double targetX, double targetZ) {
        return (float) Math.toDegrees(Math.atan2(targetZ - z, targetX - x)) - 90;
    }
    public static String poseName(Pose pose) {
        return switch (pose) { case HIGH_FIVE -> "Дать пять"; case DANCE -> "Танец"; case DUEL -> "Дуэльная поза"; };
    }
    private static AbstractClientPlayer nearby(Minecraft client, UUID uuid) {
        if (client.level == null || client.player == null) return null;
        for (var player : client.level.players()) {
            if (player != client.player && player.getUUID().equals(uuid) && !player.isInvisible()
                    && player.distanceToSqr(client.player) <= 25) return player;
        }
        return null;
    }

    public static Playback playback(int entityId) {
        var module = enabled;
        var state = module == null ? null : module.active;
        if (state == null || !module.isEnabled()) return null;
        long now = System.nanoTime();
        if (now - state.invite().start() < 0 || now - state.invite().expires() >= 0) return null;
        boolean own = entityId == state.own().entityId();
        if (!own && entityId != state.other().entityId()) return null;
        var actor = own ? state.own() : state.other();
        boolean second = own == state.invite().incoming();
        return new Playback(state.invite().pose(), second, (now - state.invite().start()) / 1e9f, actor.yaw());
    }

    public static float face(float bodyYaw, Playback pose) {
        float turns = (pose.yaw() - bodyYaw) / 360;
        float shortest = (turns - Math.round(turns)) * 360;
        return bodyYaw + shortest * pose.weight();
    }

    public void command(String argument) {
        var args = argument.split("\\s+");
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("on") || action.equals("off")) {
            setEnabled(action.equals("on"));
            tell(isEnabled() ? "Готово. Друг может прислать приглашение." : "Парные эмоции выключены.");
            return;
        }
        if (!action.equals("invite") && !action.equals("accept") && !action.equals("cancel")) { tell(HELP); return; }
        if (!action.equals("invite")) { reply(action.equals("accept")); return; }
        if (args.length != 3) { tell(HELP); return; }
        Pose pose;
        try { pose = Pose.valueOf(args[2].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { tell(HELP); return; }
        var candidate = nearbyFriends().stream().filter(friend -> friend.name().equalsIgnoreCase(args[1])).findFirst();
        if (candidate.isPresent()) invite(candidate.get(), pose);
        else tell("Подойди к другу из списка Aero ID на расстояние до пяти блоков.");
    }

    public String status() { return status; }
    public boolean busy() { return pending != null && announce; }
    public boolean hasInvitation() { return invitation != null && System.nanoTime() - invitation.expires() < 0; }
    public boolean canAccept() { return hasInvitation() && invitation.incoming() && invitation.start() == 0; }

    public List<Candidate> nearbyFriends() {
        var client = Minecraft.getInstance();
        var runtime = ClientRuntime.getNullable();
        if (runtime == null || runtime.friends() == null || client.level == null || client.player == null) return List.of();
        var friends = runtime.friends().snapshot().friends();
        var candidates = new ArrayList<Candidate>();
        for (var player : client.level.players()) {
            if (player == client.player || player.isInvisible() || player.distanceToSqr(client.player) > 25) continue;
            String name = player.getGameProfile().name();
            var account = friends.stream().filter(friend -> friend.minecraftNames().stream().anyMatch(name::equalsIgnoreCase)
                    || friend.players().stream().anyMatch(live -> name.equalsIgnoreCase(live.name()))).findFirst();
            account.ifPresent(friend -> candidates.add(new Candidate(player.getUUID(), friend.id(), name)));
        }
        candidates.sort(Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(candidates);
    }

    public void invite(Candidate candidate, Pose pose) {
        if (!prepare()) return;
        if (!nearbyFriends().contains(candidate)) { tell("Друг должен оставаться рядом."); return; }
        var body = new JsonObject();
        body.addProperty("action", "invite");
        body.addProperty("friendId", candidate.friendId());
        body.addProperty("target", candidate.player().toString());
        body.addProperty("pose", pose.name().toLowerCase(Locale.ROOT));
        status = "Отправляю приглашение…";
        request(body, true);
    }

    public void reply(boolean accept) {
        if (!prepare()) return;
        if (!hasInvitation() || accept && !canAccept()) { tell("Нет активного приглашения."); return; }
        if (accept && nearby(Minecraft.getInstance(), invitation.other()) == null) {
            tell("Подойди к другу на расстояние до пяти блоков."); return;
        }
        var body = new JsonObject();
        body.addProperty("action", accept ? "accept" : "cancel");
        body.addProperty("id", invitation.id().toString());
        status = accept ? "Подтверждаю приглашение…" : "Отменяю приглашение…";
        request(body, true);
    }

    private boolean prepare() {
        setEnabled(true);
        var current = session(Minecraft.getInstance());
        if (current == null) { tell("Нужен вход в Aero Launcher и подключение к серверу."); return false; }
        if (!current.equals(session)) reset(current);
        if (busy()) { tell("Подожди ответ на предыдущее действие."); return false; }
        return true;
    }

    private void request(JsonObject body, boolean manual) {
        if (pending != null) pending.cancel(true);
        announce = manual;
        long sent = System.nanoTime();
        nextPoll = sent + 1_000_000_000L;
        var owner = session;
        String path = "api/client/paired-emotes" + (body == null ? "?player=" + owner.player() : "");
        var builder = HttpRequest.newBuilder(owner.access().base().resolve(path)).timeout(Duration.ofSeconds(4))
                .header("Authorization", "Bearer " + owner.access().token()).header("Accept", "application/json");
        if (body != null) {
            body.addProperty("player", owner.player().toString());
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        }
        pending = http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> decode(response, sent)).exceptionally(failure -> new Response(null, "Сервис парных эмоций недоступен."));
    }

    private static Response decode(HttpResponse<String> response, long sent) {
        var root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() / 100 != 2) {
            return new Response(null, root.has("error") ? root.get("error").getAsString() : "Сервис парных эмоций недоступен.");
        }
        var data = root.getAsJsonObject("data");
        if (!data.has("invite") || data.get("invite").isJsonNull()) return new Response(null, null);
        var invite = data.getAsJsonObject("invite");
        long localNow = System.nanoTime(), serverNow = data.get("serverNow").getAsLong();
        long anchor = localNow - Math.max(0, localNow - sent) / 2;
        long start = invite.get("startAt").getAsLong();
        long expires = invite.get("expiresAt").getAsLong();
        return new Response(new Invite(UUID.fromString(invite.get("id").getAsString()), UUID.fromString(invite.get("other").getAsString()),
                Pose.valueOf(invite.get("pose").getAsString().toUpperCase(Locale.ROOT)), invite.get("incoming").getAsBoolean(),
                start == 0 ? 0 : anchor + (start - serverNow) * 1_000_000L, anchor + (expires - serverNow) * 1_000_000L), null);
    }

    private void tell(String message) {
        status = message;
        ClientCommandHandler.send("§dПарные эмоции §7· §f" + message);
    }
}
