package net.aero.visuals.render.font;

import java.util.LinkedHashMap;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

public final class NameTagTextCache {
    private final LinkedHashMap<Key, TextRenderer.GlyphDrawable> labels = new LinkedHashMap<>(64, 0.75f, true);
    private TextRenderer font;
    private Language language;
    private boolean privacyCacheable = true;

    private record Key(Text text, float x, float y, int color, boolean shadow, int background) {}

    public TextRenderer.GlyphDrawable prepare(TextRenderer font, Text text, float x, float y, int color,
                                              boolean shadow, int background) {
        var streamer = net.aero.visuals.feature.ImportedFeatures.get(net.aero.visuals.feature.ImportedFeatures.Streamer.class);
        boolean cacheable = streamer == null || !streamer.isEnabled();
        if (cacheable != privacyCacheable) {
            labels.clear();
            privacyCacheable = cacheable;
        }
        Language currentLanguage = Language.getInstance();
        if (this.font != font || language != currentLanguage) {
            labels.clear();
            this.font = font;
            language = currentLanguage;
        }
        var key = new Key(text, x, y, color, shadow, background);
        var prepared = cacheable ? labels.get(key) : null;
        if (prepared != null) return prepared;

        var sequence = text.asOrderedText();
        if (!sequence.accept((index, style, codePoint) -> !style.isObfuscated())) return null;
        prepared = font.prepare(sequence, x, y, color, shadow, false, background);
        if (cacheable) {
            if (labels.size() >= 512) labels.pollFirstEntry();
            labels.put(new Key(snapshot(text), x, y, color, shadow, background), prepared);
        }
        return prepared;
    }

    private static Text snapshot(Text text) {
        var copy = text.copyContentOnly().setStyle(text.getStyle());
        for (var sibling : text.getSiblings()) copy.append(snapshot(sibling));
        return copy;
    }
}
