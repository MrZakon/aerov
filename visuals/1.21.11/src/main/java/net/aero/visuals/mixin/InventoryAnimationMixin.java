package net.aero.visuals.mixin;
import net.aero.visuals.feature.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(HandledScreen.class)
public abstract class InventoryAnimationMixin {
    @Unique private final Map<Integer,ItemStack> aero$items=new HashMap<>();
    @Unique private final Map<Integer,Long> aero$changes=new HashMap<>();
    @Unique private long aero$opened;
    @Unique private boolean aero$pushed;
    @Unique private float aero$hoverX,aero$hoverY;
    @Unique private boolean aero$hasHover;
    @Shadow protected Slot focusedSlot;
    @Inject(method="init",at=@At("TAIL"))private void aero$init(CallbackInfo ci){aero$opened=System.nanoTime();aero$items.clear();aero$changes.clear();aero$hasHover=false;}
    @Inject(method="drawSlot",at=@At("HEAD"))private void aero$slot(DrawContext context,Slot slot,int mouseX,int mouseY,CallbackInfo ci){aero$pushed=false;var m=ImportedFeatures.get(ExtraFeatures.InventoryAnimation.class);if(m==null||!m.isEnabled())return;long now=System.nanoTime();ItemStack stack=slot.getStack(),before=aero$items.get(slot.id);if(before==null||!ItemStack.areEqual(before,stack)){aero$items.put(slot.id,stack.copy());aero$changes.put(slot.id,before==null?aero$opened:now);}double t=Math.clamp((now-aero$changes.getOrDefault(slot.id,aero$opened))/1e6/m.duration.get(),0,1),ease=1-Math.pow(1-t,3);float scale=(float)(.65+.35*ease);var matrices=context.getMatrices();matrices.pushMatrix();matrices.translate(slot.x+8,slot.y+8);matrices.scale(scale,scale);matrices.translate(-slot.x-8,-slot.y-8);aero$pushed=true;}
    @Inject(method="drawSlot",at=@At("RETURN"))private void aero$end(DrawContext context,Slot slot,int mouseX,int mouseY,CallbackInfo ci){if(aero$pushed){context.getMatrices().popMatrix();aero$pushed=false;}}
    @Inject(method="drawSlotHighlightBack",at=@At("HEAD"),cancellable=true)private void aero$hover(DrawContext context,CallbackInfo ci){var m=ImportedFeatures.get(ExtraFeatures.InventoryAnimation.class);if(m==null||!m.isEnabled()||focusedSlot==null)return;if(!aero$hasHover){aero$hoverX=focusedSlot.x;aero$hoverY=focusedSlot.y;aero$hasHover=true;}float factor=1-(float)Math.exp(-MinecraftClient.getInstance().getRenderTickCounter().getDynamicDeltaTicks()*1.1);aero$hoverX+=(focusedSlot.x-aero$hoverX)*factor;aero$hoverY+=(focusedSlot.y-aero$hoverY)*factor;context.fill((int)aero$hoverX-1,(int)aero$hoverY-1,(int)aero$hoverX+17,(int)aero$hoverY+17,m.color.get().eval());ci.cancel();}
}
