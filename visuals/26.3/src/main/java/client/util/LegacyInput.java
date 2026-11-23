package client.util;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLMouse;
/** Converts native SDL input to the stable key codes stored in Aero configs. */
public final class LegacyInput {
    public static final int GLFW_PRESS=1,GLFW_RELEASE=0,GLFW_MOUSE_BUTTON_LEFT=0;
    public static final int GLFW_KEY_ESCAPE=256,GLFW_KEY_ENTER=257,GLFW_KEY_KP_ENTER=335,GLFW_KEY_BACKSPACE=259,GLFW_KEY_DELETE=261,GLFW_KEY_F5=294,GLFW_KEY_LEFT_SHIFT=340,GLFW_KEY_RIGHT_SHIFT=344;
    public static final int GLFW_KEY_SPACE=32,GLFW_KEY_F9=298,GLFW_KEY_F10=299;
    private static final int[] SDL=new int[512],LEGACY=new int[512];
    static {java.util.Arrays.fill(SDL,-1);java.util.Arrays.fill(LEGACY,-1);
        map(32,InputConstants.KEY_SPACE);
        map(39,InputConstants.KEY_APOSTROPHE);
        map(44,InputConstants.KEY_COMMA);
        map(45,InputConstants.KEY_MINUS);
        map(46,InputConstants.KEY_PERIOD);
        map(47,InputConstants.KEY_SLASH);
        map(48,InputConstants.KEY_0);
        map(49,InputConstants.KEY_1);
        map(50,InputConstants.KEY_2);
        map(51,InputConstants.KEY_3);
        map(52,InputConstants.KEY_4);
        map(53,InputConstants.KEY_5);
        map(54,InputConstants.KEY_6);
        map(55,InputConstants.KEY_7);
        map(56,InputConstants.KEY_8);
        map(57,InputConstants.KEY_9);
        map(59,InputConstants.KEY_SEMICOLON);
        map(61,InputConstants.KEY_EQUALS);
        map(65,InputConstants.KEY_A);
        map(66,InputConstants.KEY_B);
        map(67,InputConstants.KEY_C);
        map(68,InputConstants.KEY_D);
        map(69,InputConstants.KEY_E);
        map(70,InputConstants.KEY_F);
        map(71,InputConstants.KEY_G);
        map(72,InputConstants.KEY_H);
        map(73,InputConstants.KEY_I);
        map(74,InputConstants.KEY_J);
        map(75,InputConstants.KEY_K);
        map(76,InputConstants.KEY_L);
        map(77,InputConstants.KEY_M);
        map(78,InputConstants.KEY_N);
        map(79,InputConstants.KEY_O);
        map(80,InputConstants.KEY_P);
        map(81,InputConstants.KEY_Q);
        map(82,InputConstants.KEY_R);
        map(83,InputConstants.KEY_S);
        map(84,InputConstants.KEY_T);
        map(85,InputConstants.KEY_U);
        map(86,InputConstants.KEY_V);
        map(87,InputConstants.KEY_W);
        map(88,InputConstants.KEY_X);
        map(89,InputConstants.KEY_Y);
        map(90,InputConstants.KEY_Z);
        map(91,InputConstants.KEY_LBRACKET);
        map(92,InputConstants.KEY_BACKSLASH);
        map(93,InputConstants.KEY_RBRACKET);
        map(96,InputConstants.KEY_GRAVE);
        map(256,InputConstants.KEY_ESCAPE);
        map(257,InputConstants.KEY_RETURN);
        map(258,InputConstants.KEY_TAB);
        map(259,InputConstants.KEY_BACKSPACE);
        map(260,InputConstants.KEY_INSERT);
        map(261,InputConstants.KEY_DELETE);
        map(262,InputConstants.KEY_RIGHT);
        map(263,InputConstants.KEY_LEFT);
        map(264,InputConstants.KEY_DOWN);
        map(265,InputConstants.KEY_UP);
        map(266,InputConstants.KEY_PAGEUP);
        map(267,InputConstants.KEY_PAGEDOWN);
        map(268,InputConstants.KEY_HOME);
        map(269,InputConstants.KEY_END);
        map(280,InputConstants.KEY_CAPSLOCK);
        map(281,InputConstants.KEY_SCROLLLOCK);
        map(282,InputConstants.KEY_NUMLOCK);
        map(283,InputConstants.KEY_PRINTSCREEN);
        map(284,InputConstants.KEY_PAUSE);
        map(290,InputConstants.KEY_F1);
        map(291,InputConstants.KEY_F2);
        map(292,InputConstants.KEY_F3);
        map(293,InputConstants.KEY_F4);
        map(294,InputConstants.KEY_F5);
        map(295,InputConstants.KEY_F6);
        map(296,InputConstants.KEY_F7);
        map(297,InputConstants.KEY_F8);
        map(298,InputConstants.KEY_F9);
        map(299,InputConstants.KEY_F10);
        map(300,InputConstants.KEY_F11);
        map(301,InputConstants.KEY_F12);
        map(302,InputConstants.KEY_F13);
        map(303,InputConstants.KEY_F14);
        map(304,InputConstants.KEY_F15);
        map(305,InputConstants.KEY_F16);
        map(306,InputConstants.KEY_F17);
        map(307,InputConstants.KEY_F18);
        map(308,InputConstants.KEY_F19);
        map(309,InputConstants.KEY_F20);
        map(310,InputConstants.KEY_F21);
        map(311,InputConstants.KEY_F22);
        map(312,InputConstants.KEY_F23);
        map(313,InputConstants.KEY_F24);
        map(320,InputConstants.KEY_NUMPAD0);
        map(321,InputConstants.KEY_NUMPAD1);
        map(322,InputConstants.KEY_NUMPAD2);
        map(323,InputConstants.KEY_NUMPAD3);
        map(324,InputConstants.KEY_NUMPAD4);
        map(325,InputConstants.KEY_NUMPAD5);
        map(326,InputConstants.KEY_NUMPAD6);
        map(327,InputConstants.KEY_NUMPAD7);
        map(328,InputConstants.KEY_NUMPAD8);
        map(329,InputConstants.KEY_NUMPAD9);
        map(330,99);
        map(331,84);
        map(332,InputConstants.KEY_MULTIPLY);
        map(333,86);
        map(334,InputConstants.KEY_ADD);
        map(335,InputConstants.KEY_NUMPADENTER);
        map(336,InputConstants.KEY_NUMPADEQUALS);
        map(340,InputConstants.KEY_LSHIFT);
        map(341,InputConstants.KEY_LCONTROL);
        map(342,InputConstants.KEY_LALT);
        map(343,InputConstants.KEY_LGUI);
        map(344,InputConstants.KEY_RSHIFT);
        map(345,InputConstants.KEY_RCONTROL);
        map(346,InputConstants.KEY_RALT);
        map(347,InputConstants.KEY_RGUI);
    }
    private LegacyInput(){}
    private static void map(int old,int scan){SDL[old]=scan;LEGACY[scan]=old;}
    public static int key(KeyEvent event){return key(event.key());}
    public static int key(int scan){return scan>=0&&scan<LEGACY.length?LEGACY[scan]:-1;}
    public static int modifiers(int sdl) {
        int glfw = 0;
        if ((sdl & 0x0003) != 0) glfw |= 0x01;
        if ((sdl & 0x00C0) != 0) glfw |= 0x02;
        if ((sdl & 0x0300) != 0) glfw |= 0x04;
        if ((sdl & 0x0C00) != 0) glfw |= 0x08;
        if ((sdl & 0x2000) != 0) glfw |= 0x10;
        if ((sdl & 0x1000) != 0) glfw |= 0x20;
        return glfw;
    }
    public static int button(int sdl){return switch(sdl){case 1->0;case 3->1;case 2->2;default->sdl-1;};}
    public static boolean isKeyDown(com.mojang.blaze3d.platform.Window window,int key){return glfwGetKey(window.handle(),key)==1;}
    public static int glfwGetKey(long window,int key){int scan=key>=0&&key<SDL.length?SDL[key]:-1;return scan>=0&&InputConstants.isKeyDown(scan)?1:0;}
    public static int glfwGetMouseButton(long window,int old){int sdl=switch(old){case 0->1;case 1->3;case 2->2;default->old+1;};return (SDLMouse.SDL_GetMouseState((java.nio.FloatBuffer)null,null)&(1<<(sdl-1)))!=0?1:0;}
    public static long glfwGetCurrentContext(){var mc=Minecraft.getInstance();return mc!=null&&mc.getWindow()!=null?mc.getWindow().handle():0L;}
}