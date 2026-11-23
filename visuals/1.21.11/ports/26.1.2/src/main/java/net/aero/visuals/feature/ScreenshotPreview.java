package net.aero.visuals.feature;

import com.mojang.blaze3d.platform.NativeImage;
import net.aero.visuals.AeroVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScreenshotPreview {
    private static final Identifier SLOT_A = Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID, "screenshot_preview");
    private static final Identifier SLOT_B = Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID, "screenshot_preview_next");
    private static final long WATCH_RETRY_NANOS = 5_000_000_000L;
    private static final long DECODE_RETRY_NANOS = 300_000_000L;
    private static final int MAX_RETRIES = 3;
    private static final int THUMBNAIL_LIMIT = 480;
    private static final long MAX_SCREENSHOT_BYTES = 128L * 1024 * 1024;
    private static final long MAX_SCREENSHOT_PIXELS = 8192L * 4608;
    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int PNG_IHDR = 0x49484452;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Aero screenshot thumbnail");
        thread.setDaemon(true);
        return thread;
    });

    private record WatchRegistration(WatchService service, Path latest) {}
    private record Request(Path path, boolean scan, boolean announce, int attempt, long retryAt) {}
    private record Thumbnail(Path path, NativeImage image) implements AutoCloseable {
        @Override public void close() { image.close(); }
    }

    // The client thread owns requests and textures. The worker returns resources through futures.
    private static boolean active;
    private static Path folder;
    private static WatchService watcher;
    private static CompletableFuture<WatchRegistration> pendingWatch;
    private static long watchRetryAt;
    private static Request request;
    private static Request decoding;
    private static CompletableFuture<Thumbnail> pendingDecode;
    private static CompletableFuture<Void> pendingCleanup;
    private static Identifier texture;
    private static Path latest;
    private static long shown;
    private static int width = 320;
    private static int height = 180;

    private ScreenshotPreview() {}

    public static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
    }

    public static void start() {
        close();
        folder = directory();
        active = true;
    }

    public static void tick() {
        if (!active) return;
        if (pendingCleanup != null) {
            if (!pendingCleanup.isDone()) return;
            pendingCleanup = null;
        }
        long now = System.nanoTime();
        updateWatch(now);
        pollWatch();
        finishDecode(now);
        if (request != null && pendingDecode == null
                && (request.retryAt() == 0 || now - request.retryAt() >= 0)) {
            Request next = request;
            Path currentFolder = folder;
            decoding = next;
            pendingDecode = CompletableFuture.supplyAsync(() -> decode(currentFolder, next), WORKER);
        }
    }

    private static void updateWatch(long now) {
        if (pendingWatch != null && pendingWatch.isDone()) {
            var completed = pendingWatch;
            pendingWatch = null;
            try {
                WatchRegistration registration = completed.join();
                watcher = registration.service();
                if (request == null && registration.latest() != null && !registration.latest().equals(latest)) {
                    request = new Request(registration.latest(), false, false, 0, 0);
                }
            } catch (CompletionException failure) {
                AeroVisuals.LOGGER.debug("Could not watch screenshots", failure.getCause());
                watchRetryAt = now + WATCH_RETRY_NANOS;
            }
        }
        if (watcher == null && pendingWatch == null && (watchRetryAt == 0 || now - watchRetryAt >= 0)) {
            Path currentFolder = folder;
            pendingWatch = CompletableFuture.supplyAsync(() -> registerWatch(currentFolder), WORKER);
        }
    }

    private static WatchRegistration registerWatch(Path folder) {
        WatchService service = null;
        try {
            Files.createDirectories(folder);
            service = folder.getFileSystem().newWatchService();
            folder.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            Path last = null;
            try {
                last = newest(folder);
            } catch (IOException | RuntimeException failure) {
                AeroVisuals.LOGGER.debug("Could not list existing screenshots", failure);
            }
            return new WatchRegistration(service, last);
        } catch (IOException | RuntimeException failure) {
            closeWatch(service);
            throw new CompletionException(failure);
        }
    }

    private static Path newest(Path folder) throws IOException {
        Path latestFile = null;
        FileTime latestTime = null;
        try (var files = Files.newDirectoryStream(folder, ScreenshotPreview::isPng)) {
            for (Path file : files) {
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(file, BasicFileAttributes.class);
                } catch (java.nio.file.NoSuchFileException removed) {
                    continue;
                }
                if (!attributes.isRegularFile()) continue;
                FileTime modified = attributes.lastModifiedTime();
                int order = latestTime == null ? 1 : modified.compareTo(latestTime);
                if (order > 0 || (order == 0 && file.getFileName().toString().compareTo(latestFile.getFileName().toString()) > 0)) {
                    latestTime = modified;
                    latestFile = file;
                }
            }
        }
        return latestFile;
    }

    private static boolean isPng(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private static void pollWatch() {
        if (watcher == null) return;
        Path found = null;
        boolean scan = false;
        try {
            WatchKey key;
            while ((key = watcher.poll()) != null) {
                for (var event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        scan = true;
                        continue;
                    }
                    if (!(event.context() instanceof Path relative) || !isPng(relative)) continue;
                    Path path = folder.resolve(relative);
                    if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        if (path.equals(found) || path.equals(latest)
                                || request != null && path.equals(request.path())) scan = true;
                    } else {
                        if (found != null && !found.equals(path)) scan = true;
                        found = path;
                    }
                }
                if (!key.reset()) {
                    resetWatch();
                    scan = true;
                    break;
                }
            }
        } catch (ClosedWatchServiceException ignored) {
            resetWatch();
            scan = true;
        }
        if (scan) {
            boolean announce = found != null || request != null && request.announce();
            request = new Request(null, true, announce, 0, 0);
        } else if (found != null) {
            request = new Request(found, false, true, 0, 0);
        }
    }

    private static Thumbnail decode(Path folder, Request request) {
        NativeImage small = null;
        try {
            Path file = request.scan() ? newest(folder) : request.path();
            if (file == null) return null;
            if (Files.size(file) > MAX_SCREENSHOT_BYTES) return null;
            try (var input = new BufferedInputStream(Files.newInputStream(file))) {
                input.mark(24);
                var header = new DataInputStream(input);
                if (header.readLong() != PNG_SIGNATURE || header.readInt() != 13 || header.readInt() != PNG_IHDR) {
                    return null;
                }
                int sourceWidth = header.readInt(), sourceHeight = header.readInt();
                if (sourceWidth <= 0 || sourceHeight <= 0
                        || (long) sourceWidth * sourceHeight > MAX_SCREENSHOT_PIXELS) return null;
                input.reset();
                NativeImage full = NativeImage.read(input);
                try {
                    int longest = Math.max(full.getWidth(), full.getHeight());
                    if (longest <= THUMBNAIL_LIMIT) {
                        small = full;
                        full = null;
                    } else {
                        double scale = (double) THUMBNAIL_LIMIT / longest;
                        int w = Math.max(1, (int) (full.getWidth() * scale));
                        int h = Math.max(1, (int) (full.getHeight() * scale));
                        small = new NativeImage(w, h, false);
                        full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), small);
                    }
                } finally {
                    if (full != null) full.close();
                }
            }
            var thumbnail = new Thumbnail(file, small);
            small = null;
            return thumbnail;
        } catch (IOException | RuntimeException failure) {
            throw new CompletionException(failure);
        } finally {
            if (small != null) small.close();
        }
    }

    private static void finishDecode(long now) {
        if (pendingDecode == null || !pendingDecode.isDone()) return;
        var completed = pendingDecode;
        Request finished = decoding;
        pendingDecode = null;
        decoding = null;

        try {
            Thumbnail thumbnail = completed.join();
            if (request != finished) {
                if (thumbnail != null) thumbnail.close();
                return;
            }
            if (thumbnail != null) {
                publish(thumbnail, finished.announce());
            } else if (finished.scan() || finished.path().equals(latest)) {
                releasePreview();
            }
            request = null;
        } catch (RuntimeException failure) {
            if (request != finished) return;
            if (finished.attempt() < MAX_RETRIES) {
                request = new Request(finished.path(), finished.scan(), finished.announce(),
                        finished.attempt() + 1, now + DECODE_RETRY_NANOS);
            } else {
                if (finished.scan()) releasePreview();
                request = null;
                AeroVisuals.LOGGER.debug("Could not load screenshot preview", failure);
            }
        }
    }

    private static void publish(Thumbnail thumbnail, boolean announce) {
        NativeImage image = thumbnail.image();
        DynamicTexture replacement = null;
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            replacement = new DynamicTexture(() -> "Screenshot preview", image);
            image = null;
            var manager = Minecraft.getInstance().getTextureManager();
            Identifier next = SLOT_A.equals(texture) ? SLOT_B : SLOT_A;
            manager.register(next, replacement);
            replacement = null;
            Identifier previous = texture;
            texture = next;
            latest = thumbnail.path();
            width = w;
            height = h;
            if (previous != null) manager.release(previous);
            if (announce) {
                shown = System.nanoTime();
                try {
                    FeatureJournal.record("Скриншот: " + latest.getFileName());
                } catch (RuntimeException failure) {
                    AeroVisuals.LOGGER.debug("Could not record screenshot", failure);
                }
            }
        } finally {
            if (replacement != null) replacement.close();
            if (image != null) image.close();
        }
    }

    public static void overlay(GuiGraphicsExtractor d, double seconds) {
        if (texture == null || shown == 0 || (System.nanoTime() - shown) / 1e9 > seconds
                || Minecraft.getInstance().options.hideGui) return;
        int viewportWidth = d.guiWidth();
        int viewportHeight = d.guiHeight();
        if (viewportWidth <= 18 || viewportHeight <= 38) return;
        double scale = Math.min(Math.min(144, viewportWidth - 18) / (double) width,
                (viewportHeight - 38) / (double) height);
        int w = Math.max(1, (int) (width * scale));
        int h = Math.max(1, (int) (height * scale));
        int x = viewportWidth - w - 12;
        int y = 12;
        d.fill(x - 3, y - 3, x + w + 3, y + h + 20, 0xD0141420);
        draw(d, x, y, w, h);
        d.text(Minecraft.getInstance().font,"Снимок сохранён · F8",x,y+h+5,0xFFFFFFFF,true);
    }
    public static void draw(GuiGraphicsExtractor d,int x,int y,int w,int h){if(texture!=null)d.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,0,0,w,h,width,height,width,height);}

    public static boolean ready() { return texture != null; }
    public static Path latest() { return latest; }
    public static double ratio() { return (double) height / width; }

    public static void close() {
        active = false;
        request = null;
        decoding = null;
        folder = null;
        resetWatch();
        if (pendingWatch != null) {
            trackCleanup(pendingWatch.thenAccept(registration -> closeWatch(registration.service())));
            pendingWatch = null;
        }
        if (pendingDecode != null) {
            trackCleanup(pendingDecode.thenAccept(thumbnail -> {
                if (thumbnail != null) thumbnail.close();
            }));
            pendingDecode = null;
        }
        releasePreview();
    }

    private static void trackCleanup(CompletableFuture<Void> cleanup) {
        pendingCleanup = pendingCleanup == null || pendingCleanup.isDone()
                ? cleanup : CompletableFuture.allOf(pendingCleanup, cleanup);
    }

    private static void releasePreview() {
        Identifier previous = texture;
        texture = null;
        latest = null;
        shown = 0;
        if (previous != null) Minecraft.getInstance().getTextureManager().release(previous);
    }

    private static void resetWatch() {
        closeWatch(watcher);
        watcher = null;
        watchRetryAt = 0;
    }

    private static void closeWatch(WatchService service) {
        if (service == null) return;
        try {
            service.close();
        } catch (IOException failure) {
            AeroVisuals.LOGGER.debug("Could not close screenshot watcher", failure);
        }
    }
}
