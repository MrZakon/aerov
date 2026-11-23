package net.aero.visuals.module.impl.pvp;

import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.LineRenderer;
import net.aero.visuals.render.WorldRenderable;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

    @Override public void renderWorld(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || context.poseStack() == null || context.bufferSource() == null) return;
        ItemStack held = client.player.getMainHandItem();
        if (!supported(held.getItem())) held = client.player.getOffhandItem();
        if (!supported(held.getItem())) return;
        Item item = held.getItem();
        boolean splash = item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
        double speed = 1.5;
        double gravity = 0.03;
        double drag = 0.99;
        float pitchOffset = 0;
        if (item instanceof BowItem) {
            int ticks = client.player.getTicksUsingItem();
            float pull = client.player.isUsingItem() ? BowItem.getPowerForTime(ticks) : 1;
            if (pull <= 0.1f) return;
            speed = pull * 3.0;
            gravity = 0.05;
        } else if (item instanceof CrossbowItem) { speed = 3.15; gravity = 0.05; }
        else if (item instanceof TridentItem) { speed = 2.5; gravity = 0.05; }
        else if (splash) { speed = 0.55; gravity = 0.05; pitchOffset = -20; }
        else if (item instanceof WindChargeItem) { gravity = 0; drag = 1; }
        double yaw = Math.toRadians(client.player.getYRot());
        double pitch = Math.toRadians(client.player.getXRot() + pitchOffset);
        Vec3 velocity = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize().scale(speed);
        Vec3 current = client.player.getEyePosition();
        List<Vec3> points = new ArrayList<>((int) (double) steps.get() + 1);
        points.add(current);
        Vec3 landing = null;
        for (int i = 0; i < steps.get(); i++) {
            Vec3 next = current.add(velocity);
            AABB check = new AABB(current, next).inflate(0.4);
            for (Entity entity : client.level.getEntities(client.player, check, e -> e instanceof LivingEntity && e.isAlive())) {
                var hit = entity.getBoundingBox().inflate(0.25).clip(current, next);
                if (hit.isPresent()) { landing = hit.get(); break; }
            }
            if (landing != null) { points.add(landing); break; }
            BlockHitResult hit = client.level.clip(new ClipContext(current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            if (hit.getType() != HitResult.Type.MISS) { landing = hit.getLocation(); points.add(landing); break; }
            points.add(next);
            current = next;
            velocity = velocity.scale(drag).subtract(0, gravity, 0);
        }
        Vec3 cam = client.gameRenderer.getMainCamera().position();
        PoseStack matrices = context.poseStack();
        VertexConsumer lines = context.bufferSource().getBuffer(RenderTypes.linesTranslucent());
        matrices.pushPose();
        try {
            matrices.translate(-cam.x, -cam.y, -cam.z);
            PoseStack.Pose entry = matrices.last();
            int argb = color.get().eval();
            for (int i = 1; i < points.size(); i++) {
                Vec3 a = points.get(i - 1), b = points.get(i);
                LineRenderer.line(entry, lines, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, argb);
            }
            if (landing != null) {
                double radius = splash ? 4 : 0.45;
                for (int i = 0; i < 32; i++) {
                    double a = i * Math.PI / 16, b = (i + 1) * Math.PI / 16;
                    LineRenderer.line(entry, lines, (float) (landing.x + Math.cos(a) * radius), (float) (landing.y + 0.05), (float) (landing.z + Math.sin(a) * radius), (float) (landing.x + Math.cos(b) * radius), (float) (landing.y + 0.05), (float) (landing.z + Math.sin(b) * radius), argb);
                }
            }
        } finally { matrices.popPose(); }
    }

    private boolean supported(Item item) {
        if (item == Items.ENDER_PEARL) return pearl.get();
        if (item instanceof BowItem || item instanceof CrossbowItem) return arrow.get();
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) return potion.get();
        return other.get() && (item == Items.SNOWBALL || item == Items.EGG || item instanceof TridentItem || item instanceof WindChargeItem);
    }
}
