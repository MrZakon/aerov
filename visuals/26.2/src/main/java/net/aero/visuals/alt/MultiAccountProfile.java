package net.aero.visuals.alt;

import net.aero.visuals.AeroVisuals;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

record MultiAccountProfile(int heapMb, int renderDistance) {
    static final MultiAccountProfile DEFAULT = new MultiAccountProfile(0, 0);
    static final int HEAP_STEP_MB = 256;
    static final int MIN_RENDER_DISTANCE = 2;
    static final int MAX_RENDER_DISTANCE = 32;

    MultiAccountProfile {
        if (heapMb != 0) {
            int clamped = Math.clamp(heapMb, MultiAccountBudget.MIN_HEAP_MB, MultiAccountBudget.MAX_HEAP_MB);
            heapMb = Math.round((float) clamped / HEAP_STEP_MB) * HEAP_STEP_MB;
        }
        if (renderDistance != 0) renderDistance = Math.clamp(renderDistance, MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE);
    }

    int effectiveHeapMb(int defaultHeapMb) {
        return heapMb == 0 ? defaultHeapMb : heapMb;
    }

    static Path accountDirectory(String name) {
        if (!AltManager.isValidUsername(name)) throw new IllegalArgumentException("Invalid background account name");
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().resolve("config").resolve("aerovisuals-accounts")
                .resolve(name.toLowerCase(Locale.ROOT));
    }

    static MultiAccountProfile load(String name) {
        try {
            var profile = MultiAccountFiles.read(accountDirectory(name).resolve("profile.json"), MultiAccountProfile.class);
            if (profile != null) return profile;
        } catch (IOException failure) {
            AeroVisuals.LOGGER.warn("Could not read settings for background account {}", name, failure);
        }
        return DEFAULT;
    }

    void save(String name) throws IOException {
        Path directory = accountDirectory(name);
        Files.createDirectories(directory);
        MultiAccountFiles.write(directory.resolve("profile.json"), this);
    }
}
