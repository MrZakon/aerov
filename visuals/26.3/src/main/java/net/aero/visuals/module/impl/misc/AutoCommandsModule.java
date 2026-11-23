package net.aero.visuals.module.impl.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.aero.visuals.event.Subscribe;
import net.aero.visuals.event.impl.TickEvent;
import net.aero.visuals.module.Module;
import net.aero.visuals.module.ModuleCategory;
import net.aero.visuals.module.setting.Setting;

public final class AutoCommandsModule extends Module {
    public static final String ID = "auto_commands";
    public static final int MAX_RULES = 12;
    public static final int MAX_COMMAND_LENGTH = 256;

    public enum Unit {
        SECONDS(86_400, 1_000_000_000L),
        MINUTES(1_440, 60_000_000_000L);

        private final int maxInterval;
        private final long nanos;

        Unit(int maxInterval, long nanos) {
            this.maxInterval = maxInterval;
            this.nanos = nanos;
        }

        public int maxInterval() {
            return maxInterval;
        }
    }

    public record Rule(String command, int interval, Unit unit) {
        public Rule {
            command = command.strip();
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank() || Character.isWhitespace(command.charAt(0))
                    || command.length() > MAX_COMMAND_LENGTH
                    || command.chars().anyMatch(ch -> Character.isISOControl(ch) || ch == '§')) {
                throw new IllegalArgumentException("Invalid command");
            }
            if (interval < 1 || interval > unit.maxInterval()) {
                throw new IllegalArgumentException("Invalid interval");
            }
            command = "/" + command;
        }

        long delayNanos() {
            return interval * unit.nanos;
        }
    }

    private final ScheduleSetting schedules = add(new ScheduleSetting());
    private Object connection;
    private long suspendedAt;
    private List<Rule> activeRules;
    private long[] deadlines;

    public AutoCommandsModule() {
        super(ID, "Auto Commands", "Отправляет заданные команды по расписанию.", ModuleCategory.MISC);
    }

    public List<Rule> rules() {
        return schedules.get();
    }

    public void setRules(List<Rule> rules) {
        schedules.set(rules);
    }

    @Subscribe
    private void tick(TickEvent.Client event) {
        var client = event.client();
        var network = client.getConnection();
        var rules = schedules.get();
        if (!isEnabled() || network == null || rules.isEmpty()) {
            resetSchedule();
            return;
        }

        long now = System.nanoTime();
        if (connection != network || activeRules != rules) {
            connection = network;
            suspendedAt = 0;
            activeRules = rules;
            deadlines = new long[rules.size()];
            for (int i = 0; i < rules.size(); i++) {
                deadlines[i] = now + rules.get(i).delayNanos();
            }
        }

        if (client.isPaused() || client.player == null || client.level == null) {
            if (suspendedAt == 0) suspendedAt = now;
            return;
        }
        if (suspendedAt != 0) {
            long paused = now - suspendedAt;
            for (int i = 0; i < deadlines.length; i++) deadlines[i] += paused;
            suspendedAt = 0;
        }

        int due = -1;
        for (int i = 0; i < deadlines.length; i++) {
            if (now - deadlines[i] >= 0 && (due < 0 || deadlines[i] - deadlines[due] < 0)) {
                due = i;
            }
        }
        if (due >= 0) {
            Rule rule = rules.get(due);
            deadlines[due] = now + rule.delayNanos();
            network.sendCommand(rule.command().substring(1));
        }
    }

    @Override
    protected void onDisable() {
        resetSchedule();
    }

    private void resetSchedule() {
        connection = null;
        suspendedAt = 0;
        activeRules = null;
        deadlines = null;
    }

    private static final class ScheduleSetting extends Setting<List<Rule>> {
        ScheduleSetting() {
            super("schedule", "Расписание", "Локальное расписание команд.", List.of());
        }

        @Override
        protected List<Rule> normalize(List<Rule> rules) {
            if (rules.size() > MAX_RULES) throw new IllegalArgumentException("Too many commands");
            return List.copyOf(rules);
        }

        @Override
        protected JsonElement encode(List<Rule> rules) {
            var saved = new JsonArray();
            for (Rule rule : rules) {
                var entry = new JsonObject();
                entry.addProperty("command", rule.command());
                entry.addProperty("interval", rule.interval());
                entry.addProperty("unit", rule.unit().name().toLowerCase(Locale.ROOT));
                saved.add(entry);
            }
            return saved;
        }

        @Override
        protected List<Rule> decode(JsonElement value) {
            var rules = new ArrayList<Rule>();
            for (JsonElement element : value.getAsJsonArray()) {
                var entry = element.getAsJsonObject();
                rules.add(new Rule(entry.get("command").getAsString(), entry.get("interval").getAsInt(),
                        Unit.valueOf(entry.get("unit").getAsString().toUpperCase(Locale.ROOT))));
                if (rules.size() == MAX_RULES) break;
            }
            return rules;
        }
    }
}
