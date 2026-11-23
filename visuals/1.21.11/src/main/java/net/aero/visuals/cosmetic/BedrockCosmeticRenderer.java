package net.aero.visuals.cosmetic;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.user.AeroUserService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.UUID;

/** Draws selected Bedrock cosmetic cubes in the player render pass. */
public final class BedrockCosmeticRenderer {
    private BedrockCosmeticRenderer() {}

    public static void render(MatrixStack pose, OrderedRenderCommandQueue queue, UUID owner) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null || pose == null || queue == null || runtime.cosmetics() == null) return;
        for (String type : new String[]{"cape", "wings", "bodywear", "hat", "pet"}) {
            UUID local = MinecraftClient.getInstance().player == null ? null : MinecraftClient.getInstance().player.getUuid();
            int id = owner == null || owner.equals(local) ? runtime.cosmeticSelection().get(type) : AeroUserService.cosmetic(owner, type);
            if (id < 0) continue;
            var entry = runtime.cosmetics().entries().stream().filter(e -> e.type().equals(type) && e.id() == id).findFirst().orElse(null);
            if (entry == null) continue;
            try {
                String relative = entry.resourcePath().substring("assets/aerovisuals/".length());
                Identifier texture = Identifier.of("aerovisuals", relative + "/texture.png");
                boolean hasTexture = BedrockCosmeticRenderer.class.getClassLoader().getResource("assets/aerovisuals/" + relative + "/texture.png") != null;
                if (!entry.hasModel()) {
                    if ("cape".equals(type) && hasTexture) renderCape(pose, queue, texture);
                    continue;
                }
                var geometry = runtime.cosmetics().loadGeometry(entry);
                pose.push();
                switch (CosmeticAttachment.point(type)) {
                    case HEAD -> { pose.translate(-0.25f, 1.48f, -0.25f); pose.scale(0.0625f, 0.0625f, 0.0625f); }
                    case BACK -> { pose.translate(0f, 1.05f, 0.18f); pose.scale(0.0625f, 0.0625f, 0.0625f); }
                    case FOLLOW, SHOULDER -> { pose.translate(0.45f, 0.75f, 0.1f); pose.scale(0.0625f, 0.0625f, 0.0625f); }
                    default -> { pose.pop(); continue; }
                }
                queue.submitCustom(pose, hasTexture ? RenderLayers.entityCutout(texture) : RenderLayers.debugFilledBox(), (matrix, consumer) -> {
                    for (var bone : geometry.bones()) for (var cube : bone.cubes()) {
                        double x = cube.x(), y = -cube.y() - cube.sy(), z = cube.z();
                        if (hasTexture) fillBoxTextured(consumer, matrix, x, y, z, x + cube.sx(), y + cube.sy(), z + cube.sz(), cube.u(), cube.v(), geometry.width(), geometry.height(), bone);
                        else fillBox(consumer, matrix, x, y, z, x + cube.sx(), y + cube.sy(), z + cube.sz(), color(type), bone);
                    }
                });
                pose.pop();
            } catch (Exception ignored) { }
        }
    }

    private static void renderCape(MatrixStack pose, OrderedRenderCommandQueue queue, Identifier texture) {
        pose.push();
        pose.translate(0f, 1.42f, 0.16f);
        pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6f));
        pose.scale(0.0625f, 0.0625f, 0.0625f);
        queue.submitCustom(pose, RenderLayers.entityCutout(texture), (matrix, consumer) -> {
            float x1 = -5f, x2 = 5f;
            float y1 = -16f, y2 = 0f;
            float z1 = 0f, z2 = 0.8f;

            float uBack1 = 12f / 64f, uBack2 = 22f / 64f;
            float vBack1 = 1f / 32f,  vBack2 = 17f / 32f;

            float uFront1 = 1f / 64f, uFront2 = 11f / 64f;
            float vFront1 = 1f / 32f, vFront2 = 17f / 32f;

            float uLeft1 = 11f / 64f, uLeft2 = 12f / 64f;
            float uRight1 = 0f / 64f, uRight2 = 1f / 64f;

            float uTop1 = 1f / 64f,   uTop2 = 11f / 64f;
            float vTop1 = 0f / 32f,   vTop2 = 1f / 32f;

            float uBot1 = 11f / 64f,  uBot2 = 21f / 64f;
            float vBot1 = 0f / 32f,   vBot2 = 1f / 32f;

            // 1. Back face (outside - visible from behind)
            vertex(consumer, matrix, x2, y2, z2, 0xFFFFFFFF, uBack1, vBack1, 0, 0, 1);
            vertex(consumer, matrix, x1, y2, z2, 0xFFFFFFFF, uBack2, vBack1, 0, 0, 1);
            vertex(consumer, matrix, x1, y1, z2, 0xFFFFFFFF, uBack2, vBack2, 0, 0, 1);
            vertex(consumer, matrix, x2, y1, z2, 0xFFFFFFFF, uBack1, vBack2, 0, 0, 1);

            // 2. Front face (inside - facing back of player)
            vertex(consumer, matrix, x1, y2, z1, 0xFFFFFFFF, uFront1, vFront1, 0, 0, -1);
            vertex(consumer, matrix, x2, y2, z1, 0xFFFFFFFF, uFront2, vFront1, 0, 0, -1);
            vertex(consumer, matrix, x2, y1, z1, 0xFFFFFFFF, uFront2, vFront2, 0, 0, -1);
            vertex(consumer, matrix, x1, y1, z1, 0xFFFFFFFF, uFront1, vFront2, 0, 0, -1);

            // 3. Left edge
            vertex(consumer, matrix, x1, y2, z2, 0xFFFFFFFF, uLeft1, vBack1, -1, 0, 0);
            vertex(consumer, matrix, x1, y2, z1, 0xFFFFFFFF, uLeft2, vBack1, -1, 0, 0);
            vertex(consumer, matrix, x1, y1, z1, 0xFFFFFFFF, uLeft2, vBack2, -1, 0, 0);
            vertex(consumer, matrix, x1, y1, z2, 0xFFFFFFFF, uLeft1, vBack2, -1, 0, 0);

            // 4. Right edge
            vertex(consumer, matrix, x2, y2, z1, 0xFFFFFFFF, uRight1, vBack1, 1, 0, 0);
            vertex(consumer, matrix, x2, y2, z2, 0xFFFFFFFF, uRight2, vBack1, 1, 0, 0);
            vertex(consumer, matrix, x2, y1, z2, 0xFFFFFFFF, uRight2, vBack2, 1, 0, 0);
            vertex(consumer, matrix, x2, y1, z1, 0xFFFFFFFF, uRight1, vBack2, 1, 0, 0);

            // 5. Top edge
            vertex(consumer, matrix, x1, y2, z2, 0xFFFFFFFF, uTop1, vTop2, 0, 1, 0);
            vertex(consumer, matrix, x2, y2, z2, 0xFFFFFFFF, uTop2, vTop2, 0, 1, 0);
            vertex(consumer, matrix, x2, y2, z1, 0xFFFFFFFF, uTop2, vTop1, 0, 1, 0);
            vertex(consumer, matrix, x1, y2, z1, 0xFFFFFFFF, uTop1, vTop1, 0, 1, 0);

            // 6. Bottom edge
            vertex(consumer, matrix, x1, y1, z1, 0xFFFFFFFF, uBot1, vBot1, 0, -1, 0);
            vertex(consumer, matrix, x2, y1, z1, 0xFFFFFFFF, uBot2, vBot1, 0, -1, 0);
            vertex(consumer, matrix, x2, y1, z2, 0xFFFFFFFF, uBot2, vBot2, 0, -1, 0);
            vertex(consumer, matrix, x1, y1, z2, 0xFFFFFFFF, uBot1, vBot2, 0, -1, 0);
        });
        pose.pop();
    }

    public static void vertex(VertexConsumer consumer, MatrixStack.Entry matrix,
                               float x, float y, float z,
                               int color,
                               float u, float v,
                               float nx, float ny, float nz) {
        consumer.vertex(matrix, x, y, z)
                .color(color)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(matrix, nx, ny, nz);
    }

    public static void fillBoxTextured(VertexConsumer c, MatrixStack.Entry m, double x1, double y1, double z1, double x2, double y2, double z2, float u, float v, float width, float height, BedrockGeometryLoader.Bone bone) {
        float u1 = u / width, v1 = v / height, u2 = (u + (float)(x2 - x1)) / width, v2 = (v + (float)(y2 - y1)) / height;
        texturedQuad(c, m, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, u1, v1, u2, v2, bone);
        texturedQuad(c, m, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, u1, v1, u2, v2, bone);
        texturedQuad(c, m, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, u1, v1, u2, v2, bone);
        texturedQuad(c, m, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, u1, v1, u2, v2, bone);
        texturedQuad(c, m, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, u1, v1, u2, v2, bone);
        texturedQuad(c, m, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, u1, v1, u2, v2, bone);
    }

    private static void texturedQuad(VertexConsumer c, MatrixStack.Entry m, double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz, float u1, float v1, float u2, float v2, BedrockGeometryLoader.Bone bone) {
        double[] a = rot(ax, ay, az, bone), b = rot(bx, by, bz, bone), cc = rot(cx, cy, cz, bone), d = rot(dx, dy, dz, bone);
        vertex(c, m, (float)a[0], (float)a[1], (float)a[2], 0xFFFFFFFF, u1, v1, 0, 1, 0);
        vertex(c, m, (float)b[0], (float)b[1], (float)b[2], 0xFFFFFFFF, u1, v2, 0, 1, 0);
        vertex(c, m, (float)cc[0], (float)cc[1], (float)cc[2], 0xFFFFFFFF, u2, v2, 0, 1, 0);
        vertex(c, m, (float)d[0], (float)d[1], (float)d[2], 0xFFFFFFFF, u2, v1, 0, 1, 0);
    }

    private static int color(String type) { return switch (type) { case "hat" -> 0xFFE9A8FF; case "pet" -> 0xFF77E7A2; default -> 0xFFA66BFF; }; }

    private static void fillBox(VertexConsumer c, MatrixStack.Entry m, double x1, double y1, double z1, double x2, double y2, double z2, int color, BedrockGeometryLoader.Bone bone) {
        quad(c, m, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, color, bone);
        quad(c, m, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, color, bone);
        quad(c, m, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, color, bone);
        quad(c, m, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, color, bone);
        quad(c, m, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, color, bone);
        quad(c, m, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, color, bone);
    }

    private static void quad(VertexConsumer c, MatrixStack.Entry m, double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz, int color, BedrockGeometryLoader.Bone bone) {
        double[] a = rot(ax, ay, az, bone), b = rot(bx, by, bz, bone), cc = rot(cx, cy, cz, bone), d = rot(dx, dy, dz, bone);
        c.vertex(m, (float)a[0], (float)a[1], (float)a[2]).color(color);
        c.vertex(m, (float)b[0], (float)b[1], (float)b[2]).color(color);
        c.vertex(m, (float)cc[0], (float)cc[1], (float)cc[2]).color(color);
        c.vertex(m, (float)d[0], (float)d[1], (float)d[2]).color(color);
    }

    private static double[] rot(double x, double y, double z, BedrockGeometryLoader.Bone b) {
        double px = b.px(), py = -b.py(), pz = b.pz();
        x -= px; y -= py; z -= pz;
        double ax = Math.toRadians(b.rx()), ay = Math.toRadians(b.ry()), az = Math.toRadians(b.rz());
        double cy = Math.cos(ax), sy = Math.sin(ax), ny = y * cy - z * sy;
        z = y * sy + z * cy; y = ny;
        cy = Math.cos(ay); sy = Math.sin(ay);
        double nx = x * cy + z * sy;
        z = -x * sy + z * cy; x = nx;
        cy = Math.cos(az); sy = Math.sin(az);
        nx = x * cy - y * sy;
        y = x * sy + y * cy; x = nx;
        return new double[]{x + px, y + py, z + pz};
    }
}
