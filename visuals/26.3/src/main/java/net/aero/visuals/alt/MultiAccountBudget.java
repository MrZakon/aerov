package net.aero.visuals.alt;

import java.lang.management.ManagementFactory;

/** Shared limits for hidden Minecraft processes and their streams. */
final class MultiAccountBudget {
    static final int DEFAULT_HEAP_MB = 1280;
    static final int MIN_HEAP_MB = 768;
    static final int MAX_HEAP_MB = 4096;
    static final int NATIVE_ALLOWANCE_MB = 512;
    static final int LOW_MEMORY_MB = 768;
    static final int MAIN_RESERVE_MB = LOW_MEMORY_MB + 256;
    static final int IDLE_FPS = 20;
    static final int CONTROL_FPS = 60;
    static final int UNLIMITED_FPS = 260;

    private MultiAccountBudget() {}

    static long freeMemoryMb() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                long bytes = os.getFreeMemorySize();
                if (bytes >= 0) return bytes / (1024 * 1024);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A custom Java runtime may omit operating system memory metrics.
        }
        return -1;
    }
}
