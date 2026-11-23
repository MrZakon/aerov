package net.aero.visuals.render;

import net.aero.visuals.AeroVisuals;
import net.minecraft.client.Minecraft;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Samples only stalled in-world frames; no file IO or stack capture on normal frames. */
public final class FrameStallMonitor {
    private static final long STALL_NANOS=2_000_000_000L;
    private static ScheduledThreadPoolExecutor worker;
    private static ScheduledFuture<?> sampler;
    private static volatile Thread renderThread;
    private static volatile long frameStarted;
    private static volatile Class<?> screen;
    private static volatile long reportedFrame;
    private static final long REPORT_INTERVAL_NANOS = 30_000_000_000L;
    private static volatile long lastReport;
    private static final AtomicReference<SlowFrame> completed = new AtomicReference<>();

    private record SlowFrame(long started, long elapsed) {}
    private static boolean wasFocused=true;

    private FrameStallMonitor(){}

    public static void beginFrame(Minecraft client){
        frameStarted=0;
        boolean focus=client.isWindowActive();
        if(focus&&!wasFocused&&client.getFramerateLimitTracker()!=null)client.getFramerateLimitTracker().onInputReceived();
        wasFocused=focus;
        if (!focus || client.getWindow().isIconified() || client.level==null || client.player==null || client.getOverlay()!=null) {
            suspendSampling();
            return;
        }
        if (sampler == null) {
            if (worker == null) {
                worker = new ScheduledThreadPoolExecutor(1, task -> {
                    var thread = new Thread(task, "Aero-Frame-Diagnostics");
                    thread.setDaemon(true);
                    return thread;
                });
                worker.setRemoveOnCancelPolicy(true);
            }
            sampler = worker.scheduleWithFixedDelay(FrameStallMonitor::inspect, 500, 500, TimeUnit.MILLISECONDS);
        }
        renderThread = Thread.currentThread();
        screen=client.screen==null?null:client.screen.getClass();
        frameStarted=System.nanoTime();
    }

    public static void endFrame() {
        long started = frameStarted;
        frameStarted = 0;
        if (started == 0 || worker == null || reportedFrame == started) return;
        long now = System.nanoTime();
        long elapsed = now - started;
        if (elapsed >= STALL_NANOS && !reportThrottled(now) && completed.get() == null) {
            // Keep one sample even if a stop-the-world pause prevented live inspection.
            completed.set(new SlowFrame(started, elapsed));
        }
    }

    private static void reportCompleted(long now) {
        SlowFrame frame = completed.getAndSet(null);
        if (frame == null || reportedFrame == frame.started() || reportThrottled(now)) return;
        lastReport = now;
        reportedFrame = frame.started();
        AeroVisuals.LOGGER.warn("Aero slow frame finished after {} ms; no live stack was captured for this frame",
                frame.elapsed() / 1_000_000L);
    }

    private static void inspect(){
        long now = System.nanoTime();
        reportCompleted(now);
        long started = frameStarted;
        var thread=renderThread;
        if(started==0||thread==null||now-started<STALL_NANOS||reportThrottled(now)||reportedFrame==started)return;
        var screenType=screen;
        var trace=thread.getStackTrace();
        if(frameStarted!=started)return;
        StringBuilder stack=new StringBuilder();
        for(int i=0;i<Math.min(48,trace.length);i++)stack.append("\n\tat ").append(trace[i]);
        lastReport = now;
        reportedFrame=started;
        AeroVisuals.LOGGER.warn("Aero client frame stalled for {} ms (screen={}, state={}). Render stack:{}",
                (now-started)/1_000_000L,screenType==null?"game":screenType.getSimpleName(),thread.getState(),stack);
    }

    private static boolean reportThrottled(long now) {
        return lastReport != 0 && now - lastReport < REPORT_INTERVAL_NANOS;
    }

    private static void suspendSampling() {
        if (sampler == null) return;
        sampler.cancel(false);
        sampler = null;
        renderThread = null;
        screen = null;
        if (completed.get() != null) {
            // Preserve a completed slow frame from before the transition.
            worker.execute(() -> reportCompleted(System.nanoTime()));
        }
    }

    public static void close() {
        frameStarted = 0;
        if (worker != null) worker.shutdownNow();
        worker = null;
        sampler = null;
        renderThread = null;
        screen = null;
        completed.set(null);
    }
}
