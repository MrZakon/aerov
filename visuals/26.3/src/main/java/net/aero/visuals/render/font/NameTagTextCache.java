package net.aero.visuals.render.font;

import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.block.BlockModelSet;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

/** Shares prepared glyphs between both render passes for stable entity labels. */
public final class NameTagTextCache {
    private final LinkedHashMap<Component, FormattedCharSequence> names = new LinkedHashMap<>(64, 0.75f, true);
    private Language language;
    private boolean privacyCacheable = true;
    private BlockModelSet models;

    public FormattedCharSequence resolve(Component name, FormattedCharSequence fallback) {
        var streamer = net.aero.visuals.feature.ImportedFeatures.get(net.aero.visuals.feature.ImportedFeatures.Streamer.class);
        boolean cacheable = streamer == null || !streamer.isEnabled();
        if (cacheable != privacyCacheable) {
            names.clear();
            privacyCacheable = cacheable;
        }
        if (!cacheable) return fallback;
        Language currentLanguage = Language.getInstance();
        BlockModelSet currentModels = Minecraft.getInstance().getModelManager().getBlockModelSet();
        if (language != currentLanguage || models != currentModels) {
            names.clear();
            language = currentLanguage;
            models = currentModels;
        }
        var existing = names.get(name);
        if (existing != null) return existing;
        if (!fallback.accept((index, style, codePoint) -> !style.isObfuscated())) return fallback;
        var sequence = new CachedSequence(fallback);
        if (names.size() >= 512) names.pollFirstEntry();
        names.put(snapshot(name), sequence);
        return sequence;
    }

    private static Component snapshot(Component text) {
        var copy = text.plainCopy().setStyle(text.getStyle());
        for (var sibling : text.getSiblings()) copy.append(snapshot(sibling));
        return copy;
    }

    public static final class CachedSequence implements FormattedCharSequence {
        private final FormattedCharSequence text;
        private final LinkedHashMap<Layout, Font.PreparedText> layouts = new LinkedHashMap<>(4, 0.75f, true);

        private record Layout(Font font, float x, float y, int color, boolean shadow,
                              boolean inverseDepth, int background) {}

        private CachedSequence(FormattedCharSequence text) {
            this.text = text;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            return text.accept(sink);
        }

        public synchronized Font.PreparedText prepare(Font font, float x, float y, int color, boolean shadow,
                                                       boolean inverseDepth, int background) {
            var streamer = net.aero.visuals.feature.ImportedFeatures.get(net.aero.visuals.feature.ImportedFeatures.Streamer.class);
            if (streamer != null && streamer.isEnabled()) {
                layouts.clear();
                return font.prepareText(text, x, y, color, shadow, inverseDepth, background);
            }
            var layout = new Layout(font, x, y, color, shadow, inverseDepth, background);
            var prepared = layouts.get(layout);
            if (prepared != null) return prepared;
            prepared = font.prepareText(text, x, y, color, shadow, inverseDepth, background);
            if (layouts.size() >= 4) layouts.pollFirstEntry();
            layouts.put(layout, prepared);
            return prepared;
        }
    }
}
