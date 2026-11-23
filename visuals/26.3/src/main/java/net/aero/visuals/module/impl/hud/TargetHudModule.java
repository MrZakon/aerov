package net.aero.visuals.module.impl.hud;

import net.aero.visuals.hud.HudAnchor;
import net.aero.visuals.hud.HudPosition;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.render.UiRenderer;
import net.aero.visuals.render.VisualTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;
import client.api.Theme;

public final class TargetHudModule extends HudWidgetModule {
    private final DoubleSetting keep = add(new DoubleSetting("keep_seconds", "Время показа после удара (сек.)", "Сколько секунд показывать последнюю атакованную цель после отвода прицела.", 5, 1, 15, 1));
    private final VisualTarget attack = new VisualTarget();
    private LivingEntity target;
    private UUID drawn;private double healthTrail,healthNow;private long frame;

    public TargetHudModule() {
        super("target_hud", "TargetHUD", "Показывает имя, здоровье, поглощение и расстояние до цели под прицелом или последней атакованной цели.", new HudPosition(HudAnchor.CENTER, 110, 60, 1));
    }
    public void onAttack(Entity entity) {
        if (!isEnabled() || !(entity instanceof LivingEntity living) || !living.isAlive()) return;
        attack.remember(Minecraft.getInstance(), living, (long) (keep.get() * 1_000_000_000L));
    }
    public void clearTarget() { attack.clear(); target = null; }
    @Override protected void onDisable() { clearTarget(); }
    @Override protected void refresh(Minecraft client, float tickDelta) {
        target = VisualTarget.underCrosshair(client);
        if (target == null) target = attack.lastAttacked(client, System.nanoTime());
        content = target != null;
        if (target == null && isEditing()) target = client.player;
        width = 204; height = 70;
    }
    @Override protected void renderHud(GuiGraphicsExtractor context, Minecraft client) {
        if (target == null) return;
        long now=System.nanoTime();double dt=frame==0?.016:Math.min(.1,(now-frame)/1e9);frame=now;
        float hp=Math.max(0,target.getHealth()),max=Math.max(1,target.getMaxHealth());if(!target.getUUID().equals(drawn)){drawn=target.getUUID();healthNow=healthTrail=hp;}
        healthNow+=(hp-healthNow)*(1-Math.exp(-16*dt));healthTrail+=(hp-healthTrail)*(1-Math.exp(-4*dt));
        // Native entity preview also supports mobs: no placeholder circle or question mark.
        var transform=context.pose();var lo=transform.transformPosition(new org.joml.Vector2f(7,6));var hi=transform.transformPosition(new org.joml.Vector2f(45,60));float zoom=Math.max(.1f,(hi.x-lo.x)/38);
        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(context,(int)lo.x,(int)lo.y,(int)hi.x,(int)hi.y,Math.max(10,(int)(25*zoom)),.02f,(lo.x+hi.x)/2,(lo.y+hi.y)/2,target);
        String name=target.getName().getString();var lines=client.font.split(net.minecraft.network.chat.Component.literal(name),145);double ty=8;for(int i=0;i<Math.min(2,lines.size());i++){context.pose().pushMatrix();context.pose().translate(51,(float)ty);context.pose().scale(.9f,.9f);context.text(client.font,lines.get(i),0,0,0xFFF1F4FF,false);context.pose().popMatrix();ty+=11;}
        String info=String.format(java.util.Locale.ROOT,"%.1f / %.1f HP",hp,max);if(target.getAbsorptionAmount()>0)info+=String.format(java.util.Locale.ROOT," +%.1f",target.getAbsorptionAmount());
        text(context,fit(info,width-60,8),51,33,8,0xFFBCC4D5);
        String distance=String.format(java.util.Locale.ROOT,"%.1f m",client.player.distanceTo(target));text(context,distance,width-8-textWidth(distance,8),46,8,Theme.mutedFg());
        double bar=width-60;UiRenderer.roundedRect(context,51,58,bar,5,2.5,0x35FFFFFF);UiRenderer.roundedRect(context,51,58,bar*Math.clamp(healthTrail/max,0,1),5,2.5,0x668B78FF);UiRenderer.roundedRect(context,51,58,bar*Math.clamp(healthNow/max,0,1),5,2.5,Theme.primary());

        target = null;
    }
}
