package net.aero.visuals.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Keyboard.class)
public interface MultiAccountKeyboardInvoker {
    @Invoker("onKey")
    void aero$sendKey(long window, int action, KeyInput input);

    @Invoker("onChar")
    void aero$sendCharacter(long window, CharInput input);
}
