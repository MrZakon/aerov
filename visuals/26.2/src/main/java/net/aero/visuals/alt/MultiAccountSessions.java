package net.aero.visuals.alt;

import net.aero.visuals.AeroVisuals;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.notification.NotificationManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class MultiAccountSessions {
    private static final long REFRESH = 300_000_000L;
    private static final long STOP_GRACE_SECONDS = 10;
    private static final String ONLINE = "online";
    static final int MAX_RUNNING = 10;
    private final MultiAccountModule module;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private long nextRefresh;
    private long nextMemoryCheck;
    private long lowMemorySince;
    private MemoryShortfall memoryShortfall;
    private Account lastPlayed;
    private int cycleKey;
    private boolean autoRespawn;

    MultiAccountSessions(MultiAccountModule module) {
        this.module = module;
    }

    void openMenu(Minecraft client, Screen parent, Account previous) {
        client.gui.setScreen(new MultiAccountScreen(parent, this, module, previous));
    }

    void showPrimary(Minecraft client) {
        lastPlayed = null;
        client.gui.setScreen(null);
    }

    static final class Account {
        final String name;
        MultiAccountProfile profile;
        boolean microsoft;
        Path directory;
        CompletableFuture<StartedProcess> launch;
        Process process;
        MultiAccountFiles.State state = new MultiAccountFiles.State();
        int command;
        int heapMb;
        boolean stopping;
        boolean preview;
        boolean activePreview;
        boolean lastRespawn;
        boolean deathNotified;

        Account(String name) {
            this.name = name;
            profile = MultiAccountProfile.load(name);
            microsoft = MultiAccountMicrosoft.exists(name);
            state.phase = "idle";
        }

        boolean online() {
            return ONLINE.equals(state.phase) && live() && !stopping;
        }

        boolean live() {
            return launch != null || process != null && process.isAlive();
        }

        boolean active() {
            return live() || process != null;
        }
    }

    List<Account> accounts() {
        return List.copyOf(accounts.values());
    }

    boolean add(String name, Minecraft client) {
        if (!AltManager.isValidUsername(name) || name.equalsIgnoreCase(client.getUser().getName())) return false;
        String id = name.toLowerCase(Locale.ROOT);
        if (accounts.containsKey(id)) return false;
        accounts.put(id, new Account(name));
        AltManager.addAlt(name);
        return true;
    }

    Account addMicrosoft(MultiAccountMicrosoft.Profile identity, Minecraft client) throws IOException {
        String name = identity.name();
        if (name.equalsIgnoreCase(client.getUser().getName())) {
            throw new IOException(LocalText.pick("Этот аккаунт уже основной", "This is already the primary account", "Цей акаунт уже основний"));
        }
        String id = name.toLowerCase(Locale.ROOT);
        Account existing = accounts.get(id);
        if (existing != null && existing.active()) {
            throw new IOException(LocalText.pick("Сначала останови этот аккаунт", "Stop this account first", "Спочатку зупини цей акаунт"));
        }

        MultiAccountMicrosoft.save(name, identity);
        AltManager.addAlt(name);
        Account account = accounts.computeIfAbsent(id, ignored -> new Account(name));
        account.microsoft = true;
        return account;
    }

    void load(Minecraft client) {
        var saved = AltManager.getAlts();
        String primary = client.getUser().getName();
        accounts.values().removeIf(account -> !account.active() && (account.name.equalsIgnoreCase(primary)
                || saved.stream().noneMatch(account.name::equalsIgnoreCase)));
        for (String name : saved) {
            if (AltManager.isValidUsername(name) && !name.equalsIgnoreCase(client.getUser().getName())) {
                accounts.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new Account(name));
            }
        }
    }

    void remove(Account account) {
        if (account == null || account.active()) return;
        if (account.microsoft) {
            try { MultiAccountMicrosoft.forget(account.name); }
            catch (IOException failure) {
                AeroVisuals.LOGGER.warn("Could not remove Microsoft profile for {}", account.name, failure);
                return;
            }
        }
        accounts.remove(account.name.toLowerCase(Locale.ROOT));
        AltManager.removeAlt(account.name);
    }

    boolean start(Account account, Minecraft client, int defaultMemoryMb) {
        memoryShortfall = null;
        if (!canControl() || account == null || account.name.equalsIgnoreCase(client.getUser().getName())) return false;
        if (account.online() || account.stopping || client.getConnection() == null || client.level == null) return false;
        var current = client.getCurrentServer();
        if (current == null || !ServerAddress.isValidAddress(current.ip)) return false;
        if (account.process != null && account.process.isAlive() && "disconnected".equals(account.state.phase)) {
            return send(account, "reconnect", null);
        }
        if (account.active() || runningCount() >= MAX_RUNNING) return false;
        var profile = account.profile;
        int memoryMb = profile.effectiveHeapMb(defaultMemoryMb);
        long available = MultiAccountBudget.freeMemoryMb();
        long growthReserve = 0;
        for (Account existing : accounts.values()) {
            if (!existing.live()) continue;
            growthReserve += existing.launch != null
                    ? existing.heapMb + MultiAccountBudget.NATIVE_ALLOWANCE_MB
                    : existing.heapMb / 2L;
        }
        long required = MultiAccountBudget.MAIN_RESERVE_MB + memoryMb + MultiAccountBudget.NATIVE_ALLOWANCE_MB + growthReserve;
        if ((available >= 0 && available < required) || (available < 0 && runningCount() > 0)) {
            memoryShortfall = new MemoryShortfall(available, required);
            return false;
        }
        account.heapMb = memoryMb;
        account.state = new MultiAccountFiles.State();
        account.directory = null;
        account.command = 0;
        account.deathNotified = false;
        account.activePreview = false;
        account.lastRespawn = false;
        account.stopping = false;
        String name = account.name;
        boolean microsoft = account.microsoft;
        String nameOnList = current.name;
        String address = current.ip;
        var packPolicy = current.getResourcePackStatus();
        account.launch = CompletableFuture.supplyAsync(() -> {
            Path directory = null;
            try {
                directory = Files.createTempDirectory("aerovisuals-multi-");
                ServerData copy = new ServerData(nameOnList, address, ServerData.Type.OTHER);
                copy.setResourcePackStatus(packPolicy);
                var identity = microsoft ? MultiAccountMicrosoft.refresh(name) : null;
                var process = MultiAccountLaunch.start(directory, name, copy, memoryMb, profile.renderDistance(), identity);
                Path sessionDirectory = directory;
                process.onExit().thenRunAsync(() -> cleanSessionDirectory(sessionDirectory, 4),
                        CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.SECONDS));
                return new StartedProcess(process, directory);
            } catch (IOException | InterruptedException | RuntimeException failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                if (directory != null) cleanSessionDirectory(directory, 4);
                throw new java.util.concurrent.CompletionException(failure);
            }
        });
        return true;
    }

    private record StartedProcess(Process process, Path directory) {}

    private static void cleanSessionDirectory(Path directory, int attempts) {
        try {
            Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
            Path target = directory.toRealPath();
            if (!temporaryRoot.equals(target.getParent()) || !target.getFileName().toString().startsWith("aerovisuals-multi-")) return;
            java.util.List<Path> files;
            try (var walk = Files.walk(target)) {
                files = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            }
            for (Path file : files) Files.deleteIfExists(file);
        } catch (java.nio.file.NoSuchFileException ignored) {
            // Already removed.
        } catch (IOException failure) {
            if (attempts > 1) {
                CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.SECONDS)
                        .execute(() -> cleanSessionDirectory(directory, attempts - 1));
            } else {
                AeroVisuals.LOGGER.debug("Could not remove background session files", failure);
            }
        }
    }

    void stop(Account account) {
        if (account == null || account.stopping || !account.active()) return;
        account.stopping = true;
        if (account.process != null) {
            if (account.process.isAlive()) {
                send(account, "stop", null);
                enforceStop(account.process);
            }
        } else if (account.launch != null) {
            // No other commands can have been sent while the process is still starting.
            account.launch.thenAccept(started -> requestStop(started.process(), started.directory(), 1));
        }
    }

    void stopAll() {
        for (Account account : accounts.values()) stop(account);
    }

    private static void requestStop(Process process, Path directory, int number) {
        if (!process.isAlive()) return;
        var request = new MultiAccountFiles.Request();
        request.action = "stop";
        writeCommand(directory, number, request);
        enforceStop(process);
    }

    private static void enforceStop(Process process) {
        CompletableFuture.delayedExecutor(STOP_GRACE_SECONDS, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
            if (process.isAlive()) process.destroyForcibly();
        });
    }

    boolean canControl() {
        return module.isEnabled();
    }

    void tick(int cycleKey) {
        this.cycleKey = cycleKey;
        long now = System.nanoTime();
        if (now < nextRefresh) return;
        nextRefresh = now + REFRESH;
        // Each worker keeps its own connection across transfers in the primary client.
        boolean active = false;
        for (Account account : accounts.values()) {
            refresh(account);
            active |= account.active();
        }
        if (!active) {
            lowMemorySince = 0;
        } else {
            protectMainClient(now);
        }
    }

    MemoryShortfall memoryShortfall() {
        return memoryShortfall;
    }

    record MemoryShortfall(long availableMb, long requiredMb) {}

    private void protectMainClient(long now) {
        if (now < nextMemoryCheck) return;
        nextMemoryCheck = now + 1_000_000_000L;
        long free = MultiAccountBudget.freeMemoryMb();
        if (free < 0 || free >= MultiAccountBudget.LOW_MEMORY_MB) {
            lowMemorySince = 0;
            return;
        }
        if (lowMemorySince == 0) lowMemorySince = now;
        if (now - lowMemorySince < 5_000_000_000L) return;
        for (Account account : accounts.values()) {
            if (!account.live() || account.stopping || (MultiAccountView.active() && account == lastPlayed)) continue;
            stop(account);
            lowMemorySince = now;
            NotificationManager.post(account.name + " · " + LocalText.pick(
                    "Останавливаем фоновый Minecraft: системе не хватает оперативной памяти",
                    "Stopping background Minecraft: system memory is low",
                    "Зупиняємо фоновий Minecraft через нестачу оперативної пам’яті"), false);
            return;
        }
    }

    private void refresh(Account account) {
        if (account.launch != null && account.launch.isDone()) {
            try {
                StartedProcess started = account.launch.join();
                account.directory = started.directory();
                account.process = started.process();
            } catch (java.util.concurrent.CompletionException failure) {
                account.stopping = false;
                account.state.phase = "failed";
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                account.state.detail = java.util.Objects.toString(cause.getMessage(), "");
                AeroVisuals.LOGGER.warn("Could not start Aero background session", cause);
            } finally {
                account.launch = null;
            }
        }
        if (account.process == null) return;
        if (!account.process.isAlive()) {
            if (account.stopping) account.state.phase = "stopped";
            else if (!"disconnected".equals(account.state.phase)) account.state.phase = "failed";
            account.process = null;
            account.stopping = false;
            return;
        }
        try {
            var state = MultiAccountFiles.read(account.directory.resolve("state.json"), MultiAccountFiles.State.class);
            if (state != null) {
                account.state = state;
                if (state.dead && !account.deathNotified) {
                    NotificationManager.post(account.name + " · " + LocalText.pick("Погиб", "Died", "Загинув"), false);
                }
                account.deathNotified = state.dead;
            }
            if (account.preview != account.activePreview || autoRespawn != account.lastRespawn) {
                var options = new MultiAccountFiles.Request();
                options.preview = account.preview;
                options.respawn = autoRespawn;
                MultiAccountFiles.write(account.directory.resolve("options.json"), options);
                account.activePreview = account.preview;
                account.lastRespawn = autoRespawn;
            }
        } catch (IOException failure) {
            AeroVisuals.LOGGER.debug("Could not read background account state", failure);
        }
    }

    boolean play(Minecraft client, Account account, boolean control) {
        if (!canControl() || account == null || !account.online() || account.process == null) return false;
        int viewId = account.command + 1;
        ViewSize size = viewSize(client, control);
        if (!sendView(account, "view", viewId, size)) return false;
        var current = client.gui.screen();
        var parent = current instanceof MultiAccountView view ? view.parent() : current;
        lastPlayed = account;
        client.gui.setScreen(new MultiAccountView(parent, this, account, viewId, control, size));
        return true;
    }

    boolean playNext(Minecraft client) {
        if (!(client.gui.screen() instanceof MultiAccountView)) lastPlayed = null;
        List<Account> online = accounts.values().stream().filter(Account::online).toList();
        int next = online.indexOf(lastPlayed) + 1;
        if (next == online.size()) {
            lastPlayed = null;
            return false;
        }
        return play(client, online.get(next), false);
    }

    int cycleKey() { return cycleKey; }

    record ViewSize(int width, int height, int fps) {}

    static ViewSize viewSize(Minecraft client, boolean control) {
        int width = Math.max(1, MultiAccountFocus.frameWidth(client));
        int height = Math.max(1, MultiAccountFocus.frameHeight(client));
        int fps = control ? MultiAccountBudget.CONTROL_FPS
                : Math.clamp(MultiAccountFocus.preferredFps(client), 10, MultiAccountBudget.UNLIMITED_FPS);
        double pixelBudget = control ? 1920.0 * 1080 : MultiAccountFeed.MAX_PIXEL_BYTES / 4.0;
        int maxDimension = control ? 1920 : MultiAccountFeed.MAX_DIMENSION;
        double scale = Math.min(1.0, Math.min(
                (double) maxDimension / Math.max(width, height),
                Math.sqrt(pixelBudget / ((double) width * height))));
        return new ViewSize(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), fps);
    }

    boolean resizeView(Account account, Path directory, int viewId, ViewSize size) {
        return account.live() && !account.stopping && directory.equals(account.directory)
                && sendView(account, "resize", viewId, size);
    }

    void endView(Account account, Path directory, int viewId) {
        if (!account.stopping && account.process != null && account.process.isAlive()
                && directory.equals(account.directory)) {
            send(account, "unview", null, viewId);
        }
    }

    boolean atLimit() {
        return runningCount() >= MAX_RUNNING;
    }

    private long runningCount() {
        return accounts.values().stream().filter(Account::active).count();
    }

    boolean chat(Account account, String message) {
        return account != null && account.online() && send(account, "chat", message);
    }

    int broadcast(String message) {
        int sent = 0;
        for (Account account : accounts.values()) {
            if (chat(account, message)) sent++;
        }
        return sent;
    }

    boolean autoRespawn() {
        return autoRespawn;
    }

    void toggleAutoRespawn() {
        autoRespawn = !autoRespawn;
    }

    void setPreview(Account selected) {
        for (Account account : accounts.values()) account.preview = account == selected;
    }

    private boolean send(Account account, String action, String message) {
        return send(account, action, message, 0);
    }

    private boolean sendView(Account account, String action, int viewId, ViewSize size) {
        var request = new MultiAccountFiles.Request();
        request.action = action;
        request.viewId = viewId;
        request.width = size.width();
        request.height = size.height();
        request.fps = size.fps();
        return send(account, request);
    }

    private boolean send(Account account, String action, String message, int viewId) {
        var request = new MultiAccountFiles.Request();
        request.action = action;
        request.message = message;
        request.viewId = viewId;
        return send(account, request);
    }

    private boolean send(Account account, MultiAccountFiles.Request request) {
        int number = account.command + 1;
        if (!writeCommand(account.directory, number, request)) return false;
        account.command = number;
        return true;
    }

    private static boolean writeCommand(Path directory, int number, MultiAccountFiles.Request request) {
        try {
            MultiAccountFiles.write(directory.resolve("command-" + number + ".json"), request);
            return true;
        } catch (IOException failure) {
            AeroVisuals.LOGGER.warn("Could not send background account command", failure);
            return false;
        }
    }
}
