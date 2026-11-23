package net.aero.visuals.gui;

import client.api.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.module.impl.misc.AutoCommandsModule;
import net.aero.visuals.module.impl.misc.AutoCommandsModule.Rule;
import net.aero.visuals.module.impl.misc.AutoCommandsModule.Unit;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class AutoCommandsScreen extends Screen {
    private static final int ROW_TOP = 50;
    private static final int ROW_HEIGHT = 26;

    private final Screen parent;
    private final AutoCommandsModule module;
    private final List<Draft> drafts = new ArrayList<>();
    private int page;
    private int rowsPerPage;
    private int panelLeft;
    private int panelWidth;
    private int commandWidth;
    private String error = "";

    public AutoCommandsScreen(Screen parent, AutoCommandsModule module) {
        super(Component.literal(tr("Автокоманды", "Auto Commands", "Автокоманди")));
        this.parent = parent;
        this.module = module;
        for (Rule rule : module.rules()) {
            drafts.add(new Draft(rule.command(), Integer.toString(rule.interval()), rule.unit()));
        }
        if (drafts.isEmpty()) drafts.add(Draft.blank());
    }

    @Override
    protected void init() {
        panelWidth = Math.min(500, width - 12);
        panelLeft = (width - panelWidth) / 2;
        int intervalWidth = Math.clamp(panelWidth / 6, 28, 44);
        int unitWidth = Math.clamp(panelWidth / 5, 36, 60);
        commandWidth = Math.max(20, panelWidth - 54 - intervalWidth - unitWidth);
        rowsPerPage = Math.max(1, (height - ROW_TOP - 66) / ROW_HEIGHT);
        page = Math.clamp(page, 0, lastPage());

        int x = panelLeft + 10;
        int end = Math.min(drafts.size(), (page + 1) * rowsPerPage);
        for (int i = page * rowsPerPage; i < end; i++) {
            int index = i;
            int y = ROW_TOP + (i % rowsPerPage) * ROW_HEIGHT;
            Draft draft = drafts.get(i);
            field(x, y, commandWidth, draft.command, tr("Введите команду", "Enter command", "Введіть команду"),
                    AutoCommandsModule.MAX_COMMAND_LENGTH + 1, value -> draft.command = value);
            field(x + commandWidth + 4, y, intervalWidth, draft.interval,
                    tr("Интервал", "Interval", "Інтервал"), 5, value -> draft.interval = value);
            int unitX = x + commandWidth + intervalWidth + 8;
            button(unitLabel(draft.unit), unitX, y, unitWidth, widget -> {
                draft.unit = draft.unit == Unit.SECONDS ? Unit.MINUTES : Unit.SECONDS;
                widget.setMessage(Component.literal(unitLabel(draft.unit)));
                error = "";
            });
            button("−", unitX + unitWidth + 4, y, 22, ignored -> {
                drafts.remove(index);
                if (drafts.isEmpty()) drafts.add(Draft.blank());
                rebuild();
            });
        }

        int bottom = height - 30;
        int buttonWidth = Math.min(86, (panelWidth - 28) / 3);
        button(tr("+ Команда", "+ Command", "+ Команда"), x, bottom, buttonWidth, ignored -> {
            drafts.add(Draft.blank());
            page = lastPage();
            rebuild();
        }).active = drafts.size() < AutoCommandsModule.MAX_RULES;
        button(tr("Сохранить", "Save", "Зберегти"), x + buttonWidth + 4, bottom, buttonWidth, ignored -> save());
        button(tr("Назад", "Back", "Назад"), x + 2 * (buttonWidth + 4), bottom, buttonWidth, ignored -> onClose());
        if (lastPage() > 0) {
            button("‹", x, bottom - 25, 24, ignored -> { page--; rebuild(); }).active = page > 0;
            button("›", x + 74, bottom - 25, 24, ignored -> { page++; rebuild(); }).active = page < lastPage();
        }
    }

    private void field(int x, int y, int width, String value, String hint, int max,
                                   Consumer<String> changed) {
        var field = addRenderableWidget(new AccountTextField(font, x, y, width, 20, Component.literal(hint)));
        field.setMaxLength(max);
        field.setValue(value);
        field.setHint(Component.literal(hint));
        field.setResponder(text -> {
            changed.accept(text);
            error = "";
        });
    }

    private Button button(String label, int x, int y, int width, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, width, 20).build());
    }

    private int lastPage() {
        return (drafts.size() - 1) / rowsPerPage;
    }

    private void rebuild() {
        setFocused(null);
        clearWidgets();
        init();
        error = "";
    }

    private void save() {
        var rules = new ArrayList<Rule>();
        for (int i = 0; i < drafts.size(); i++) {
            Draft draft = drafts.get(i);
            if (draft.command.isBlank()) continue;
            int interval;
            try {
                interval = draft.interval.matches("[0-9]+") ? Integer.parseInt(draft.interval) : 0;
            } catch (NumberFormatException invalid) {
                interval = 0;
            }
            if (interval < 1 || interval > draft.unit.maxInterval()) {
                invalidRow(i, tr("Интервал должен быть от 1 до ", "Interval must be between 1 and ",
                        "Інтервал має бути від 1 до ") + draft.unit.maxInterval());
                return;
            }
            try {
                rules.add(new Rule(draft.command, interval, draft.unit));
            } catch (IllegalArgumentException invalid) {
                invalidRow(i, tr("Проверь текст команды.", "Check the command text.", "Перевір текст команди."));
                return;
            }
        }
        module.setRules(rules);
        onClose();
    }

    private void invalidRow(int index, String reason) {
        page = index / rowsPerPage;
        rebuild();
        error = reason;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);
        UiRenderer.roundedRect(context, panelLeft, 7, panelWidth, height - 14, 8, Theme.background());
        int x = panelLeft + 10;
        AccountPanelStyle.text(context, font, title.getString(), x, 16, Theme.foreground(), false);
        AccountPanelStyle.text(context, font, tr("Команда", "Command", "Команда"), x, 35, Theme.mutedFg(), false);
        AccountPanelStyle.text(context, font, tr("Интервал", "Interval", "Інтервал"),
                x + commandWidth + 4, 35, Theme.mutedFg(), false);
        if (lastPage() > 0) {
            AccountPanelStyle.buttonText(context, (page + 1) + "/" + (lastPage() + 1),
                    x + 24, height - 55, 50, 20, Theme.foreground());
        }
        if (!error.isEmpty()) {
            AccountPanelStyle.text(context, font, AccountPanelStyle.trim(error, panelWidth - 120),
                    x + 100, height - 50, 0xFFFF7777, false);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String unitLabel(Unit unit) {
        return unit == Unit.SECONDS ? tr("Сек.", "Sec", "Сек.") : tr("Мин.", "Min", "Хв.");
    }

    private static String tr(String ru, String en, String uk) {
        return LocalText.pick(ru, en, uk);
    }

    private static final class Draft {
        private String command;
        private String interval;
        private Unit unit;

        Draft(String command, String interval, Unit unit) {
            this.command = command;
            this.interval = interval;
            this.unit = unit;
        }

        static Draft blank() {
            return new Draft("", "60", Unit.SECONDS);
        }
    }
}
