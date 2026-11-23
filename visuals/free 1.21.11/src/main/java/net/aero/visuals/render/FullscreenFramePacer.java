package net.aero.visuals.render;

import java.util.concurrent.locks.LockSupport;

/** Frame pacing without entering GLFW's native event-wait loop a second time per frame. */
public final class FullscreenFramePacer {
    private static long previousFrame;
    private static int previousLimit;
    private static boolean previousFocus,previousMinimized;

    private FullscreenFramePacer(){}

    public static void reset(){previousFrame=0;}

    public static void limit(int fps,boolean focused,boolean minimized){
        long now=System.nanoTime();
        if(fps<=0){reset();return;}
        // Native Minecraft caps minimized windows at 10 FPS; never accumulate frame debt.
        long period=Math.min(100_000_000L,1_000_000_000L/fps);
        boolean transition=previousFrame==0||fps!=previousLimit||focused!=previousFocus||minimized!=previousMinimized;
        previousLimit=fps;
        previousFocus=focused;
        previousMinimized=minimized;
        if(transition||now-previousFrame>=period||now<previousFrame){previousFrame=now;return;}
        long deadline=previousFrame+period;
        long remaining=deadline-now;
        // Window events are already polled by RenderSystem.flipFrame before and after presentation.
        // Small Java waits avoid nesting a native fullscreen event handler in the FPS limiter.
        while(remaining>0){
            if(Thread.currentThread().isInterrupted())break;
            LockSupport.parkNanos(Math.min(remaining,1_000_000L));
            remaining=deadline-System.nanoTime();
        }
        previousFrame=System.nanoTime();
    }
}
