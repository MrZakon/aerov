package net.aero.visuals.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.aero.visuals.AeroVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Централизованно компилирует и освобождает direct-GLSL программы. */
public final class ShaderManager implements AutoCloseable {
    private static final Map<String, String> SOURCES = Map.of(
            "liquid_glass", "core/liquidglassnew",
            "rounded_outline", "core/dw_rounded_outline",
            "border", "core/border",
            "msdf_font", "core/msdf_font");
    private final Map<String, Optional<ShaderProgram>> programs = new LinkedHashMap<>();
    private boolean initialized;

    public void initializeOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        initialized = true;
    }

    public Optional<ShaderProgram> find(String name) {
        if (!initialized || name == null || !SOURCES.containsKey(name)) return Optional.empty();
        RenderSystem.assertOnRenderThread();
        return programs.computeIfAbsent(name, this::load);
    }

    public boolean isInitialized() { return initialized; }

    private Optional<ShaderProgram> load(String name) {
        String basePath = SOURCES.get(name);
        ResourceManager resources = MinecraftClient.getInstance().getResourceManager();
        try {
            String vertex = ShaderSourceLoader.load(resources,
                    Identifier.of(AeroVisuals.MOD_ID, "shaders/" + basePath + ".vsh"));
            String fragment = ShaderSourceLoader.load(resources,
                    Identifier.of(AeroVisuals.MOD_ID, "shaders/" + basePath + ".fsh"));
            return Optional.of(new ShaderProgram(vertex, fragment, name));
        } catch (IOException | RuntimeException exception) {
            AeroVisuals.LOGGER.warn("Custom shader '{}' is unavailable; UI fallback stays active", name, exception);
            return Optional.empty();
        }
    }

    public void reload() {
        close();
        initializeOnRenderThread();
    }

    @Override
    public void close() {
        programs.values().forEach(program -> program.ifPresent(ShaderProgram::close));
        programs.clear();
        initialized = false;
    }
}
