package net.aero.visuals.event;

import java.lang.invoke.MethodHandle;

final class ListenerInvoker {
    private final Object owner;
    private final Class<? extends Event> eventType;
    private final EventPriority priority;
    private final boolean receiveCancelled;
    private final MethodHandle handle;
    private volatile boolean active = true;
    private long nextFailureLog;
    private int suppressedFailures;

    ListenerInvoker(
            Object owner,
            Class<? extends Event> eventType,
            EventPriority priority,
            boolean receiveCancelled,
            MethodHandle handle
    ) {
        this.owner = owner;
        this.eventType = eventType;
        this.priority = priority;
        this.receiveCancelled = receiveCancelled;
        this.handle = handle;
    }

    Object owner() {
        return owner;
    }

    Class<? extends Event> eventType() {
        return eventType;
    }

    int priorityWeight() {
        return priority.weight();
    }

    synchronized int recordFailure(long now) {
        if (nextFailureLog != 0 && now - nextFailureLog < 0) {
            if (suppressedFailures < Integer.MAX_VALUE) suppressedFailures++;
            return -1;
        }
        int suppressed = suppressedFailures;
        suppressedFailures = 0;
        nextFailureLog = now + 10_000_000_000L;
        return suppressed;
    }

    void deactivate() {
        active = false;
    }

    void invoke(Event event) throws Throwable {
        if (!active) return;
        if (event instanceof CancellableEvent cancellable && cancellable.isCancelled() && !receiveCancelled) {
            return;
        }
        handle.invokeExact(event);
    }
}
