package net.aero.visuals.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MultiAccountMouseInvoker {
    @Invoker("onButton")
    void aero$sendButton(long window, MouseButtonInfo input, int action);

    @Invoker("onScroll")
    void aero$sendScroll(long window, double x, double y);
}
