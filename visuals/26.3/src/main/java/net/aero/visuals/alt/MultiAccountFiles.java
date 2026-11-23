package net.aero.visuals.alt;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

final class MultiAccountFiles {
    private static final Gson JSON = new Gson();

    private MultiAccountFiles() {}

    static <T> T read(Path path, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JSON.fromJson(reader, type);
        } catch (NoSuchFileException missing) {
            return null;
        } catch (com.google.gson.JsonParseException invalid) {
            throw new IOException("Invalid session file: " + path.getFileName(), invalid);
        }
    }

    static void write(Path path, Object value) throws IOException {
        write(path, temp -> Files.writeString(temp, JSON.toJson(value)));
    }

    static void write(Path path, Writer writer) throws IOException {
        Path temp = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            writer.write(temp);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @FunctionalInterface
    interface Writer {
        void write(Path path) throws IOException;
    }

    static final class Request {
        String server;
        String serverName;
        String resourcePack;
        String action;
        int viewId;
        int width;
        int height;
        int fps;
        String message;
        boolean preview;
        boolean respawn;
    }

    static final class State {
        String phase = "starting";
        String detail = "";
        String world = "";
        String mode = "";
        float health;
        float maxHealth;
        int food;
        int latency = -1;
        boolean dead;
        long previewId;
        List<String> chat = List.of();
        List<HotbarItem> hotbar = List.of();
    }

    record HotbarItem(String id, int count) {}
}
