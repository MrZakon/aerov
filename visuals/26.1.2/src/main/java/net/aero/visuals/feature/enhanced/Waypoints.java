package net.aero.visuals.feature.enhanced;

import com.google.gson.*;
import net.aero.visuals.AeroVisuals;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.*;
import net.aero.visuals.command.ClientCommandHandler;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.*;
import net.aero.visuals.render.*;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Vector2f;

/** Manual, server-and-dimension scoped markers. Linken Waypoints is the interaction reference. */
public final class Waypoints extends VisualExpansion.Feature implements AutoCloseable {
    private static final int MAX_POINTS = 128;
    // Includes worst-case JSON escaping of all accepted strings in 128 records.
    private static final long MAX_FILE_BYTES = 2_000_000;
    private record Point(String server,String dimension,String name,double x,double y,double z){Vec3 position(){return new Vec3(x,y,z);}}
    private final BooleanSetting distance=flag("distance","Показывать расстояние",true),quickAdd=flag("add_here","Добавить метку здесь",false),removeNear=flag("remove_nearest","Удалить ближайшую метку",false);
    private final DoubleSetting range=number("range","Дальность отображения",2000,32,5000,32),scale=number("scale","Размер меток",1,.6,1.8,.1);
    private final ColorSetting tint=color("color","Цвет меток",0xFF9B8AFF);
    private final List<Point> points=new ArrayList<>();
    private final Vector2f markerScreen = new Vector2f();
    private final Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("aerovisuals-waypoints-scoped.json");
    private final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Aero waypoint storage");t.setDaemon(true);return t;});
    private final Object saveLock = new Object();
    private record SaveRequest(List<Point> points) {}
    private SaveRequest pendingSave;
    private int saveFailures;
    private boolean saveScheduled;
    private boolean loaded,loadFailed,closed;
    private Object scopeDimensionKey;
    private WeakReference<MinecraftServer> scopeIntegratedServer;
    private String scopeAddress, server, dimension;
    private int sequence=1;
    public Waypoints(){super("waypoints","Waypoints","Собственные метки мест с названием и расстоянием. Команды .wp add, .wp remove, .wp list. Отдельно для каждого сервера и измерения.", ModuleCategory.MISC);load();}
    private void load() {
        io.execute(() -> {
            try {
                List<Point> read = readPoints();
                Minecraft.getInstance().execute(() -> {
                    if (closed) return;
                    points.addAll(read);
                    loaded = true;
                });
            } catch (IOException | RuntimeException failure) {
                AeroVisuals.LOGGER.warn("Could not load waypoints", failure);
                Minecraft.getInstance().execute(() -> {
                    if (!closed) loadFailed = true;
                });
            }
        });
    }

    private List<Point> readPoints() throws IOException {
        boolean useLegacyFile = Files.notExists(file);
        Path source = useLegacyFile ? file.resolveSibling("aerovisuals-waypoints.json") : file;
        if (Files.notExists(source)) return List.of();
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_FILE_BYTES) {
            throw new IOException("Invalid waypoint file or size: " + source);
        }
        var array = JsonParser.parseString(Files.readString(source)).getAsJsonArray();
        List<Point> read = new ArrayList<>();
        int rejected = 0;
        for (var element : array) {
            if (read.size() >= MAX_POINTS) break;
            try {
                var o = element.getAsJsonObject();
                if (useLegacyFile && o.has("id")) continue;
                var point = new Point(o.get("server").getAsString(), o.get("dimension").getAsString(),
                        o.get("name").getAsString(), o.get("x").getAsDouble(),
                        o.get("y").getAsDouble(), o.get("z").getAsDouble());
                if (valid(point)) {
                    read.add(point);
                    continue;
                }
            } catch (RuntimeException ignored) {
                // A damaged entry must not hide the valid entries that follow it.
            }
            rejected++;
        }
        if (rejected > 0) AeroVisuals.LOGGER.warn("Skipped {} invalid waypoint entries", rejected);
        return read;
    }
    private static boolean valid(Point p){return p.name!=null&&!p.name.isBlank()&&p.name.length()<=48&&p.server.length()<=1024&&p.dimension.length()<=128&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<=30_000_000&&Math.abs(p.z)<=30_000_000&&Math.abs(p.y)<=20_000_000;}
    private void save() {
        synchronized (saveLock) {
            if (closed) return;
            pendingSave = new SaveRequest(List.copyOf(points));
            saveFailures = 0;
            startSave();
        }
    }

    // Caller holds saveLock. The request stays dirty until its write succeeds.
    private void startSave() {
        if (pendingSave != null && !saveScheduled) {
            saveScheduled = true;
            io.execute(this::drainSaves);
        }
    }

    private void drainSaves() {
        while (true) {
            SaveRequest request;
            synchronized (saveLock) {
                request = pendingSave;
                if (request == null) {
                    saveScheduled = false;
                    return;
                }
            }

            Exception failure = null;
            try {
                writeSnapshot(request.points());
            } catch (IOException | RuntimeException error) {
                failure = error;
            }

            synchronized (saveLock) {
                if (pendingSave != request) continue;
                if (failure == null) {
                    pendingSave = null;
                    saveScheduled = false;
                    return;
                }
                if (!closed && failure instanceof IOException && ++saveFailures <= 2) {
                    io.schedule(this::drainSaves, 250L * saveFailures, TimeUnit.MILLISECONDS);
                    return;
                }
                saveScheduled = false;
            }

            AeroVisuals.LOGGER.warn("Could not save waypoints", failure);
            Minecraft.getInstance().execute(() -> {
                synchronized (saveLock) {
                    if (closed || pendingSave != request || saveScheduled) return;
                }
                net.aero.visuals.notification.NotificationManager.post("Не удалось сохранить метки", false);
            });
            return;
        }
    }

    private static String encode(List<Point> snapshot) {
        JsonArray data = new JsonArray();
        for (var point : snapshot) {
            var o = new JsonObject();
            o.addProperty("server", point.server);
            o.addProperty("dimension", point.dimension);
            o.addProperty("name", point.name);
            o.addProperty("x", point.x);
            o.addProperty("y", point.y);
            o.addProperty("z", point.z);
            data.add(o);
        }
        return data.toString();
    }

    private void writeSnapshot(List<Point> snapshot) throws IOException {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "aero-waypoints-", ".tmp");
            Files.writeString(temporary, encode(snapshot));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }
    private boolean context() {
        var mc = Minecraft.getInstance();
        var world = mc.level;
        if (!loaded || closed || world == null || mc.player == null) {
            return clearContext();
        }
        var entry = mc.getCurrentServer();
        String address = entry == null ? null : entry.ip;
        var integrated = address == null ? mc.getSingleplayerServer() : null;
        if (address == null && integrated == null) return clearContext();
        var dimensionKey = world.dimension();
        var cachedIntegrated = scopeIntegratedServer == null ? null : scopeIntegratedServer.get();
        if (scopeDimensionKey == null || !scopeDimensionKey.equals(dimensionKey)
                || integrated != cachedIntegrated || !Objects.equals(address, scopeAddress)) {
            server = address != null ? "server:" + address.toLowerCase(Locale.ROOT)
                    : "world:" + integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            dimension = dimensionKey.identifier().toString();
            scopeDimensionKey = dimensionKey;
            scopeIntegratedServer = integrated == null ? null : new WeakReference<>(integrated);
            scopeAddress = address;
        }
        return true;
    }

    private boolean clearContext() {
        scopeDimensionKey = null;
        scopeIntegratedServer = null;
        scopeAddress = server = dimension = null;
        return false;
    }

    private boolean here(Point p){return p.server.equals(server)&&p.dimension.equals(dimension);}
    @Override public String displayValue() {
        if (!context()) return "0";
        int count = 0;
        for (var point : points) if (here(point)) count++;
        return Integer.toString(count);
    }
    @Subscribe private void tick(TickEvent.Client e){if(!context())return;if(quickAdd.get()){quickAdd.set(false);String name;do{name="Метка_"+sequence++;}while(find(name)!=null);add(name,e.client().player.position());}if(removeNear.get()){removeNear.set(false);var pos=e.client().player.position();var nearest=points.stream().filter(this::here).filter(p->p.position().distanceToSqr(pos)<=1024).min(Comparator.comparingDouble(p->p.position().distanceToSqr(pos)));if(nearest.isPresent()){points.remove(nearest.get());save();ClientCommandHandler.send("Удалена метка: "+nearest.get().name);}else ClientCommandHandler.send("В радиусе 32 блоков нет меток.");}}
    private Point find(String name){return points.stream().filter(this::here).filter(p->p.name.equalsIgnoreCase(name)).findFirst().orElse(null);}
    private void add(String name,Vec3 pos){if(points.size()>=MAX_POINTS){ClientCommandHandler.send("Достигнут лимит: 128 меток.");return;}var p=new Point(server,dimension,name,pos.x,pos.y,pos.z);if(!valid(p)){ClientCommandHandler.send("Некорректное название или координаты.");return;}if(find(name)!=null){ClientCommandHandler.send("Метка с таким названием уже есть.");return;}points.add(p);save();ClientCommandHandler.send("Создана метка: "+name+" · "+Math.round(pos.x)+" "+Math.round(pos.y)+" "+Math.round(pos.z));}
    private static final String PET_HOME = "Дом_питомца";

    public double petHomeDistanceSquared(Vec3 here) {
        if (!context()) return Double.POSITIVE_INFINITY;
        for (var point : points) {
            if (point.name().equals(PET_HOME) && here(point)) return point.position().distanceToSqr(here);
        }
        return Double.POSITIVE_INFINITY;
    }

    public String petHome(boolean clear) {
        if (!net.aero.visuals.regulator.ServerRestrictions.allowed("pet")) return "Питомцы недоступны по правилам сервера.";
        if (!net.aero.visuals.guard.AeroGuard.allowed() || !context()) return "Открой мир и дождись загрузки меток.";
        var previous = find(PET_HOME);
        if (clear) {
            if (previous != null) { points.remove(previous); save(); }
            return previous != null ? "Дом питомца сброшен." : "Дом ещё не задан.";
        }
        var at = Minecraft.getInstance().player.position();
        var home = new Point(server, dimension, PET_HOME, at.x, at.y, at.z);
        if (!valid(home) || previous == null && points.size() >= MAX_POINTS) return "Не удалось сохранить дом питомца.";
        if (previous != null) points.remove(previous);
        points.add(home);
        save();
        return "Дом питомца сохранён в этом месте.";
    }

    public void petHomeCommand(String operation) {
        ClientCommandHandler.send(switch (operation.toLowerCase(Locale.ROOT)) {
            case "home" -> petHome(false);
            case "clearhome" -> petHome(true);
            default -> ".pet home · .pet clearhome";
        });
    }

    public String markChest(int x, int y, int z) {
        if (!net.aero.visuals.guard.AeroGuard.allowed() || !context()) return "Открой мир и дождись загрузки меток.";
        String name = "Chest_" + x + "_" + y + "_" + z;
        boolean exists = find(name) != null;
        var marker = new Point(server, dimension, name, x + .5, y + 1.0, z + .5);
        if (!valid(marker) || !exists && points.size() >= MAX_POINTS) return "Не удалось создать метку. Проверь лимит меток.";
        setEnabled(true);
        if (!isEnabled()) return "Метки недоступны в этом мире.";
        if (exists) return "Метка этого контейнера уже есть.";
        points.add(marker);
        save();
        return "Метка поставлена: " + x + " " + y + " " + z;
    }

    public void command(String[] parts){
        if(!net.aero.visuals.guard.AeroGuard.allowed()){ClientCommandHandler.send("Сначала завершите проверку доступа AeroVisuals.");return;}
        if(loadFailed){ClientCommandHandler.send("Не удалось загрузить метки. Проверьте файл меток в папке config.");return;}
        if(!context()){ClientCommandHandler.send(loaded?"Откройте мир перед работой с метками.":"Метки ещё загружаются.");return;}
        if(parts.length<2){help();return;}String operation=parts[1].toLowerCase(Locale.ROOT);
        try{switch(operation){
            case "add"->{if(parts.length!=3&&parts.length!=6){help();break;}Vec3 pos=Minecraft.getInstance().player.position();if(parts.length==6)pos=new Vec3(Double.parseDouble(parts[3]),Double.parseDouble(parts[4]),Double.parseDouble(parts[5]));setEnabled(true);add(parts[2],pos);}
            case "look"->{if(parts.length!=3){help();break;}var mc=Minecraft.getInstance();var hit=mc.player.pick(64,1,false);if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK){var p=block.getBlockPos();setEnabled(true);add(parts[2],new Vec3(p.getX()+.5,p.getY()+1,p.getZ()+.5));}else ClientCommandHandler.send("Наведитесь на блок в пределах 64 блоков.");}
            case "remove","del"->{if(parts.length!=3){help();break;}var p=find(parts[2]);if(p==null)ClientCommandHandler.send("Метка не найдена в этом измерении.");else{points.remove(p);save();ClientCommandHandler.send("Удалена метка: "+p.name);}}
            case "list"->{var current=points.stream().filter(this::here).toList();if(current.isEmpty())ClientCommandHandler.send("Меток в этом измерении нет.");for(var p:current)ClientCommandHandler.send(p.name+" · "+Math.round(p.x)+" "+Math.round(p.y)+" "+Math.round(p.z));}
            case "clear"->{if(parts.length==3&&parts[2].equalsIgnoreCase("confirm")){points.removeIf(this::here);save();ClientCommandHandler.send("Метки текущего измерения удалены.");}else ClientCommandHandler.send("Для удаления меток этого измерения: .wp clear confirm");}
            default->help();
        }}catch(NumberFormatException e){ClientCommandHandler.send("Координаты должны быть числами.");}
    }
    private void help(){ClientCommandHandler.send(".wp add <имя> [x y z] · .wp look <имя> · .wp remove <имя> · .wp list · .wp clear confirm");}
    @Subscribe private void render(Render2DEvent event) {
        if (points.isEmpty() || !context() || !TargetMarkerProjection.hasCurrentCapture()) return;
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        var gui = event.context();
        var playerPosition = mc.player.position();
        int screenWidth = gui.guiWidth(), screenHeight = gui.guiHeight();
        double maxRange = range.get();
        double maxDistanceSquared = maxRange * maxRange;
        boolean showDistance = distance.get();
        double font = 10 * scale.get();
        int color = tint.get().eval();
        int drawn = 0;
        for (var point : points) {
            if (!here(point)) continue;
            double dx = point.x - playerPosition.x;
            double dy = point.y - playerPosition.y;
            double dz = point.z - playerPosition.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > maxDistanceSquared) continue;
            var screen = markerScreen;
            if (!TargetMarkerProjection.projectCaptured(point.x, point.y + .6, point.z, screenWidth, screenHeight, screen)) continue;
            String label = showDistance ? point.name + " · " + Math.round(Math.sqrt(distanceSquared)) + " м" : point.name;
            double width = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, label, font) + 14;
            double height = font + 10;
            double x = Math.clamp(screen.x - width / 2, 2, Math.max(2, screenWidth - width - 2));
            double y = Math.clamp(screen.y - height, 2, Math.max(2, screenHeight - height - 2));
            UiRenderer.roundedRect(gui, x, y, width, height, 6, 0xDB101118);
            UiRenderer.roundedRect(gui, x + 5, y + 4, 2, height - 8, 1, color);
            UiRenderer.font(gui, FontFace.GOLOS_SEMI, label, x + 10, y + 5, font, 0xFFF1F4FF);
            if (++drawn >= 64) break;
        }
    }
    @Override public void close() {
        synchronized (saveLock) {
            if (closed) return;
            closed = true;
            clearContext();
            startSave();
            io.shutdown();
            if (!saveScheduled) return;
        }
        try {
            if (!io.awaitTermination(2, TimeUnit.SECONDS)) {
                AeroVisuals.LOGGER.warn("Timed out waiting for the final waypoint save");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
