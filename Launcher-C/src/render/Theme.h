#pragma once
#include <d2d1.h>
#include <string>

namespace aero {

struct Color {
    static constexpr D2D1_COLOR_F FromRgba(unsigned char r, unsigned char g, unsigned char b, float a = 1.0f) {
        return D2D1_COLOR_F{ r / 255.0f, g / 255.0f, b / 255.0f, a };
    }

    static constexpr D2D1_COLOR_F FromHex(unsigned int hex, float a = 1.0f) {
        return D2D1_COLOR_F{
            ((hex >> 16) & 0xFF) / 255.0f,
            ((hex >> 8) & 0xFF) / 255.0f,
            (hex & 0xFF) / 255.0f,
            a
        };
    }
};

enum class ThemeKind {
    Emerald,
    Violet,
    Cyan,
    Amber
};

struct Theme {
    ThemeKind kind = ThemeKind::Emerald;

    D2D1_COLOR_F bg         = Color::FromHex(0x171918);
    D2D1_COLOR_F surface    = Color::FromHex(0x202320);
    D2D1_COLOR_F panel      = Color::FromHex(0x292e29);
    D2D1_COLOR_F panel2     = Color::FromHex(0x353c33);
    D2D1_COLOR_F line       = Color::FromRgba(185, 194, 171, 0.16f);
    D2D1_COLOR_F lineHi     = Color::FromRgba(240, 183, 130, 0.48f);
    D2D1_COLOR_F text       = Color::FromHex(0xf4f1e8);
    D2D1_COLOR_F muted      = Color::FromHex(0xa1a89c);
    D2D1_COLOR_F red        = Color::FromHex(0xfb7185);
    D2D1_COLOR_F amber      = Color::FromHex(0xfbbf24);

    // Accent
    D2D1_COLOR_F accent1    = Color::FromHex(0xf0b782); // Violet
    D2D1_COLOR_F accent2    = Color::FromHex(0xdba16c); // Cyan / End of gradient
    D2D1_COLOR_F mint       = Color::FromHex(0xb2c78e); // Online mint

    void Apply(ThemeKind newKind) {
        kind = newKind;
        switch (newKind) {
        case ThemeKind::Emerald:
            accent1 = Color::FromHex(0xf0b782);
            accent2 = Color::FromHex(0xdba16c);
            mint    = Color::FromHex(0xb2c78e);
            lineHi  = Color::FromRgba(240, 183, 130, 0.48f);
            break;
        case ThemeKind::Violet:
            accent1 = Color::FromHex(0xc084fc);
            accent2 = Color::FromHex(0xf472b6);
            mint    = Color::FromHex(0xe9b5ff);
            lineHi  = Color::FromRgba(216, 180, 254, 0.58f);
            break;
        case ThemeKind::Cyan:
            accent1 = Color::FromHex(0x67e8f9);
            accent2 = Color::FromHex(0x38bdf8);
            mint    = Color::FromHex(0x67e8f9);
            lineHi  = Color::FromRgba(103, 232, 249, 0.62f);
            break;
        case ThemeKind::Amber:
            accent1 = Color::FromHex(0xfbbf24);
            accent2 = Color::FromHex(0xfb7185);
            mint    = Color::FromHex(0xfacc6a);
            lineHi  = Color::FromRgba(251, 191, 36, 0.62f);
            break;
        }
    }

    static Theme& Instance() {
        static Theme s_instance;
        return s_instance;
    }
};

} // namespace aero
