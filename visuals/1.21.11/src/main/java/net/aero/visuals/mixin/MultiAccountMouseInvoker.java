package net.aero.visuals.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MultiAccountMouseInvoker {
    @Invoker("onMouseButton")
    void aero$sendButton(long window, MouseInput input, int action);

    @Invoker("onMouseScroll")
    void aero$sendScroll(long window, double x, double y);
}
