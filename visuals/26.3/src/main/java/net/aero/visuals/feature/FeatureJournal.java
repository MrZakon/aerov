package net.aero.visuals.feature;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class FeatureJournal {
    public record Entry(long time,String text){}
    private static final int MAX_ENTRIES=200, MAX_TEXT=1000;
    // 200 x 1000 UTF-16 chars may require 1.2 MB after JSON escaping, plus metadata.
    private static final long MAX_FILE_BYTES=2_000_000;
    private static final Deque<Entry> entries=new ArrayDeque<>();
    private static boolean loaded,dirty;
    private static boolean saveImmediately;
    private static List<Entry> displaySnapshot;
    private static final long SAVE_INTERVAL = 3_000_000_000L;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static CompletableFuture<List<Entry>> pendingLoad;
    private static CompletableFuture<Boolean> pendingWrite;
    private static long lastWrite;
    private static final Path file=FabricLoader.getInstance().getConfigDir().resolve("aerovisuals-notifications.json");

    // Only callers holding the journal lock may publish the loaded history.
    private static void updateLoad() {
        if (loaded) return;
        if (pendingLoad == null) {
            pendingLoad = CompletableFuture.supplyAsync(FeatureJournal::readHistory,
                    task -> Thread.ofVirtual().name("Aero notification history read").start(task));
        }
        if (pendingLoad.isDone()) finishLoad();
    }

    private static List<Entry> readHistory() {
        try (var input = Files.newInputStream(file)) {
            byte[] data = input.readNBytes((int) MAX_FILE_BYTES + 1);
            if (data.length > MAX_FILE_BYTES) throw new java.io.IOException("Notification history exceeds 2 MB");
            var values = JSON.fromJson(new String(data, StandardCharsets.UTF_8), Entry[].class);
            if (values == null) return List.of();
            Deque<Entry> history = new ArrayDeque<>();
            for (Entry entry : values) {
                if (entry == null || entry.text() == null) continue;
                history.addLast(new Entry(entry.time(), limitText(entry.text())));
                if (history.size() > MAX_ENTRIES) history.removeFirst();
            }
            return List.copyOf(history.reversed());
        } catch (NoSuchFileException ignored) {
            return List.of();
        } catch (Exception failure) {
            net.aero.visuals.AeroVisuals.LOGGER.warn("Could not read notification history", failure);
            return List.of();
        }
    }

    private static void finishLoad() {
        if (pendingLoad == null) return;
        try {
            // New entries may already be visible. Prepend only the newest saved entries that still fit.
            for (Entry entry : pendingLoad.join()) {
                if (entries.size() == MAX_ENTRIES) break;
                entries.addFirst(entry);
            }
            displaySnapshot = null;
        } catch (CompletionException failure) {
            net.aero.visuals.AeroVisuals.LOGGER.warn("Could not load notification history", failure.getCause());
        } finally {
            pendingLoad = null;
            loaded = true;
        }
    }

    private static String limitText(String text) {
        if (text.length() <= MAX_TEXT) return text;
        int end = MAX_TEXT;
        if (Character.isSurrogatePair(text.charAt(end - 1), text.charAt(end))) end--;
        return text.substring(0, end);
    }

    public static synchronized void record(String text){
        var module=ImportedFeatures.get(ExtraFeatures.History.class);
        if(module==null||!module.isEnabled()||text==null||text.isBlank())return;
        updateLoad();
        entries.add(new Entry(System.currentTimeMillis(), limitText(text)));
        while(entries.size()>MAX_ENTRIES)entries.removeFirst();
        dirty=true;
        displaySnapshot = null;
    }

    public static synchronized List<Entry> entries() {
        updateLoad();
        if (displaySnapshot == null) displaySnapshot = List.copyOf(entries.reversed());
        return displaySnapshot;
    }

    public static synchronized void clear() {
        // Explicitly clearing history supersedes any read already in progress.
        loaded = true;
        pendingLoad = null;
        entries.clear();
        displaySnapshot = List.of();
        dirty = true;
        saveImmediately = true;
        flushIfNeeded();
    }

    public static synchronized void flushIfNeeded() {
        if (pendingLoad != null) {
            if (!pendingLoad.isDone()) return;
            finishLoad();
        }
        if (pendingWrite != null) {
            if (!pendingWrite.isDone()) return;
            finishPendingWrite();
        }
        if (!dirty || (!saveImmediately && lastWrite != 0 && System.nanoTime() - lastWrite < SAVE_INTERVAL)) return;
        var snapshot = List.copyOf(entries);
        pendingWrite = CompletableFuture.supplyAsync(() -> write(snapshot),
                task -> Thread.ofVirtual().name("Aero notification history").start(task));
        lastWrite = System.nanoTime();
        dirty = false;
        saveImmediately = false;
    }

    public static synchronized void flush() {
        finishPendingWrite();
        if (dirty) {
            finishLoad();
            dirty = !write(List.copyOf(entries));
            lastWrite = System.nanoTime();
        }
    }

    private static void finishPendingWrite() {
        if (pendingWrite == null) return;
        try {
            if (!pendingWrite.join()) dirty = true;
        } catch (CompletionException failure) {
            dirty = true;
            net.aero.visuals.AeroVisuals.LOGGER.warn("Could not save notification history", failure.getCause());
        } finally {
            pendingWrite = null;
        }
    }

    private static boolean write(List<Entry> snapshot) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "aero-history-", ".tmp");
            Files.writeString(temporary, JSON.toJson(snapshot), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception failure) {
            net.aero.visuals.AeroVisuals.LOGGER.warn("Could not save notification history", failure);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (java.io.IOException ignored) {}
            }
        }
    }
}
