package net.aero.visuals.alt;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.minecraft.client.network.ServerInfo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

final class MultiAccountLaunch {
    private MultiAccountLaunch() {}

    static Process start(Path directory, String name, ServerInfo server, int memoryMb, int renderDistance,
                         MultiAccountMicrosoft.Profile identity) throws IOException {
        var fabric = FabricLoader.getInstance();
        Path origin = fabric.getGameDir().toAbsolutePath();
        // Only the IPC files live in the temporary directory. Fabric uses this account-specific game directory.
        Path instance = MultiAccountProfile.accountDirectory(name).resolve("minecraft");
        Files.createDirectories(instance);
        writeWorkerOptions(instance, renderDistance);
        List<String> args = new ArrayList<>();
        args.add("-Xms128M");
        args.add("-Xmx" + memoryMb + "M");
        args.add("-XX:+UseG1GC");
        args.add("-XX:G1PeriodicGCInterval=30000");
        args.add("-XX:+G1PeriodicGCInvokesConcurrent");
        args.add("-XX:MinHeapFreeRatio=10");
        args.add("-XX:MaxHeapFreeRatio=30");
        args.add("-XX:ActiveProcessorCount=" + Math.min(4, Runtime.getRuntime().availableProcessors()));
        args.add("-Daero.multi.directory=" + directory);
        args.add("-Daero.multi.parent=" + ProcessHandle.current().pid());
        for (String property : List.of("aero.sessionFile", "lunacy.sessionFile")) {
            String sessionFile = System.getProperty(property);
            if (sessionFile != null && !sessionFile.isBlank()) {
                args.add("-D" + property + "=" + Path.of(sessionFile).toAbsolutePath());
            }
        }
        for (String key : List.of("java.library.path", "org.lwjgl.librarypath", "org.lwjgl.system.SharedLibraryExtractPath")) {
            String value = System.getProperty(key);
            if (value != null && !value.isEmpty()) args.add("-D" + key + "=" + absolutePaths(value));
        }

        String classpath = FabricLauncherBase.getLauncher().getClassPath().stream()
                .map(p -> origin.resolve(p).normalize().toString())
                .collect(Collectors.joining(File.pathSeparator));
        if (classpath.isBlank()) throw new IOException("Fabric classpath is empty");
        List<Path> mods = new ArrayList<>();
        for (String id : List.of("aerovisuals", "fabric-api")) {
            var mod = fabric.getModContainer(id).orElseThrow(() -> new IOException("Missing mod: " + id));
            var modOrigin = mod.getOrigin();
            if (modOrigin.getKind() != ModOrigin.Kind.PATH) throw new IOException("Unsupported mod origin: " + id);
            mods.addAll(modOrigin.getPaths());
        }
        args.add("-Dfabric.addMods=" + mods.stream().map(p -> p.toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator)));
        args.add("-cp");
        args.add(classpath);
        args.add("net.fabricmc.loader.impl.launch.knot.KnotClient");
        String playerName = identity == null ? name : identity.name();
        UUID playerId = identity == null
                ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)) : identity.uuid();
        args.addAll(List.of("--gameDir", instance.toString(), "--username", playerName,
                "--uuid", playerId.toString().replace("-", ""),
                "--accessToken", identity == null ? "0" : identity.accessToken(),
                "--userType", identity == null ? "legacy" : "msa",
                "--width", Integer.toString(MultiAccountFocus.BACKGROUND_WIDTH),
                "--height", Integer.toString(MultiAccountFocus.BACKGROUND_HEIGHT)));
        String[] original = fabric.getLaunchArguments(true);
        copyArgument(original, args, "--version", "AeroVisuals account");
        copyArgument(original, args, "--versionType", null);
        copyArgument(original, args, "--assetIndex", null);
        copyArgument(original, args, "--assetsDir", origin.resolve("assets").toString());
        int assetsIndex = args.indexOf("--assetsDir") + 1;
        args.set(assetsIndex, origin.resolve(args.get(assetsIndex)).normalize().toString());

        var setup = new MultiAccountFiles.Request();
        setup.server = server.address;
        setup.serverName = server.name;
        setup.resourcePack = server.getResourcePackPolicy().name();
        MultiAccountFiles.write(directory.resolve("setup.json"), setup);
        Path arguments = directory.resolve("java.args");
        Files.write(arguments, args.stream().map(MultiAccountLaunch::quote).toList(), StandardCharsets.UTF_8);
        String executable = File.separatorChar == '\\' ? "javaw.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        return new ProcessBuilder(java.toString(), "@" + arguments)
                .directory(instance.toFile())
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("console.log").toFile())
                .start();
    }

    private static void writeWorkerOptions(Path instance, int renderDistance) throws IOException {
        Path path = instance.resolve("options.txt");
        var values = new java.util.LinkedHashMap<String, String>();
        String original = Files.exists(path) ? Files.readString(path) : "";
        original.lines().filter(line -> !line.isEmpty()).forEach(line -> {
            int separator = line.indexOf(':');
            if (separator > 0) values.put(line.substring(0, separator), line.substring(separator + 1));
            else values.put(line, null);
        });
        values.put("fullscreen", "false");
        values.put("exclusiveFullscreen", "false");
        values.put("pauseOnLostFocus", "false");
        values.put("enableVsync", "false");
        values.putIfAbsent("soundCategory_master", "1.0");
        values.put("maxFps", Integer.toString(MultiAccountBudget.IDLE_FPS));
        if (renderDistance > 0) values.put("renderDistance", Integer.toString(renderDistance));

        var output = new StringBuilder();
        values.forEach((key, value) -> {
            output.append(key);
            if (value != null) output.append(':').append(value);
            output.append('\n');
        });
        if (!original.contentEquals(output)) {
            MultiAccountFiles.write(path, temp -> Files.writeString(temp, output));
        }
    }

    private static String absolutePaths(String value) {
        return java.util.Arrays.stream(value.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(p -> Path.of(p).toAbsolutePath().normalize().toString())
                .collect(Collectors.joining(File.pathSeparator));
    }

    private static String quote(String argument) {
        return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static void copyArgument(String[] from, List<String> into, String name, String fallback) {
        for (int i = 0; i + 1 < from.length; i++) {
            if (from[i].equals(name)) {
                into.add(name);
                into.add(from[i + 1]);
                return;
            }
        }
        if (fallback != null) {
            into.add(name);
            into.add(fallback);
        }
    }
}
