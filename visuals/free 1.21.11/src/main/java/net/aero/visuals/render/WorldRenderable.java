package net.aero.visuals.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/**
 * Interface implemented by modules that perform custom in-world 3D rendering
 * during WorldRenderEvents.AFTER_ENTITIES.
 */
@FunctionalInterface
public interface WorldRenderable {
    void renderWorld(WorldRenderContext context);
}
