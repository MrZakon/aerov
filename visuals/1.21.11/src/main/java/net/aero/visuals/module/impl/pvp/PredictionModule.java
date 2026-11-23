package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.LineRenderer;
import net.aero.visuals.render.WorldRenderable;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.ArrayList;
import java.util.List;

public final class PredictionModule extends Module implements WorldRenderable {
    private final BooleanSetting pearl = add(new BooleanSetting("pearl", "Жемчуг Эндера", "Показывать траекторию жемчуга Эндера.", true));
    private final BooleanSetting arrow = add(new BooleanSetting("arrow", "Лук и арбалет", "Показывать траекторию стрел.", true));
    private final BooleanSetting potion = add(new BooleanSetting("potion", "Зелья", "Показывать траекторию бросаемых зелий.", true));
    private final BooleanSetting other = add(new BooleanSetting("other", "Другие снаряды", "Снежки, яйца, трезубец и заряд ветра.", true));
    private final ColorSetting color = add(new ColorSetting("color", "Цвет линии", "Цвет линии траектории.", ColorValue.solid(0xFF5CE1F0)));
    private final DoubleSetting steps = add(new DoubleSetting("steps", "Длина прогноза", "Максимальное число шагов расчёта.", 90, 30, 150, 10));

    public PredictionModule() {
        super("prediction", "Prediction", "Показывает траекторию и точку падения жемчуга, стрел, зелий и других снарядов.", ModuleCategory.PVP);
    }

    @Override public void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || context.matrices() == null || context.consumers() == null) return;
        ItemStack held = client.player.getMainHandStack();
        if (!supported(held.getItem())) held = client.player.getOffHandStack();
        if (!supported(held.getItem())) return;
        Item item = held.getItem();
        boolean splash = item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
        double speed = 1.5;
        double gravity = 0.03;
        double drag = 0.99;
        float pitchOffset = 0;
        if (item instanceof BowItem) {
            int ticks = client.player.getItemUseTime();
            float pull = client.player.isUsingItem() ? BowItem.getPullProgress(ticks) : 1;
            if (pull <= 0.1f) return;
            speed = pull * 3.0;
            gravity = 0.05;
        } else if (item instanceof CrossbowItem) { speed = 3.15; gravity = 0.05; }
        else if (item instanceof TridentItem) { speed = 2.5; gravity = 0.05; }
        else if (splash) { speed = 0.55; gravity = 0.05; pitchOffset = -20; }
        else if (item instanceof WindChargeItem) { gravity = 0; drag = 1; }
        double yaw = Math.toRadians(client.player.getYaw());
        double pitch = Math.toRadians(client.player.getPitch() + pitchOffset);
        Vec3d velocity = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize().multiply(speed);
        if (!(item instanceof WindChargeItem) && !(item instanceof CrossbowItem)) {
            var movement = client.player.getMovement();
            velocity = velocity.add(movement.x, client.player.isOnGround() ? 0 : movement.y, movement.z);
        }
        double waterDrag = item instanceof TridentItem ? 0.99 : item instanceof WindChargeItem ? 1.0
                : item instanceof BowItem || item instanceof CrossbowItem ? 0.6 : 0.8;
        Vec3d current = client.player.getEyePos();
        List<Vec3d> points = new ArrayList<>((int) (double) steps.get() + 1);
        points.add(current);
        Vec3d landing = null;
        for (int i = 0; i < steps.get(); i++) {
            Vec3d next = current.add(velocity);
            BlockHitResult hit = client.world.raycast(new RaycastContext(current, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
            landing = hit.getType() == HitResult.Type.MISS ? null : hit.getPos();
            double nearest = current.squaredDistanceTo(landing == null ? next : landing);
            Box check = new Box(current, next).expand(0.4);
            for (Entity entity : client.world.getOtherEntities(client.player, check, e -> e instanceof LivingEntity && e.isAlive())) {
                var entityHit = entity.getBoundingBox().expand(0.25).raycast(current, next);
                if (entityHit.isPresent()) {
                    double distance = current.squaredDistanceTo(entityHit.get());
                    if (distance < nearest || landing == null && distance <= nearest) { nearest = distance; landing = entityHit.get(); }
                }
            }
            if (landing != null) { points.add(landing); break; }
            points.add(next);
            current = next;
            boolean inWater = client.world.getFluidState(net.minecraft.util.math.BlockPos.ofFloored(current)).isIn(net.minecraft.registry.tag.FluidTags.WATER);
            velocity = velocity.multiply(inWater ? waterDrag : drag).subtract(0, gravity, 0);
        }
        Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = context.matrices();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayers.linesTranslucent());
        matrices.push();
        try {
            matrices.translate(-cam.x, -cam.y, -cam.z);
            MatrixStack.Entry entry = matrices.peek();
            int argb = color.get().eval();
            for (int i = 1; i < points.size(); i++) {
                Vec3d a = points.get(i - 1), b = points.get(i);
                LineRenderer.line(entry, lines, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, argb);
            }
            if (landing != null) {
                double radius = splash ? 4 : 0.45;
                for (int i = 0; i < 32; i++) {
                    double a = i * Math.PI / 16, b = (i + 1) * Math.PI / 16;
                    LineRenderer.line(entry, lines, (float) (landing.x + Math.cos(a) * radius), (float) (landing.y + 0.05), (float) (landing.z + Math.sin(a) * radius), (float) (landing.x + Math.cos(b) * radius), (float) (landing.y + 0.05), (float) (landing.z + Math.sin(b) * radius), argb);
                }
            }
        } finally { matrices.pop(); }
    }

    private boolean supported(Item item) {
        if (item == Items.ENDER_PEARL) return pearl.get();
        if (item instanceof BowItem || item instanceof CrossbowItem) return arrow.get();
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) return potion.get();
        return other.get() && (item == Items.SNOWBALL || item == Items.EGG || item instanceof TridentItem || item instanceof WindChargeItem);
    }
}
