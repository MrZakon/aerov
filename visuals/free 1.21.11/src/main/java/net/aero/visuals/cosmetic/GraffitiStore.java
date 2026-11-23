package net.aero.visuals.cosmetic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Session-only graffiti marks. They survive chunk unloads for three hours, but never persist to disk. */
public final class GraffitiStore {
    private static final long UNLOADED_TTL_MS = 3L * 60L * 60L * 1000L;
    private static final ConcurrentHashMap<BlockPos, Entry> MARKS = new ConcurrentHashMap<>();
    private static Object world;
    private GraffitiStore() {}

    public static boolean place(MinecraftClient client, BlockHitResult hit, int cosmeticId) {
        if (client == null || client.world == null || hit == null || cosmeticId < 0) return false;
        ensureWorld(client.world);
        MARKS.put(hit.getBlockPos(), new Entry(hit.getBlockPos().toImmutable(), hit.getSide(), cosmeticId, System.currentTimeMillis()));
        return true;
    }

    public static List<Entry> visible(MinecraftClient client) {
        if (client == null || client.world == null) { clear(); return List.of(); }
        ensureWorld(client.world);
        long now = System.currentTimeMillis();
        List<Entry> result = new ArrayList<>();
        for (var entry : MARKS.entrySet()) {
            Entry mark = entry.getValue();
            if (client.world.isChunkLoaded(mark.pos())) {
                Entry touched = mark.withLastLoadedAt(now);
                MARKS.replace(entry.getKey(), mark, touched);
                result.add(touched);
            } else if (now - mark.lastLoadedAt() >= UNLOADED_TTL_MS) {
                MARKS.remove(entry.getKey(), mark);
            }
        }
        return result;
    }

    public static boolean contains(BlockPos pos) { return pos != null && MARKS.containsKey(pos); }
    public static void clear() { MARKS.clear(); world = null; }
    private static void ensureWorld(Object current) { if (world != current) { MARKS.clear(); world = current; } }

    public record Entry(BlockPos pos, Direction face, int cosmeticId, long lastLoadedAt) {
        Entry withLastLoadedAt(long value) { return new Entry(pos, face, cosmeticId, value); }
    }
}
