package client.module;

import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.setting.EnumSetting;
import net.aero.visuals.module.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public class AeroModuleAdapter extends Module {
    private final net.aero.visuals.module.Module lunacyModule;
    private boolean syncingSettings;

    private void syncSetting(Runnable update) {
        boolean previous = syncingSettings;
        syncingSettings = true;
        try { update.run(); } finally { syncingSettings = previous; }
    }

    public AeroModuleAdapter(net.aero.visuals.module.Module lunacyModule) {
        super(lunacyModule.name(), mapCategory(lunacyModule.category()));
        this.lunacyModule = lunacyModule;
        if (lunacyModule.isLocked()) this.getSettings().remove(this.keybind);
        this.setDescription(lunacyModule.description());

        if (lunacyModule.isConfiguredEnabled()) {
            this.setEnabledSilent(true);
        }

        lunacyModule.onStateChanged(this::setEnabledSilent);

        // Map settings
        for (Setting<?> setting : lunacyModule.settings()) {
            if (setting instanceof BooleanSetting bs) {
                client.setting.BooleanSetting s = new client.setting.BooleanSetting(bs.name(), bs.description(), bs.get());
                s.setOnChange(() -> { if (!syncingSettings) bs.set(s.isFlag()); });
                bs.onChanged(value -> syncSetting(() -> s.setBoolean(value)));
                s.setVisibleWhen(bs::isVisible);
                this.addSetting(s);
            } else if (setting instanceof DoubleSetting ds) {
                client.setting.SliderSetting s = new client.setting.SliderSetting(ds.name(), ds.description(), ds.get(), ds.minimum(), ds.maximum(), ds.step());
                s.setOnChange(() -> { if (!syncingSettings) ds.set(s.getValue()); });
                ds.onChanged(value -> syncSetting(() -> s.setDouble(value)));
                s.setVisibleWhen(ds::isVisible);
                this.addSetting(s);
            } else if (setting instanceof ColorSetting cs) {
                client.setting.ColorSetting s = new client.setting.ColorSetting(cs.name(), cs.description(), cs.get().primaryArgb(), true);
                s.setOnChange(() -> {
                    if (!syncingSettings) {
                        ColorValue old = cs.get();
                        cs.set(new ColorValue(s.getInt2(), old.secondaryArgb(), old.rainbow(), old.rainbowSpeed()));
                    }
                });
                cs.onChanged(value -> syncSetting(() -> s.setInt(value.primaryArgb())));
                s.setVisibleWhen(cs::isVisible);
                this.addSetting(s);
            } else if (setting instanceof EnumSetting<?> es) {
                Enum<?>[] vals = es.values();
                List<String> names = new ArrayList<>();
                for (Enum<?> val : vals) {
                    names.add(val.name());
                }
                client.setting.ListSetting s = new client.setting.ListSetting(es.name(), es.description(), names, List.of(es.get().name()), false, 1, 1);
                s.setOnChange(() -> {
                    if (syncingSettings) return;
                    String selected = s.getString();
                    for (Enum<?> val : vals) {
                        if (val.name().equalsIgnoreCase(selected) || s.isString(val.name())) {
                            setEnumUnchecked(es, val);
                            break;
                        }
                    }
                });
                es.onChanged(val -> syncSetting(() -> s.onString3(val.name())));
                s.setVisibleWhen(es::isVisible);
                this.addSetting(s);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> void setEnumUnchecked(EnumSetting<?> es, Enum<?> val) {
        ((EnumSetting<E>) es).set((E) val);
    }

    public net.aero.visuals.module.Module getAeroModule() {
        return lunacyModule;
    }

    @Override
    public String getDisplayName() {
        return net.aero.visuals.localization.AeroI18n.moduleName(this.lunacyModule);
    }

    @Override
    public String getDescription() {
        return net.aero.visuals.localization.AeroI18n.moduleDescription(this.lunacyModule);
    }

    @Override
    public void onEnable() {
        lunacyModule.setEnabled(true);
    }

    @Override
    public void onDisable() {
        lunacyModule.setEnabled(false);
    }

    @Override
    public boolean isEnabled() {
        return lunacyModule.isEnabled();
    }

    @Override
    public void toggle() {
        lunacyModule.toggle();
    }

    public static Category mapCategory(ModuleCategory category) {
        return category == null ? Category.HUD : Category.valueOf(category.name());
    }
}
