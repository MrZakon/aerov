package net.aero.visuals.event;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

public final class Subscription implements AutoCloseable {
    private final EventBus bus;
    private final Object owner;
    private final List<ListenerInvoker> registration;
    private final AtomicBoolean closed = new AtomicBoolean();

    Subscription(EventBus bus, Object owner, List<ListenerInvoker> registration) {
        this.bus = bus;
        this.owner = owner;
        this.registration = registration;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            bus.unregister(owner, registration);
        }
    }
}
