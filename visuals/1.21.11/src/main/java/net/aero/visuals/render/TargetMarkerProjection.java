package net.aero.visuals.render;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import java.lang.ref.WeakReference;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
/** Copies the actual world projection, including FOV, zoom and view bobbing. */
public final class TargetMarkerProjection {
    private static final Matrix4f viewProjection=new Matrix4f();private static Vec3d camera=Vec3d.ZERO;private static WeakReference<Object> world = new WeakReference<>(null);private static long frame;
    public static void capture(Matrix4f view,Matrix4f projection,Camera eye){var mc=MinecraftClient.getInstance();viewProjection.set(projection).mul(view);camera=eye.getCameraPos();if (world.get() != mc.world) world = new WeakReference<>(mc.world);frame=System.nanoTime();}
    // Used only by the HUD render thread; callers own their screen coordinates.
    private static final Vector4f clipScratch = new Vector4f();
    private static final Vector2f screenScratch = new Vector2f();

    private TargetMarkerProjection() {}

    public static boolean hasCurrentCapture() {
        var level = MinecraftClient.getInstance().world;
        return level != null && world.get() == level && frame != 0
                && System.nanoTime() - frame <= 250_000_000L;
    }

    public static Vector2f project(Vec3d point, int width, int height) {
        return project(point.x, point.y, point.z, width, height);
    }

    public static Vector2f project(double px, double py, double pz, int width, int height) {
        if (!hasCurrentCapture()) return null;
        return projectCaptured(px, py, pz, width, height, screenScratch) ? new Vector2f(screenScratch) : null;
    }

    /** Call after checking hasCurrentCapture(), on the same render thread and pass. */
    public static boolean projectCaptured(double px, double py, double pz, int width, int height, Vector2f out) {
        var projected = clipScratch.set((float) (px - camera.x), (float) (py - camera.y),
                (float) (pz - camera.z), 1).mul(viewProjection);
        if (projected.w <= .001f) return false;
        float x = projected.x / projected.w;
        float y = projected.y / projected.w;
        float z = projected.z / projected.w;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || z < -1 || z > 1 || Math.abs(x) > 1.2 || Math.abs(y) > 1.2) return false;
        out.set((x + 1) * width * .5f, (1 - y) * height * .5f);
        return true;
    }
}
