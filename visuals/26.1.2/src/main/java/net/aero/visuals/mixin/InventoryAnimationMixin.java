package net.aero.visuals.mixin;
import net.aero.visuals.feature.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryAnimationMixin {
    @Unique private final Map<Integer,ItemStack> aero$items=new HashMap<>();
    @Unique private final Map<Integer,Long> aero$changes=new HashMap<>();
    @Unique private long aero$opened;
    @Unique private boolean aero$pushed;
    @Unique private float aero$hoverX,aero$hoverY;
    @Unique private boolean aero$hasHover;
    @Shadow protected Slot hoveredSlot;
    @Inject(method="init",at=@At("TAIL"))private void aero$init(CallbackInfo ci){aero$opened=System.nanoTime();aero$items.clear();aero$changes.clear();aero$hasHover=false;}
    @Inject(method="extractSlot",at=@At("HEAD"))private void aero$slot(GuiGraphicsExtractor context,Slot slot,int mouseX,int mouseY,CallbackInfo ci){aero$pushed=false;var m=ImportedFeatures.get(ExtraFeatures.InventoryAnimation.class);if(m==null||!m.isEnabled())return;long now=System.nanoTime();ItemStack stack=slot.getItem(),before=aero$items.get(slot.index);if(before==null||!ItemStack.matches(before,stack)){aero$items.put(slot.index,stack.copy());aero$changes.put(slot.index,before==null?aero$opened:now);}double t=Math.clamp((now-aero$changes.getOrDefault(slot.index,aero$opened))/1e6/m.duration.get(),0,1),ease=1-Math.pow(1-t,3);float scale=(float)(.65+.35*ease);var matrices=context.pose();matrices.pushMatrix();matrices.translate(slot.x+8,slot.y+8);matrices.scale(scale,scale);matrices.translate(-slot.x-8,-slot.y-8);aero$pushed=true;}
    @Inject(method="extractSlot",at=@At("RETURN"))private void aero$end(GuiGraphicsExtractor context,Slot slot,int mouseX,int mouseY,CallbackInfo ci){if(aero$pushed){context.pose().popMatrix();aero$pushed=false;}}
    @Inject(method="extractSlotHighlightBack",at=@At("HEAD"),cancellable=true)private void aero$hover(GuiGraphicsExtractor context,CallbackInfo ci){var m=ImportedFeatures.get(ExtraFeatures.InventoryAnimation.class);if(m==null||!m.isEnabled()||hoveredSlot==null)return;if(!aero$hasHover){aero$hoverX=hoveredSlot.x;aero$hoverY=hoveredSlot.y;aero$hasHover=true;}float factor=1-(float)Math.exp(-Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks()*1.1);aero$hoverX+=(hoveredSlot.x-aero$hoverX)*factor;aero$hoverY+=(hoveredSlot.y-aero$hoverY)*factor;context.fill((int)aero$hoverX-1,(int)aero$hoverY-1,(int)aero$hoverX+17,(int)aero$hoverY+17,m.color.get().eval());ci.cancel();}
}
