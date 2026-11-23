package net.aero.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Клиентская точка входа AeroVisuals.
 */
public final class AeroVisuals implements ClientModInitializer {
    public static final String MOD_ID = "aerovisuals";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        net.aero.visuals.guard.AeroGuard.verifyOrHalt();
        LOGGER.info("AeroVisuals bootstrap started");
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
            boolean registered = ResourceLoader.registerBuiltinPack(
                    Identifier.of(MOD_ID, "punchy"), container,
                    Text.literal("AeroVisuals Punchy Animations"), PackActivationType.ALWAYS_ENABLED);
            if (!registered) LOGGER.warn("Could not register the bundled Punchy resource pack");
        });
        ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(
                Identifier.of(MOD_ID, "render_resources"), (SynchronousResourceReloader) resources -> {
                    var runtime = ClientRuntime.getNullable();
                    if (runtime != null) runtime.renderEngine().reloadResources();
                });
        net.aero.visuals.alt.MultiAccountWorker.install();
        ClientRuntime.initialize();
    }
}
