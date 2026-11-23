package net.aero.visuals.alt;

import net.fabricmc.loader.api.FabricLoader;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages alternate offline accounts, persistence in lunacy_alts.txt,
 * and seamless on-the-fly session switching without restarting launcher or game.
 */
public final class AltManager {
    private static final List<String> ALTS = new CopyOnWriteArrayList<>();
    private static final Path FILE = getStoragePath();
    private static boolean loaded = false;
    private static final Random RANDOM = new Random();

    private AltManager() {}

    private static Path getStoragePath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("lunacy_alts.txt");
        } catch (Throwable t) {
            return Path.of("lunacy_alts.txt");
        }
    }

    public static synchronized void load() {
        if (loaded) return;
        ALTS.clear();
        try {
            if (Files.exists(FILE)) {
                List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String clean = line.trim();
                    if (!clean.isEmpty() && !ALTS.contains(clean)) {
                        ALTS.add(clean);
                    }
                }
            }
        } catch (IOException e) {
            AeroVisuals.LOGGER.warn("Failed to load AeroVisuals alts", e);
        }
        loaded = true;
    }

    public static synchronized void save() {
        try {
            if (FILE.getParent() != null) {
                Files.createDirectories(FILE.getParent());
            }
            Files.write(FILE, ALTS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AeroVisuals.LOGGER.warn("Failed to save AeroVisuals alts", e);
        }
    }

    public static List<String> getAlts() {
        if (!loaded) load();
        return Collections.unmodifiableList(ALTS);
    }

    public static boolean isValidUsername(String name) {
        return name != null && name.matches("^[a-zA-Z0-9_]{3,16}$");
    }

    public static boolean addAlt(String name) {
        if (!loaded) load();
        String clean = name == null ? "" : name.trim();
        if (!isValidUsername(clean)) return false;
        if (!ALTS.contains(clean)) {
            ALTS.add(clean);
            save();
            return true;
        }
        return false;
    }

    public static boolean removeAlt(String name) {
        if (!loaded) load();
        if (ALTS.remove(name)) {
            save();
            return true;
        }
        return false;
    }

    public static void clearAlts() {
        if (!loaded) load();
        ALTS.clear();
        save();
    }

    public static String randomName() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder("Lnc_");
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static boolean login(String name) {
        if (name == null) return false;
        String clean = name.trim();
        if (!isValidUsername(clean)) return false;

        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + clean).getBytes(StandardCharsets.UTF_8));
            Session newSession = new Session(clean, uuid, "", Optional.empty(), Optional.empty());
            MinecraftClient client = MinecraftClient.getInstance();
            ((MinecraftClientAccessor) client).aero$setSession(newSession);
            addAlt(clean);
            AeroVisuals.LOGGER.info("Switched to alt account: {}", clean);
            return true;
        } catch (Throwable t) {
            AeroVisuals.LOGGER.error("Failed to switch alt account: {}", clean, t);
            return false;
        }
    }

    public static String getCurrentUsername() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSession() != null) {
            return client.getSession().getUsername();
        }
        return "Player";
    }
}
