package net.aero.visuals.media;

import net.aero.visuals.AeroVisuals;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class MediaArtHelper {
    private static final long MAX_ARTWORK_BYTES = 16 * 1024 * 1024;
    private static final int MAX_SOURCE_SIDE = 8192;
    private static final int MAX_RENDER_SIDE = 512;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_NANOS = 5_000_000_000L;
    private static final Identifier slotA=Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID,"media_art_a");
    private static final Identifier slotB=Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID,"media_art_b");
    private static Identifier currentId;
    private static DynamicTexture currentTexture;
    private record Artwork(WindowsMediaTracker.MediaTrack track, String path) {}
    private record Decode(Artwork artwork, CompletableFuture<NativeImage> image) {}
    private static Artwork requested;
    private static Decode pending;
    private static CompletableFuture<Void> pendingCleanup;
    private static int attempts;
    private static long retryAt;
    private static boolean loaded;
    private static int textureW=256, textureH=256;

    private MediaArtHelper() {}

    public static Identifier getArtTexture(WindowsMediaTracker.MediaTrack track, String path) {
        if (requested == null || !requested.track().equals(track) || !requested.path().equals(path)) {
            requested = new Artwork(track, path);
            loaded = false;
            retryAt = 0;
            attempts = 0;
        }
        finishDecode();
        if (path.isBlank()) {
            if (!loaded) releaseCurrent();
            loaded = true;
            return null;
        }
        long now = System.nanoTime();
        if (pendingCleanup != null && pendingCleanup.isDone()) pendingCleanup = null;
        if (!loaded && pending == null && pendingCleanup == null && (retryAt == 0 || now - retryAt >= 0)) {
            attempts++;
            var result = CompletableFuture.supplyAsync(() -> decode(path),
                    task -> Thread.ofVirtual().name("AeroVisuals artwork decode").start(task));
            pending = new Decode(requested, result);
        }
        return loaded ? currentId : null;
    }

    private static NativeImage decode(String path) {
        NativeImage image = null;
        try {
            Path source = Path.of(path);
            if (!Files.isRegularFile(source)) throw new IOException("Artwork file is unavailable");
            try (var input = new FileImageInputStream(source.toFile())) {
                if (input.length() > MAX_ARTWORK_BYTES) return null;
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return null;
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE) return null;
                    int step = Math.max(1, Math.ceilDiv(Math.max(width, height), MAX_RENDER_SIDE));
                    var parameters = reader.getDefaultReadParam();
                    parameters.setSourceSubsampling(step, step, 0, 0);
                    var decoded = reader.read(0, parameters);
                    width = decoded.getWidth();
                    height = decoded.getHeight();
                    if (width > MAX_RENDER_SIDE || height > MAX_RENDER_SIDE) return null;
                    image = new NativeImage(width, height, false);
                    int[] pixels = new int[width];
                    for (int y = 0; y < height; y++) {
                        decoded.getRGB(0, y, width, 1, pixels, 0, width);
                        for (int x = 0; x < width; x++) image.setPixel(x, y, pixels[x]);
                    }
                } finally {
                    reader.dispose();
                }
            }
            NativeImage result = image;
            image = null;
            return result;
        } catch (IOException | RuntimeException failure) {
            throw new CompletionException(failure);
        } finally {
            if (image != null) image.close();
        }
    }

    private static void finishDecode() {
        if (pending == null || !pending.image().isDone()) return;
        var completed = pending;
        pending = null;
        try {
            var image = completed.image().join();
            if (completed.artwork() != requested) {
                if (image != null) image.close();
                return;
            }
            if (image == null) {
                releaseCurrent();
                loaded = true;
            } else {
                upload(image);
                if (!loaded) retryOrRelease();
            }
        } catch (CompletionException failure) {
            if (completed.artwork() == requested) {
                AeroVisuals.LOGGER.debug("Could not decode media artwork", failure.getCause());
                retryOrRelease();
            }
        }
    }

    private static void retryOrRelease() {
        if (attempts >= MAX_ATTEMPTS) {
            releaseCurrent();
            loaded = true;
        } else {
            retryAt = System.nanoTime() + RETRY_DELAY_NANOS;
        }
    }

    // The texture manager and GPU upload stay on the render thread.
    private static void upload(NativeImage image) {
        DynamicTexture replacement=null;
        try {
            var client=Minecraft.getInstance();
            if(client==null||client.getTextureManager()==null)return;
            int w=image.getWidth(),h=image.getHeight();
            replacement=new DynamicTexture(()->"media_art",image);
            image=null;
            // Two slots keep the visible art alive until its replacement has uploaded.
            Identifier next=slotA.equals(currentId)?slotB:slotA;
            client.getTextureManager().register(next,replacement);
            Identifier previous=currentId;
            currentId=next;currentTexture=replacement;replacement=null;
            textureW=w;textureH=h;loaded=true;
            if(previous!=null)client.getTextureManager().release(previous);
        }catch(RuntimeException failure){
            AeroVisuals.LOGGER.debug("Could not upload media artwork",failure);
        }finally{
            if(replacement!=null)replacement.close();
            if(image!=null)image.close();
        }
    }

    private static void releaseCurrent(){
        var client=Minecraft.getInstance();
        if(currentId!=null&&client!=null&&client.getTextureManager()!=null)client.getTextureManager().release(currentId);
        else if(currentTexture!=null)currentTexture.close();
        currentId=null;currentTexture=null;
        textureW=textureH=256;
    }

    public static void close(){
        if (pending != null) {
            // Keep the next decode blocked until the old task has released its image.
            pendingCleanup = pending.image().handle((image, failure) -> {
                if (image != null) image.close();
                return null;
            });
            pending = null;
        }
        releaseCurrent();
        requested = null;
        retryAt = 0;
        attempts = 0;
        loaded = false;
    }
    public static int getTextureWidth(){return textureW>0?textureW:256;}
    public static int getTextureHeight(){return textureH>0?textureH:256;}
}
