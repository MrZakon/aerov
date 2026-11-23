#pragma once
#include "D2DRenderer.h"
#include <initializer_list>

namespace aero::aero {
inline void Polygon(D2DRenderer& r, std::initializer_list<D2D1_POINT_2F> points, D2D1_COLOR_F color) {
    if (points.size() < 3) return;
    ID2D1Factory* factory = nullptr;
    r.GetRenderTarget()->GetFactory(&factory);
    ID2D1PathGeometry* path = nullptr;
    if (SUCCEEDED(factory->CreatePathGeometry(&path))) {
        ID2D1GeometrySink* sink = nullptr;
        if (SUCCEEDED(path->Open(&sink))) {
            auto it = points.begin();
            sink->BeginFigure(*it++, D2D1_FIGURE_BEGIN_FILLED);
            for (; it != points.end(); ++it) sink->AddLine(*it);
            sink->EndFigure(D2D1_FIGURE_END_CLOSED);
            if (SUCCEEDED(sink->Close())) {
                r.GetSolidBrush()->SetColor(color);
                r.GetRenderTarget()->FillGeometry(path, r.GetSolidBrush());
            }
            sink->Release();
        }
        path->Release();
    }
    factory->Release();
}

// Resolution-independent landscape, drawn locally without external assets.
inline void Landscape(D2DRenderer& r, D2D1_RECT_F box) {
    const float w = box.right - box.left, h = box.bottom - box.top;
    auto p = [&](float x, float y) { return D2D1::Point2F(box.left + w*x, box.top + h*y); };
    r.PushClip(box);
    r.FillGradientRoundedRect(box, 0, Color::FromHex(0x544d43), Color::FromHex(0xb79a6f), true);
    r.GetSolidBrush()->SetColor(Color::FromHex(0xf4c98c));
    r.GetRenderTarget()->FillEllipse(D2D1::Ellipse(p(.73f,.26f), w*.055f, w*.055f), r.GetSolidBrush());
    Polygon(r, {p(0,.69f),p(.19f,.30f),p(.38f,.63f),p(.53f,.37f),p(.80f,.70f),p(1,.42f),p(1,1),p(0,1)}, Color::FromHex(0x696454));
    Polygon(r, {p(.19f,.30f),p(.38f,.63f),p(.26f,.54f)}, Color::FromHex(0x968b70));
    Polygon(r, {p(0,.82f),p(.25f,.56f),p(.46f,.83f),p(.71f,.49f),p(1,.78f),p(1,1),p(0,1)}, Color::FromHex(0x39463b));
    Polygon(r, {p(0,.96f),p(.16f,.79f),p(.35f,.87f),p(.60f,.70f),p(.86f,.90f),p(1,.79f),p(1,1),p(0,1)}, Color::FromHex(0x202e29));
    for (int i=0; i<15; ++i) {
        float x=.025f+i*.073f, y=.86f + (i%3)*.035f, s=.06f+(i%4)*.018f;
        Polygon(r, {p(x,y-s),p(x-.018f,y),p(x+.018f,y)}, Color::FromHex(0x17221e));
    }
    r.FillGradientRoundedRect(box, 0, Color::FromRgba(10,15,13,0), Color::FromRgba(10,15,13,.78f), true);
    r.PopClip();
}

inline void Panel(D2DRenderer& r, D2D1_RECT_F box) {
    r.FillRoundedRect(box, 14, Theme::Instance().surface);
    r.DrawRoundedRect(box, 14, Theme::Instance().line);
}
}
