package net.aero.visuals.feature.utility;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import net.aero.visuals.AeroVisuals;

/** One local index, isolated by world and dimension. The owner thread publishes immutable snapshots. */
final class ChestIndex implements AutoCloseable {
    record Spot(WorldScope scope, int x, int y, int z) {}
    record Item(String id, String name, String search, int amount) {}
    record Entry(Spot spot, String label, List<Item> items, long seenAt) {}
    private static final int LIMIT = 1024;
    private static final Gson GSON = new Gson();
    private final Path file;
    private final LinkedHashMap<Spot, Entry> entries = new LinkedHashMap<>();
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "Aero Chest Tracker IO");
        thread.setDaemon(true);
        return thread;
    });
    private final Future<Entry[]> pendingRead;
    private boolean loaded, failed, closed;
    private ScheduledFuture<?> pendingWrite;

    ChestIndex(Path file) {
        this.file = file;
        pendingRead = io.submit(this::load);
    }

    private Entry[] load() throws IOException {
        if (!Files.exists(file)) return new Entry[0];
        if (Files.size(file) > 32L * 1024 * 1024) throw new IOException("Chest index exceeds size limit");
        try (var reader = Files.newBufferedReader(file)) {
            Entry[] read = GSON.fromJson(reader, Entry[].class);
            return read == null ? new Entry[0] : read;
        }
    }

    boolean ready() {
        if (closed || failed) return false;
        if (loaded) return true;
        if (!pendingRead.isDone()) return false;
        try {
            for (Entry entry : pendingRead.get()) {
                if (entry == null || entry.spot() == null || entry.spot().scope() == null || entry.label() == null
                        || entry.items() == null || entry.items().size() > 54) continue;
                var valid = entry.items().stream().filter(Objects::nonNull).filter(item -> item.id() != null
                        && item.name() != null && item.search() != null && item.amount() > 0).toList();
                entries.put(entry.spot(), new Entry(entry.spot(), entry.label(), valid, entry.seenAt()));
                if (entries.size() > LIMIT) entries.pollFirstEntry();
            }
            loaded = true;
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException failure) {
            failed = true;
            AeroVisuals.LOGGER.warn("Could not read Chest Tracker index", failure);
        }
        return false;
    }

    boolean failed() { return failed; }
    List<Entry> entries() { return List.copyOf(entries.values()); }

    void record(Spot spot, String label, List<Item> items) {
        if (!ready()) return;
        var previous = entries.get(spot);
        if (previous != null && previous.label().equals(label) && previous.items().equals(items)
                && System.currentTimeMillis() - previous.seenAt() < 60_000) return;
        entries.remove(spot);
        entries.put(spot, new Entry(spot, label, List.copyOf(items), System.currentTimeMillis()));
        if (entries.size() > LIMIT) entries.pollFirstEntry();
        schedule();
    }

    void forget(Spot spot) { if (ready() && entries.remove(spot) != null) schedule(); }

    private void schedule() {
        if (pendingWrite != null) pendingWrite.cancel(false);
        var snapshot = entries();
        pendingWrite = io.schedule(() -> save(snapshot), 1500, TimeUnit.MILLISECONDS);
    }

    private void save(List<Entry> snapshot) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "aero-chest-", ".tmp");
            try (var writer = Files.newBufferedWriter(temporary)) { GSON.toJson(snapshot, writer); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failure) {
            AeroVisuals.LOGGER.warn("Could not save Chest Tracker index", failure);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        io.shutdown();
        if (pendingWrite == null) return;
        try { pendingWrite.get(3, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        catch (ExecutionException | TimeoutException failure) { AeroVisuals.LOGGER.warn("Chest Tracker save did not complete", failure); }
    }
}
