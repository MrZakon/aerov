package net.aero.visuals.animation;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

/** Ordinary delegate: its constructor must never capture a Mixin instance. */
public final class AnimatedChatBackend implements ChatComponent.ChatGraphicsAccess {
    private final ChatComponent.ChatGraphicsAccess backend;
    public AnimatedChatBackend(ChatComponent.ChatGraphicsAccess backend) {
        this.backend = backend;
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
    public boolean handleMessage(int y, float alpha, FormattedCharSequence text) {
        float progress = ChatMotion.message(text);
        if (progress == 1) return backend.handleMessage(y, alpha, text);
        boolean interactive = Minecraft.getInstance().screen instanceof ChatScreen;
        float offset = interactive ? 0 : (1 - progress) * 14;
        if (offset != 0) backend.updatePose(pose -> pose.translate(-offset, 0));
        try {
            return backend.handleMessage(y, alpha * progress, text);
        } finally {
            if (offset != 0) backend.updatePose(pose -> pose.translate(offset, 0));
        }
    }

    @Override
    public void handleTag(int a, int b, int c, int d, float opacity, GuiMessageTag indicator) {
        backend.handleTag(a, b, c, d, opacity, indicator);
    }

    @Override
    public void handleTagIcon(int a, int b, boolean c, GuiMessageTag indicator, GuiMessageTag.Icon icon) {
        backend.handleTagIcon(a, b, c, indicator, icon);
    }
}
