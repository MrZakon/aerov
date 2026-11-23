package net.aero.visuals.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.aero.visuals.config.GuiScaleMemory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restore the last GUI Scale the player chose.
 * On first launch, initializes to 2 ("Интерфейс 2") and saves it.
 * When player chooses another scale, it is remembered across launches.
 */
@Mixin(Options.class)
public class OptionsMixin {
    @Shadow
    @Final
    private OptionInstance<Integer> guiScale;

    @Inject(method = "load()V", at = @At("RETURN"))
    private void aerovisuals$restoreGuiScale(CallbackInfo ci) {
        int saved = GuiScaleMemory.read(FabricLoader.getInstance().getConfigDir());
        if (saved != GuiScaleMemory.UNSET) {
            if (!this.guiScale.get().equals(saved)) {
                this.guiScale.set(saved);
            }
            return;
        }
        this.guiScale.set(GuiScaleMemory.FIRST_LAUNCH_DEFAULT);
        GuiScaleMemory.write(FabricLoader.getInstance().getConfigDir(), GuiScaleMemory.FIRST_LAUNCH_DEFAULT);
    }
}
