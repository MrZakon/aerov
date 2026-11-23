package net.aero.visuals.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.multiplayer.CodeOfConductScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;

/** Shared opt-in skin for Minecraft screens; container/inventory screens deliberately stay vanilla. */
public final class AeroVanillaChrome {
    private AeroVanillaChrome() {}

    public static boolean shouldTheme(Screen screen) {
        return screen != null && !(screen instanceof AbstractContainerScreen<?>) && !(screen instanceof ChatScreen)
                && !isSettingsBranch(screen) && !isOfficialMultiplayerDialog(screen);
    }

    public static boolean shouldThemeBackground(Screen screen) {
        return screen != null && !(screen instanceof AbstractContainerScreen<?>) && !(screen instanceof ChatScreen);
    }

    public static boolean shouldThemeButtons(Screen screen) {
        return shouldTheme(screen) || isConnectionDialog(screen);
    }

    public static boolean useBlackButtons(Screen screen) {
        return screen instanceof JoinMultiplayerScreen || isConnectionDialog(screen);
    }

    private static boolean isConnectionDialog(Screen screen) {
        return screen instanceof ManageServerScreen
                || screen instanceof DirectJoinServerScreen
                || screen instanceof SafetyScreen
                || screen instanceof CodeOfConductScreen;
    }

    public static boolean isSettingsBranch(Screen screen) {
        return screen instanceof OptionsScreen
                || screen instanceof OptionsSubScreen
                || screen instanceof PackSelectionScreen
                || screen instanceof TelemetryInfoScreen
                || screen instanceof CreditsAndAttributionScreen;
    }

    /** Native forms keep Minecraft's own font metrics, field padding and button labels. */
    private static boolean isOfficialMultiplayerDialog(Screen screen) {
        return isConnectionDialog(screen) || screen instanceof SocialInteractionsScreen;
    }

    public static void extractBackground(Screen screen, GuiGraphicsExtractor context,
                                        int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            AeroMenuChrome.background(context, screen.width, screen.height, mouseX, mouseY, false);
        } else {
            AeroMenuChrome.worldBackdrop(context, screen.width, screen.height);
        }
    }
}
