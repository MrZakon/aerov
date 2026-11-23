package net.aero.visuals;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.aero.visuals.config.ClientSettings;
import net.aero.visuals.config.ConfigManager;
import net.aero.visuals.cosmetic.CosmeticCatalog;
import net.aero.visuals.cosmetic.CosmeticSelection;
import net.aero.visuals.cosmetic.CosmeticSyncService;
import net.aero.visuals.config.GuiScaleMemory;
import net.aero.visuals.config.StudioProfiles;
import net.aero.visuals.event.EventBus;
import net.aero.visuals.event.impl.KeyEvent;
import net.aero.visuals.event.impl.MouseEvent;
import net.aero.visuals.event.impl.Render2DEvent;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.gui.ClickGuiScreen;
import net.aero.visuals.gui.AeroBranding;
import net.aero.visuals.gui.AeroPauseScreen;
import net.aero.visuals.gui.AeroTitleScreen;
import net.aero.visuals.gui.StartupWelcomeAnimation;
import net.aero.visuals.hud.HudLayout;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.module.ModuleRegistry;
import net.aero.visuals.render.RenderEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ClientRuntime implements AutoCloseable {
    private static ClientRuntime instance;

    private final EventBus events = new EventBus();
    private final ModuleManager modules = new ModuleManager(events);
    private final RenderEngine renderEngine = new RenderEngine();
    private final ClientSettings settings = new ClientSettings();
    private final HudLayout hudLayout = new HudLayout();
    private final Render2DEvent render2DEvent = new Render2DEvent();
    private final TickEvent.Client tickEvent = new TickEvent.Client();
    private final StartupWelcomeAnimation startupWelcome = new StartupWelcomeAnimation();
    private CosmeticCatalog cosmeticCatalog;
    private CosmeticSelection cosmeticSelection;
    private CosmeticSyncService cosmeticSync;
    private net.aero.visuals.friend.FriendsService friends;
    private net.aero.visuals.friend.FriendChatService friendChat;
    private net.aero.visuals.config.CloudConfigsService cloudConfigs;
    private ConfigManager config;
    private StudioProfiles profiles;
    private KeyBinding inspectKey;
    private Object lastWorld;
    private boolean windowBrandingApplied;
    private boolean lastDeviceAccess;
    private int lastPersistedGuiScale = Integer.MIN_VALUE;
    private long guiScaleRetryAtNanos;
    private int pendingGuiScale;
    private java.util.concurrent.CompletableFuture<Boolean> guiScaleWrite;

    public static void initialize() {
        if (instance != null) return;
        instance = new ClientRuntime();
        instance.start();
    }

    private void start() {
        cosmeticCatalog = CosmeticCatalog.load();
        cosmeticSelection = new CosmeticSelection(FabricLoader.getInstance().getConfigDir().resolve("lunacy_visuals_cosmetics.json"));
        cosmeticSelection.load();
        ModuleRegistry.registerAll(modules, settings);
        config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve("lunacy_visuals.json"), modules, settings);
        config.load();
        net.aero.visuals.regulator.ServerRestrictions.initialize(this);
        cosmeticSync=new CosmeticSyncService(this);
        cosmeticSync.start();
        friends=new net.aero.visuals.friend.FriendsService(this);friends.start();
        friendChat=new net.aero.visuals.friend.FriendChatService(this);friendChat.start();
        cloudConfigs=new net.aero.visuals.config.CloudConfigsService(this,task->MinecraftClient.getInstance().execute(task));cloudConfigs.start();
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var client = MinecraftClient.getInstance();
            if (player == client.player) {
                var tracker = module(net.aero.visuals.feature.utility.ChestTracker.class);
                if (tracker != null) tracker.clicked(client, hit.getBlockPos());
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if(!net.aero.visuals.guard.AeroGuard.allowed())return net.minecraft.util.ActionResult.PASS;
            if (player == MinecraftClient.getInstance().player) {
                var targetHud = module(net.aero.visuals.module.impl.hud.TargetHudModule.class);
                if (targetHud != null) targetHud.onAttack(entity);
                var targetEsp = module(net.aero.visuals.module.impl.pvp.TargetEspModule.class);
                if (targetEsp != null) targetEsp.onAttack(entity);
                var hitSounds = module(net.aero.visuals.module.impl.pvp.HitSoundsModule.class);
                if (hitSounds != null) hitSounds.onAttack(entity);
                net.aero.visuals.feature.showcase.ShowcaseModules.attack(entity);
                net.aero.visuals.feature.premium.PremiumEvents.attack(entity);
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        profiles = new StudioProfiles(config, FabricLoader.getInstance().getConfigDir().resolve("lunacy_visuals_profiles/1.21.11"));
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addLast(Identifier.of(AeroVisuals.MOD_ID, "visual_overlay"), (context, counter) -> {
            if(!net.aero.visuals.guard.AeroGuard.allowed())return;
            if (MinecraftClient.getInstance().player == null) return;
            renderEngine.beginFrame(settings.customShaders());
            try {
                events.post(render2DEvent.reset(context, counter.getTickProgress(true)));
                if (!MinecraftClient.getInstance().options.hudHidden) {
                    net.aero.visuals.notification.NotificationManager.render(context);
                }
            } finally {
                render2DEvent.reset(null, 0);
            }
        });
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> net.aero.visuals.feature.premium.PremiumPostFx.background());
        WorldRenderEvents.AFTER_ENTITIES.register(this::renderWorldEffects);
        ClientLifecycleEvents.CLIENT_STARTED.register(net.aero.visuals.config.DefaultServers::addOnce);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mcClient -> close());
        client.concurrent.SystemClient.getInstance();
        net.aero.visuals.config.ConfigSnapshots.loadInterface(this);
        AeroVisuals.LOGGER.info("AeroVisuals initialized with {} modules", modules.all().size());
    }



    private final java.util.Map<String, Long> worldRenderFailures = new java.util.HashMap<>();

    private void renderWorldEffects(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        for (var module : modules.worldRenderers()) {
            if (!module.isEnabled()) continue;
            try {
                long started = net.aero.visuals.render.performance.PerformanceProbe.begin();
                try { ((net.aero.visuals.render.WorldRenderable) module).renderWorld(context); }
                finally { net.aero.visuals.render.performance.PerformanceProbe.end(module.name(), started); }
            } catch (RuntimeException | LinkageError failure) {
                reportWorldRenderFailure(module.id(), failure);
            }
        }
        try {
            long started = net.aero.visuals.render.performance.PerformanceProbe.begin();
            try { net.aero.visuals.feature.ImportedModels.renderPets(context); }
            finally { net.aero.visuals.render.performance.PerformanceProbe.end("Питомцы · рендер", started); }
        } catch (RuntimeException | LinkageError failure) {
            reportWorldRenderFailure("pet_render", failure);
        }
    }

    private void reportWorldRenderFailure(String effect, Throwable failure) {
        long now = System.nanoTime();
        Long last = worldRenderFailures.get(effect);
        if (last != null && now - last < 10_000_000_000L) return;
        worldRenderFailures.put(effect, now);
        AeroVisuals.LOGGER.error("World effect {} failed during rendering", effect, failure);
    }

    private void onTick(MinecraftClient mcClient) {
        if(net.aero.visuals.guard.AeroGuard.allowed())net.aero.visuals.hud.HudDragController.tick(mcClient);else net.aero.visuals.hud.HudDragController.finish();
        net.aero.visuals.regulator.ServerRestrictions.clientTick(mcClient);
        if (cosmeticSync != null) cosmeticSync.tick(mcClient);
        for(var module:modules.all())module.refreshAccess();
        boolean deviceAccess=net.aero.visuals.guard.AeroGuard.allowed();
        if(deviceAccess!=lastDeviceAccess){lastDeviceAccess=deviceAccess;var legacy=client.concurrent.SystemClient.INSTANCE;if(legacy!=null&&legacy.getModuleRegistry()!=null)legacy.getModuleRegistry().update2();}
        if(!net.aero.visuals.guard.AeroGuard.allowed()&&(mcClient.currentScreen instanceof client.gui.screen.ClickGuiScreen||mcClient.currentScreen instanceof net.aero.visuals.gui.CosmeticPreviewScreen))mcClient.setScreen(new net.aero.visuals.gui.DeviceAccessScreen());
        persistGuiScale(mcClient, false);
        if (!windowBrandingApplied) {
            windowBrandingApplied = AeroBranding.applyWindowIcon(mcClient);
        }

        try {
            var server = mcClient.getCurrentServerEntry();
            profiles.switchServer(server == null ? "" : server.address);
        } catch (java.io.IOException exception) {
            AeroVisuals.LOGGER.warn("Could not switch AeroVisuals profile", exception);
        }
        config.tick();
        net.aero.visuals.feature.FeatureJournal.flushIfNeeded();
        var multiAccount = module(net.aero.visuals.alt.MultiAccountModule.class);
        if (multiAccount != null) multiAccount.tickSessions();
        events.post(tickEvent.reset(mcClient));
        long petsStarted = net.aero.visuals.render.performance.PerformanceProbe.begin();
        try { net.aero.visuals.feature.ImportedModels.tickPets(mcClient); }
        finally { net.aero.visuals.render.performance.PerformanceProbe.end("Питомцы · физика", petsStarted); }
        net.aero.visuals.feature.showcase.ShowcaseModules.tick(mcClient);
        net.aero.visuals.feature.premium.PremiumEvents.tick(mcClient);
        if (mcClient.player != null && net.aero.visuals.guard.AeroGuard.allowed()) {
            try {
                client.concurrent.SystemClient sc = client.concurrent.SystemClient.getInstance();
                if (sc != null && sc.getModuleDispatcher() != null) {
                    sc.getModuleDispatcher().update6();
                    sc.getModuleDispatcher().update2();
                }
            } catch (Throwable ignored) {
            }
        }

        
        if (mcClient.world != lastWorld) {
            lastWorld = mcClient.world;
            var targetHud = module(net.aero.visuals.module.impl.hud.TargetHudModule.class);
            if (targetHud != null) targetHud.clearTarget();
        }

        if (mcClient.currentScreen instanceof TitleScreen && !(mcClient.currentScreen instanceof AeroTitleScreen)) {
            mcClient.setScreen(new AeroTitleScreen());
        } else if (mcClient.currentScreen instanceof GameMenuScreen && !(mcClient.currentScreen instanceof AeroPauseScreen)) {
            mcClient.setScreen(new AeroPauseScreen());
        }
    }

    private void persistGuiScale(MinecraftClient client, boolean closing) {
        if (guiScaleWrite != null && (closing || guiScaleWrite.isDone())) {
            try {
                if (guiScaleWrite.join()) {
                    lastPersistedGuiScale = pendingGuiScale;
                    guiScaleRetryAtNanos = 0;
                } else {
                    guiScaleRetryAtNanos = System.nanoTime() + 1_000_000_000L;
                }
            } catch (java.util.concurrent.CompletionException failure) {
                AeroVisuals.LOGGER.warn("Could not save GUI scale", failure.getCause());
                guiScaleRetryAtNanos = System.nanoTime() + 1_000_000_000L;
            } finally {
                guiScaleWrite = null;
            }
        }
        if (guiScaleWrite != null || client.options == null) return;
        int current = client.options.getGuiScale().getValue();
        if (current == lastPersistedGuiScale || !GuiScaleMemory.isValid(current)) return;
        if (!closing && guiScaleRetryAtNanos != 0 && System.nanoTime() - guiScaleRetryAtNanos < 0) return;

        var directory = FabricLoader.getInstance().getConfigDir();
        if (closing) {
            if (!GuiScaleMemory.write(directory, current)) AeroVisuals.LOGGER.warn("Could not save GUI scale");
            return;
        }
        pendingGuiScale = current;
        guiScaleWrite = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> GuiScaleMemory.write(directory, current),
                task -> Thread.ofVirtual().name("Aero GUI scale save").start(task));
    }

    public boolean onKey(int key, int scanCode, int action, int modifiers) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (action == 1) {
            if (net.aero.visuals.guard.AeroGuard.allowed() && mc.currentScreen instanceof ChatScreen) {
                if (client.gui.widget.UiContext.getInstance().isIntIntInt2(scanCode, key, modifiers)) {
                    return true;
                }
            }
            if (mc.currentScreen == null) {
                if (ClickGuiScreen.matchesMenuKey(key, modifiers)) {
                    mc.setScreen(net.aero.visuals.guard.AeroGuard.allowed()?ClickGuiScreen.getOrInstance():new net.aero.visuals.gui.DeviceAccessScreen());
                    return true;
                }
                if(!net.aero.visuals.guard.AeroGuard.allowed())return false;
                ClickGuiScreen.onGlobalKeyPress(key);
            }
        }
        KeyEvent event = new KeyEvent(key, scanCode, action, modifiers);
        events.post(event);
        return event.isCancelled();
    }

    public boolean onMouseButton(int button, int action, int modifiers) {
        if (net.aero.visuals.guard.AeroGuard.allowed() && net.aero.visuals.hud.HudDragController.mouseButton(button, action)) return true;
        if (action == 1 && MinecraftClient.getInstance().currentScreen == null) {
            if (ClickGuiScreen.matchesMenuKey(button, modifiers)) {
                MinecraftClient.getInstance().setScreen(net.aero.visuals.guard.AeroGuard.allowed()?ClickGuiScreen.getOrInstance():new net.aero.visuals.gui.DeviceAccessScreen());
                return true;
            }
            if(!net.aero.visuals.guard.AeroGuard.allowed())return false;
            ClickGuiScreen.onGlobalKeyPress(net.aero.visuals.gui.KeyUtils.toMouseBind(button));
        }
        MouseEvent event = MouseEvent.button(button, action, modifiers);
        events.post(event);
        return event.isCancelled();
    }

    public boolean onMouseScroll(double horizontal, double vertical) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (net.aero.visuals.guard.AeroGuard.allowed() && mc.currentScreen instanceof ChatScreen && mc.mouse != null) {
            if (client.gui.widget.UiContext.getInstance().isDoubleDoubleDouble(mc.mouse.getX(), vertical, mc.mouse.getY())) {
                return true;
            }
        }
        MouseEvent event = MouseEvent.scroll(horizontal, vertical);
        events.post(event);
        return event.isCancelled();
    }



    private final java.util.Map<Class<?>, net.aero.visuals.module.Module> moduleClassCache = new java.util.concurrent.ConcurrentHashMap<>(128);

    @SuppressWarnings("unchecked")
    public <T extends net.aero.visuals.module.Module> T module(Class<T> type) {
        net.aero.visuals.module.Module cached = moduleClassCache.get(type);
        if (cached != null) return (T) cached;
        for (net.aero.visuals.module.Module m : modules.all()) {
            if (type.isInstance(m)) {
                moduleClassCache.put(type, m);
                return (T) m;
            }
        }
        return null;
    }

    public static ClientRuntime get() {
        if (instance == null) throw new IllegalStateException("AeroVisuals is not initialized");
        return instance;
    }

    public static ClientRuntime getNullable() { return instance; }
    public CosmeticCatalog cosmetics() { return cosmeticCatalog; }
    public void applyCosmetic(CosmeticCatalog.Entry entry) {
        if (entry == null) return;
        var equipped=new java.util.HashMap<>(net.aero.visuals.cosmetic.CosmeticLoadout.capture());
        equipped.put(entry.type(),entry.id());
        net.aero.visuals.cosmetic.CosmeticLoadout.apply(equipped);
    }
    public CosmeticSelection cosmeticSelection() { return cosmeticSelection; }
    public net.aero.visuals.friend.FriendsService friends(){return friends;}
    public net.aero.visuals.friend.FriendChatService friendChat(){return friendChat;}
    public net.aero.visuals.config.CloudConfigsService cloudConfigs(){return cloudConfigs;}
    public CosmeticSyncService cosmeticSync() { return cosmeticSync; }
    public EventBus events() { return events; }
    public ModuleManager modules() { return modules; }
    public ConfigManager config() { return config; }
    public StudioProfiles profiles() { return profiles; }
    public ClientSettings settings() { return settings; }
    public RenderEngine renderEngine() { return renderEngine; }
    public HudLayout hudLayout() { return hudLayout; }
    public StartupWelcomeAnimation startupWelcome() { return startupWelcome; }
    public KeyBinding inspectKey() { return inspectKey; }

    @Override
    public void close() {
        var waypoints=module(net.aero.visuals.feature.enhanced.Waypoints.class);if(waypoints!=null)waypoints.close();
        net.aero.visuals.render.FrameStallMonitor.close();
        net.aero.visuals.hud.HudDragController.finish();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            persistGuiScale(client, true);
        }
        if (profiles != null) profiles.close();
        if (config != null) config.flush();
        if(cloudConfigs!=null)cloudConfigs.close();
        net.aero.visuals.config.ConfigSnapshots.saveInterface(this);
        if(friendChat!=null)friendChat.close();
        if(friends!=null)friends.close();
        if (cosmeticSync != null) cosmeticSync.close();
        net.aero.visuals.media.WindowsMediaTracker.get().stop();
        net.aero.visuals.media.MediaArtHelper.close();
        net.aero.visuals.feature.showcase.ShowcaseModules.close();
        var chestTracker = module(net.aero.visuals.feature.utility.ChestTracker.class);
        if (chestTracker != null) chestTracker.closeIndex();
        modules.close();
        net.aero.visuals.feature.FeatureJournal.flush();
        net.aero.visuals.cosmetic.BedrockMesh.clearCache();
        renderEngine.close();
    }
}
