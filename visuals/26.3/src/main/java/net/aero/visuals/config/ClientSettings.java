package net.aero.visuals.config;

import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.setting.EnumSetting;
import net.aero.visuals.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Global appearance and renderer options which are not visual modules themselves. */
public final class ClientSettings {
    public enum InterfaceLanguage { RUSSIAN, ENGLISH, UKRAINIAN }
    public enum ThemeMode { LIGHT, DARK }
    public enum MenuBackdrop { CLEAN, ARTWORK }
    public enum HudStyle { CLEAN, GLASS }
    public enum ShaderQuality { PERFORMANCE, BALANCED, QUALITY }
    public enum ShaderPreset { SOFT, LIQUID_GLASS, NEON }

    private final List<Setting<?>> settings = new ArrayList<>();

    private final EnumSetting<InterfaceLanguage> language=add(new EnumSetting<>("interface_language","Язык","Язык клиента",InterfaceLanguage.RUSSIAN,InterfaceLanguage.class));
    public ClientSettings(){language.onChanged(value->{var lang=switch(value){case RUSSIAN->client.enums.Language.RU;case ENGLISH->client.enums.Language.EN;case UKRAINIAN->client.enums.Language.UA;};client.concurrent.Translations.getInstance().setLanguage(lang);client.render.TextShader.clearWrapCache();});}
    public EnumSetting<InterfaceLanguage> language(){return language;}
    private final EnumSetting<ThemeMode> theme = add(new EnumSetting<>(
            "theme", "Theme", "Dark AeroVisuals glass or a softer charcoal variant",
            ThemeMode.DARK, ThemeMode.class));
    private final ColorSetting accent = add(new ColorSetting(
            "accent", "Accent", "Emerald-to-violet UI and HUD accent",
            new ColorValue(0xFF77E7A2, 0xFFA66BFF, false, 0.75)));
    private final DoubleSetting uiScale = add(new DoubleSetting(
            "ui_scale", "Interface scale", "Scale of AeroVisuals menus", 1.0, 0.80, 1.20, 0.05));
    private final DoubleSetting clickGuiScale = add(new DoubleSetting(
            "click_gui_scale", "Размер ClickGUI", "Масштаб окна ClickGUI", 0.85, 0.50, 1.50, 0.05));
    private final DoubleSetting clickGuiTextScale = add(new DoubleSetting(
            "click_gui_text_scale", "Размер текста", "Масштаб текста в ClickGUI", 0.95, 0.75, 1.25, 0.05));
    private final EnumSetting<MenuBackdrop> menuBackdrop = add(new EnumSetting<>(
            "menu_backdrop", "Menu backdrop", "Clean canvas or the bundled artwork",
            MenuBackdrop.ARTWORK, MenuBackdrop.class));
    private final BooleanSetting menuDimming = add(new BooleanSetting(
            "menu_blur", "World shading", "Darken the live world behind menus without blurring it", true));
    private final BooleanSetting reducedMotion = add(new BooleanSetting(
            "reduced_motion", "Reduced motion", "Disable parallax and shorten interface animations", false));
    private final EnumSetting<HudStyle> hudStyle = add(new EnumSetting<>(
            "hud_style", "HUD style", "Clean light cards or translucent glass panels",
            HudStyle.GLASS, HudStyle.class));
    private final EnumSetting<ShaderQuality> shaderQuality = add(new EnumSetting<>(
            "shader_quality", "Shader quality", "Controls cosmetic density and effect cadence",
            ShaderQuality.BALANCED, ShaderQuality.class));
    private final EnumSetting<ShaderPreset> shaderPreset = add(new EnumSetting<>(
            "shader_preset", "Shader preset", "Soft, liquid-glass, or neon presentation",
            ShaderPreset.NEON, ShaderPreset.class));
    private final BooleanSetting customShaders = add(new BooleanSetting(
            "custom_shaders", "Custom shader programs", "Compile bundled blur, border, liquid-glass and MSDF programs", true));
    private final BooleanSetting changelog = add(new BooleanSetting(
            "show_changelog", "Menu changelog", "Show the status and changelog cards on the title screen", false));

    private <T extends Setting<?>> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> all() { return Collections.unmodifiableList(settings); }
    public ThemeMode theme() { return theme.get(); }
    public ColorSetting accent() { return accent; }
    public double uiScale() { return uiScale.get(); }
    public DoubleSetting clickGuiScale() { return clickGuiScale; }
    public DoubleSetting clickGuiTextScale() { return clickGuiTextScale; }
    public MenuBackdrop menuBackdrop() { return menuBackdrop.get(); }
    public boolean menuDimming() { return menuDimming.get(); }
    public boolean reducedMotion() { return reducedMotion.get(); }
    public HudStyle hudStyle() { return hudStyle.get(); }
    public ShaderQuality shaderQuality() { return shaderQuality.get(); }
    public ShaderPreset shaderPreset() { return shaderPreset.get(); }
    public boolean customShaders() { return customShaders.get(); }
    public boolean showChangelog() { return changelog.get(); }
}
