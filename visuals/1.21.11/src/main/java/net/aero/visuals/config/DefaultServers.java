package net.aero.visuals.config;

import net.aero.visuals.AeroVisuals;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DefaultServers {
    private static final String ADDRESS = "mc.deathmine.fun";
    private static final String NAME = "DeathMine - анархия";

    private DefaultServers() {}

    public static void addOnce(MinecraftClient client) {
        Path marker = FabricLoader.getInstance().getConfigDir()
                .resolve("aerovisuals-default-servers").resolve("deathmine.added");
        if (Files.exists(marker)) return;
        try {
            Path dataFile = client.runDirectory.toPath().resolve("servers.dat");
            if (Files.exists(dataFile) && NbtIo.read(dataFile) == null) {
                throw new IOException("Existing server list could not be read");
            }
            ServerList servers = new ServerList(client);
            servers.loadFile();
            ServerInfo existing = find(servers);
            if (existing == null) {
                existing = servers.tryUnhide(ADDRESS);
                if (existing == null) {
                    existing = new ServerInfo(NAME, ADDRESS, ServerInfo.ServerType.OTHER);
                    servers.add(existing, false);
                }
            }
            existing.name = NAME;
            servers.saveFile();
            ServerList saved = new ServerList(client);
            saved.loadFile();
            ServerInfo verified = find(saved);
            if (verified == null || !NAME.equals(verified.name)) {
                throw new IOException("Default server was not saved");
            }
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, ADDRESS);
        } catch (Exception error) {
            AeroVisuals.LOGGER.warn("Could not add the default DeathMine server", error);
        }
    }

    private static ServerInfo find(ServerList servers) {
        for (int index = 0; index < servers.size(); index++) {
            ServerInfo server = servers.get(index);
            if (server.address == null) continue;
            ServerAddress address = ServerAddress.parse(server.address.trim());
            if (ADDRESS.equalsIgnoreCase(address.getAddress()) && address.getPort() == 25565) return server;
        }
        return null;
    }
}
