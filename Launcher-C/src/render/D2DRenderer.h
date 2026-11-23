#pragma once
#include <d2d1.h>
#include <dwrite.h>
#include <wincodec.h>
#include <string>
#include <memory>
#include <unordered_map>
#include "Theme.h"

namespace aero {

enum class FontStyle {
    Regular,
    Bold,
    Mono
};

class D2DRenderer {
public:
    D2DRenderer();
    ~D2DRenderer();

    bool Initialize(HWND hwnd);
    void Cleanup();
    void Resize(UINT width, UINT height);
    void SetDpi(float dpi);

    void BeginDraw();
    bool EndDraw();

    ID2D1HwndRenderTarget* GetRenderTarget() const { return renderTarget_; }
    ID2D1SolidColorBrush* GetSolidBrush() const { return solidBrush_; }

    // Primitives
    void Clear(const D2D1_COLOR_F& color);
    void DrawLine(const D2D1_POINT_2F& p1, const D2D1_POINT_2F& p2, const D2D1_COLOR_F& color, float strokeWidth = 1.0f);
    void DrawRect(const D2D1_RECT_F& rect, const D2D1_COLOR_F& color, float strokeWidth = 1.0f);
    void FillRect(const D2D1_RECT_F& rect, const D2D1_COLOR_F& color);
    void DrawRoundedRect(const D2D1_RECT_F& rect, float radius, const D2D1_COLOR_F& color, float strokeWidth = 1.0f);
    void FillRoundedRect(const D2D1_RECT_F& rect, float radius, const D2D1_COLOR_F& color);
    void FillGradientRoundedRect(const D2D1_RECT_F& rect, float radius, const D2D1_COLOR_F& startColor, const D2D1_COLOR_F& endColor, bool vertical = false);

    // Text
    void DrawString(const std::wstring& text, const D2D1_RECT_F& rect, const D2D1_COLOR_F& color, float fontSize = 13.0f, FontStyle style = FontStyle::Regular, DWRITE_TEXT_ALIGNMENT align = DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT valign = DWRITE_PARAGRAPH_ALIGNMENT_NEAR);
    D2D1_SIZE_F MeasureString(const std::wstring& text, float fontSize = 13.0f, FontStyle style = FontStyle::Regular, float maxWidth = 10000.0f);

    // Images
    ID2D1Bitmap* LoadBitmapFromFile(const std::wstring& filePath);
    ID2D1Bitmap* LoadBitmapFromMemory(const void* data, size_t size);
    void DrawBitmap(ID2D1Bitmap* bitmap, const D2D1_RECT_F& destRect, float opacity = 1.0f);
    bool DrawResourceCover(int resourceId, const D2D1_RECT_F& rect, float horizontalAnchor = 0.5f);

    // Clipping
    void PushClip(const D2D1_RECT_F& rect);
    void PopClip();

    // Layers & Opacity
    void PushLayer(float opacity);
    void PopLayer();

    // Icons
    void DrawIcon(const std::string& iconName, const D2D1_RECT_F& rect, const D2D1_COLOR_F& color, float strokeWidth = 1.6f);

    // Ambient glow & background
    void DrawBackground(float width, float height, float time);

    IDWriteTextFormat* GetTextFormat(float fontSize, FontStyle style);

private:
    HWND hwnd_ = nullptr;
    float dpi_ = 96;
    ID2D1Factory* d2dFactory_ = nullptr;
    IDWriteFactory* dwriteFactory_ = nullptr;
    IWICImagingFactory* wicFactory_ = nullptr;
    ID2D1HwndRenderTarget* renderTarget_ = nullptr;
    ID2D1SolidColorBrush* solidBrush_ = nullptr;

    std::unordered_map<std::wstring, ID2D1Bitmap*> bitmapCache_;
    std::unordered_map<std::string, IDWriteTextFormat*> textFormatCache_;
};

} // namespace aero
