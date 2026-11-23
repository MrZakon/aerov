package client.network;

import client.concurrent.MainThread;
import client.data.ConfigEntry;
import client.util.DateUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigApi {
   private static final Gson gson = new Gson();
   private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(2500)).build();
   private static final Map<String, Integer> idByName = new ConcurrentHashMap<>();
   private static final Map<String, ConfigEntry> cacheByName = new ConcurrentHashMap<>();
   private static volatile List<ConfigEntry> cachedConfigs = Collections.emptyList();
   private static volatile String activeConfig = "";
   private static volatile String activeBaseUrl = null;

   private ConfigApi() {}

   private static List<String> candidateBaseUrls() {
      List<String> list = new ArrayList<>();
      String current = activeBaseUrl;
      if (current != null && !current.isBlank()) {
         list.add(current);
      }
      String explicit = System.getProperty("lunacy.apiBaseUrl", System.getenv("LUNACY_API_BASE_URL"));
      if (explicit != null && !explicit.isBlank()) {
         String clean = explicit.replaceAll("/+$", "");
         if (!list.contains(clean)) list.add(clean);
      }
      if (!list.contains("https://lunacyvisual.fun")) list.add("https://lunacyvisual.fun");
      if (!list.contains("http://127.0.0.1:15005")) list.add("http://127.0.0.1:15005");
      if (!list.contains("http://localhost:15005")) list.add("http://localhost:15005");
      return list;
   }

   private static String readToken() {
      String sessionFile = System.getProperty("lunacy.sessionFile", System.getenv("LUNACY_SESSION_FILE"));
      if (sessionFile != null && !sessionFile.isBlank()) {
         String token = extractToken(Path.of(sessionFile));
         if (!token.isBlank()) return token;
      }
      String appdata = System.getenv("APPDATA");
      if (appdata != null && !appdata.isBlank()) {
         String token = extractToken(Path.of(appdata, "Lunacy Launcher", "lunacy-session.json"));
         if (!token.isBlank()) return token;
         token = extractToken(Path.of(appdata, ".lunacy", "lunacy-session.json"));
         if (!token.isBlank()) return token;
      }
      String userHome = System.getProperty("user.home");
      if (userHome != null && !userHome.isBlank()) {
         String token = extractToken(Path.of(userHome, ".lunacy", "lunacy-session.json"));
         if (!token.isBlank()) return token;
      }
      return "";
   }

   private static String extractToken(Path path) {
      if (!Files.exists(path)) return "";
      try {
         String json = Files.readString(path, StandardCharsets.UTF_8);
         JsonObject session = JsonParser.parseString(json).getAsJsonObject();
         return session.has("token") && !session.get("token").isJsonNull()
                 ? session.get("token").getAsString().trim() : "";
      } catch (Exception ignored) {
         return "";
      }
   }

   private static HttpResponse<String> sendRequest(String method, String endpoint, String jsonBody) {
      String token = readToken();
      for (String baseUrl : candidateBaseUrls()) {
         try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + endpoint))
                    .timeout(Duration.ofMillis(3000))
                    .header("Accept", "application/json");
            if (!token.isBlank()) {
               builder.header("Authorization", "Bearer " + token);
            }
            if (jsonBody != null) {
               builder.header("Content-Type", "application/json; charset=utf-8");
               builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            } else {
               builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 == 2 || response.statusCode() == 404 || response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403) {
               activeBaseUrl = baseUrl;
               return response;
            }
         } catch (Exception ignored) {
         }
      }
      return null;
   }

   private static ConfigEntry parseConfigEntry(JsonObject obj) {
      ConfigEntry entry = new ConfigEntry();
      String name = getStringByJsonObject(obj, "name", "config_name");
      if (name != null) entry.setText(name);

      String desc = getStringByJsonObject(obj, "description", "desc");
      if (desc != null) entry.setText2(desc);

      String shareCode = getStringByJsonObject(obj, "share_code", "shareCode", "config_key", "code");
      if (shareCode != null) entry.setText3(shareCode);

      String createdAt = getStringByJsonObject(obj, "created_at", "createdAt");
      if (createdAt != null) {
         String formatted = DateUtil.getStringByString3(createdAt);
         entry.setText4(formatted != null ? formatted : createdAt);
      }

      String updatedAt = getStringByJsonObject(obj, "updated_at", "updatedAt");
      if (updatedAt != null) entry.setText5(updatedAt);

      if (obj.has("is_private") && !obj.get("is_private").isJsonNull()) {
         entry.setFlag2(obj.get("is_private").getAsBoolean());
      }
      if (obj.has("is_imported") && !obj.get("is_imported").isJsonNull()) {
         entry.setFlag3(obj.get("is_imported").getAsBoolean());
      }
      return entry;
   }

   private static String getStringByJsonObject(JsonObject obj, String... keys) {
      for (String k : keys) {
         if (obj.has(k) && !obj.get(k).isJsonNull()) {
            return obj.get(k).getAsString();
         }
      }
      return null;
   }

   public static void update() {
      getList3();
   }

   public static void update2() {
      getJsonArray();
   }

   public static String getString() {
      if (!activeConfig.isBlank()) {
         return activeConfig;
      }
      List<ConfigEntry> list = getList3();
      if (!list.isEmpty() && list.get(0).getText() != null) {
         activeConfig = list.get(0).getText();
         return activeConfig;
      }
      return null;
   }

   public static String getStringByString(String text) {
      if (text == null || text.isBlank()) return "";
      ConfigEntry cached = cacheByName.get(text);
      if (cached != null && cached.getText3() != null && !cached.getText3().isBlank()) {
         return cached.getText3();
      }
      for (ConfigEntry entry : getList3()) {
         if (text.equals(entry.getText()) && entry.getText3() != null && !entry.getText3().isBlank()) {
            return entry.getText3();
         }
      }
      return "";
   }

   public static String getStringByString2(String text) {
      return getStringByString(text);
   }

   public static String getStringByString3(String text) {
      if (text == null || text.isBlank()) return null;
      try {
         Integer id = idByName.get(text);
         if (id == null) {
            getList3();
            id = idByName.get(text);
         }
         if (id != null) {
            HttpResponse<String> resp = sendRequest("GET", "/api/profile/configs/" + id + "/download", null);
            if (resp != null && resp.statusCode() == 200) {
               activeConfig = text;
               return resp.body();
            }
         }
         String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
         HttpResponse<String> resp = sendRequest("GET", "/api/profile/configs/by-name/" + encoded + "/download", null);
         if (resp != null && resp.statusCode() == 200) {
            activeConfig = text;
            return resp.body();
         }
      } catch (Exception ignored) {
      }
      return null;
   }

   public static boolean isString(String text) {
      if (text == null || text.isBlank()) return false;
      activeConfig = text;
      return true;
   }

   public static boolean isString2(String text) {
      if (text == null) return false;
      try {
         JsonObject body = new JsonObject();
         body.addProperty("friends", text);
         HttpResponse<String> resp = sendRequest("POST", "/api/profile/friends/sync", gson.toJson(body));
         return resp != null && (resp.statusCode() == 200 || resp.statusCode() == 201);
      } catch (Exception ignored) {
         return false;
      }
   }

   public static boolean unfriend(String username) {
      if (username == null || username.isBlank()) return false;
      try {
         JsonObject body = new JsonObject();
         body.addProperty("username", username.trim());
         HttpResponse<String> resp = sendRequest("POST", "/api/profile/friends/unfriend", gson.toJson(body));
         return resp != null && (resp.statusCode() == 200 || resp.statusCode() == 204 || resp.statusCode() == 404);
      } catch (Exception ignored) {
         return false;
      }
   }

   public static boolean isString3(String text) {
      if (text == null || text.isBlank()) return false;
      try {
         Integer id = idByName.get(text);
         if (id == null) {
            getList3();
            id = idByName.get(text);
         }
         if (id != null) {
            HttpResponse<String> resp = sendRequest("DELETE", "/api/profile/configs/" + id, null);
            if (resp != null && (resp.statusCode() == 200 || resp.statusCode() == 204)) {
               idByName.remove(text);
               cacheByName.remove(text);
               if (text.equals(activeConfig)) activeConfig = "";
               getList3();
               return true;
            }
         }
         String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
         HttpResponse<String> resp = sendRequest("DELETE", "/api/profile/configs/by-name/" + encoded, null);
         if (resp != null && (resp.statusCode() == 200 || resp.statusCode() == 204)) {
            idByName.remove(text);
            cacheByName.remove(text);
            if (text.equals(activeConfig)) activeConfig = "";
            getList3();
            return true;
         }
      } catch (Exception ignored) {
      }
      return false;
   }

   public static boolean isStringString(String text, String text2) {
      if (text == null || text2 == null) return false;
      try {
         Path dir = Path.of("lunacy", "waypoints");
         if (!Files.exists(dir)) Files.createDirectories(dir);
         String safe = text2.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
         Files.writeString(dir.resolve(safe), text, StandardCharsets.UTF_8);
         return true;
      } catch (Exception ignored) {
         return false;
      }
   }

   public static boolean isStringString2(String text, String text2) {
      if (text == null || text2 == null) return false;
      try {
         JsonObject body = new JsonObject();
         body.addProperty("old_name", text2);
         body.addProperty("new_name", text);
         HttpResponse<String> resp = sendRequest("POST", "/api/profile/configs/rename", gson.toJson(body));
         if (resp != null && (resp.statusCode() == 200 || resp.statusCode() == 204)) {
            Integer id = idByName.remove(text2);
            if (id != null) idByName.put(text, id);
            ConfigEntry cached = cacheByName.remove(text2);
            if (cached != null) {
               cached.setText(text);
               cacheByName.put(text, cached);
            }
            if (text2.equals(activeConfig)) activeConfig = text;
            getList3();
            return true;
         }
      } catch (Exception ignored) {
      }
      return false;
   }

   public static void isStringString3(String text, String text2) {
      if (text == null) return;
      try {
         JsonObject body = new JsonObject();
         body.addProperty("name", text);
         body.addProperty("description", text2 != null ? text2 : "");
         sendRequest("POST", "/api/profile/configs/description", gson.toJson(body));
         ConfigEntry cached = cacheByName.get(text);
         if (cached != null) cached.setText2(text2);
      } catch (Exception ignored) {
      }
   }

   public static void isStringString4(String text, String text2) {
   }

   public static boolean isStringString5(String text, String text2) {
      if (text == null || text.isBlank()) return false;
      try {
         JsonObject body = new JsonObject();
         body.addProperty("name", text);
         body.addProperty("configData", text2 != null ? text2 : "");
         HttpResponse<String> resp = sendRequest("POST", "/api/profile/configs", gson.toJson(body));
         if (resp != null && (resp.statusCode() == 200 || resp.statusCode() == 201)) {
            activeConfig = text;
            getList3();
            return true;
         }
      } catch (Exception ignored) {
      }
      return false;
   }

   public static void isBooleanString(boolean flag, String text) {
      if (text == null || text.isBlank()) return;
      ConfigEntry entry = cacheByName.get(text);
      if (entry != null) {
         entry.setFlag2(flag);
      }
      MainThread.onRunnable2(() -> {
         try {
            JsonObject body = new JsonObject();
            body.addProperty("name", text);
            body.addProperty("is_private", flag);
            sendRequest("POST", "/api/profile/configs/privacy", gson.toJson(body));
         } catch (Exception ignored) {
         }
      });
   }

   public static ConfigEntry getConfigEntryByString(String text) {
      if (text == null || text.isBlank()) return null;
      try {
         JsonObject body = new JsonObject();
         body.addProperty("share_code", text.trim().toUpperCase(Locale.ROOT));
         HttpResponse<String> resp = sendRequest("POST", "/api/profile/configs/import", gson.toJson(body));
         if (resp != null && (resp.statusCode() == 200 || resp.statusCode() == 201)) {
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
               JsonObject obj = root.getAsJsonObject("data");
               ConfigEntry entry = parseConfigEntry(obj);
               if (obj.has("id")) {
                  idByName.put(entry.getText(), obj.get("id").getAsInt());
               }
               cacheByName.put(entry.getText(), entry);
               activeConfig = entry.getText();
               getList3();
               return entry;
            }
         }
      } catch (Exception ignored) {
      }
      return null;
   }

   public static ConfigEntry getConfigEntryByStringStringString(String s1, String s2, String s3) {
      String data = s1 != null ? s1 : "";
      String description = s2 != null ? s2 : "";
      String name = s3 != null ? s3 : "";
      if (name.isBlank() && !description.isBlank()) {
         name = description;
         description = "";
      }
      if (name.isBlank()) {
         name = "Config " + System.currentTimeMillis();
      }
      try {
         JsonObject body = new JsonObject();
         body.addProperty("name", name);
         body.addProperty("description", description);
         body.addProperty("configData", data);
         HttpResponse<String> resp = sendRequest("POST", "/api/profile/configs", gson.toJson(body));
         if (resp != null && (resp.statusCode() == 200 || resp.statusCode() == 201)) {
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
               JsonObject obj = root.getAsJsonObject("data");
               ConfigEntry entry = parseConfigEntry(obj);
               if (obj.has("id")) {
                  idByName.put(entry.getText(), obj.get("id").getAsInt());
               }
               cacheByName.put(entry.getText(), entry);
               activeConfig = entry.getText();
               getList3();
               return entry;
            }
         }
      } catch (Exception ignored) {
      }
      ConfigEntry fallback = new ConfigEntry();
      fallback.setText(name);
      fallback.setText2(description);
      cacheByName.put(name, fallback);
      activeConfig = name;
      return fallback;
   }

   public static List<ConfigEntry> getList3() {
      try {
         HttpResponse<String> resp = sendRequest("GET", "/api/profile/configs", null);
         if (resp != null && resp.statusCode() == 200) {
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
               JsonArray data = root.getAsJsonArray("data");
               List<ConfigEntry> list = new ArrayList<>();
               idByName.clear();
               cacheByName.clear();
               for (JsonElement el : data) {
                  if (el.isJsonObject()) {
                     JsonObject obj = el.getAsJsonObject();
                     ConfigEntry entry = parseConfigEntry(obj);
                     list.add(entry);
                     if (entry.getText() != null) {
                        cacheByName.put(entry.getText(), entry);
                        if (obj.has("id") && !obj.get("id").isJsonNull()) {
                           idByName.put(entry.getText(), obj.get("id").getAsInt());
                        }
                     }
                  }
               }
               cachedConfigs = list;
               saveDiskCache(data.toString());
               return list;
            }
         }
      } catch (Exception ignored) {
      }
      if (cachedConfigs == null) {
         loadDiskCache();
      }
      return cachedConfigs != null ? new ArrayList<>(cachedConfigs) : Collections.emptyList();
   }

   private static void saveDiskCache(String json) {
      try {
         Path dir = Path.of("lunacy");
         if (!Files.exists(dir)) Files.createDirectories(dir);
         Files.writeString(dir.resolve("cloud_configs_cache.json"), json, StandardCharsets.UTF_8);
      } catch (Exception ignored) {
      }
   }

   private static void loadDiskCache() {
      try {
         Path file = Path.of("lunacy", "cloud_configs_cache.json");
         if (Files.exists(file)) {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonArray()) {
               List<ConfigEntry> list = new ArrayList<>();
               idByName.clear();
               cacheByName.clear();
               for (JsonElement item : el.getAsJsonArray()) {
                  if (item.isJsonObject()) {
                     JsonObject obj = item.getAsJsonObject();
                     ConfigEntry entry = parseConfigEntry(obj);
                     list.add(entry);
                     if (entry.getText() != null) {
                        cacheByName.put(entry.getText(), entry);
                        if (obj.has("id") && !obj.get("id").isJsonNull()) {
                           idByName.put(entry.getText(), obj.get("id").getAsInt());
                        }
                     }
                  }
               }
               cachedConfigs = list;
            }
         }
      } catch (Exception ignored) {
      }
   }

   public static List<ConfigEntry> getListByString(String text) {
      if (text == null || text.isBlank()) return Collections.emptyList();
      ConfigEntry entry = cacheByName.get(text);
      if (entry != null) {
         return List.of(entry);
      }
      for (ConfigEntry e : getList3()) {
         if (text.equals(e.getText())) {
            return List.of(e);
         }
      }
      return Collections.emptyList();
   }

   public static Map<String, Object> getMap() {
      return Collections.emptyMap();
   }

   public static Map<String, String> getMapByString(String text) {
      return Collections.emptyMap();
   }

   public static JsonArray getJsonArray() {
      try {
         HttpResponse<String> resp = sendRequest("GET", "/api/profile/friends/list", null);
         if (resp != null && resp.statusCode() == 200) {
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonArray()) {
               JsonArray raw = root.getAsJsonArray("data");
               JsonArray result = new JsonArray();
               for (JsonElement el : raw) {
                  if (el.isJsonObject()) {
                     JsonObject obj = el.getAsJsonObject();
                     String username = getStringByJsonObject(obj, "username", "name");
                     if (username != null && !username.isBlank()) {
                        JsonObject friend = new JsonObject();
                        friend.addProperty("name", username);
                        friend.addProperty("nickname", username);
                        String created = getStringByJsonObject(obj, "created_at", "createdAt");
                        if (created != null) {
                           friend.addProperty("created_at", created);
                        }
                        result.add(friend);
                     }
                  }
               }
               return result;
            }
         }
      } catch (Exception ignored) {
      }
      return null;
   }

   public static JsonArray getJsonArrayByString(String text) {
      if (text == null) return new JsonArray();
      try {
         Path dir = Path.of("lunacy", "waypoints");
         String safe = text.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
         Path file = dir.resolve(safe);
         if (Files.exists(file)) {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonArray()) {
               return el.getAsJsonArray();
            }
         }
      } catch (Exception ignored) {
      }
      return new JsonArray();
   }
}
