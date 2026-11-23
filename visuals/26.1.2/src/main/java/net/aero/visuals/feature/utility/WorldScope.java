package net.aero.visuals.feature.utility;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

public record WorldScope(String world, String dimension) {
    public static WorldScope current(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        var integrated = client.getSingleplayerServer();
        var remote = client.getCurrentServer();
        String world;
        if (integrated != null) world = "world:" + integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if (remote != null && !remote.ip.isBlank()) world = "server:" + remote.ip.strip().toLowerCase(Locale.ROOT);
        else return null;
        return new WorldScope(world, client.level.dimension().identifier().toString());
    }
}
