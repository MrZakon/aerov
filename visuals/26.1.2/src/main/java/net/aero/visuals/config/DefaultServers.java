package net.aero.visuals.config;

import net.aero.visuals.AeroVisuals;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DefaultServers {
    private static final String ADDRESS = "mc.deathmine.fun";
    private static final String NAME = "DeathMine - анархия";

    private DefaultServers() {}

    public static void addOnce(Minecraft client) {
        Path marker = FabricLoader.getInstance().getConfigDir()
                .resolve("aerovisuals-default-servers").resolve("deathmine.added");
        if (Files.exists(marker)) return;
        try {
            Path dataFile = client.gameDirectory.toPath().resolve("servers.dat");
            if (Files.exists(dataFile) && NbtIo.read(dataFile) == null) {
                throw new IOException("Existing server list could not be read");
            }
            ServerList servers = new ServerList(client);
            servers.load();
            ServerData existing = find(servers);
            if (existing == null) {
                existing = servers.unhide(ADDRESS);
                if (existing == null) {
                    existing = new ServerData(NAME, ADDRESS, ServerData.Type.OTHER);
                    servers.add(existing, false);
                }
            }
            existing.name = NAME;
            servers.save();
            ServerList saved = new ServerList(client);
            saved.load();
            ServerData verified = find(saved);
            if (verified == null || !NAME.equals(verified.name)) {
                throw new IOException("Default server was not saved");
            }
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, ADDRESS);
        } catch (Exception error) {
            AeroVisuals.LOGGER.warn("Could not add the default DeathMine server", error);
        }
    }

    private static ServerData find(ServerList servers) {
        for (int index = 0; index < servers.size(); index++) {
            ServerData server = servers.get(index);
            if (server.ip == null) continue;
            ServerAddress address = ServerAddress.parseString(server.ip.trim());
            if (ADDRESS.equalsIgnoreCase(address.getHost()) && address.getPort() == 25565) return server;
        }
        return null;
    }
}
