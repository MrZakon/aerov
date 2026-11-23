package net.aero.visuals.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Interface implemented by modules that perform custom in-world 3D rendering
 * during LevelRenderEvents.AFTER_SOLID_FEATURES.
 */
@FunctionalInterface
public interface WorldRenderable {
    void renderWorld(LevelRenderContext context);
}
