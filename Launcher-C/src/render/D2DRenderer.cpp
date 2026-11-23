#include "D2DRenderer.h"
#include <cmath>
#include <sstream>
#include <algorithm>

#pragma comment(lib, "d2d1.lib")
#pragma comment(lib, "dwrite.lib")
#pragma comment(lib, "windowscodecs.lib")

namespace aero {

D2DRenderer::D2DRenderer() = default;

D2DRenderer::~D2DRenderer() {
    Cleanup();
}

bool D2DRenderer::Initialize(HWND hwnd) {
    hwnd_ = hwnd;
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);

    HRESULT hr = D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, &d2dFactory_);
    if (FAILED(hr)) return false;

    hr = DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory), reinterpret_cast<IUnknown**>(&dwriteFactory_));
    if (FAILED(hr)) return false;

    hr = CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&wicFactory_));
    if (FAILED(hr)) return false;

    RECT rc;
    GetClientRect(hwnd_, &rc);
    D2D1_SIZE_U size = D2D1::SizeU(rc.right - rc.left, rc.bottom - rc.top);

    D2D1_RENDER_TARGET_PROPERTIES rtProps = D2D1::RenderTargetProperties(
        D2D1_RENDER_TARGET_TYPE_DEFAULT,
        D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED)
    );

    D2D1_HWND_RENDER_TARGET_PROPERTIES hwndProps = D2D1::HwndRenderTargetProperties(
        hwnd_,
        size,
        D2D1_PRESENT_OPTIONS_IMMEDIATELY
    );

    hr = d2dFactory_->CreateHwndRenderTarget(rtProps, hwndProps, &renderTarget_);
    if (FAILED(hr)) return false;

    hr = renderTarget_->CreateSolidColorBrush(D2D1::ColorF(D2D1::ColorF::White), &solidBrush_);
    return SUCCEEDED(hr);
}

void D2DRenderer::Cleanup() {
    for (auto& [_, fmt] : textFormatCache_) {
        if (fmt) fmt->Release();
    }
    textFormatCache_.clear();

    for (auto& [_, bmp] : bitmapCache_) {
        if (bmp) bmp->Release();
    }
    bitmapCache_.clear();

    if (solidBrush_) { solidBrush_->Release(); solidBrush_ = nullptr; }
    if (renderTarget_) { renderTarget_->Release(); renderTarget_ = nullptr; }
    if (wicFactory_) { wicFactory_->Release(); wicFactory_ = nullptr; }
    if (dwriteFactory_) { dwriteFactory_->Release(); dwriteFactory_ = nullptr; }
    if (d2dFactory_) { d2dFactory_->Release(); d2dFactory_ = nullptr; }
}

void D2DRenderer::Resize(UINT width, UINT height) {
    if (renderTarget_) {
        renderTarget_->Resize(D2D1::SizeU(width, height));
    }
}

void D2DRenderer::SetDpi(float dpi) {
    dpi_ = dpi;
    if (renderTarget_) renderTarget_->SetDpi(dpi_, dpi_);
}

void D2DRenderer::BeginDraw() {
    if (renderTarget_) {
        renderTarget_->BeginDraw();
    }
}

bool D2DRenderer::EndDraw() {
    if (!renderTarget_) return false;
    HRESULT hr = renderTarget_->EndDraw();
    if (hr == D2DERR_RECREATE_TARGET) {
        for (auto& [_, bitmap] : bitmapCache_) if (bitmap) bitmap->Release();
        bitmapCache_.clear();
        if (solidBrush_) { solidBrush_->Release(); solidBrush_ = nullptr; }
        if (renderTarget_) { renderTarget_->Release(); renderTarget_ = nullptr; }
        RECT rc;
        GetClientRect(hwnd_, &rc);
        D2D1_SIZE_U size = D2D1::SizeU(rc.right - rc.left, rc.bottom - rc.top);
        d2dFactory_->CreateHwndRenderTarget(
            D2D1::RenderTargetProperties(D2D1_RENDER_TARGET_TYPE_DEFAULT, D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED)),
            D2D1::HwndRenderTargetProperties(hwnd_, size, D2D1_PRESENT_OPTIONS_IMMEDIATELY),
            &renderTarget_
        );
        if (renderTarget_) {
            renderTarget_->SetDpi(dpi_, dpi_);
            renderTarget_->CreateSolidColorBrush(D2D1::ColorF(D2D1::ColorF::White), &solidBrush_);
        }
        return false;
    }
    return SUCCEEDED(hr);
}

void D2DRenderer::Clear(const D2D1_COLOR_F& color) {
    if (renderTarget_) {
        renderTarget_->Clear(color);
    }
}

void D2DRenderer::DrawLine(const D2D1_POINT_2F& p1, const D2D1_POINT_2F& p2, const D2D1_COLOR_F& color, float strokeWidth) {
    if (!renderTarget_ || !solidBrush_) return;
    solidBrush_->SetColor(color);
    renderTarget_->DrawLine(p1, p2, solidBrush_, strokeWidth);
}

void D2DRenderer::DrawRect(const D2D1_RECT_F& rect, const D2D1_COLOR_F& color, float strokeWidth) {
    if (!renderTarget_ || !solidBrush_) return;
    solidBrush_->SetColor(color);
    renderTarget_->DrawRectangle(rect, solidBrush_, strokeWidth);
}

void D2DRenderer::FillRect(const D2D1_RECT_F& rect, const D2D1_COLOR_F& color) {
    if (!renderTarget_ || !solidBrush_) return;
    solidBrush_->SetColor(color);
    renderTarget_->FillRectangle(rect, solidBrush_);
}

void D2DRenderer::DrawRoundedRect(const D2D1_RECT_F& rect, float radius, const D2D1_COLOR_F& color, float strokeWidth) {
    if (!renderTarget_ || !solidBrush_) return;
    solidBrush_->SetColor(color);
    D2D1_ROUNDED_RECT rrect = D2D1::RoundedRect(rect, radius, radius);
    renderTarget_->DrawRoundedRectangle(rrect, solidBrush_, strokeWidth);
}

void D2DRenderer::FillRoundedRect(const D2D1_RECT_F& rect, float radius, const D2D1_COLOR_F& color) {
    if (!renderTarget_ || !solidBrush_) return;
    solidBrush_->SetColor(color);
    D2D1_ROUNDED_RECT rrect = D2D1::RoundedRect(rect, radius, radius);
    renderTarget_->FillRoundedRectangle(rrect, solidBrush_);
}

void D2DRenderer::FillGradientRoundedRect(const D2D1_RECT_F& rect, float radius, const D2D1_COLOR_F& startColor, const D2D1_COLOR_F& endColor, bool vertical) {
    if (!renderTarget_) return;

    D2D1_GRADIENT_STOP stops[2];
    stops[0].color = startColor;
    stops[0].position = 0.0f;
    stops[1].color = endColor;
    stops[1].position = 1.0f;

    ID2D1GradientStopCollection* pStops = nullptr;
    HRESULT hr = renderTarget_->CreateGradientStopCollection(stops, 2, &pStops);
    if (FAILED(hr)) return;

    D2D1_POINT_2F p1 = D2D1::Point2F(rect.left, rect.top);
    D2D1_POINT_2F p2 = vertical ? D2D1::Point2F(rect.left, rect.bottom) : D2D1::Point2F(rect.right, rect.bottom);

    ID2D1LinearGradientBrush* pBrush = nullptr;
    hr = renderTarget_->CreateLinearGradientBrush(
        D2D1::LinearGradientBrushProperties(p1, p2),
        pStops,
        &pBrush
    );
    pStops->Release();

    if (SUCCEEDED(hr)) {
        D2D1_ROUNDED_RECT rrect = D2D1::RoundedRect(rect, radius, radius);
        renderTarget_->FillRoundedRectangle(rrect, pBrush);
        pBrush->Release();
    }
}

IDWriteTextFormat* D2DRenderer::GetTextFormat(float fontSize, FontStyle style) {
    if (!dwriteFactory_) return nullptr;

    std::ostringstream ss;
    ss << fontSize << "_" << static_cast<int>(style);
    std::string key = ss.str();

    auto it = textFormatCache_.find(key);
    if (it != textFormatCache_.end()) {
        return it->second;
    }

    const wchar_t* fontFamily = L"Segoe UI";
    DWRITE_FONT_WEIGHT weight = DWRITE_FONT_WEIGHT_NORMAL;

    if (style == FontStyle::Bold) {
        weight = DWRITE_FONT_WEIGHT_SEMI_BOLD;
    } else if (style == FontStyle::Mono) {
        fontFamily = L"Consolas";
    }

    IDWriteTextFormat* format = nullptr;
    HRESULT hr = dwriteFactory_->CreateTextFormat(
        fontFamily,
        nullptr,
        weight,
        DWRITE_FONT_STYLE_NORMAL,
        DWRITE_FONT_STRETCH_NORMAL,
        fontSize,
        L"ru-RU",
        &format
    );

    if (SUCCEEDED(hr)) {
        textFormatCache_[key] = format;
        return format;
    }
    return nullptr;
}

void D2DRenderer::DrawString(const std::wstring& text, const D2D1_RECT_F& rect, const D2D1_COLOR_F& color, float fontSize, FontStyle style, DWRITE_TEXT_ALIGNMENT align, DWRITE_PARAGRAPH_ALIGNMENT valign) {
    if (!renderTarget_ || !solidBrush_ || text.empty()) return;

    IDWriteTextFormat* format = GetTextFormat(fontSize, style);
    if (!format) return;

    format->SetTextAlignment(align);
    format->SetParagraphAlignment(valign);

    solidBrush_->SetColor(color);
    renderTarget_->DrawText(text.c_str(), static_cast<UINT32>(text.length()), format, rect, solidBrush_);
}

D2D1_SIZE_F D2DRenderer::MeasureString(const std::wstring& text, float fontSize, FontStyle style, float maxWidth) {
    if (!dwriteFactory_ || text.empty()) return D2D1::SizeF(0, 0);

    IDWriteTextFormat* format = GetTextFormat(fontSize, style);
    if (!format) return D2D1::SizeF(0, 0);

    IDWriteTextLayout* layout = nullptr;
    HRESULT hr = dwriteFactory_->CreateTextLayout(
        text.c_str(),
        static_cast<UINT32>(text.length()),
        format,
        maxWidth,
        10000.0f,
        &layout
    );

    if (SUCCEEDED(hr)) {
        DWRITE_TEXT_METRICS metrics;
        layout->GetMetrics(&metrics);
        layout->Release();
        return D2D1::SizeF(metrics.widthIncludingTrailingWhitespace, metrics.height);
    }
    return D2D1::SizeF(0, 0);
}

ID2D1Bitmap* D2DRenderer::LoadBitmapFromFile(const std::wstring& filePath) {
    if (!wicFactory_ || !renderTarget_) return nullptr;

    auto it = bitmapCache_.find(filePath);
    if (it != bitmapCache_.end()) {
        return it->second;
    }

    IWICBitmapDecoder* decoder = nullptr;
    HRESULT hr = wicFactory_->CreateDecoderFromFilename(
        filePath.c_str(),
        nullptr,
        GENERIC_READ,
        WICDecodeMetadataCacheOnLoad,
        &decoder
    );
    if (FAILED(hr)) return nullptr;

    IWICBitmapFrameDecode* frame = nullptr;
    hr = decoder->GetFrame(0, &frame);
    decoder->Release();
    if (FAILED(hr)) return nullptr;

    IWICFormatConverter* converter = nullptr;
    hr = wicFactory_->CreateFormatConverter(&converter);
    if (FAILED(hr)) { frame->Release(); return nullptr; }

    hr = converter->Initialize(
        frame,
        GUID_WICPixelFormat32bppPBGRA,
        WICBitmapDitherTypeNone,
        nullptr,
        0.0f,
        WICBitmapPaletteTypeMedianCut
    );
    frame->Release();
    if (FAILED(hr)) { converter->Release(); return nullptr; }

    ID2D1Bitmap* bitmap = nullptr;
    hr = renderTarget_->CreateBitmapFromWicBitmap(converter, nullptr, &bitmap);
    converter->Release();

    if (SUCCEEDED(hr)) {
        bitmapCache_[filePath] = bitmap;
        return bitmap;
    }
    return nullptr;
}

ID2D1Bitmap* D2DRenderer::LoadBitmapFromMemory(const void* data, size_t size) {
    if (!wicFactory_ || !renderTarget_ || !data || size == 0) return nullptr;

    IWICStream* stream = nullptr;
    HRESULT hr = wicFactory_->CreateStream(&stream);
    if (FAILED(hr)) return nullptr;

    hr = stream->InitializeFromMemory(reinterpret_cast<BYTE*>(const_cast<void*>(data)), static_cast<DWORD>(size));
    if (FAILED(hr)) { stream->Release(); return nullptr; }

    IWICBitmapDecoder* decoder = nullptr;
    hr = wicFactory_->CreateDecoderFromStream(stream, nullptr, WICDecodeMetadataCacheOnLoad, &decoder);
    stream->Release();
    if (FAILED(hr)) return nullptr;

    IWICBitmapFrameDecode* frame = nullptr;
    hr = decoder->GetFrame(0, &frame);
    decoder->Release();
    if (FAILED(hr)) return nullptr;

    IWICFormatConverter* converter = nullptr;
    hr = wicFactory_->CreateFormatConverter(&converter);
    if (FAILED(hr)) { frame->Release(); return nullptr; }

    hr = converter->Initialize(
        frame,
        GUID_WICPixelFormat32bppPBGRA,
        WICBitmapDitherTypeNone,
        nullptr,
        0.0f,
        WICBitmapPaletteTypeMedianCut
    );
    frame->Release();
    if (FAILED(hr)) { converter->Release(); return nullptr; }

    ID2D1Bitmap* bitmap = nullptr;
    hr = renderTarget_->CreateBitmapFromWicBitmap(converter, nullptr, &bitmap);
    converter->Release();

    return SUCCEEDED(hr) ? bitmap : nullptr;
}

void D2DRenderer::DrawBitmap(ID2D1Bitmap* bitmap, const D2D1_RECT_F& destRect, float opacity) {
    if (!renderTarget_ || !bitmap) return;
    renderTarget_->DrawBitmap(bitmap, destRect, opacity, D2D1_BITMAP_INTERPOLATION_MODE_LINEAR);
}

bool D2DRenderer::DrawResourceCover(int resourceId, const D2D1_RECT_F& rect, float horizontalAnchor) {
    if (!renderTarget_ || rect.right <= rect.left || rect.bottom <= rect.top) return false;
    const auto key = L"resource:" + std::to_wstring(resourceId);
    auto it = bitmapCache_.find(key);
    if (it == bitmapCache_.end()) {
        const auto module = GetModuleHandleW(nullptr);
        const auto resource = FindResourceW(module, MAKEINTRESOURCEW(resourceId), RT_RCDATA);
        if (!resource) return false;
        const auto handle = LoadResource(module, resource);
        auto* bitmap = LoadBitmapFromMemory(LockResource(handle), SizeofResource(module, resource));
        if (!bitmap) return false;
        it = bitmapCache_.emplace(key, bitmap).first;
    }
    const auto size = it->second->GetSize();
    const float width = rect.right - rect.left;
    const float height = rect.bottom - rect.top;
    const float scale = (std::max)(width / size.width, height / size.height);
    const float left = (size.width - width / scale) * horizontalAnchor;
    const float top = (size.height - height / scale) * 0.5f;
    const auto crop = D2D1::RectF(left, top, left + width / scale, top + height / scale);
    renderTarget_->DrawBitmap(it->second, rect, 1, D2D1_BITMAP_INTERPOLATION_MODE_LINEAR, crop);
    return true;
}

void D2DRenderer::PushClip(const D2D1_RECT_F& rect) {
    if (renderTarget_) {
        renderTarget_->PushAxisAlignedClip(rect, D2D1_ANTIALIAS_MODE_PER_PRIMITIVE);
    }
}

void D2DRenderer::PopClip() {
    if (renderTarget_) {
        renderTarget_->PopAxisAlignedClip();
    }
}

void D2DRenderer::PushLayer(float opacity) {
    if (!renderTarget_) return;
    renderTarget_->PushLayer(
        D2D1::LayerParameters(
            D2D1::InfiniteRect(),
            nullptr,
            D2D1_ANTIALIAS_MODE_PER_PRIMITIVE,
            D2D1::IdentityMatrix(),
            opacity
        ),
        nullptr
    );
}

void D2DRenderer::PopLayer() {
    if (!renderTarget_) return;
    renderTarget_->PopLayer();
}

void D2DRenderer::DrawBackground(float width, float height, float /*time*/) {
    if (!renderTarget_) return;

    // Dark canvas background
    auto& theme = Theme::Instance();
    Clear(theme.bg);

    // Quiet atmospheric canvas, shared by every screen.
    FillGradientRoundedRect(D2D1::RectF(0, 0, width, height), 0.0f,
        Color::FromHex(0x242923), theme.bg, true);
    DrawLine(D2D1::Point2F(0, 0), D2D1::Point2F(width, 0), theme.line);
}
void D2DRenderer::DrawIcon(const std::string& iconName, const D2D1_RECT_F& rect, const D2D1_COLOR_F& color, float strokeWidth) {
    if (!renderTarget_ || !solidBrush_ || !d2dFactory_) return;
    solidBrush_->SetColor(color);

    float cx = (rect.left + rect.right) * 0.5f;
    float cy = (rect.top + rect.bottom) * 0.5f;
    float w = rect.right - rect.left;
    float h = rect.bottom - rect.top;
    float r = (std::min)(w, h) * 0.45f;

    if (iconName == "minimize") {
        DrawLine(D2D1::Point2F(cx - r * 0.7f, cy), D2D1::Point2F(cx + r * 0.7f, cy), color, strokeWidth);
    } else if (iconName == "maximize") {
        DrawRect(D2D1::RectF(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f), color, strokeWidth);
    } else if (iconName == "close") {
        float d = r * 0.6f;
        DrawLine(D2D1::Point2F(cx - d, cy - d), D2D1::Point2F(cx + d, cy + d), color, strokeWidth);
        DrawLine(D2D1::Point2F(cx + d, cy - d), D2D1::Point2F(cx - d, cy + d), color, strokeWidth);
    } else if (iconName == "play") {
        ID2D1PathGeometry* path = nullptr;
        if (SUCCEEDED(d2dFactory_->CreatePathGeometry(&path))) {
            ID2D1GeometrySink* sink = nullptr;
            if (SUCCEEDED(path->Open(&sink))) {
                sink->BeginFigure(D2D1::Point2F(cx - r * 0.5f, cy - r * 0.7f), D2D1_FIGURE_BEGIN_FILLED);
                sink->AddLine(D2D1::Point2F(cx + r * 0.7f, cy));
                sink->AddLine(D2D1::Point2F(cx - r * 0.5f, cy + r * 0.7f));
                sink->EndFigure(D2D1_FIGURE_END_CLOSED);
                sink->Close();
                sink->Release();
                renderTarget_->FillGeometry(path, solidBrush_);
            }
            path->Release();
        }
    } else if (iconName == "home" || iconName == "dashboard") {
        ID2D1PathGeometry* path = nullptr;
        if (SUCCEEDED(d2dFactory_->CreatePathGeometry(&path))) {
            ID2D1GeometrySink* sink = nullptr;
            if (SUCCEEDED(path->Open(&sink))) {
                sink->BeginFigure(D2D1::Point2F(cx, cy - r * 0.8f), D2D1_FIGURE_BEGIN_HOLLOW);
                sink->AddLine(D2D1::Point2F(cx + r * 0.8f, cy - r * 0.1f));
                sink->AddLine(D2D1::Point2F(cx + r * 0.6f, cy - r * 0.1f));
                sink->AddLine(D2D1::Point2F(cx + r * 0.6f, cy + r * 0.8f));
                sink->AddLine(D2D1::Point2F(cx - r * 0.6f, cy + r * 0.8f));
                sink->AddLine(D2D1::Point2F(cx - r * 0.6f, cy - r * 0.1f));
                sink->AddLine(D2D1::Point2F(cx - r * 0.8f, cy - r * 0.1f));
                sink->EndFigure(D2D1_FIGURE_END_CLOSED);
                sink->Close();
                sink->Release();
                renderTarget_->DrawGeometry(path, solidBrush_, strokeWidth);
            }
            path->Release();
        }
    } else if (iconName == "user" || iconName == "accounts") {
        D2D1_ELLIPSE head = D2D1::Ellipse(D2D1::Point2F(cx, cy - r * 0.35f), r * 0.35f, r * 0.35f);
        renderTarget_->DrawEllipse(head, solidBrush_, strokeWidth);
        D2D1_ROUNDED_RECT body = D2D1::RoundedRect(D2D1::RectF(cx - r * 0.75f, cy + r * 0.2f, cx + r * 0.75f, cy + r * 0.9f), 4.0f, 4.0f);
        renderTarget_->DrawRoundedRectangle(body, solidBrush_, strokeWidth);
    } else if (iconName == "versions") {
        DrawRect(D2D1::RectF(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f), color, strokeWidth);
        DrawLine(D2D1::Point2F(cx - r * 0.7f, cy), D2D1::Point2F(cx + r * 0.7f, cy), color, strokeWidth);
        DrawLine(D2D1::Point2F(cx, cy - r * 0.7f), D2D1::Point2F(cx, cy + r * 0.7f), color, strokeWidth);
    } else if (iconName == "workshop") {
        // Shopping bag or package box
        DrawRoundedRect(D2D1::RectF(cx - r * 0.7f, cy - r * 0.3f, cx + r * 0.7f, cy + r * 0.8f), 3.0f, color, strokeWidth);
        ID2D1PathGeometry* path = nullptr;
        if (SUCCEEDED(d2dFactory_->CreatePathGeometry(&path))) {
            ID2D1GeometrySink* sink = nullptr;
            if (SUCCEEDED(path->Open(&sink))) {
                sink->BeginFigure(D2D1::Point2F(cx - r * 0.4f, cy - r * 0.3f), D2D1_FIGURE_BEGIN_HOLLOW);
                sink->AddArc(D2D1::ArcSegment(D2D1::Point2F(cx + r * 0.4f, cy - r * 0.3f), D2D1::SizeF(r * 0.4f, r * 0.45f), 0, D2D1_SWEEP_DIRECTION_CLOCKWISE, D2D1_ARC_SIZE_SMALL));
                sink->EndFigure(D2D1_FIGURE_END_OPEN);
                sink->Close();
                sink->Release();
                renderTarget_->DrawGeometry(path, solidBrush_, strokeWidth);
            }
            path->Release();
        }
    } else if (iconName == "gallery") {
        DrawRoundedRect(D2D1::RectF(cx - r * 0.8f, cy - r * 0.6f, cx + r * 0.8f, cy + r * 0.6f), 3.0f, color, strokeWidth);
        D2D1_ELLIPSE sun = D2D1::Ellipse(D2D1::Point2F(cx - r * 0.35f, cy - r * 0.2f), r * 0.18f, r * 0.18f);
        renderTarget_->FillEllipse(sun, solidBrush_);
    } else if (iconName == "console") {
        DrawRoundedRect(D2D1::RectF(cx - r * 0.8f, cy - r * 0.6f, cx + r * 0.8f, cy + r * 0.6f), 3.0f, color, strokeWidth);
        DrawLine(D2D1::Point2F(cx - r * 0.5f, cy - r * 0.2f), D2D1::Point2F(cx - r * 0.2f, cy), color, strokeWidth);
        DrawLine(D2D1::Point2F(cx - r * 0.2f, cy), D2D1::Point2F(cx - r * 0.5f, cy + r * 0.2f), color, strokeWidth);
        DrawLine(D2D1::Point2F(cx, cy + r * 0.25f), D2D1::Point2F(cx + r * 0.45f, cy + r * 0.25f), color, strokeWidth);
    } else if (iconName == "settings" || iconName == "gear") {
        D2D1_ELLIPSE outer = D2D1::Ellipse(D2D1::Point2F(cx, cy), r * 0.7f, r * 0.7f);
        renderTarget_->DrawEllipse(outer, solidBrush_, strokeWidth * 1.5f);
        D2D1_ELLIPSE inner = D2D1::Ellipse(D2D1::Point2F(cx, cy), r * 0.35f, r * 0.35f);
        renderTarget_->DrawEllipse(inner, solidBrush_, strokeWidth);
    } else if (iconName == "check") {
        DrawLine(D2D1::Point2F(cx - r * 0.6f, cy), D2D1::Point2F(cx - r * 0.15f, cy + r * 0.5f), color, strokeWidth * 1.3f);
        DrawLine(D2D1::Point2F(cx - r * 0.15f, cy + r * 0.5f), D2D1::Point2F(cx + r * 0.7f, cy - r * 0.5f), color, strokeWidth * 1.3f);
    } else if (iconName == "search") {
        D2D1_ELLIPSE glass = D2D1::Ellipse(D2D1::Point2F(cx - r * 0.2f, cy - r * 0.2f), r * 0.45f, r * 0.45f);
        renderTarget_->DrawEllipse(glass, solidBrush_, strokeWidth);
        DrawLine(D2D1::Point2F(cx + r * 0.15f, cy + r * 0.15f), D2D1::Point2F(cx + r * 0.65f, cy + r * 0.65f), color, strokeWidth * 1.3f);
    } else if (iconName == "folder") {
        DrawRoundedRect(D2D1::RectF(cx - r * 0.8f, cy - r * 0.4f, cx + r * 0.8f, cy + r * 0.65f), 3.0f, color, strokeWidth);
        DrawLine(D2D1::Point2F(cx - r * 0.8f, cy - r * 0.4f), D2D1::Point2F(cx - r * 0.3f, cy - r * 0.4f), color, strokeWidth);
        DrawLine(D2D1::Point2F(cx - r * 0.3f, cy - r * 0.7f), D2D1::Point2F(cx - r * 0.8f, cy - r * 0.7f), color, strokeWidth);
    } else if (iconName == "refresh") {
        D2D1_ELLIPSE circ = D2D1::Ellipse(D2D1::Point2F(cx, cy), r * 0.65f, r * 0.65f);
        renderTarget_->DrawEllipse(circ, solidBrush_, strokeWidth);
    } else {
        // Fallback circle
        D2D1_ELLIPSE circ = D2D1::Ellipse(D2D1::Point2F(cx, cy), r * 0.6f, r * 0.6f);
        renderTarget_->DrawEllipse(circ, solidBrush_, strokeWidth);
    }
}

} // namespace aero
