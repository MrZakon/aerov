package net.aero.visuals.guard;
/** Device access is granted only by the authenticated website response. */
public final class AeroGuard {
    private record Access(boolean verified,String message){}
    private static volatile Access access=new Access(false,"Войдите в Aero Launcher. Авторизация и проверка HWID выполняются автоматически.");
    private AeroGuard(){}
    public static void verifyOrHalt(){reset();}
    public static boolean allowed(){return access.verified;}
    public static String message(){return access.message;}
    public static void reset(){access=new Access(false,"Войдите в Aero Launcher. Авторизация и проверка HWID выполняются автоматически.");}
    public static void checking(){access=new Access(false,"Проверка авторизации, HWID и подписки при запуске…");}
    public static void verified(){access=new Access(true,"Авторизация, HWID и подписка подтверждены до перезапуска Minecraft");}
    public static void denied(String message){access=new Access(false,message);}
    public static void unavailable(){access=new Access(false,"Не удалось проверить HWID. Проверьте соединение и повторите.");}
}
