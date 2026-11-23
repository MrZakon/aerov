package net.aero.visuals.alt;

import client.api.Theme;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class MultiAccountScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
    private static final Identifier PREVIEW_TEXTURE = Identifier.of("aerovisuals", "multi_account_preview");

    private final Screen parent;
    private final MultiAccountSessions sessions;
    private final MultiAccountModule module;
    private final MultiAccountSessions.Account previous;
    private MultiAccountSessions.Account selected;
    private MultiAccountSessions.Account shownPreview;
    private Path previewDirectory;
    private NativeImageBackedTexture previewTexture;
    private PreviewLoad pendingPreview;
    private long previewRetryAt;
    private long previewId;
    private int previewWidth;
    private int previewHeight;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int right;
    private int rightWidth;
    private int listWidth;
    private int listTop;
    private int rows;
    private int scroll;
    private int chatScroll;
    private boolean chatOpen;
    private List<String> chatMessages = List.of();
    private List<net.minecraft.text.OrderedText> chatLines = List.of();
    private int chatWidth;
    private String status = "";
    private TextFieldWidget search;
    private TextFieldWidget chat;
    private ButtonWidget connect;
    private ButtonWidget stop;
    private ButtonWidget play;
    private ButtonWidget control;
    private ButtonWidget remove;
    private ButtonWidget settings;
    private ButtonWidget send;
    private ButtonWidget everyone;
    private ButtonWidget respawn;

    MultiAccountScreen(Screen parent, MultiAccountSessions sessions, MultiAccountModule module, MultiAccountSessions.Account previous) {
        super(Text.literal("Multi Account"));
        this.parent = parent;
        this.sessions = sessions;
        this.module = module;
        this.previous = previous;
        this.selected = previous;
    }

    @Override
    protected void init() {
        sessions.load(client);
        if (selected == null) select(sessions.accounts().stream().findFirst().orElse(null));
        else sessions.setPreview(chatOpen ? null : selected);
        int margin = height < 260 ? 6 : 16;
        panelWidth = Math.min(650, width - 2 * margin);
        panelHeight = Math.min(440, height - 2 * margin);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        listWidth = (panelWidth - 28) / 3 + 16;
        right = left + listWidth + 18;
        rightWidth = left + panelWidth - 10 - right;
        listTop = top + 87;
        rows = Math.max(1, (panelHeight - 161) / ROW_HEIGHT);

        button("×", left + panelWidth - 32, top + 8, 22, this::close).setTooltip(null);
        settings = button(tr("Настройки", "Settings", "Налаштування"), left + panelWidth - 124, top + 8, 88, () -> {
            if (selected != null) client.setScreen(new MultiAccountSettingsScreen(this, selected, module.memoryMb()));
        });
        button("Microsoft", left + panelWidth - 208, top + 8, 80,
                () -> client.setScreen(new MultiAccountMicrosoftScreen(this)));
        int leftColumn = left + 10;
        String addLabel = tr("Добавить", "Add", "Додати");
        int addWidth = Math.min(listWidth - 43, Math.max(50, textRenderer.getWidth(addLabel) + 16));
        search = field(search, leftColumn, top + 37, listWidth - addWidth - 13, 16,
                tr("Ник", "Nickname", "Нік"));
        search.setChangedListener(ignored -> scroll = 0);
        button(addLabel, leftColumn + listWidth - addWidth - 8, top + 37, addWidth, this::addAccount);
        button(tr("Основной", "Primary", "Основний") + " · " + client.getSession().getUsername(), leftColumn, top + 61, listWidth - 8,
                () -> sessions.showPrimary(client));
        chat = field(chat, right, top + panelHeight - 45, rightWidth - 96, 256,
                tr("Сообщение", "Message", "Повідомлення"));
        int bottom = top + panelHeight - 26;
        int halfList = (listWidth - 13) / 2;
        connect = button(tr("Подключить", "Connect", "Підключити"), leftColumn, bottom - 23, halfList, this::startSelected);
        stop = button(tr("Отключить", "Stop", "Відключити"), leftColumn + halfList + 5, bottom - 23, halfList, () -> sessions.stop(selected));
        button(tr("Все в игру", "Connect all", "Підключити всіх"), leftColumn, bottom, halfList,
                this::startAll);
        button(tr("Стоп всем", "Stop all", "Зупинити всіх"), leftColumn + halfList + 5, bottom, halfList, sessions::stopAll);

        int third = (rightWidth - 8) / 3;
        control = button(tr("Управлять", "Control", "Керувати"), right, top + 36, third, () -> focus(true));
        play = button(tr("Играть за", "Play as", "Грати за"), right + third + 4, top + 36, third, () -> focus(false));
        remove = button(tr("Удалить", "Remove", "Видалити"), right + 2 * (third + 4), top + 36, third, () -> {
            sessions.remove(selected);
            select(sessions.accounts().stream().findFirst().orElse(null));
        });
        int halfRight = (rightWidth - 4) / 2;
        button(tr("Вид игрока", "Player view", "Вид гравця"), right, bottom, halfRight, () -> setChat(false));
        button(tr("Чат", "Chat", "Чат"), right + halfRight + 4, bottom, halfRight, () -> setChat(true));

        send = button(tr("С этого", "Only this", "Із цього"), right + rightWidth - 92, bottom - 19, 43, () -> sendChat(false));
        everyone = button(tr("От всех", "Everyone", "Від усіх"), right + rightWidth - 45, bottom - 19, 45, () -> sendChat(true));
        respawn = button(respawnLabel(), right, bottom - 23, rightWidth, () -> {
            sessions.toggleAutoRespawn();
            respawn.setMessage(Text.literal(respawnLabel()));
        });
        updateButtons();
    }

    private TextFieldWidget field(TextFieldWidget current, int x, int y, int w, int limit, String label) {
        if (current == null) current = new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(label));
        current.setX(x);
        current.setY(y);
        current.setWidth(w);
        current.setMaxLength(limit);
        current.setPlaceholder(Text.literal(label));
        addDrawableChild(current);
        return current;
    }

    private ButtonWidget button(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
                .dimensions(x, y, w, 20).build());
    }

    void acceptMicrosoft(MultiAccountMicrosoft.Profile identity) throws IOException {
        if (!module.isEnabled()) throw new IOException(tr("Модуль отключён", "Module disabled", "Модуль вимкнено"));
        select(sessions.addMicrosoft(identity, client));
        search.setText("");
        status = "";
        scroll = Math.max(0, sessions.accounts().indexOf(selected) - rows + 1);
    }

    private void addAccount() {
        String name = search.getText().strip();
        if (!sessions.add(name, client)) {
            status = tr("Нужен уникальный ник от 3 до 16 символов", "Enter a unique 3–16 character nickname",
                    "Введи унікальний нік на 3–16 символів");
            return;
        }
        select(sessions.accounts().getLast());
        search.setText("");
        status = "";
        scroll = Math.max(0, sessions.accounts().size() - rows);
    }

    private List<MultiAccountSessions.Account> filtered() {
        String filter = search.getText().strip().toLowerCase(java.util.Locale.ROOT);
        return sessions.accounts().stream()
                .filter(account -> account.name.toLowerCase(java.util.Locale.ROOT).contains(filter))
                .toList();
    }

    private void select(MultiAccountSessions.Account account) {
        selected = account;
        chatScroll = 0;
        sessions.setPreview(chatOpen ? null : account);
        resetPreview();
    }

    private void setChat(boolean value) {
        chatOpen = value;
        if (chatOpen) discardPendingPreview();
        sessions.setPreview(chatOpen ? null : selected);
        chatScroll = 0;
        updateButtons();
        if (chatOpen) setFocused(chat);
        else setFocused(null);
    }

    private void startAll() {
        if (client.getCurrentServerEntry() == null) {
            status = tr("Сначала войди на сервер", "Join a server first", "Спочатку зайди на сервер");
            return;
        }
        MultiAccountSessions.MemoryShortfall shortage = null;
        for (var account : sessions.accounts()) {
            if (!sessions.start(account, client, module.memoryMb()) && shortage == null) {
                shortage = sessions.memoryShortfall();
            }
        }
        status = shortage != null ? memoryLabel(shortage) : sessions.atLimit() ? limitLabel() : "";
    }

    private String limitLabel() {
        return tr("Лимит фоновых аккаунтов: %d", "Background account limit: %d", "Ліміт фонових акаунтів: %d")
                .formatted(MultiAccountSessions.MAX_RUNNING);
    }

    private static String memoryLabel(MultiAccountSessions.MemoryShortfall shortage) {
        if (shortage.availableMb() < 0) {
            return tr("Не удалось измерить доступную ОЗУ", "Could not measure available memory", "Не вдалося виміряти доступну пам’ять");
        }
        return tr("Доступно %d МБ\nНужно %d МБ", "Available %d MB\nRequired %d MB", "Доступно %d МБ\nПотрібно %d МБ")
                .formatted(shortage.availableMb(), shortage.requiredMb());
    }

    private void startSelected() {
        if (sessions.start(selected, client, module.memoryMb())) {
            status = "";
            return;
        }
        var shortage = sessions.memoryShortfall();
        status = shortage != null ? memoryLabel(shortage)
                : sessions.atLimit() ? limitLabel()
                : tr("Сначала войди на сервер", "Join a server first", "Спочатку зайди на сервер");
    }

    private void focus(boolean temporary) {
        if (sessions.play(client, selected, temporary)) status = temporary
                ? tr("Esc возвращает в панель", "Esc returns to the panel", "Esc повертає до панелі")
                : tr("F9 возвращает в панель", "F9 returns to the panel", "F9 повертає до панелі");
    }

    private void sendChat(boolean all) {
        String message = chat.getText().strip();
        if (message.isEmpty()) return;
        if (all && sessions.broadcast(message) > 0 || !all && sessions.chat(selected, message)) {
            chat.setText("");
        }
    }

    private String respawnLabel() {
        return sessions.autoRespawn() ? tr("Автовозрождение в фоне: вкл", "Background respawn: on", "Відродження у фоні: увімк.")
                : tr("Автовозрождение в фоне: выкл", "Background respawn: off", "Відродження у фоні: вимк.");
    }

    private void updateButtons() {
        boolean hasAccount = selected != null;
        boolean live = hasAccount && selected.live();
        boolean ready = hasAccount && selected.online() && selected.process != null;
        connect.active = hasAccount && (!selected.active() || live && !selected.stopping && "disconnected".equals(selected.state.phase)) && client.getCurrentServerEntry() != null;
        stop.active = live && !selected.stopping;
        control.active = ready;
        play.active = ready;
        remove.active = hasAccount && !selected.active();
        settings.active = hasAccount;
        chat.visible = chatOpen;
        send.visible = chatOpen;
        everyone.visible = chatOpen;
        respawn.visible = !chatOpen;
        send.active = hasAccount && selected.online();
        everyone.active = sessions.accounts().stream().anyMatch(MultiAccountSessions.Account::online);
    }

    @Override
    public void tick() {
        if (!module.isEnabled()) {
            close();
            return;
        }
        updateButtons();
        if (!chatOpen) updatePreview();
    }

    private record PreviewLoad(MultiAccountSessions.Account account, Path directory, long revision,
                               CompletableFuture<NativeImage> pixels) {}

    private void updatePreview() {
        var account = selected;
        Path directory = account == null || !account.live() ? null : account.directory;
        if (shownPreview != null && (shownPreview != account || !previewDirectory.equals(directory))) {
            resetPreview();
        }

        if (pendingPreview != null && pendingPreview.pixels().isDone()) {
            var loaded = pendingPreview;
            pendingPreview = null;
            NativeImage image = loaded.pixels().getNow(null);
            if (loaded.account() == account && loaded.directory().equals(directory)) {
                if (image == null || !uploadPreview(loaded, image)) {
                    previewRetryAt = System.nanoTime() + 250_000_000L;
                }
            } else if (image != null) {
                image.close();
            }
        }

        if (directory == null || pendingPreview != null || previewRetryAt != 0 && System.nanoTime() < previewRetryAt) return;
        long revision = account.state.previewId;
        if (revision == 0 || shownPreview == account && revision == previewId) return;
        previewRetryAt = 0;
        Path file = directory.resolve("preview.png");
        var pixels = CompletableFuture.supplyAsync(() -> decodePreview(file),
                task -> Thread.ofVirtual().name("Aero preview decode").start(task));
        pendingPreview = new PreviewLoad(account, directory, revision, pixels);
    }

    private static NativeImage decodePreview(Path file) {
        try (var input = Files.newInputStream(file)) {
            return NativeImage.read(input);
        } catch (IOException | RuntimeException | LinkageError failure) {
            // The worker can replace or remove the preview while the panel is open.
            return null;
        }
    }

    private boolean uploadPreview(PreviewLoad loaded, NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (previewTexture != null && width == previewWidth && height == previewHeight) {
            previewTexture.setImage(image);
            try {
                previewTexture.upload();
            } catch (RuntimeException | LinkageError failure) {
                resetPreview();
                return false;
            }
        } else {
            NativeImageBackedTexture texture = null;
            try {
                texture = new NativeImageBackedTexture(() -> "Multi Account preview", image);
                client.getTextureManager().registerTexture(PREVIEW_TEXTURE, texture);
                previewTexture = texture;
            } catch (RuntimeException | LinkageError failure) {
                if (texture == null) image.close();
                else texture.close();
                return false;
            }
        }
        previewWidth = width;
        previewHeight = height;
        shownPreview = loaded.account();
        previewDirectory = loaded.directory();
        previewId = loaded.revision();
        return true;
    }

    private void discardPendingPreview() {
        var pending = pendingPreview;
        pendingPreview = null;
        if (pending != null) pending.pixels().thenAccept(image -> {
            if (image != null) image.close();
        });
    }

    private void resetPreview() {
        if (previewTexture != null && client != null) client.getTextureManager().destroyTexture(PREVIEW_TEXTURE);
        previewTexture = null;
        shownPreview = null;
        previewDirectory = null;
        previewId = 0;
        previewRetryAt = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x65000000);
        UiRenderer.roundedRect(context, left, top, panelWidth, panelHeight, 12, Theme.background());
        UiRenderer.roundedBorder(context, left, top, panelWidth, panelHeight, 12, 1, Theme.border());
        context.drawText(textRenderer, title, left + 12, top + 13, Theme.foreground(), false);
        String subtitle = client.getCurrentServerEntry() == null ? tr("Вне сервера", "No server", "Поза сервером")
                : client.getCurrentServerEntry().address;
        subtitle = client.getSession().getUsername() + " · " + subtitle;
        text(context, subtitle, left + 12, top + 25, panelWidth - 222, Theme.mutedFg());
        drawAccounts(context, mouseX, mouseY);
        if (chatOpen) drawChat(context);
        else drawPreview(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawAccounts(DrawContext context, int mouseX, int mouseY) {
        var list = filtered();
        scroll = Math.clamp(scroll, 0, Math.max(0, list.size() - rows));
        if (list.isEmpty()) {
            text(context, tr("Введи ник и нажми «Добавить»", "Enter a nickname and select Add", "Введи нік і натисни «Додати»"),
                    left + 11, listTop + 4, listWidth - 11, Theme.mutedFg());
        }
        var handler = client.getNetworkHandler();
        for (int index = scroll; index < Math.min(list.size(), scroll + rows); index++) {
            var account = list.get(index);
            int x = left + 10;
            int y = listTop + (index - scroll) * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + listWidth - 9 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            UiRenderer.roundedRect(context, x, y, listWidth - 8, ROW_HEIGHT - 3, 6,
                    account == selected || hovered ? Theme.elevated() : Theme.surface());
            var entry = handler == null ? null : handler.getPlayerListEntry(account.name);
            var skin = entry == null ? DefaultSkinHelper.getSteve() : entry.getSkinTextures();
            PlayerSkinDrawer.draw(context, skin, x + 5, y + 5, 15);
            text(context, account.microsoft ? account.name + " · Microsoft" : account.name, x + 25, y + 3, listWidth - 36, Theme.foreground());
            text(context, phase(account), x + 25, y + 14, listWidth - 36,
                    account.online() ? Theme.success() : Theme.mutedFg());
        }
        if (!status.isBlank()) {
            var lines = textRenderer.wrapLines(Text.literal(status), listWidth - 6);
            int y = top + panelHeight - 72;
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                context.drawText(textRenderer, lines.get(i), left + 10, y + i * 11, Theme.caution(), false);
            }
        }
    }

    private void drawPreview(DrawContext context) {
        int y = top + 61;
        int end = top + panelHeight - 54;
        int details = 40;
        if (selected == null || end - y <= details) return;
        int viewHeight = end - y - details;
        UiRenderer.roundedRect(context, right, y, rightWidth, viewHeight, 7, Theme.surface());
        if (shownPreview == selected) {
            float scale = Math.min((float) rightWidth / previewWidth, (float) viewHeight / previewHeight);
            double w = previewWidth * scale;
            double h = previewHeight * scale;
            UiRenderer.texture(context, PREVIEW_TEXTURE, right + (rightWidth - w) * .5, y + (viewHeight - h) * .5,
                    w, h, previewWidth, previewHeight);
        } else {
            String detail = selected.state.detail;
            String text = detail == null || detail.isBlank() ? phase(selected) : detail;
            text(context, text, right + 7, y + 7, rightWidth - 14, Theme.mutedFg());
        }
        if (!selected.online()) return;

        var state = selected.state;
        int barY = y + viewHeight + 4;
        int meter = (rightWidth - 5) / 2;
        bar(context, right, barY, meter, state.health / Math.max(1, state.maxHealth), 0xFFDB626B);
        bar(context, right + meter + 5, barY, meter, state.food / 20f, 0xFFF2B15F);
        String ping = state.latency < 0 ? "—" : state.latency + " ms";
        String stats = Math.round(state.health) + " HP  ·  " + state.food + "/20  ·  " + ping + "  ·  " + state.mode + "  ·  " + state.world;
        text(context, stats, right, barY + 7, rightWidth, Theme.foreground());
        if (rightWidth < 176) return;
        for (int i = 0; i < state.hotbar.size(); i++) {
            var item = state.hotbar.get(i);
            if (item.count() == 0) continue;
            var id = Identifier.tryParse(item.id());
            if (id == null) continue;
            var stack = new ItemStack(Registries.ITEM.get(id), item.count());
            int x = right + i * 19;
            context.drawItem(stack, x, barY + 17);
            context.drawStackOverlay(textRenderer, stack, x, barY + 17);
        }
    }

    private void drawChat(DrawContext context) {
        if (selected == null) return;
        if (chatWidth != rightWidth || !chatMessages.equals(selected.state.chat)) {
            chatWidth = rightWidth;
            chatMessages = selected.state.chat;
            chatLines = chatMessages.stream().flatMap(message ->
                    textRenderer.wrapLines(Text.literal(message), Math.max(1, rightWidth - 12)).stream()).toList();
        }
        int y = top + 62;
        int bottom = top + panelHeight - 49;
        UiRenderer.roundedRect(context, right, y, rightWidth, Math.max(0, bottom - y), 7, Theme.surface());
        int count = Math.max(0, (bottom - y - 9) / 11);
        chatScroll = Math.clamp(chatScroll, 0, Math.max(0, chatLines.size() - count));
        int end = chatLines.size() - chatScroll;
        for (int i = Math.max(0, end - count); i < end; i++) {
            context.drawText(textRenderer, chatLines.get(i), right + 6, bottom - 4 - (end - i) * 11, Theme.foreground(), false);
        }
    }

    private void bar(DrawContext context, int x, int y, int width, float progress, int color) {
        context.fill(x, y, x + width, y + 4, Theme.elevated());
        context.fill(x, y, x + Math.round(width * Math.clamp(progress, 0, 1)), y + 4, color);
    }

    private void text(DrawContext context, String value, int x, int y, int width, int color) {
        if (width > 0) context.drawText(textRenderer, textRenderer.trimToWidth(value, width), x, y, color, false);
    }

    private String phase(MultiAccountSessions.Account account) {
        if (account.stopping) return tr("Отключение…", "Stopping…", "Відключення…");
        return switch (account.state.phase) {
            case "starting" -> tr("Запуск…", "Starting…", "Запуск…");
            case "connecting" -> tr("Подключение…", "Connecting…", "Підключення…");
            case "online" -> account.state.dead ? tr("Погиб", "Died", "Загинув") : tr("В игре", "Online", "У грі");
            case "failed" -> tr("Ошибка запуска", "Launch failed", "Помилка запуску");
            default -> tr("Не в игре", "Offline", "Поза грою");
        };
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || click.x() < left + 10
                || click.x() >= left + listWidth || click.y() < listTop || click.y() >= listTop + rows * ROW_HEIGHT) return false;
        int index = scroll + ((int) click.y() - listTop) / ROW_HEIGHT;
        var list = filtered();
        if (index >= list.size()) return false;
        select(list.get(index));
        if (doubleClick) focus(false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && y >= listTop && y < top + panelHeight - 57) {
            if (x >= left + 10 && x < left + listWidth) {
                scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, filtered().size() - rows));
                return true;
            }
            if (chatOpen && x >= right && x < right + rightWidth) {
                chatScroll = Math.max(0, chatScroll + 2 * (int) Math.signum(vertical));
                return true;
            }
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (search.isFocused()) {
                addAccount();
                return true;
            }
            if (chatOpen && chat.isFocused()) {
                sendChat(false);
                return true;
            }
        }
        if (input.key() == GLFW.GLFW_KEY_F9) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override
    public boolean shouldPause() { return false; }
    @Override
    public void removed() {
        sessions.setPreview(null);
        discardPendingPreview();
        resetPreview();
    }
    @Override
    public void close() {
        if (previous != null && module.isEnabled() && sessions.play(client, previous, false)) return;
        client.setScreen(parent);
    }

    private static String tr(String ru, String en, String uk) { return LocalText.pick(ru, en, uk); }
}
