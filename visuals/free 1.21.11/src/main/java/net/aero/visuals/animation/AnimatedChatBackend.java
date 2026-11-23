package net.aero.visuals.animation;

import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.OrderedText;
import org.joml.Matrix3x2f;

/** Ordinary delegate: its constructor must never capture a Mixin instance. */
public final class AnimatedChatBackend implements ChatHud.Backend {
    private final ChatHud.Backend backend;
    private final boolean scrolled;

    public AnimatedChatBackend(ChatHud.Backend backend, boolean scrolled) {
        this.backend = backend;
        this.scrolled = scrolled;
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> pose) {
        backend.updatePose(pose);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        backend.fill(x1, y1, x2, y2, color);
    }

    @Override
    public boolean text(int y, float alpha, OrderedText text) {
        float progress = scrolled ? 1 : ChatMotion.message(text);
        boolean interactive = MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
        float offset = interactive ? 0 : (1 - progress) * 14;
        if (offset != 0) backend.updatePose(pose -> pose.translate(-offset, 0));
        try {
            return backend.text(y, alpha * progress, text);
        } finally {
            if (offset != 0) backend.updatePose(pose -> pose.translate(offset, 0));
        }
    }

    @Override
    public void indicator(int a, int b, int c, int d, float opacity, MessageIndicator indicator) {
        backend.indicator(a, b, c, d, opacity, indicator);
    }

    @Override
    public void indicatorIcon(int a, int b, boolean c, MessageIndicator indicator, MessageIndicator.Icon icon) {
        backend.indicatorIcon(a, b, c, indicator, icon);
    }
}
