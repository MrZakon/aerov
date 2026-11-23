package net.aero.visuals.command;

import net.aero.visuals.ClientRuntime;
import net.aero.visuals.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ClientCommandHandler {
    private ClientCommandHandler() {}

    public static boolean handle(String message) {
        if (message == null || !message.startsWith(".")) {
            return false;
        }

        String raw = message.substring(1).trim();
        if (raw.isEmpty()) return false;

        String[] parts = raw.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);

        if ("chest".equals(cmd) || "craft".equals(cmd) || "pair".equals(cmd) || "pet".equals(cmd)) {
            handleUtility(cmd, raw.substring(parts[0].length()).strip());
            return true;
        } else if ("fps".equals(cmd)) {
            handleFps(parts);
            return true;
        } else if ("wp".equals(cmd) || "waypoint".equals(cmd)) {
            var module=net.aero.visuals.feature.enhanced.VisualExpansion.get(net.aero.visuals.feature.enhanced.Waypoints.class);if(module!=null)module.command(parts);return true;
        } else if ("cfg".equals(cmd) || "config".equals(cmd)) {
            handleConfig(parts);
            return true;
        } else if ("alt".equals(cmd)) {
            handleAlt(parts);
            return true;
        } else if ("help".equals(cmd)) {
            handleHelp();
            return true;
        }

        return false;
    }

    private static void handleUtility(String command, String argument) {
        var runtime = ClientRuntime.getNullable();
        if (runtime == null || !net.aero.visuals.guard.AeroGuard.allowed()) return;
        switch (command) {
            case "chest" -> runtime.module(net.aero.visuals.feature.utility.ChestTracker.class).command(argument);
            case "craft" -> runtime.module(net.aero.visuals.module.impl.hud.CraftPlannerModule.class).command(argument);
            case "pair" -> runtime.module(net.aero.visuals.feature.utility.PairedEmotes.class).command(argument);
            case "pet" -> runtime.module(net.aero.visuals.feature.enhanced.Waypoints.class).petHomeCommand(argument);
        }
    }

    private static void handleFps(String[] parts) {
        var runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        var inspector = runtime.module(net.aero.visuals.module.impl.hud.FpsInspectorModule.class);
        if (inspector == null) return;
        String action = parts.length < 2 ? "toggle" : parts[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "toggle" -> { inspector.toggle(); send("§7FPS Inspector: " + (inspector.isEnabled() ? "§aвключён" : "§cвыключен")); }
            case "on" -> inspector.setEnabled(true);
            case "off" -> inspector.setEnabled(false);
            case "before" -> {
                inspector.setEnabled(true);
                if (inspector.isEnabled()) { inspector.before(); send("§aЗамер ДО: закрой чат, 2 сек. прогрев + 10 сек. игры."); }
            }
            case "after" -> {
                if (inspector.isEnabled() && inspector.after()) send("§aЗамер ПОСЛЕ: закрой чат и повтори ту же сцену.");
                else send("§eСначала заверши .fps before в том же мире.");
            }
            case "reset", "cancel" -> { inspector.resetMeasurements(); send("§7Замеры FPS сброшены."); }
            default -> send("§7.fps [on | off | before | after | reset]");
        }
    }

    private static void handleConfig(String[] parts) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null || runtime.config() == null) {
            send("§cКонфиг-менеджер не инициализирован.");
            return;
        }

        ConfigManager config = runtime.config();

        if (parts.length < 2 || "help".equalsIgnoreCase(parts[1])) {
            send("§7========== §d§lAeroVisuals Configs §r§7==========");
            send("§d.cfg save [имя] §7- Сохранить профиль или текущий конфиг");
            send("§d.cfg load <имя> §7- Загрузить профиль");
            send("§d.cfg list §7- Список сохранённых профилей");
            send("§d.cfg reset §7- Сбросить настройки к умолчаниям");
            send("§d.cfg dir §7- Открыть папку с конфигами");
            return;
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "save" -> {
                if (parts.length >= 3) {
                    String name = parts[2].trim();
                    if (config.saveProfile(name)) {
                        send("§aПрофиль §e" + name + " §aуспешно сохранён!");
                    } else {
                        send("§cНе удалось сохранить профиль §e" + name + "§c.");
                    }
                } else {
                    send(config.flushAndCheck()
                            ? "§aОсновная конфигурация сохранена."
                            : "§cНе удалось записать конфигурацию на ПК.");
                }
            }
            case "load" -> {
                if (parts.length < 3) {
                    send("§cИспользование: §f.cfg load <имя_профиля>");
                    return;
                }
                String name = parts[2].trim();
                if (config.loadProfile(name)) {
                    send("§aПрофиль §e" + name + " §aуспешно загружен!");
                } else {
                    send("§cПрофиль §e" + name + "§c не загружен. Проверьте файл профиля и доступ к папке настроек.");
                }
            }
            case "list" -> {
                List<String> list = config.listProfiles();
                if (list.isEmpty()) {
                    send("§7Список профилей пуст. Создайте профиль: §f.cfg save <имя>");
                } else {
                    send("§7Сохранённые профили (§d" + list.size() + "§7): §f" + String.join("§7, §f", list));
                }
            }
            case "reset" -> {
                send(config.resetToDefaults()
                        ? "§6Настройки сброшены к значениям по умолчанию."
                        : "§cНе удалось сбросить настройки.");
            }
            case "dir" -> {
                try {
                    Path dir = config.getProfilesDir();
                    new ProcessBuilder("explorer.exe", dir.toAbsolutePath().toString()).start();
                    send("§aПапка профилей открыта.");
                } catch (Exception e) {
                    send("§cНе удалось открыть проводник: " + e.getMessage());
                }
            }
            default -> send("§cНеизвестная подкоманда. Используйте: §f.cfg help");
        }
    }

    private static void handleAlt(String[] parts) {
        if (parts.length < 2 || "help".equalsIgnoreCase(parts[1])) {
            send("§7========== §d§lAeroVisuals Alts §r§7==========");
            send("§d.alt <ник> §7- Быстро переключиться на аккаунт");
            send("§d.alt add <ник> §7- Добавить аккаунт в список");
            send("§d.alt del <ник> §7- Удалить аккаунт из списка");
            send("§d.alt list §7- Список сохранённых аккаунтов");
            send("§d.alt random §7- Сгенерировать и переключить случайный ник");
            send("§d.alt current §7- Показать текущий никнейм");
            return;
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> {
                if (parts.length < 3) {
                    send("§cИспользование: §f.alt add <ник>");
                    return;
                }
                String name = parts[2].trim();
                if (net.aero.visuals.alt.AltManager.addAlt(name)) {
                    send("§aАккаунт §e" + name + " §aдобавлен в список!");
                } else {
                    send("§cНе удалось добавить аккаунт §e" + name + " §c(уже существует или неверный).");
                }
            }
            case "del", "remove" -> {
                if (parts.length < 3) {
                    send("§cИспользование: §f.alt del <ник>");
                    return;
                }
                String name = parts[2].trim();
                if (net.aero.visuals.alt.AltManager.removeAlt(name)) {
                    send("§aАккаунт §e" + name + " §aудалён из списка.");
                } else {
                    send("§cАккаунт §e" + name + " §cне найден в списке.");
                }
            }
            case "list" -> {
                List<String> list = net.aero.visuals.alt.AltManager.getAlts();
                String cur = net.aero.visuals.alt.AltManager.getCurrentUsername();
                if (list.isEmpty()) {
                    send("§7Список сохранённых аккаунтов пуст. Текущий: §e" + cur);
                } else {
                    send("§7Сохранённые аккаунты (§d" + list.size() + "§7): §f" + String.join("§7, §f", list));
                    send("§7Текущий активный: §a" + cur);
                }
            }
            case "random", "rnd" -> {
                String rnd = net.aero.visuals.alt.AltManager.randomName();
                if (net.aero.visuals.alt.AltManager.login(rnd)) {
                    send("§aСгенерирован и активирован ник: §e" + rnd);
                } else {
                    send("§cОшибка переключения на случайный ник.");
                }
            }
            case "current" -> {
                send("§7Текущий никнейм: §a" + net.aero.visuals.alt.AltManager.getCurrentUsername());
            }
            default -> {
                String name = parts[1].trim();
                if (net.aero.visuals.alt.AltManager.login(name)) {
                    send("§aУспешный вход под ником: §e" + name);
                } else {
                    send("§cОшибка переключения на ник §e" + name + "§c. (Длина 1-16 символов)");
                }
            }
        }
    }

    private static void handleHelp() {
        send("§7========== §d§lAeroVisuals §r§7==========");
        send("§d.cfg §7- Управление профилями и сохранением конфигураций");
        send("§d.alt §7- Менеджер ALT аккаунтов (смена ника без перезапуска)");
        send("§d.wp §7- Собственные метки мест: add, look, remove, list");
        send("§d.fps §7- FPS Inspector: on, off, before, after, reset");
        send("§d.chest §7- Chest Tracker: on, поиск, mark, forget");
        send("§d.craft §7- План крафта: add, list, remove, clear");
        send("§d.pair §7- Парные эмоции: invite, accept, cancel");
        send("§d.pet §7- Дом питомца: home, clearhome");
        send("§d.help §7- Справка по доступным командам");
    }

    public static void send(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.empty().append(Component.literal("[").withStyle(style->style.withColor(0x82909A))).append(Component.literal("AeroVisuals").withStyle(style->style.withColor(client.api.Theme.primary()).withBold(true))).append(Component.literal("] ").withStyle(style->style.withColor(0x82909A))).append(Component.literal(text)));
        }
    }
}
