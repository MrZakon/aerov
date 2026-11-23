package net.aero.visuals.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StudioProfilesTest {
 @org.junit.jupiter.api.io.TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER)
 java.nio.file.Path directory;

 @Test void failedSwitchCanBeRetriedAfterProfileIsRepaired() throws Exception {
     ConfigManager config = new ConfigManager(directory.resolve("config.json"),
             new net.aero.visuals.module.ModuleManager(new net.aero.visuals.event.EventBus()));
     java.nio.file.Path profilesDir = directory.resolve("profiles");
     java.nio.file.Files.createDirectories(profilesDir);
     java.nio.file.Files.writeString(profilesDir.resolve("servers.properties"), "example=bound\n");
     var clock = new java.util.concurrent.atomic.AtomicLong();
     StudioProfiles profiles = new StudioProfiles(config, profilesDir, clock::get);
     assertThrows(java.io.IOException.class, () -> profiles.switchServer("example"));
     assertDoesNotThrow(() -> profiles.switchServer("example"));
     var saved = config.snapshot();
     saved.addProperty("menuKey", 66);
     java.nio.file.Files.writeString(profilesDir.resolve("bound.json"), saved.toString());
     try {
         clock.set(5_000_000_000L);
         profiles.switchServer("example");
         assertEquals(66, config.snapshot().get("menuKey").getAsInt());
         assertTrue(config.flushAndCheck());
         try (var reader = java.nio.file.Files.newBufferedReader(config.getConfigPath())) {
             var persisted = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
             assertEquals(344, persisted.get("menuKey").getAsInt());
         }
         assertEquals(66, config.snapshot().get("menuKey").getAsInt());
         profiles.switchServer("");
         assertEquals(344, config.snapshot().get("menuKey").getAsInt());
     } finally {
         net.aero.visuals.gui.ClickGuiScreen.setMenuKey(344);
     }
 }

 @Test void malformedProfileReportsIoFailureAndRollsBack() throws Exception {
     ConfigManager config = new ConfigManager(directory.resolve("config.json"),
             new net.aero.visuals.module.ModuleManager(new net.aero.visuals.event.EventBus()));
     StudioProfiles profiles = new StudioProfiles(config, directory.resolve("profiles"));
     var previous = config.snapshot();
     java.nio.file.Files.writeString(profiles.directory().resolve("broken.json"),
             "{\"modules\":{},\"menuKey\":{}}");
     assertThrows(java.io.IOException.class, () -> profiles.load("broken"));
     assertEquals(previous, config.snapshot());
 }
 @Test void acceptsPortableNames(){assertEquals("PVP 1",StudioProfiles.safeName(" PVP 1 "));assertEquals("Сакура",StudioProfiles.safeName("Сакура"));}
 @Test void rejectsTraversalAndEmptyNames(){assertThrows(IllegalArgumentException.class,()->StudioProfiles.safeName("../secret"));assertThrows(IllegalArgumentException.class,()->StudioProfiles.safeName(" "));}
 @Test void globalChangeCommitsOrRestoresTheActiveServerProfile() throws Exception {
     ConfigManager config = new ConfigManager(directory.resolve("config.json"),
             new net.aero.visuals.module.ModuleManager(new net.aero.visuals.event.EventBus()));
     int originalKey = net.aero.visuals.gui.ClickGuiScreen.menuKeyCode;
     try {
         net.aero.visuals.gui.ClickGuiScreen.setMenuKey(344);
         var server = config.snapshot();
         server.addProperty("menuKey", 66);
         config.beginServerProfile(server);
         var global = config.snapshot();
         global.addProperty("menuKey", 67);

         assertThrows(java.io.IOException.class, () -> config.commitGlobalChange(
                 () -> config.restorePortable(global), () -> { throw new java.io.IOException("Interface write failed"); }));
         assertEquals(66, config.snapshot().get("menuKey").getAsInt());
         assertEquals(344, savedMenuKey(config));

         config.commitGlobalChange(() -> config.restorePortable(global), () -> {});
         config.endServerProfile();
         assertEquals(67, config.snapshot().get("menuKey").getAsInt());
         assertEquals(67, savedMenuKey(config));
     } finally {
         net.aero.visuals.gui.ClickGuiScreen.setMenuKey(originalKey);
     }
 }

 private static int savedMenuKey(ConfigManager config) throws java.io.IOException {
     try (var reader = java.nio.file.Files.newBufferedReader(config.getConfigPath())) {
         return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("menuKey").getAsInt();
     }
 }

}
