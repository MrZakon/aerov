#include "TitleBarView.h"

namespace aero {

TitleBarView::TitleBarView(Window& window)
    : window_(window) {
    SetBounds(0, 0, 1280, 60);

    btnMin_.SetIcon("minimize");
    btnMin_.SetOnClick([this]() { window_.Minimize(); });

    btnMax_.SetIcon("maximize");
    btnMax_.SetOnClick([this]() { window_.Maximize(); });

    btnClose_.SetIcon("close");
    btnClose_.SetOnClick([this]() { window_.Close(); });
}

void TitleBarView::Resize(float width) {
    SetBounds(0, 0, width, 60);

    float btnW = 34.0f;
    float btnH = 30.0f;
    float topY = 15.0f;

    btnMin_.SetBounds(width - 120.0f, topY, btnW, btnH);
    btnMax_.SetBounds(width - 82.0f, topY, btnW, btnH);
    btnClose_.SetBounds(width - 44.0f, topY, btnW, btnH);
}

void TitleBarView::Update(float dt) {
    animTime_ += dt;
    btnMin_.Update(dt);
    btnMax_.Update(dt);
    btnClose_.Update(dt);
}

bool TitleBarView::OnMouseMove(float x, float y) {
    bool handled = false;
    handled |= btnMin_.OnMouseMove(x, y);
    handled |= btnMax_.OnMouseMove(x, y);
    handled |= btnClose_.OnMouseMove(x, y);
    return handled;
}

bool TitleBarView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (btnMin_.OnMouseButton(x, y, btn, down)) return true;
    if (btnMax_.OnMouseButton(x, y, btn, down)) return true;
    if (btnClose_.OnMouseButton(x, y, btn, down)) return true;
    return false;
}

void TitleBarView::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    r.FillRect(D2D1::RectF(0, 0, bounds_.right, 60), theme.bg);
    r.DrawLine(D2D1::Point2F(0, 59.5f), D2D1::Point2F(bounds_.right, 59.5f), theme.line);
    auto mark = D2D1::RectF(22, 14, 54, 46);
    r.DrawRoundedRect(mark, 7, theme.accent1, 1.5f);
    r.DrawString(L"A", mark, theme.accent1, 23, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    r.DrawString(L"AERO", D2D1::RectF(66, 17, 140, 43), theme.text, 18, FontStyle::Bold);
    r.DrawString(L"ЛИЧНОЕ ПРОСТРАНСТВО ДЛЯ ИГРЫ", D2D1::RectF(152, 24, 350, 40), theme.muted, 9, FontStyle::Regular);
    // Buttons
    btnMin_.Render(r);
    btnMax_.Render(r);
    btnClose_.Render(r);
}

} // namespace aero
