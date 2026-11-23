package net.aero.visuals.feature;



import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

public final class SpatialAudioManager {
    @com.google.gson.annotations.SerializedName("skda")
    public static final SpatialAudioManager instance = new SpatialAudioManager();

    @com.google.gson.annotations.SerializedName("nhoca")
    public volatile boolean enabled;

    @com.google.gson.annotations.SerializedName("n2")
    public volatile boolean reverbEnabled;

    @com.google.gson.annotations.SerializedName("getField680")
    public volatile boolean lowPassEnabled;

    @com.google.gson.annotations.SerializedName("updateField37")
    public volatile boolean pitchShiftEnabled;

    @com.google.gson.annotations.SerializedName("startField88")
    public volatile float effectStrength = 1.0F;

    @com.google.gson.annotations.SerializedName("deleteField32")
    public volatile float enclosureFactor;

    @com.google.gson.annotations.SerializedName("processField900")
    public volatile float lowPassGain = 1.0F;

    @com.google.gson.annotations.SerializedName("st")
    public volatile float lowPassHighFrequencyGain = 1.0F;

    @com.google.gson.annotations.SerializedName("transformField55")
    public int reverbEffectId = -1;

    @com.google.gson.annotations.SerializedName("handleField75")
    public int auxiliaryEffectSlotId = -1;

    @com.google.gson.annotations.SerializedName("kokkf")
    public int lowPassFilterId = -1;

    @com.google.gson.annotations.SerializedName("destroyField99")
    public boolean initializationAttempted;

    @com.google.gson.annotations.SerializedName("ybfus")
    public boolean effectsAvailable;

    @com.google.gson.annotations.SerializedName("leefe")
    public long lastEnvironmentScanMs;
    private long lastScanNanos;
    private java.lang.ref.WeakReference<Object> scannedWorld;
    private boolean scannedHighQuality;
    private final BlockPos.Mutable samplePosition = new BlockPos.Mutable();
    private float solidFraction;

    public SpatialAudioManager() {}

    public static SpatialAudioManager getInstance() {
        return instance;
    }

    public void update(
            MinecraftClient client,
            boolean highQuality,
            boolean reverbEnabled,
            boolean lowPassEnabled,
            boolean pitchShiftEnabled,
            float strength) {
        if (client == null || client.player == null || client.world == null) {
            disable();
            return;
        }
        var player = client.player;
        var world = client.world;
        float currentStrength = Float.isFinite(strength) ? MathHelper.clamp(strength, 0.0F, 1.5F) : 0.0F;
        if (reverbEnabled || lowPassEnabled) {
            long now = System.nanoTime();
            if (scannedWorld == null || scannedWorld.get() != world || scannedHighQuality != highQuality
                    || now - lastScanNanos >= 300_000_000L) {
                int radius = highQuality ? 4 : 3;
                int verticalRadius = highQuality ? 3 : 2;
                BlockPos center = player.getBlockPos();
                int solidBlocks = 0;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -verticalRadius; y <= verticalRadius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            samplePosition.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                            if (!world.getBlockState(samplePosition).isAir()) solidBlocks++;
                        }
                    }
                }
                int diameter = radius * 2 + 1;
                solidFraction = (float) solidBlocks / (diameter * diameter * (verticalRadius * 2 + 1));
                if (scannedWorld == null || scannedWorld.get() != world) {
                    scannedWorld = new java.lang.ref.WeakReference<>(world);
                }
                scannedHighQuality = highQuality;
                lastScanNanos = now;
                lastEnvironmentScanMs = System.currentTimeMillis();
            }
            float enclosure = solidFraction;
            if (player.isSubmergedInWater()) {
                enclosure = Math.max(enclosure, 0.72F);
            } else if (player.isInLava()) {
                enclosure = Math.max(enclosure, 0.82F);
            }
            enclosureFactor = MathHelper.clamp(enclosure * 2.2F, 0.0F, 1.0F);
            lowPassGain = MathHelper.clamp(1.0F - enclosureFactor * 0.28F * currentStrength, 0.52F, 1.0F);
            lowPassHighFrequencyGain = MathHelper.clamp(1.0F - enclosureFactor * 0.58F * currentStrength, 0.22F, 1.0F);
        } else {
            scannedWorld = null;
        }
        this.effectStrength = currentStrength;
        this.reverbEnabled = reverbEnabled;
        this.lowPassEnabled = lowPassEnabled;
        this.pitchShiftEnabled = pitchShiftEnabled;
        this.enabled = true;
    }

    public void disable() {
        enabled = false;
        scannedWorld = null;
    }

    public void applySource(int var2, boolean relative, float pitch) {
        if (this.enabled && !relative && var2 > 0) {
            this.initializeEffects();
            if (this.effectsAvailable) {
                try {
                    if (this.reverbEnabled
                            && this.reverbEffectId != -1
                            && this.auxiliaryEffectSlotId != -1) {
                        float var3 = this.enclosureFactor * this.effectStrength;
                        EXTEfx.alEffectf(
                                this.reverbEffectId,
                                1,
                                MathHelper.clamp(0.55F + var3 * 0.45F, 0.0F, 1.0F));
                        EXTEfx.alEffectf(this.reverbEffectId, 2, 0.85F);
                        EXTEfx.alEffectf(
                                this.reverbEffectId,
                                3,
                                MathHelper.clamp(0.12F + var3 * 0.24F, 0.0F, 1.0F));
                        EXTEfx.alEffectf(
                                this.reverbEffectId,
                                4,
                                MathHelper.clamp(0.9F - var3 * 0.22F, 0.0F, 1.0F));
                        EXTEfx.alEffectf(
                                this.reverbEffectId,
                                5,
                                MathHelper.clamp(0.45F + var3 * 2.6F, 0.1F, 20.0F));
                        EXTEfx.alEffectf(this.reverbEffectId, 6, 0.82F);
                        EXTEfx.alEffectf(
                                this.reverbEffectId, 7, MathHelper.clamp(var3 * 0.3F, 0.0F, 3.16F));
                        EXTEfx.alEffectf(
                                this.reverbEffectId, 8, MathHelper.clamp(var3 * 0.04F, 0.0F, 0.3F));
                        EXTEfx.alEffectf(
                                this.reverbEffectId,
                                9,
                                MathHelper.clamp(0.8F + var3 * 1.5F, 0.0F, 10.0F));
                        EXTEfx.alEffectf(
                                this.reverbEffectId,
                                10,
                                MathHelper.clamp(0.01F + var3 * 0.025F, 0.0F, 0.1F));
                        EXTEfx.alAuxiliaryEffectSloti(
                                this.auxiliaryEffectSlotId, 1, this.reverbEffectId);
                        AL11.alSource3i(var2, 131078, this.auxiliaryEffectSlotId, 0, 0);
                    } else {
                        AL11.alSource3i(var2, EXTEfx.AL_AUXILIARY_SEND_FILTER, 0, 0, 0);
                    }

                    if (this.lowPassEnabled && this.lowPassFilterId != -1) {
                        EXTEfx.alFilterf(this.lowPassFilterId, 1, this.lowPassGain);
                        EXTEfx.alFilterf(this.lowPassFilterId, 2, this.lowPassHighFrequencyGain);
                        AL10.alSourcei(var2, 131077, this.lowPassFilterId);
                    } else {
                        AL10.alSourcei(var2, EXTEfx.AL_DIRECT_FILTER, 0);
                    }

                    if (this.pitchShiftEnabled) {
                        float var5 =
                                MathHelper.clamp(
                                        pitch
                                                * (1.0F
                                                        + this.enclosureFactor
                                                                * 0.025F
                                                                * this.effectStrength),
                                        0.5F,
                                        2.0F);
                        AL10.alSourcef(var2, 4099, var5);
                    }
                } catch (Throwable throwable) {
                }
            }
        }
    }

    public void resetContext() {
        initializationAttempted = false;
        effectsAvailable = false;
        reverbEffectId = auxiliaryEffectSlotId = lowPassFilterId = -1;
    }

    public void initializeEffects() {
        if (!this.initializationAttempted) {
            this.initializationAttempted = true;

            try {
                if (AL.getCapabilities() == null || !AL.getCapabilities().ALC_EXT_EFX) {
                    net.aero.visuals.AeroVisuals.LOGGER.warn("Spatial Audio disabled: OpenAL EFX is unavailable");
                    return;
                }

                this.reverbEffectId = EXTEfx.alGenEffects();
                EXTEfx.alEffecti(this.reverbEffectId, 32769, 1);
                this.auxiliaryEffectSlotId = EXTEfx.alGenAuxiliaryEffectSlots();
                this.lowPassFilterId = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(this.lowPassFilterId, 32769, 1);
                this.effectsAvailable = AL10.alGetError() == 0;
            } catch (Throwable throwable) {
                net.aero.visuals.AeroVisuals.LOGGER.warn("Spatial Audio initialization failed", throwable);
                this.effectsAvailable = false;
            }
        }
    }
}
