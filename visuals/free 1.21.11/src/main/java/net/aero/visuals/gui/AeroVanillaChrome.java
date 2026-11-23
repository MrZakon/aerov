package net.aero.visuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.option.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.TelemetryInfoScreen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.CodeOfConductScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;

/** Shared opt-in skin for Minecraft screens; container/inventory screens deliberately stay vanilla. */
public final class AeroVanillaChrome {
    private AeroVanillaChrome() {}

    public static boolean shouldTheme(Screen screen) {
        return screen != null && !(screen instanceof HandledScreen<?>) && !(screen instanceof ChatScreen)
                && !isSettingsBranch(screen) && !isOfficialMultiplayerDialog(screen);
    }

    public static boolean shouldThemeBackground(Screen screen) {
        return screen != null && !(screen instanceof HandledScreen<?>) && !(screen instanceof ChatScreen);
    }

    public static boolean shouldThemeButtons(Screen screen) {
        return shouldTheme(screen) || isConnectionDialog(screen);
    }

    public static boolean useBlackButtons(Screen screen) {
        return screen instanceof MultiplayerScreen || isConnectionDialog(screen);
    }

    private static boolean isConnectionDialog(Screen screen) {
        return screen instanceof AddServerScreen
                || screen instanceof DirectConnectScreen
                || screen instanceof MultiplayerWarningScreen
                || screen instanceof CodeOfConductScreen;
    }

    public static boolean isSettingsBranch(Screen screen) {
        return screen instanceof OptionsScreen
                || screen instanceof GameOptionsScreen
                || screen instanceof PackScreen
                || screen instanceof TelemetryInfoScreen
                || screen instanceof CreditsAndAttributionScreen;
    }

    /** Native forms keep Minecraft's own font metrics, field padding and button labels. */
    private static boolean isOfficialMultiplayerDialog(Screen screen) {
        return isConnectionDialog(screen) || screen instanceof SocialInteractionsScreen;
    }

    public static void renderBackground(Screen screen, DrawContext context,
                                        int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            AeroMenuChrome.background(context, screen.width, screen.height, mouseX, mouseY, false);
        } else {
            AeroMenuChrome.worldBackdrop(context, screen.width, screen.height);
        }
    }
}
