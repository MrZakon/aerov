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

        if ("cfg".equals(cmd) || "config".equals(cmd)) {
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

    private static void handleConfig(String[] parts) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null || runtime.config() == null) {
            send("§cКонфиг-менеджер не инициализирован.");
            return;
        }

        ConfigManager config = runtime.config();

        if (parts.length < 2 || "help".equalsIgnoreCase(parts[1])) {
            send("§7========== §d§lLunacy Configs §r§7==========");
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
            send("§7========== §d§lLunacy Alts §r§7==========");
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
        send("§7========== §d§lLunacy Visuals §r§7==========");
        send("§d.cfg §7- Управление профилями и сохранением конфигураций");
        send("§d.alt §7- Менеджер ALT аккаунтов (смена ника без перезапуска)");
        send("§d.help §7- Справка по доступным командам");
    }

    public static void send(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§7[§d§lLunacy§r§7] §f" + text));
        }
    }
}
