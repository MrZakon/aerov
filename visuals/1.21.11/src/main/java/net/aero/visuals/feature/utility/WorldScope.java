package net.aero.visuals.feature.utility;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

public record WorldScope(String world, String dimension) {
    public static WorldScope current(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;
        var integrated = client.getServer();
        var remote = client.getCurrentServerEntry();
        String world;
        if (integrated != null) world = "world:" + integrated.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        else if (remote != null && !remote.address.isBlank()) world = "server:" + remote.address.strip().toLowerCase(Locale.ROOT);
        else return null;
        return new WorldScope(world, client.world.getRegistryKey().getValue().toString());
    }
}
