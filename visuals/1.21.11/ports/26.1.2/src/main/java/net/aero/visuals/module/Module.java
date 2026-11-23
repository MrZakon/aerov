package net.aero.visuals.module;

import net.aero.visuals.AeroVisuals;
import net.aero.visuals.event.EventBus;
import net.aero.visuals.event.Subscription;
import net.aero.visuals.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class Module {
    private final String id;
    private final String name;
    private final String description;
    private final ModuleCategory category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private EventBus eventBus;
    private Subscription subscription;
    private boolean enabled;
    private boolean active;
    private boolean closed;
    private final List<Consumer<Boolean>> stateListeners = new ArrayList<>();

    protected Module(
            String id,
            String name,
            String description,
            ModuleCategory category
    ) {
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Module id must be snake_case: " + id);
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.category = Objects.requireNonNull(category, "category");
    }

    final void attach(EventBus eventBus) {
        if (this.eventBus != null) {
            throw new IllegalStateException("Module is already attached: " + id);
        }
        this.eventBus = eventBus;
        refreshAccess();
    }

    public final String id() {
        return id;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final ModuleCategory category() {
        return category;
    }

    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public final boolean isEnabled() {
        return !closed && enabled && net.aero.visuals.guard.AeroGuard.allowed();
    }

    /** Locked modules are a permanent part of the client identity. */
    public boolean isLocked() {
        return false;
    }

    /** Короткое динамическое значение для ArrayList и карточки GUI. */
    public String displayValue() {
        return "";
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    /** The saved selection remains intact while device access is suspended. */
    public final boolean isConfiguredEnabled() { return enabled; }

    public final void setEnabled(boolean enabled) {
        if (closed || (isLocked() && !enabled) || this.enabled == enabled) return;
        this.enabled = enabled;
        refreshAccess();
        if (this.enabled == enabled) {
            AeroVisuals.LOGGER.debug("Module {} is now {}", id, enabled ? "enabled" : "disabled");
            stateListeners.forEach(listener -> listener.accept(enabled));
        }
    }

    public final void refreshAccess() {
        if (eventBus == null || closed) return;
        boolean desired = enabled && net.aero.visuals.guard.AeroGuard.allowed();
        if (active == desired) return;

        if (!desired) {
            deactivate();
            return;
        }

        try {
            try {
                subscription = eventBus.register(this);
            } catch (EventBus.NoSubscribersException noListeners) {
                subscription = null;
            }
            onEnable();
            active = true;
        } catch (RuntimeException | LinkageError failure) {
            enabled = false;
            closeSubscription();
            try {
                onDisable();
            } catch (RuntimeException | LinkageError cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            AeroVisuals.LOGGER.error("Module {} could not be enabled; rolled back", id, failure);
            stateListeners.forEach(listener -> listener.accept(false));
        }
    }

    final void shutdown() {
        if (closed) return;
        closed = true;
        if (active) deactivate();
    }

    private void deactivate() {
        active = false;
        closeSubscription();
        try {
            onDisable();
        } catch (RuntimeException | LinkageError failure) {
            AeroVisuals.LOGGER.error("Module {} failed during cleanup", id, failure);
        }
    }

    private void closeSubscription() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    public final Module onStateChanged(Consumer<Boolean> listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    protected final <T extends Setting<?>> T add(T setting) {
        if (settings.stream().anyMatch(existing -> existing.key().equals(setting.key()))) {
            throw new IllegalArgumentException("Duplicate setting " + setting.key() + " in " + id);
        }
        settings.add(setting);
        return setting;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }
}
