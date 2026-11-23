package net.aero.visuals.render;

import net.aero.visuals.AeroVisuals;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Samples only stalled in-world frames; no file IO or stack capture on normal frames. */
public final class FrameStallMonitor {
    private static final long STALL_NANOS=2_000_000_000L;
    private static ScheduledExecutorService worker;
    private static volatile Thread renderThread;
    private static volatile long frameStarted;
    private static volatile boolean focused,minimized;
    private static volatile Class<?> screen;
    private static volatile long reportedFrame;
    private static long nextReport;
    private static boolean wasFocused=true;

    private FrameStallMonitor(){}

    public static void beginFrame(MinecraftClient client){
        frameStarted=0;
        boolean focus=client.isWindowFocused();
        if(focus&&!wasFocused&&client.getInactivityFpsLimiter()!=null)client.getInactivityFpsLimiter().onInput();
        wasFocused=focus;
        if(client.world==null||client.player==null||client.getOverlay()!=null)return;
        if(worker==null){
            worker=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"Aero-Frame-Diagnostics");thread.setDaemon(true);return thread;});
            worker.scheduleWithFixedDelay(FrameStallMonitor::inspect,500,500,TimeUnit.MILLISECONDS);
        }
        renderThread=Thread.currentThread();
        focused=focus;
        minimized=client.getWindow().isMinimized();
        screen=client.currentScreen==null?null:client.currentScreen.getClass();
        frameStarted=System.nanoTime();
    }

    public static void endFrame(){
        long started=frameStarted;
        frameStarted=0;
        long elapsed=System.nanoTime()-started;
        if(started!=0&&elapsed>=STALL_NANOS&&worker!=null&&reportedFrame!=started){
            // Also records a long stop-the-world pause that prevented the sampler from running.
            worker.execute(()->{
                long now=System.nanoTime();
                if(now<nextReport||reportedFrame==started)return;
                nextReport=now+30_000_000_000L;
                reportedFrame=started;
                AeroVisuals.LOGGER.warn("Aero slow frame finished after {} ms; no live stack was captured for this frame",elapsed/1_000_000L);
            });
        }
    }

    private static void inspect(){
        long started=frameStarted,now=System.nanoTime();
        var thread=renderThread;
        if(started==0||thread==null||now-started<STALL_NANOS||now<nextReport||reportedFrame==started)return;
        boolean hadFocus=focused,wasMinimized=minimized;
        var screenType=screen;
        var trace=thread.getStackTrace();
        if(frameStarted!=started)return;
        StringBuilder stack=new StringBuilder();
        for(int i=0;i<Math.min(48,trace.length);i++)stack.append("\n\tat ").append(trace[i]);
        nextReport=now+30_000_000_000L;
        reportedFrame=started;
        AeroVisuals.LOGGER.warn("Aero client frame stalled for {} ms (focusedAtFrameStart={}, minimizedAtFrameStart={}, screen={}, state={}). Render stack:{}",
                (now-started)/1_000_000L,hadFocus,wasMinimized,screenType==null?"game":screenType.getSimpleName(),thread.getState(),stack);
    }

    public static void close(){
        frameStarted=0;
        if(worker!=null){worker.shutdownNow();worker=null;}
        renderThread=null;
    }
}
