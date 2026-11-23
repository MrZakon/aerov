package client.data;

import client.audio.SoundEngine;
import client.concurrent.MainThread;
import client.concurrent.SystemClient;
import client.enums.SoundEvent;
import client.network.ConfigApi;
import client.util.DateUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.aero.visuals.ClientRuntime;
import net.aero.visuals.cosmetic.CosmeticSyncService.WebsiteAccess;
import net.aero.visuals.guard.AeroGuard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class SystemFriend {
   private static final SystemFriend INSTANCE = new SystemFriend();
   private final Set<String> set = ConcurrentHashMap.newKeySet();
   private final Map<String, String> map = new ConcurrentHashMap<>();
   private final Map<String, String> map2 = new ConcurrentHashMap<>();
   private final Map<String, String> map3 = new ConcurrentHashMap<>();
   private WebsiteAccess serverAccess;

   private boolean hasCurrentServerAccess() {
      var runtime = ClientRuntime.getNullable();
      return this.serverAccess != null && AeroGuard.allowed() && runtime != null && runtime.cosmeticSync() != null
         && this.serverAccess.equals(runtime.cosmeticSync().friendAccess());
   }

   public void onStringString(String text, String text2) {
      if (text != null && !text.trim().isEmpty()) {
         String s = text.trim().toLowerCase(java.util.Locale.ROOT);
         if (text2 != null && !text2.trim().isEmpty() && !text2.trim().equals("SystemFriend")) {
            this.map2.put(s, text2.trim());
         } else {
            this.map2.remove(s);
         }
      }
   }

   public void onString(String text) {
      if (this.isString(text)) {
         this.removeString(text);
      } else {
         this.addString2(text);
      }
   }

   public void onStringString2(String text, String text2) {
      if (text != null && !text.trim().isEmpty()) {
         String s = text.trim().toLowerCase(java.util.Locale.ROOT);
         if (text2 != null && !text2.trim().isEmpty()) {
            this.map.put(s, text2.trim());
         } else {
            this.map.remove(s);
         }
      }
   }

   public synchronized Map<String, String> getMap() {
      return this.hasCurrentServerAccess() ? new HashMap<>(this.map2) : new HashMap<>();
   }

   public synchronized String getStringByString(String text) {
      if (text == null || !this.hasCurrentServerAccess()) {
         return "SystemFriend";
      } else {
         String s = this.map2.get(text.trim().toLowerCase(java.util.Locale.ROOT));
         return s == null ? "SystemFriend" : s;
      }
   }

   private void update() {
      try {
         SystemClient systemclient = SystemClient.getInstance();
         if (systemclient != null && systemclient.getHashUtil() != null) {
            systemclient.getHashUtil().update5();
         }
      } catch (Exception exception) {
      }
   }

   public void addString(String text) {
      if (text != null && !text.trim().isEmpty()) {
         String s = text.trim().toLowerCase(java.util.Locale.ROOT);
         this.set.add(s);
         this.map3.putIfAbsent(s, DateUtil.getString());
      }
   }

   public String getStringByString2(String text) {
      if (text == null || text.trim().isEmpty()) return DateUtil.getString();
      String date;
      boolean changed;
      synchronized (this) {
         if (!this.hasCurrentServerAccess()) return DateUtil.getString();
         String key = text.trim().toLowerCase(java.util.Locale.ROOT);
         String previous = this.map3.get(key);
         date = getStringByString3(previous);
         changed = !date.equals(previous);
         if (changed) this.map3.put(key, date);
      }
      if (changed) this.update();
      return date;
   }

   public void onStringString3(String text, String text2) {
      if (text != null && !text.trim().isEmpty()) {
         String s = text.trim().toLowerCase(java.util.Locale.ROOT);
         if (text2 != null && !text2.trim().isEmpty() && !text2.trim().equals("SystemFriend")) {
            this.map2.put(s, text2.trim());
         } else {
            this.map2.remove(s);
         }

         this.update();
      }
   }

   public synchronized Map<String, String> getMap2() {
      return this.hasCurrentServerAccess() ? new HashMap<>(this.map3) : new HashMap<>();
   }

   public void removeString(String text) {var r=net.aero.visuals.ClientRuntime.getNullable();if(r!=null&&r.friends()!=null&&text!=null)r.friends().removeByName(text);}

   public void update2() { var runtime=net.aero.visuals.ClientRuntime.getNullable();if(runtime!=null&&runtime.friends()!=null)runtime.friends().refresh(); }
   public synchronized void replaceServerFriends(net.aero.visuals.friend.FriendsService.Snapshot snapshot, WebsiteAccess owner){
      if (!Objects.equals(this.serverAccess, owner)) this.update3();
      java.util.Set<String> current=new java.util.HashSet<>();
      java.util.Map<String,String> dates=new java.util.HashMap<>();
      for(var friend:snapshot.friends()){
         java.util.Set<String> names=new java.util.HashSet<>(friend.minecraftNames());names.add(friend.name());
         for(String name:names){String key=name.toLowerCase(java.util.Locale.ROOT);current.add(key);if(!friend.createdAt().isBlank())dates.put(key,friend.createdAt());}
      }
      // Keep aliases for existing friends; discard them only when friendship ends.
      this.set.addAll(current);this.set.retainAll(current);
      this.map.keySet().retainAll(current);this.map2.keySet().retainAll(current);
      this.map3.putAll(dates);this.map3.keySet().retainAll(current);
      this.serverAccess = owner;
   }
   private static String getStringByString3(String text) {
      String s = DateUtil.getStringByString(text);
      return s == null ? DateUtil.getString() : s;
   }

   public void removeString2(String text) {var r=net.aero.visuals.ClientRuntime.getNullable();if(r!=null&&r.friends()!=null&&text!=null)r.friends().removeByName(text);}

   public void onStringString4(String text, String text2) {
      if (text != null && !text.trim().isEmpty()) {
         this.map3.put(text.trim().toLowerCase(java.util.Locale.ROOT), getStringByString3(text2));
      }
   }

   public boolean check() {update2();return net.aero.visuals.ClientRuntime.getNullable()!=null;}

   public synchronized String getString() {
      if (!this.hasCurrentServerAccess()) return "[]";
      ArrayList arraylist = new ArrayList();

      for (String s : this.set) {
         HashMap hashmap = new HashMap();
         hashmap.put("name", s);
         String s1 = this.map.get(s);
         if (s1 != null) {
            hashmap.put("nickname", s1);
         }

         String s2 = this.map2.get(s);
         if (s2 != null) {
            hashmap.put("alias", s2);
         }

         String s3 = this.map3.get(s);
         if (s3 != null) {
            hashmap.put("created_at", s3);
         }

         arraylist.add(hashmap);
      }

      return new Gson().toJson(arraylist);
   }

   public synchronized Set<String> getSet() {
      return this.hasCurrentServerAccess() ? new HashSet<>(this.set) : new HashSet<>();
   }

   public void onStringString5(String text, String text2) {
      if (text != null && !text.trim().isEmpty()) {
         String s = text.trim().toLowerCase(java.util.Locale.ROOT);
         if (text2 != null && !text2.trim().isEmpty()) {
            this.map.put(s, text2.trim());
         } else {
            this.map.remove(s);
         }

         this.update();
      }
   }

   private static String getStringByJsonObjectString(JsonObject jsonObject, String text) {
      return jsonObject.has(text) && !jsonObject.get(text).isJsonNull() ? jsonObject.get(text).getAsString() : null;
   }

   public static SystemFriend getInstance() {
      return INSTANCE;
   }

   public boolean isLivingEntity(LivingEntity livingEntity) {
      if (livingEntity == null) {
         return false;
      } else {
         return livingEntity instanceof Player playerentity ? this.isPlayerEntity(playerentity) : false;
      }
   }

   public synchronized boolean isString(String text) {
      return text != null && !text.trim().isEmpty() && this.hasCurrentServerAccess()
         && this.set.contains(text.trim().toLowerCase(java.util.Locale.ROOT));
   }

   public void addString2(String text){var r=net.aero.visuals.ClientRuntime.getNullable();if(r!=null&&r.friends()!=null&&text!=null&&!text.isBlank())r.friends().request(text);}

   public boolean isPlayerEntity(Player playerEntity) {
      return playerEntity == null ? false : this.isString(playerEntity.getName().getString());
   }

   public synchronized Map<String, String> getMap3() {
      return this.hasCurrentServerAccess() ? new HashMap<>(this.map) : new HashMap<>();
   }

   public synchronized String getStringByString4(String text) {
      return text == null || !this.hasCurrentServerAccess() ? null : this.map.get(text.trim().toLowerCase(java.util.Locale.ROOT));
   }

   public synchronized void update3() {
      this.serverAccess = null;
      this.set.clear();
      this.map.clear();
      this.map2.clear();
      this.map3.clear();
   }
}
