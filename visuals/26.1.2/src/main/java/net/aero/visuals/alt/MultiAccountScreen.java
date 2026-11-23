package net.aero.visuals.alt;

import client.api.Theme;
import net.aero.visuals.gui.AccountTextField;
import net.aero.visuals.render.ColorUtil;
import net.aero.visuals.render.font.FontFace;
import net.minecraft.client.gui.components.Tooltip;
import net.aero.visuals.localization.LocalText;
import net.aero.visuals.render.UiRenderer;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class MultiAccountScreen extends Screen {
    private static final int ROW_HEIGHT = 32;
    private static final int BUTTON_HEIGHT = 21;
    private static final int GAP = 5;
    private static final double BODY_TEXT = 9;
    private static final double SMALL_TEXT = 8;
    private static final Identifier PREVIEW_TEXTURE = Identifier.fromNamespaceAndPath("aerovisuals", "multi_account_preview");

    private final Screen parent;
    private final MultiAccountSessions sessions;
    private final MultiAccountModule module;
    private final MultiAccountSessions.Account previous;
    private MultiAccountSessions.Account selected;
    private MultiAccountSessions.Account shownPreview;
    private Path previewDirectory;
    private DynamicTexture previewTexture;
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
    private int listX;
    private int listWidth;
    private int sectionTop;
    private int primaryTop;
    private int contentTop;
    private int contentBottom;
    private int footer;
    private boolean compact;
    private int listTop;
    private int rows;
    private int scroll;
    private int chatScroll;
    private boolean chatOpen;
    private List<String> chatMessages = List.of();
    private List<net.minecraft.util.FormattedCharSequence> chatLines = List.of();
    private int chatWidth;
    private String status = "";
    private EditBox search;
    private EditBox chat;
    private Button connect;
    private Button stop;
    private Button play;
    private Button control;
    private Button remove;
    private Button settings;
    private Button send;
    private Button everyone;
    private Button respawn;
    private MenuButton previewTab;
    private MenuButton chatTab;

    MultiAccountScreen(Screen parent, MultiAccountSessions sessions, MultiAccountModule module, MultiAccountSessions.Account previous) {
        super(Component.literal("Multi Account"));
        this.parent = parent;
        this.sessions = sessions;
        this.module = module;
        this.previous = previous;
        this.selected = previous;
    }

    @Override
    protected void init() {
        sessions.load(minecraft);
        if (selected == null) select(sessions.accounts().stream().findFirst().orElse(null));
        else sessions.setPreview(chatOpen ? null : selected);
        compact = height < 265 || width < 440;
        int margin = compact ? 6 : 14;
        panelWidth = Math.min(638, width - 2 * margin);
        panelHeight = Math.min(400, height - 2 * margin);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        listX = left + 12;
        listWidth = (panelWidth - 38) * 34 / 100;
        right = listX + listWidth + 14;
        rightWidth = left + panelWidth - 12 - right;
        int end = top + panelHeight;
        sectionTop = top + (compact ? 47 : 57);
        primaryTop = sectionTop + 43;
        listTop = primaryTop + 38;
        contentTop = sectionTop + 59;
        footer = end - 29;
        contentBottom = footer - 27;
        rows = Math.max(1, (footer - 23 - listTop) / ROW_HEIGHT);

        int closeX = left + panelWidth - 33;
        iconButton("close", tr("Закрыть", "Close", "Закрити") + " · F9", closeX, top + 12, this::onClose);
        button("Microsoft", closeX - 82, top + 12, 76,
                () -> minecraft.setScreen(new MultiAccountMicrosoftScreen(this)), ButtonStyle.SECONDARY);

        search = field(search, listX, sectionTop + 16, listWidth - 26, 16,
                tr("Поиск или ник", "Search or name", "Пошук або нік"));
        search.setResponder(ignored -> scroll = 0);
        iconButton("add", tr("Добавить по нику", "Add by nickname", "Додати за ніком"),
                listX + listWidth - 21, sectionTop + 16, this::addAccount);
        addRenderableWidget(new MenuButton(
                tr("Основной аккаунт", "Primary account", "Основний акаунт"), listX, primaryTop,
                listWidth, 32, () -> sessions.showPrimary(minecraft), ButtonStyle.SECONDARY, "primary"));

        settings = iconButton("settings", tr("Настройки аккаунта", "Account settings", "Налаштування акаунта"),
                right + rightWidth - 47, sectionTop, () -> {
                    if (selected != null) minecraft.setScreen(new MultiAccountSettingsScreen(this, selected, module.memoryMb()));
                });
        remove = iconButton("remove", tr("Удалить аккаунт", "Remove account", "Видалити акаунт"),
                right + rightWidth - 21, sectionTop, () -> {
                    sessions.remove(selected);
                    select(sessions.accounts().stream().findFirst().orElse(null));
                });

        int tabWidth = (rightWidth - 6) / 2;
        previewTab = button(tr("Вид игрока", "Player view", "Вид гравця"), right, sectionTop + 32,
                tabWidth, () -> setChat(false), ButtonStyle.TAB);
        chatTab = button(tr("Чат", "Chat", "Чат"), right + tabWidth + 6, sectionTop + 32,
                rightWidth - tabWidth - 6, () -> setChat(true), ButtonStyle.TAB);

        int halfList = (listWidth - GAP) / 2;
        button(tr("Все в игру", "Connect all", "Усі в гру"), listX, footer, halfList,
                this::startAll, ButtonStyle.SECONDARY);
        button(tr("Стоп всем", "Stop all", "Стоп усім"), listX + halfList + GAP, footer, listWidth - halfList - GAP, sessions::stopAll, ButtonStyle.SECONDARY);

        int action = (rightWidth - GAP * 2) / 3;
        connect = button(tr("Подключить", "Connect", "Підключити"), right, footer, action,
                this::startSelected, ButtonStyle.PRIMARY);
        stop = button(tr("Отключить", "Disconnect", "Відключити"), right, footer, action,
                () -> sessions.stop(selected), ButtonStyle.SECONDARY);
        control = button(tr("Управлять", "Control", "Керувати"), right + action + GAP,
                footer, action, () -> focus(true), ButtonStyle.SECONDARY);
        play = button(tr("Играть за", "Play as", "Грати за"), right + 2 * (action + GAP),
                footer, rightWidth - 2 * (action + GAP), () -> focus(false), ButtonStyle.PRIMARY);

        int compose = footer - 23;
        int sendWidth = Math.min(71, rightWidth / 4);
        chat = field(chat, right, compose, rightWidth - 2 * (sendWidth + GAP), 256,
                tr("Сообщение", "Message", "Повідомлення"));
        send = button(tr("Отправить", "Send", "Надіслати"), right + rightWidth - 2 * sendWidth - GAP,
                compose, sendWidth, () -> sendChat(false), ButtonStyle.SECONDARY);
        everyone = button(tr("Всем", "To all", "Усім"), right + rightWidth - sendWidth,
                compose, sendWidth, () -> sendChat(true), ButtonStyle.SECONDARY);
        respawn = button(respawnLabel(), right, compose, rightWidth, () -> {
            sessions.toggleAutoRespawn();
            respawn.setMessage(Component.literal(respawnLabel()));
        }, ButtonStyle.QUIET);
        updateButtons();
    }

    private EditBox field(EditBox current, int x, int y, int w, int limit, String label) {
        if (current == null) current = new AccountTextField(font, x, y, w, BUTTON_HEIGHT, Component.literal(label));
        current.setX(x);
        current.setY(y);
        current.setWidth(w);
        current.setMaxLength(limit);
        current.setHint(Component.literal(label));
        return addRenderableWidget(current);
    }

    private MenuButton button(String label, int x, int y, int w, Runnable action, ButtonStyle style) {
        return addRenderableWidget(new MenuButton(label, x, y, w, BUTTON_HEIGHT, action, style, ""));
    }

    private MenuButton iconButton(String icon, String label, int x, int y, Runnable action) {
        var style = icon.equals("add") ? ButtonStyle.SECONDARY : ButtonStyle.QUIET;
        var button = addRenderableWidget(new MenuButton(label, x, y, 21, BUTTON_HEIGHT, action, style, icon));
        button.setTooltip(Tooltip.create(Component.literal(label)));
        return button;
    }

    void acceptMicrosoft(MultiAccountMicrosoft.Profile identity) throws IOException {
        if (!module.isEnabled()) throw new IOException(tr("Модуль отключён", "Module disabled", "Модуль вимкнено"));
        select(sessions.addMicrosoft(identity, minecraft));
        search.setValue("");
        status = "";
        scroll = Math.max(0, sessions.accounts().indexOf(selected) - rows + 1);
    }

    private void addAccount() {
        String name = search.getValue().strip();
        if (!sessions.add(name, minecraft)) {
            status = tr("Нужен уникальный ник от 3 до 16 символов", "Enter a unique 3–16 character nickname",
                    "Введи унікальний нік на 3–16 символів");
            return;
        }
        select(sessions.accounts().getLast());
        search.setValue("");
        status = "";
        scroll = Math.max(0, sessions.accounts().size() - rows);
    }

    private List<MultiAccountSessions.Account> filtered() {
        String filter = search.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        var accounts = sessions.accounts();
        if (filter.isEmpty()) return accounts;
        return accounts.stream()
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
        if (minecraft.getCurrentServer() == null) {
            status = tr("Сначала войди на сервер", "Join a server first", "Спочатку зайди на сервер");
            return;
        }
        MultiAccountSessions.MemoryShortfall shortage = null;
        for (var account : sessions.accounts()) {
            if (!sessions.start(account, minecraft, module.memoryMb()) && shortage == null) {
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
        if (sessions.start(selected, minecraft, module.memoryMb())) {
            status = "";
            return;
        }
        var shortage = sessions.memoryShortfall();
        status = shortage != null ? memoryLabel(shortage)
                : sessions.atLimit() ? limitLabel()
                : tr("Сначала войди на сервер", "Join a server first", "Спочатку зайди на сервер");
    }

    private void focus(boolean temporary) {
        if (sessions.play(minecraft, selected, temporary)) status = temporary
                ? tr("Esc возвращает в панель", "Esc returns to the panel", "Esc повертає до панелі")
                : tr("F9 возвращает в панель", "F9 returns to the panel", "F9 повертає до панелі");
    }

    private void sendChat(boolean all) {
        String message = chat.getValue().strip();
        if (message.isEmpty()) return;
        if (all && sessions.broadcast(message) > 0 || !all && sessions.chat(selected, message)) {
            chat.setValue("");
        }
    }

    private String respawnLabel() {
        return sessions.autoRespawn() ? tr("Автовозрождение · вкл", "Auto-respawn · on", "Автовідродження · увімк.")
                : tr("Автовозрождение · выкл", "Auto-respawn · off", "Автовідродження · вимк.");
    }

    private void updateButtons() {
        boolean hasAccount = selected != null;
        boolean live = hasAccount && selected.live();
        boolean ready = hasAccount && selected.online() && selected.process != null;
        connect.active = hasAccount && (!selected.active() || live && !selected.stopping && "disconnected".equals(selected.state.phase)) && minecraft.getCurrentServer() != null;
        stop.active = live && !selected.stopping;
        stop.visible = hasAccount && selected.active();
        connect.visible = !stop.visible || connect.active;
        boolean reconnect = stop.visible && connect.active;
        stop.setX(reconnect ? control.getX() : connect.getX());
        control.visible = !reconnect;
        previewTab.chosen = !chatOpen;
        chatTab.chosen = chatOpen;
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
            onClose();
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
            previewTexture.setPixels(image);
            try {
                previewTexture.upload();
            } catch (RuntimeException | LinkageError failure) {
                resetPreview();
                return false;
            }
        } else {
            DynamicTexture texture = null;
            try {
                texture = new DynamicTexture(() -> "Multi Account preview", image);
                minecraft.getTextureManager().register(PREVIEW_TEXTURE, texture);
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
        if (previewTexture != null && minecraft != null) minecraft.getTextureManager().release(PREVIEW_TEXTURE);
        previewTexture = null;
        shownPreview = null;
        previewDirectory = null;
        previewId = 0;
        previewRetryAt = 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        UiRenderer.roundedRect(context, left, top + 3, panelWidth, panelHeight, 13, 0x44000000);
        UiRenderer.roundedRect(context, left, top, panelWidth, panelHeight, 13, Theme.background());
        UiRenderer.roundedBorder(context, left, top, panelWidth, panelHeight, 13, .8, Theme.border());

        int headerX = left + 12;
        UiRenderer.roundedRect(context, headerX, top + 11, 26, 26, 7, tinted(Theme.surface(), .18f));
        drawIcon(context, "accounts", headerX + 13, top + 24, accent());
        String caption = tr("Мультиаккаунты", "Multi Account", "Мультиакаунти");
        drawText(context, FontFace.GOLOS_SEMI, caption, headerX + 34, top + 10, panelWidth - 170,
                compact ? 13 : 17, Theme.foreground());
        var server = minecraft.getCurrentServer();
        String address = server != null ? server.ip : minecraft.hasSingleplayerServer()
                ? tr("Одиночный мир", "Singleplayer", "Одиночний світ")
                : tr("Вне сервера", "No server", "Поза сервером");
        drawText(context, FontFace.GOLOS, address, headerX + 34, top + (compact ? 25 : 30),
                panelWidth - 170, SMALL_TEXT, Theme.mutedFg());
        int divider = sectionTop - 8;
        context.fill(left + 1, divider, left + panelWidth - 1, divider + 1, Theme.surface());
        context.fill(right - 7, divider + 1, right - 6, top + panelHeight - 8, Theme.surface());

        drawAccounts(context, mouseX, mouseY);
        drawSelection(context);
        if (chatOpen) drawChat(context);
        else drawPreview(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawAccounts(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        var list = filtered();
        var accounts = sessions.accounts();
        long activeCount = accounts.stream().filter(MultiAccountSessions.Account::online).count();
        String heading = tr("Аккаунты", "Accounts", "Акаунти");
        drawText(context, FontFace.GOLOS_SEMI, heading, listX, sectionTop, listWidth - 40, BODY_TEXT, Theme.foreground());
        String count = activeCount > 0 && listWidth >= 136
                ? activeCount + " " + tr("в игре", "online", "у грі") : Integer.toString(accounts.size());
        UiRenderer.font(context, FontFace.GOLOS, count, listX + listWidth - UiRenderer.fontWidth(FontFace.GOLOS, count, SMALL_TEXT),
                sectionTop + 1, SMALL_TEXT, activeCount > 0 ? Theme.success() : Theme.mutedFg());

        int maxScroll = Math.max(0, list.size() - rows);
        scroll = Math.clamp(scroll, 0, maxScroll);
        int bottom = footer - 23;
        if (list.isEmpty()) {
            String label = search.getValue().isBlank()
                    ? tr("Добавь аккаунт по нику", "Add an account by name", "Додай акаунт за ніком")
                    : tr("Ничего не найдено", "No matches", "Нічого не знайдено");
            drawText(context, FontFace.GOLOS, label, listX + 4, listTop + 3, listWidth - 8, SMALL_TEXT, Theme.mutedFg());
        }
        context.enableScissor(listX, listTop, listX + listWidth, Math.max(listTop + 1, bottom));
        try {
            for (int index = scroll; index < Math.min(list.size(), scroll + rows); index++) {
                var account = list.get(index);
                int y = listTop + (index - scroll) * ROW_HEIGHT;
                int w = maxScroll > 0 ? listWidth - 5 : listWidth;
                boolean hovered = UiRenderer.inside(mouseX, mouseY, listX, y, w, ROW_HEIGHT - 4);
                boolean chosen = account == selected;
                UiRenderer.roundedRect(context, listX, y, w, ROW_HEIGHT - 4, 6,
                        chosen ? tinted(Theme.elevated(), .09f) : hovered ? Theme.elevated() : Theme.surface());
                if (chosen) UiRenderer.roundedBorder(context, listX, y, w, ROW_HEIGHT - 4, 6, .8, tinted(Theme.border(), .43f));
                drawAvatar(context, account.name, listX + 6, y + 6, 16);
                drawText(context, FontFace.GOLOS_SEMI, account.name, listX + 29, y + 4,
                        w - 42, BODY_TEXT, Theme.foreground());
                String type = account.microsoft && w > 150 ? "Microsoft · " : "";
                drawText(context, FontFace.GOLOS, type + phase(account), listX + 29, y + 15,
                        w - 42, SMALL_TEXT, Theme.mutedFg());
                UiRenderer.roundedRect(context, listX + w - 10, y + 11, 4, 4, 2, phaseColor(account));
            }
        } finally {
            context.disableScissor();
        }
        if (maxScroll > 0) {
            int track = bottom - listTop - 2;
            int thumb = Math.max(9, track * rows / list.size());
            int thumbY = listTop + (track - thumb) * scroll / maxScroll;
            UiRenderer.roundedRect(context, listX + listWidth - 2, thumbY, 2, thumb, 1, Theme.border());
        }
        if (!status.isBlank()) {
            var lines = font.split(Component.literal(status), listWidth - 3);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                context.text(font, lines.get(i), listX, bottom + i * 10, Theme.caution(), false);
            }
        }
    }

    private void drawSelection(GuiGraphicsExtractor context) {
        if (selected == null) {
            drawText(context, FontFace.GOLOS_SEMI, tr("Выбери аккаунт", "Select an account", "Вибери акаунт"),
                    right, sectionTop + 5, rightWidth - 52, 12, Theme.foreground());
            return;
        }
        drawAvatar(context, selected.name, right, sectionTop + 1, 24);
        int x = right + 32;
        int room = rightWidth - 85;
        drawText(context, FontFace.GOLOS_SEMI, selected.name, x, sectionTop, room, 13, Theme.foreground());
        UiRenderer.roundedRect(context, x, sectionTop + 18, 4, 4, 2, phaseColor(selected));
        drawText(context, FontFace.GOLOS, phase(selected), x + 8, sectionTop + 15, room - 8, SMALL_TEXT, Theme.mutedFg());
    }

    private void drawAvatar(GuiGraphicsExtractor context, String name, int x, int y, int size) {
        var handler = minecraft.getConnection();
        var entry = handler == null ? null : handler.getPlayerInfo(name);
        var skin = entry == null ? DefaultPlayerSkin.getDefaultSkin() : entry.getSkin();
        var texture = skin.body().texturePath();
        UiRenderer.textureRegion(context, texture, x, y, size, size, 8, 8, 8, 8, 64, 64, 0xFFFFFFFF);
        UiRenderer.textureRegion(context, texture, x, y, size, size, 40, 8, 8, 8, 64, 64, 0xFFFFFFFF);
    }

    private void drawPreview(GuiGraphicsExtractor context) {
        boolean online = selected != null && selected.online();
        boolean hotbar = online && rightWidth >= 180 && contentBottom - contentTop >= 104;
        boolean inline = hotbar && rightWidth >= 330;
        int stats = online ? hotbar && !inline ? 44 : 25 : 0;
        int viewHeight = Math.max(1, contentBottom - contentTop - stats);
        UiRenderer.roundedRect(context, right, contentTop, rightWidth, viewHeight, 8, Theme.surface());
        UiRenderer.roundedBorder(context, right, contentTop, rightWidth, viewHeight, 8, .7, Theme.border());
        if (selected != null && shownPreview == selected && previewWidth > 0 && previewHeight > 0) {
            double scale = Math.min((rightWidth - 6.0) / previewWidth, (viewHeight - 6.0) / previewHeight);
            if (scale > 0) {
                double w = previewWidth * scale;
                double h = previewHeight * scale;
                UiRenderer.texture(context, PREVIEW_TEXTURE, right + (rightWidth - w) / 2,
                        contentTop + (viewHeight - h) / 2, w, h, previewWidth, previewHeight);
            }
        } else {
            String headline = selected == null ? tr("Нет выбранного аккаунта", "No account selected", "Акаунт не вибрано")
                    : online ? tr("Ожидание кадра", "Waiting for a frame", "Очікування кадру") : phase(selected);
            String detail = selected == null ? "" : selected.state.detail;
            if (selected != null && (detail == null || detail.isBlank())) {
                detail = tr("Здесь появится вид от лица аккаунта", "The player’s view will appear here", "Тут буде вид від імені акаунта");
            }
            drawEmpty(context, viewHeight, "screen", headline, detail);
        }
        if (!online) return;

        var state = selected.state;
        int y = contentTop + viewHeight + 5;
        int statsWidth = inline ? rightWidth - Math.min(9, state.hotbar.size()) * 19 - 8 : rightWidth;
        int meter = (statsWidth - 7) / 2;
        bar(context, right, y, meter, state.health / Math.max(1, state.maxHealth), 0xFFE16A72);
        bar(context, right + meter + 7, y, meter, state.food / 20f, 0xFFE6B371);
        String ping = state.latency < 0 ? "—" : state.latency + " ms";
        String line = Math.round(state.health) + " HP  ·  " + state.food + "/20  ·  " + ping + "  ·  " + state.mode + "  ·  " + state.world;
        drawText(context, FontFace.GOLOS, line, right, y + 7, statsWidth, SMALL_TEXT, Theme.foreground());
        if (!hotbar) return;
        for (int i = 0; i < state.hotbar.size(); i++) {
            var item = state.hotbar.get(i);
            if (item.count() == 0) continue;
            var id = Identifier.tryParse(item.id());
            if (id == null) continue;
            var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id), item.count());
            int x = right + (inline ? statsWidth + 8 : 0) + i * 19;
            int itemY = inline ? y : y + 17;
            context.item(stack, x, itemY);
            context.itemDecorations(font, stack, x, itemY);
        }
    }

    private void drawChat(GuiGraphicsExtractor context) {
        int h = Math.max(1, contentBottom - contentTop);
        UiRenderer.roundedRect(context, right, contentTop, rightWidth, h, 8, Theme.surface());
        UiRenderer.roundedBorder(context, right, contentTop, rightWidth, h, 8, .7, Theme.border());
        if (selected == null || selected.state.chat.isEmpty()) {
            drawEmpty(context, h, "chat", tr("Сообщений пока нет", "No messages yet", "Повідомлень ще немає"), "");
            return;
        }
        if (chatWidth != rightWidth || !chatMessages.equals(selected.state.chat)) {
            chatWidth = rightWidth;
            chatMessages = selected.state.chat;
            chatLines = chatMessages.stream().flatMap(message ->
                    font.split(Component.literal(message), Math.max(1, rightWidth - 18)).stream()).toList();
        }
        int count = Math.max(1, (h - 10) / 11);
        chatScroll = Math.clamp(chatScroll, 0, Math.max(0, chatLines.size() - count));
        int end = chatLines.size() - chatScroll;
        for (int i = Math.max(0, end - count); i < end; i++) {
            context.text(font, chatLines.get(i), right + 9, contentBottom - 5 - (end - i) * 11, Theme.foreground(), false);
        }
    }

    private void drawEmpty(GuiGraphicsExtractor context, int height, String icon, String headline, String detail) {
        int cx = right + rightWidth / 2;
        int cy = contentTop + height / 2;
        if (height >= 74) {
            UiRenderer.roundedRect(context, cx - 14, cy - 31, 28, 28, 8, Theme.elevated());
            drawIcon(context, icon, cx, cy - 17, Theme.mutedFg());
        }
        centered(context, headline, cx, cy + (height >= 74 ? 4 : -7), rightWidth - 24, 11, Theme.foreground());
        if (height >= 38 && !detail.isBlank()) centered(context, detail, cx, cy + 20, rightWidth - 24, SMALL_TEXT, Theme.mutedFg());
    }

    private void bar(GuiGraphicsExtractor context, int x, int y, int width, float progress, int color) {
        UiRenderer.roundedRect(context, x, y, width, 3, 1.5, Theme.elevated());
        UiRenderer.roundedRect(context, x, y, width * Math.clamp(progress, 0, 1), 3, 1.5, color);
    }

    private static int accent() { return ColorUtil.mix(Theme.primary(), Theme.success(), .48f); }
    private static int tinted(int color, float amount) { return ColorUtil.mix(color, accent(), amount); }

    private int phaseColor(MultiAccountSessions.Account account) {
        if (account.stopping || "starting".equals(account.state.phase) || "connecting".equals(account.state.phase)) return Theme.warning();
        if ("failed".equals(account.state.phase) || account.online() && account.state.dead) return Theme.danger();
        return account.online() ? Theme.success() : Theme.mutedFg();
    }

    private static void drawText(GuiGraphicsExtractor context, FontFace face, String value,
                                 int x, int y, int width, double size, int color) {
        if (width > 0) UiRenderer.font(context, face, UiRenderer.fitFont(face, value, width, size, true), x, y, size, color);
    }

    private static void centered(GuiGraphicsExtractor context, String value, double x, double y,
                                 int width, double size, int color) {
        var face = FontFace.GOLOS_SEMI;
        String label = UiRenderer.fitFont(face, value, width, size, true);
        UiRenderer.font(context, face, label, x - UiRenderer.fontWidth(face, label, size) / 2, y, size, color);
    }

    private static void drawIcon(GuiGraphicsExtractor context, String icon, int x, int y, int color) {
        switch (icon) {
            case "accounts" -> {
                UiRenderer.roundedBorder(context, x - 6, y - 4, 11, 9, 2, 1, color);
                UiRenderer.roundedRect(context, x - 3, y - 2, 3, 3, 1.5, color);
                UiRenderer.roundedRect(context, x - 4, y + 1, 5, 2, 1, color);
                UiRenderer.rect(context, x + 2, y - 5, 5, 1, color);
                UiRenderer.rect(context, x + 6, y - 4, 1, 6, color);
            }
            case "settings" -> {
                for (int i = -4; i <= 4; i += 4) {
                    int knob = i == 0 ? -1 : 1;
                    UiRenderer.rect(context, x - 5, y + i, 11, 1, color);
                    UiRenderer.rect(context, x + knob, y + i - 1, 2, 3, color);
                }
            }
            case "add" -> {
                UiRenderer.rect(context, x - 4, y, 9, 1, color);
                UiRenderer.rect(context, x, y - 4, 1, 9, color);
            }
            case "remove" -> {
                UiRenderer.rect(context, x - 4, y - 4, 9, 1, color);
                UiRenderer.roundedBorder(context, x - 3, y - 2, 7, 7, 1, 1, color);
                UiRenderer.rect(context, x - 1, y - 5, 3, 1, color);
            }
            case "close" -> centered(context, "×", x + .5, y - 7, 18, 13, color);
            case "screen" -> {
                UiRenderer.roundedBorder(context, x - 7, y - 5, 14, 9, 2, 1, color);
                UiRenderer.rect(context, x, y + 4, 1, 2, color);
                UiRenderer.rect(context, x - 3, y + 6, 7, 1, color);
            }
            case "chat" -> {
                UiRenderer.roundedBorder(context, x - 6, y - 5, 12, 9, 2, 1, color);
                UiRenderer.line(context, x - 4, y + 3, x - 4, y + 6, color);
                UiRenderer.rect(context, x - 3, y + 5, 1, 1, color);
                UiRenderer.rect(context, x - 2, y + 4, 1, 1, color);
            }
        }
    }

    private enum ButtonStyle { PRIMARY, SECONDARY, TAB, QUIET }

    private final class MenuButton extends Button {
        private final ButtonStyle style;
        private final String icon;
        private boolean chosen;

        MenuButton(String label, int x, int y, int width, int height, Runnable action, ButtonStyle style, String icon) {
            super(x, y, width, height, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
            this.style = style;
            this.icon = icon;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            boolean highlight = active && isHoveredOrFocused();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean strong = active && style == ButtonStyle.PRIMARY;
            int bg = strong ? accent() : chosen ? tinted(Theme.elevated(), .14f) : highlight ? Theme.elevated() : Theme.surface();
            if (strong && highlight) bg = ColorUtil.mix(bg, Theme.primary(), .28f);
            if (style != ButtonStyle.QUIET || highlight) UiRenderer.roundedRect(context, x, y, w, h, 6, bg);
            if (chosen || isFocused()) UiRenderer.roundedBorder(context, x, y, w, h, 6, .75, tinted(Theme.border(), .5f));
            int color = strong ? Theme.background() : active ? Theme.foreground() : ColorUtil.mix(Theme.mutedFg(), Theme.surface(), .32f);
            if (style == ButtonStyle.QUIET && !highlight && active) color = Theme.mutedFg();
            if (icon.equals("primary")) {
                String name = minecraft.getUser().getName();
                drawAvatar(context, name, x + 6, y + 7, 18);
                drawText(context, FontFace.GOLOS_SEMI, name, x + 31, y + 4, w - 36, BODY_TEXT, Theme.foreground());
                drawText(context, FontFace.GOLOS, tr("Основной", "Primary", "Основний"),
                        x + 31, y + 16, w - 36, SMALL_TEXT, Theme.mutedFg());
            } else if (!icon.isEmpty()) {
                drawIcon(context, icon, x + w / 2, y + h / 2, icon.equals("remove") && highlight ? Theme.danger() : color);
            } else {
                double size = w < 58 ? 7 : BODY_TEXT;
                centered(context, getMessage().getString(), x + w / 2.0, y + (h - size * 1.1) / 2,
                        w - 6, size, color);
            }
        }
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
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || click.x() < listX
                || click.x() >= listX + listWidth || click.y() < listTop || click.y() >= Math.min(listTop + rows * ROW_HEIGHT, footer - 23)) return false;
        int rowPosition = (int) click.y() - listTop;
        if (rowPosition % ROW_HEIGHT >= ROW_HEIGHT - 4) return false;
        int index = scroll + rowPosition / ROW_HEIGHT;
        var list = filtered();
        if (index >= list.size()) return false;
        select(list.get(index));
        if (doubleClick) focus(false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) {
            if (x >= listX && x < listX + listWidth && y >= listTop && y < footer - 23) {
                scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, filtered().size() - rows));
                return true;
            }
            if (chatOpen && x >= right && x < right + rightWidth && y >= contentTop && y < contentBottom) {
                chatScroll = Math.max(0, chatScroll + 2 * (int) Math.signum(vertical));
                return true;
            }
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
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
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {}
    @Override
    public boolean isPauseScreen() { return false; }
    @Override
    public void removed() {
        sessions.setPreview(null);
        discardPendingPreview();
        resetPreview();
    }
    @Override
    public void onClose() {
        if (previous != null && module.isEnabled() && sessions.play(minecraft, previous, false)) return;
        minecraft.setScreen(parent);
    }

    private static String tr(String ru, String en, String uk) { return LocalText.pick(ru, en, uk); }
}
