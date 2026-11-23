package net.aero.visuals.mixin;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Gui.class)
public interface GuiHudAccessor {@Accessor("hud") Hud aero$hud();}
