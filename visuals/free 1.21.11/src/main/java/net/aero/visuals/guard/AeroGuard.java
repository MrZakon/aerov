package net.aero.visuals.guard;
/** Free local features remain usable without a paid subscription or a network request. */
public final class AeroGuard {
    private AeroGuard() {}
    public static boolean allowed(){return true;}
    public static String message(){return "AeroVisuals Free";}
    public static void verifyOrHalt(){} public static void reset(){} public static void checking(){}
    public static void verified(){} public static void denied(String message){} public static void unavailable(){}
}
