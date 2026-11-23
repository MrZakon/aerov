package client.module;

import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.gui.AutoCommandsScreen;
import net.aero.visuals.alt.MultiAccountModule;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.module.impl.misc.AutoCommandsModule;
import net.minecraft.client.MinecraftClient;
import net.aero.visuals.module.setting.BooleanSetting;
import net.aero.visuals.module.setting.ColorSetting;
import net.aero.visuals.module.setting.ColorValue;
import net.aero.visuals.module.setting.DoubleSetting;
import net.aero.visuals.module.setting.EnumSetting;
import net.aero.visuals.module.setting.KeybindSetting;
import net.aero.visuals.module.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.aero.visuals.module.ModuleManager;
import net.aero.visuals.regulator.ServerRestrictions;

public class AeroModuleAdapter extends Module {
    private final net.aero.visuals.module.Module lunacyModule;
    private final EffectCards card;
    private final List<net.aero.visuals.module.Module> cardMembers;
    private final List<client.setting.Setting> cardSettings = new ArrayList<>();
    private final Map<Setting<?>, client.setting.Setting> mappedSettings = new LinkedHashMap<>();
    private boolean syncingSettings;

    private void syncSetting(Runnable update) {
        boolean previous = syncingSettings;
        syncingSettings = true;
        try { update.run(); } finally { syncingSettings = previous; }
    }

    public AeroModuleAdapter(net.aero.visuals.module.Module lunacyModule, ModuleManager manager) {
        super(lunacyModule.name(), mapCategory(lunacyModule.category()));
        this.lunacyModule = lunacyModule;
        this.card = EffectCards.find(lunacyModule.id());
        this.cardMembers = card != null && card.isPrimary(lunacyModule.id()) ? card.modules(manager) : List.of();
        if (lunacyModule.isLocked()) this.getSettings().remove(this.keybind);
        this.setDescription(lunacyModule.description());

        if (lunacyModule.isConfiguredEnabled()) {
            this.setEnabledSilent(true);
        }

        lunacyModule.onStateChanged(this::setEnabledSilent);

        var tools = switch (lunacyModule.id()) {
            case "chest_tracker" -> net.aero.visuals.gui.ClientToolsScreen.Mode.CHESTS;
            case "craft_planner" -> net.aero.visuals.gui.ClientToolsScreen.Mode.CRAFT;
            default -> null;
        };
        if (tools != null) {
            String title = tools == net.aero.visuals.gui.ClientToolsScreen.Mode.CHESTS ? "Открыть поиск" : "Настроить цели";
            var action = new client.setting.ActionSetting(title, "");
            action.setRunnable(() -> {
                var client = MinecraftClient.getInstance();
                client.setScreen(new net.aero.visuals.gui.ClientToolsScreen(client.currentScreen, tools));
            });
            addSetting(action);
        }
        if (lunacyModule instanceof AutoCommandsModule commands) {
            var action = new client.setting.ActionSetting(
                    LocalText.pick("Настроить команды", "Configure commands", "Налаштувати команди"),
                    LocalText.pick("Команды и интервалы", "Commands and intervals", "Команди й інтервали"));
            action.setRunnable(() -> {
                var client = MinecraftClient.getInstance();
                client.setScreen(new AutoCommandsScreen(client.currentScreen, commands));
            });
            this.addSetting(action);
        }

        if (lunacyModule instanceof MultiAccountModule accounts) {
            getSettings().remove(this.keybind);
            var open = new client.setting.ActionSetting(
                    LocalText.pick("Открыть меню", "Open account menu", "Відкрити меню"), "F9");
            open.setRunnable(accounts::openMenu);
            addSetting(open);
            var swap = new client.setting.ActionSetting(
                    LocalText.pick("Сменить аккаунт", "Switch account", "Змінити акаунт"), "");
            swap.setRunnable(accounts::swapAccount);
            addSetting(swap);
            return;
        }

        for (Setting<?> setting : lunacyModule.settings()) {
            addMappedSetting(setting, setting::isVisible, mapped -> { mappedSettings.put(setting, mapped); addSetting(mapped); });
        }

    }

    public void attachCardSettings(client.concurrent.ModuleRegistry registry) {
        if (!isEffectGroup()) return;
        for (var member : cardMembers) {
            var toggle = new client.setting.BooleanSetting(member.name(), member.description(), member.isConfiguredEnabled());
            toggle.setVisibleWhen(() -> !member.isServerBlocked());
            toggle.setOnChange(() -> {
                if (syncingSettings) return;
                if (!member.isServerBlocked()) member.setEnabled(toggle.isFlag());
                syncSetting(() -> toggle.setBoolean(member.isConfiguredEnabled()));
            });
            member.onStateChanged(value -> syncSetting(() -> toggle.setBoolean(value)));
            cardSettings.add(toggle);
            if (!(registry.getModuleByString(member.name()) instanceof AeroModuleAdapter adapter)) continue;
            if (!member.isLocked()) {
                var bind = adapter.getKeybindSetting();
                bind.setVisibleWhen(() -> !member.isServerBlocked());
                bind.setOnChange(() -> {
                    var system = client.concurrent.SystemClient.getInstance();
                    if (system != null && system.getHashUtil() != null) system.getHashUtil().update5();
                });
                cardSettings.add(bind);
            }
            adapter.mappedSettings.forEach((source, mapped) -> {
                mapped.setVisibleWhen(() -> member.isEnabled() && source.isVisible());
                cardSettings.add(mapped);
            });
        }
    }

    private void addMappedSetting(Setting<?> setting, Supplier<Boolean> visible, Consumer<client.setting.Setting> destination) {
        if (setting instanceof BooleanSetting bs) {
            client.setting.BooleanSetting s = new client.setting.BooleanSetting(bs.name(), bs.description(), bs.get());
            s.setOnChange(() -> { if (!syncingSettings) bs.set(s.isFlag()); });
            bs.onChanged(value -> syncSetting(() -> s.setBoolean(value)));
            s.setVisibleWhen(visible);
            destination.accept(s);
        } else if (setting instanceof KeybindSetting bind) {
            var initial = bind.get();
            var s = new client.setting.KeybindSetting(bind.name(), bind.description(), bind.defaultValue().keyCode());
            s.onIntInt(initial.keyCode(), initial.modifiers());
            s.setOnChange(() -> {
                if (!syncingSettings) {
                    var requested = new KeybindSetting.Binding(s.getValue(), s.getValue2());
                    bind.set(requested);
                    if (!requested.equals(bind.get())) {
                        syncSetting(() -> s.onIntInt(bind.get().keyCode(), bind.get().modifiers()));
                    }
                }
            });
            bind.onChanged(value -> syncSetting(() -> s.onIntInt(value.keyCode(), value.modifiers())));
            s.setVisibleWhen(visible);
            destination.accept(s);
        } else if (setting instanceof DoubleSetting ds) {
            client.setting.SliderSetting s = new client.setting.SliderSetting(ds.name(), ds.description(), ds.get(), ds.minimum(), ds.maximum(), ds.step());
            s.setOnChange(() -> {
                if (syncingSettings) return;
                ds.set(s.getValue());
                if (Double.compare(s.getValue(), ds.get()) != 0) syncSetting(() -> s.setDouble(ds.get()));
            });
            ds.onChanged(value -> syncSetting(() -> s.setDouble(value)));
            s.setVisibleWhen(visible);
            destination.accept(s);
        } else if (setting instanceof ColorSetting cs) {
            client.setting.ColorSetting s = new client.setting.ColorSetting(cs.name(), cs.description(), cs.get().primaryArgb(), cs.allowsAlpha());
            s.setOnChange(() -> {
                if (!syncingSettings) {
                    ColorValue old = cs.get();
                    cs.set(new ColorValue(s.getInt2(), old.secondaryArgb(), old.rainbow(), old.rainbowSpeed()));
                }
            });
            cs.onChanged(value -> syncSetting(() -> s.setInt(value.primaryArgb())));
            s.setVisibleWhen(visible);
            destination.accept(s);
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
            s.setVisibleWhen(visible);
            destination.accept(s);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> void setEnumUnchecked(EnumSetting<?> es, Enum<?> val) {
        ((EnumSetting<E>) es).set((E) val);
    }

    @Override
    public List<client.setting.Setting> getVisibleSettings() {
        if (card == null) return super.getVisibleSettings();
        List<client.setting.Setting> result = new ArrayList<>();
        if (!lunacyModule.isLocked() && keybind.isVisible()) result.add(keybind);
        mappedSettings.forEach((source, mapped) -> {
            if (source.isVisible()) result.add(mapped);
        });
        return result;
    }

    public boolean isEffectGroup() {
        return !cardMembers.isEmpty();
    }

    public boolean isVisibleInMenu() {
        if (card == null) return !ServerRestrictions.hidesModule(lunacyModule.id());
        return cardMembers.stream().anyMatch(member -> !ServerRestrictions.hidesModule(member.id()));
    }

    public List<client.setting.Setting> cardSettings() {
        return cardSettings;
    }

    public boolean matchesCardMember(java.util.function.Predicate<String> matches) {
        return cardMembers.stream().anyMatch(member ->
                !ServerRestrictions.hidesModule(member.id()) && matches.test(member.name()));
    }


    public net.aero.visuals.module.Module getAeroModule() {
        return lunacyModule;
    }

    @Override
    public String getDisplayName() {
        if (isEffectGroup()) return card.title();
        return net.aero.visuals.localization.AeroI18n.moduleName(this.lunacyModule);
    }

    @Override
    public String getDescription() {
        if (isEffectGroup()) return LocalText.pick("Каждый эффект включается отдельно.",
                "Each effect has its own switch.", "Кожен ефект вмикається окремо.");
        String description=net.aero.visuals.localization.AeroI18n.moduleDescription(this.lunacyModule);
        if(lunacyModule.isServerBlocked())return net.aero.visuals.localization.LocalText.pick("Запрещено сервером: ","Blocked by server: ","Заборонено сервером: ")+lunacyModule.serverBlockReason()+"\n"+description;
        return description;
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
