package net.aero.visuals.render.font;

import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.block.BlockModelSet;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Keeps reusable glyph layout independent of the camera matrix and per-entity lighting. */
public final class NameTagTextCache {
    private static final int LIMIT = 512;
    private final LinkedHashMap<Key, Font.PreparedText> labels = new LinkedHashMap<>(64, 0.75f, true);
    private Font font;
    private Language language;
    private boolean privacyCacheable = true;
    private BlockModelSet models;

    private record Key(Component text, float x, float y, int color, boolean shadow, int background) {}

    public Font.PreparedText prepare(Font font, Component text, float x, float y, int color,
                                     boolean shadow, int background) {
        var streamer = net.aero.visuals.feature.ImportedFeatures.get(net.aero.visuals.feature.ImportedFeatures.Streamer.class);
        boolean cacheable = streamer == null || !streamer.isEnabled();
        if (cacheable != privacyCacheable) {
            labels.clear();
            privacyCacheable = cacheable;
        }
        Language currentLanguage = Language.getInstance();
        BlockModelSet currentModels = Minecraft.getInstance().getModelManager().getBlockModelSet();
        if (this.font != font || language != currentLanguage || models != currentModels) {
            labels.clear();
            this.font = font;
            language = currentLanguage;
            models = currentModels;
        }

        Key key = new Key(text, x, y, color, shadow, background);
        Font.PreparedText prepared = cacheable ? labels.get(key) : null;
        if (prepared != null) return prepared;

        var sequence = text.getVisualOrderText();
        // Obfuscated text is intentionally randomized every time it is rendered.
        if (!sequence.accept((index, style, codePoint) -> !style.isObfuscated())) return null;
        prepared = font.prepareText(sequence, x, y, color, shadow, false, background);
        if (cacheable) {
            if (labels.size() >= LIMIT) labels.pollFirstEntry();
            labels.put(new Key(snapshot(text), x, y, color, shadow, background), prepared);
        }
        return prepared;
    }

    private static Component snapshot(Component text) {
        var copy = text.plainCopy().setStyle(text.getStyle());
        for (var sibling : text.getSiblings()) copy.append(snapshot(sibling));
        return copy;
    }
}
