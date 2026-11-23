package net.aero.visuals.module;

import net.aero.visuals.event.EventBus;
import net.aero.visuals.render.WorldRenderable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ModuleManager implements AutoCloseable {
    private volatile List<Module> snapshot = List.of();
    private volatile List<Module> worldRenderers = List.of();
    private final EventBus eventBus;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public <T extends Module> T register(T module) {
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        snapshot = List.copyOf(modules.values());
        if (module instanceof WorldRenderable) {
            worldRenderers = snapshot.stream().filter(WorldRenderable.class::isInstance).toList();
        }
        module.attach(eventBus);
        return module;
    }

    public Optional<Module> find(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public List<Module> all() {
        return snapshot;
    }

    public List<Module> worldRenderers() { return worldRenderers; }

    public List<Module> search(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return all();
        }
        return modules.values().stream()
                .filter(module -> module.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || module.description().toLowerCase(Locale.ROOT).contains(normalized)
                        || module.id().contains(normalized))
                .toList();
    }

    @Override
    public void close() {
        snapshot.forEach(Module::shutdown);
    }
}
