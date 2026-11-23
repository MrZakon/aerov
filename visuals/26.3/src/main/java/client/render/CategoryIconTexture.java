package client.render;

import client.data.FloatParser;
import client.data.IconSheet;
import client.data.LocalIconLoader;
import client.module.CategoryType;
import com.mojang.blaze3d.platform.NativeImage;
import net.aero.visuals.AeroVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CategoryIconTexture {
    private static final Map<CategoryType, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final int TEX_SIZE = 64;

    private CategoryIconTexture() {}

    public static Identifier get(CategoryType type) {
        if (type == null) return null;
        Identifier cached = CACHE.get(type);
        if (cached != null) return cached;
        Identifier loaded = loadTexture(type);
        if (loaded != null) {
            CACHE.put(type, loaded);
        }
        return loaded;
    }

    private static Identifier loadTexture(CategoryType type) {
        try {
            String key = type.getKey();
            if (key == null || key.isEmpty()) return null;

            IconSheet sheet = LocalIconLoader.read(key);
            if (sheet == null) {
                sheet = switch (type) {
                    case ALL -> LocalIconLoader.read("panel");
                    case ARMOR_HUD -> LocalIconLoader.read("rack");
                    case ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT -> LocalIconLoader.read("setting_alignment");
                    default -> null;
                };
            }
            if (sheet == null) return null;

            java.util.List<String> paths = sheet.getList();
            if (paths == null || paths.isEmpty()) return null;

            float w = sheet.getValue() > 0 ? sheet.getValue() : 16.0F;
            float h = sheet.getValue2() > 0 ? sheet.getValue2() : 16.0F;
            float sw = sheet.getValue3() > 0 ? sheet.getValue3() : 1.0F;
            boolean filled = sheet.isFlag();

            BufferedImage img = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            float padding = 6.0F;
            float avail = TEX_SIZE - padding * 2;
            float scale = Math.min(avail / w, avail / h);
            float offX = (TEX_SIZE - w * scale) / 2.0F;
            float offY = (TEX_SIZE - h * scale) / 2.0F;

            g2.translate(offX, offY);
            g2.scale(scale, scale);

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            for (String pathStr : paths) {
                if (pathStr == null || pathStr.isEmpty()) continue;
                Path2D.Float path = FloatParser.getFloatByString(pathStr);
                if (path != null) {
                    path.setWindingRule(Path2D.WIND_EVEN_ODD);
                    if (filled) {
                        g2.fill(path);
                    } else {
                        g2.draw(path);
                    }
                }
            }
            g2.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));

            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getTextureManager() == null) return null;

            Identifier id = Identifier.fromNamespaceAndPath(AeroVisuals.MOD_ID, "cat_icon_" + key.toLowerCase());
            DynamicTexture dynTex = new DynamicTexture(() -> "cat_icon_" + key, nativeImage);
            client.getTextureManager().register(id, dynTex);
            return id;
        } catch (Throwable t) {
            return null;
        }
    }
}
