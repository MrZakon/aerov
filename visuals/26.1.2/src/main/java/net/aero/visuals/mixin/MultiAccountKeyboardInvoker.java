package net.aero.visuals.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface MultiAccountKeyboardInvoker {
    @Invoker("keyPress")
    void aero$sendKey(long window, int action, KeyEvent input);

    @Invoker("charTyped")
    void aero$sendCharacter(long window, CharacterEvent input);
}
