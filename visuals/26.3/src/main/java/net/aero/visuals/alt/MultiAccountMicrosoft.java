package net.aero.visuals.alt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import net.aero.visuals.localization.LocalText;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class MultiAccountMicrosoft {
    private static final String AUTH_BASE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final OAuthClient LAUNCHER = new OAuthClient("00000000402B5328",
            "https://login.live.com/oauth20_connect.srf", "https://login.live.com/oauth20_token.srf",
            "service::user.auth.xboxlive.com::MBI_SSL", "t=");
    private static final Gson JSON = new Gson();
    private static final Map<Path, Profile> SESSIONS = new ConcurrentHashMap<>();

    private MultiAccountMicrosoft() {}

    private static final class Web {
        private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    record DeviceCode(String userCode, URI verificationUri) {}
    record Profile(String name, UUID uuid, String accessToken, String refreshToken, String clientId, long expiresAt) {}
    private record Saved(String uuid, String protectedRefreshToken, String clientId) {}
    private record Refresh(String access, String refresh) {}
    private record OAuthClient(String id, String deviceUrl, String tokenUrl, String scope, String ticketPrefix) {}

    static Profile signIn(Consumer<DeviceCode> showCode) throws IOException, InterruptedException {
        String configured = configuredClientId();
        OAuthClient client = configured == null ? LAUNCHER : oauthClient(configured);
        var parameters = new java.util.HashMap<>(Map.of("client_id", client.id, "scope", client.scope));
        if (client == LAUNCHER) parameters.put("response_type", "device_code");
        var request = form(client.deviceUrl, parameters);
        var code = requireSuccess(request, tr("Microsoft не выдал код", "Microsoft did not issue a code", "Microsoft не видав код"));
        String deviceCode = value(code, "device_code");
        String userCode = value(code, "user_code");
        String verificationUrl = value(code, "verification_uri");
        if (verificationUrl.isBlank() && client == LAUNCHER) verificationUrl = "https://www.microsoft.com/link";
        URI uri = verificationUri(verificationUrl);
        if (deviceCode.isBlank() || userCode.isBlank() || uri == null) throw invalidReply();
        int seconds = boundedInt(code, "expires_in", 900, 1, 1800);
        int interval = boundedInt(code, "interval", 5, 1, 30);
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        showCode.accept(new DeviceCode(userCode, uri));
        while (deadline - System.nanoTime() > Duration.ofSeconds(interval).toNanos()) {
            Thread.sleep(Duration.ofSeconds(interval));
            var reply = json(form(client.tokenUrl, Map.of("client_id", client.id, "grant_type",
                    "urn:ietf:params:oauth:grant-type:device_code", "device_code", deviceCode)));
            switch (value(reply.body, "error")) {
                case "authorization_pending" -> { }
                case "slow_down" -> interval += 5;
                case "authorization_declined", "access_denied" -> throw new IOException(
                        tr("Вход отменён в браузере", "Sign-in was cancelled in the browser", "Вхід скасовано в браузері"));
                case "expired_token", "code_expired" -> throw expired();
                default -> {
                    var token = refreshTokens(requireSuccess(reply, tr("Microsoft отклонил вход", "Microsoft sign-in failed", "Microsoft відхилив вхід")), "");
                    return minecraft(client, token);
                }
            }
        }
        throw expired();
    }

    static Profile refresh(String name) throws IOException, InterruptedException {
        Path file = path(name);
        Saved saved = MultiAccountFiles.read(file, Saved.class);
        if (saved == null) throw signInAgain();
        UUID uuid = minecraftUuid(saved.uuid);
        Profile session = SESSIONS.get(file);
        if (session != null && !uuid.equals(session.uuid)) session = null;
        if (session != null && session.expiresAt > System.currentTimeMillis() + 30_000) return session;

        // Profiles written before the client ID was saved were created with the configured Entra application.
        String id = saved.clientId == null ? configuredClientId() : saved.clientId;
        if (id == null) throw signInAgain();
        OAuthClient client = oauthClient(id);
        String refresh = readRefresh(saved, session);
        if (refresh.isBlank()) throw signInAgain();
        var reply = json(form(client.tokenUrl, Map.of("client_id", client.id, "scope", client.scope,
                "grant_type", "refresh_token", "refresh_token", refresh)));
        if ("invalid_grant".equals(value(reply.body, "error"))) throw signInAgain();
        Profile profile = minecraft(client, refreshTokens(requireSuccess(reply,
                tr("Не удалось обновить вход Microsoft", "Could not refresh Microsoft sign-in", "Не вдалося оновити вхід Microsoft")), refresh));
        if (!uuid.equals(profile.uuid)) throw signInAgain();
        save(name, profile);
        return profile;
    }

    private static Profile minecraft(OAuthClient client, Refresh token) throws IOException, InterruptedException {
        var xbox = requireSuccess(post("https://user.auth.xboxlive.com/user/authenticate", Map.of(
                "Properties", Map.of("AuthMethod", "RPS", "SiteName", "user.auth.xboxlive.com", "RpsTicket", client.ticketPrefix + token.access),
                "RelyingParty", "http://auth.xboxlive.com", "TokenType", "JWT")),
                tr("Xbox Live отклонил вход", "Xbox Live sign-in failed", "Xbox Live відхилив вхід"));
        String xboxToken = value(xbox, "Token");
        if (xboxToken.isBlank()) throw invalidReply();
        var xsts = requireSuccess(post("https://xsts.auth.xboxlive.com/xsts/authorize", Map.of(
                "Properties", Map.of("SandboxId", "RETAIL", "UserTokens", new String[] {xboxToken}),
                "RelyingParty", "rp://api.minecraftservices.com/", "TokenType", "JWT")),
                tr("Проверь профиль Xbox и семейные настройки", "Check the Xbox profile and family settings", "Перевір профіль Xbox і сімейні налаштування"));
        String xstsToken = value(xsts, "Token");
        String userHash = xboxUserHash(xsts);
        if (xstsToken.isBlank() || userHash.isBlank() || !userHash.equals(xboxUserHash(xbox))) throw invalidReply();
        var minecraft = requireSuccess(post("https://api.minecraftservices.com/authentication/login_with_xbox",
                Map.of("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken)),
                tr("Minecraft Services отклонил вход", "Minecraft Services sign-in failed", "Minecraft Services відхилив вхід"));
        String access = value(minecraft, "access_token");
        if (access.isBlank()) throw invalidReply();
        var request = request("https://api.minecraftservices.com/minecraft/profile")
                .header("Authorization", "Bearer " + access).GET().build();
        var profile = requireSuccess(request, tr("Не удалось получить профиль Minecraft Java", "Could not load the Minecraft Java profile",
                "Не вдалося отримати профіль Minecraft Java"));
        String name = value(profile, "name");
        if (!AltManager.isValidUsername(name)) throw invalidReply();
        int expiresIn = boundedInt(minecraft, "expires_in", 3600, 1, 86400);
        return new Profile(name, minecraftUuid(value(profile, "id")), access, token.refresh,
                client.id, System.currentTimeMillis() + expiresIn * 1000L);
    }

    static boolean exists(String name) {
        return Files.isRegularFile(path(name));
    }

    static void save(String name, Profile profile) throws IOException {
        Path file = path(name);
        Files.createDirectories(file.getParent());
        String protectedRefresh = "";
        if (Platform.isWindows() && !profile.refreshToken.isBlank()) {
            try {
                byte[] protectedToken = Crypt32Util.cryptProtectData(profile.refreshToken.getBytes(StandardCharsets.UTF_8));
                protectedRefresh = Base64.getEncoder().encodeToString(protectedToken);
            } catch (RuntimeException | LinkageError failure) {
                throw new IOException(tr("Не удалось защитить вход в Windows", "Could not protect the sign-in on Windows",
                        "Не вдалося захистити вхід у Windows"), failure);
            }
        }
        MultiAccountFiles.write(file, new Saved(profile.uuid.toString(), protectedRefresh, profile.clientId));
        SESSIONS.put(file, profile);
    }

    static void forget(String name) throws IOException {
        Path file = path(name);
        Files.deleteIfExists(file);
        SESSIONS.remove(file);
    }

    private static String readRefresh(Saved saved, Profile session) throws IOException {
        if (session != null && !session.refreshToken.isBlank()) return session.refreshToken;
        if (!Platform.isWindows() || saved.protectedRefreshToken == null || saved.protectedRefreshToken.isBlank()) return "";
        try {
            return new String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(saved.protectedRefreshToken)), StandardCharsets.UTF_8);
        } catch (RuntimeException | LinkageError ignored) {
            throw signInAgain();
        }
    }

    private static Path path(String name) {
        return MultiAccountProfile.accountDirectory(name).resolve("microsoft.json");
    }

    private static String configuredClientId() throws IOException {
        String configured = System.getProperty("aero.microsoft.clientId");
        if (configured == null || configured.isBlank()) configured = System.getenv("AERO_MICROSOFT_CLIENT_ID");
        if (configured == null || configured.isBlank()) {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("aerovisuals-microsoft-client-id.txt");
            if (Files.isRegularFile(file)) configured = Files.readString(file).strip();
        }
        return configured == null || configured.isBlank() ? null : configured.strip();
    }

    private static OAuthClient oauthClient(String id) throws IOException {
        if (LAUNCHER.id.equalsIgnoreCase(id)) return LAUNCHER;
        if (!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
            throw new IOException(tr("Неверный Application ID Microsoft", "Invalid Microsoft Application ID", "Невірний Application ID Microsoft"));
        }
        return new OAuthClient(id, AUTH_BASE + "devicecode", AUTH_BASE + "token",
                "XboxLive.SignIn XboxLive.offline_access", "d=");
    }

    private static Refresh refreshTokens(JsonObject response, String fallback) throws IOException {
        String access = value(response, "access_token");
        String refresh = value(response, "refresh_token");
        if (refresh.isBlank()) refresh = fallback;
        if (access.isBlank()) throw invalidReply();
        return new Refresh(access, refresh);
    }

    private static UUID minecraftUuid(String value) throws IOException {
        if (value == null) throw invalidReply();
        if (value.matches("[0-9a-fA-F]{32}")) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                    + "-" + value.substring(16, 20) + "-" + value.substring(20);
        }
        try { return UUID.fromString(value); } catch (IllegalArgumentException invalid) { throw invalidReply(); }
    }

    private static URI verificationUri(String value) {
        try {
            var uri = URI.create(value);
            String host = uri.getHost();
            if ("https".equals(uri.getScheme()) && uri.getUserInfo() == null
                    && ("www.microsoft.com".equalsIgnoreCase(host) || "login.microsoftonline.com".equalsIgnoreCase(host)
                    || "microsoft.com".equalsIgnoreCase(host) || "login.live.com".equalsIgnoreCase(host))) return uri;
        } catch (IllegalArgumentException invalid) { }
        return null;
    }

    private static String xboxUserHash(JsonObject xsts) {
        try { return value(xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject(), "uhs"); }
        catch (RuntimeException invalid) { return ""; }
    }

    private static int boundedInt(JsonObject object, String key, int fallback, int min, int max) {
        try { return Math.clamp(object.get(key).getAsInt(), min, max); }
        catch (RuntimeException invalid) { return fallback; }
    }

    private static String value(JsonObject obj, String key) {
        var element = obj.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static HttpRequest.Builder request(String endpoint) {
        return HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
    }

    private static HttpRequest form(String endpoint, Map<String, String> values) {
        String body = values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        return request(endpoint).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private static HttpRequest post(String endpoint, Object body) {
        var builder = request(endpoint).header("Content-Type", "application/json");
        if (URI.create(endpoint).getHost().endsWith(".xboxlive.com")) builder.header("x-xbl-contract-version", "1");
        return builder.POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(body))).build();
    }

    private record Reply(int status, JsonObject body) {}

    private static Reply json(HttpRequest request) throws IOException, InterruptedException {
        var response = Web.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        try {
            return new Reply(response.statusCode(), JsonParser.parseString(response.body()).getAsJsonObject());
        } catch (RuntimeException invalid) {
            if (response.statusCode() >= 200 && response.statusCode() < 300) throw invalidReply();
            return new Reply(response.statusCode(), new JsonObject());
        }
    }

    private static JsonObject requireSuccess(HttpRequest request, String message) throws IOException, InterruptedException {
        return requireSuccess(json(request), message);
    }

    private static JsonObject requireSuccess(Reply reply, String message) throws IOException {
        if (reply.status < 200 || reply.status >= 300) throw new IOException(message + " (HTTP " + reply.status + ")");
        return reply.body;
    }

    private static IOException invalidReply() {
        return new IOException(tr("Некорректный ответ авторизации", "Unexpected authentication response", "Некоректна відповідь авторизації"));
    }

    private static IOException expired() {
        return new IOException(tr("Код истёк. Начни вход заново", "Code expired. Start sign-in again", "Код прострочено. Почни вхід знову"));
    }

    private static IOException signInAgain() {
        return new IOException(tr("Повтори вход через кнопку Microsoft", "Sign in again with the Microsoft button", "Повтори вхід через кнопку Microsoft"));
    }

    private static String tr(String ru, String en, String uk) { return LocalText.pick(ru, en, uk); }
}
