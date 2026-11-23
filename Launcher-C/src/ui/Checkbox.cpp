#include "Checkbox.h"

namespace aero {

Checkbox::Checkbox(const std::wstring& label, bool checked)
    : label_(label), isChecked_(checked) {
    switchAnim_.SnapTo(checked ? 1.0f : 0.0f);
}

void Checkbox::SetChecked(bool checked) {
    isChecked_ = checked;
    switchAnim_.SetTarget(checked ? 1.0f : 0.0f);
    if (onToggle_) onToggle_(isChecked_);
}

void Checkbox::Update(float dt) {
    switchAnim_.SetTarget(isChecked_ ? 1.0f : 0.0f);
    switchAnim_.Update(dt);
    hoverAnim_.SetTarget(isHovered_ ? 1.0f : 0.0f);
    hoverAnim_.Update(dt);
}

bool Checkbox::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (!isVisible_ || !isEnabled_) return false;

    if (Contains(x, y)) {
        if (btn == MouseBtn::Left && !down) { // on click release
            isChecked_ = !isChecked_;
            if (onToggle_) onToggle_(isChecked_);
        }
        return true;
    }
    return false;
}

void Checkbox::Render(D2DRenderer& r) {
    if (!isVisible_) return;

    auto& theme = Theme::Instance();
    float t = anim::Clamp(switchAnim_.Get(), 0.0f, 1.0f);
    float h = hoverAnim_.Get();

    // Toggle switch track: 38px wide x 22px high
    float trackW = 38.0f;
    float trackH = 22.0f;
    float cy = (bounds_.top + bounds_.bottom) * 0.5f;
    D2D1_RECT_F trackRect = D2D1::RectF(bounds_.left, cy - trackH * 0.5f, bounds_.left + trackW, cy + trackH * 0.5f);

    // Subtle outer halo when hovered or active
    if (t > 0.05f || h > 0.05f) {
        float haloAlpha = 0.12f * t + 0.08f * h;
        D2D1_RECT_F haloRect = D2D1::RectF(trackRect.left - 3.0f, trackRect.top - 3.0f, trackRect.right + 3.0f, trackRect.bottom + 3.0f);
        r.DrawRoundedRect(haloRect, 6.0f, Color::FromRgba(240, 183, 130, haloAlpha), 1.5f);
    }

    // Base background (clean rounded rectangle, no capsules)
    r.FillRoundedRect(trackRect, 5.0f, Color::FromRgba(32, 35, 32, 0.9f));

    // Active fill with spring progress
    if (t > 0.01f) {
        D2D1_COLOR_F c1 = theme.accent1;
        D2D1_COLOR_F c2 = theme.accent2;
        c1.a *= t;
        c2.a *= t;
        r.FillGradientRoundedRect(trackRect, 5.0f, c1, c2, false);
    }

    // Border
    D2D1_COLOR_F borderCol = anim::LerpColor(theme.line, theme.accent2, t * 0.6f + h * 0.4f);
    r.DrawRoundedRect(trackRect, 5.0f, borderCol, 1.0f);

    // Thumb: clean 14x14 rounded rect
    float thumbHalf = 7.0f;
    float thumbMinX = trackRect.left + 3.0f + thumbHalf;
    float thumbMaxX = trackRect.right - 3.0f - thumbHalf;
    float thumbX = anim::Lerp(thumbMinX, thumbMaxX, t);
    float thumbY = cy;

    D2D1_COLOR_F thumbColor = anim::LerpColor(Color::FromHex(0xb4bdaa), D2D1::ColorF(1.0f, 1.0f, 1.0f), t);
    D2D1_RECT_F thumbRect = D2D1::RectF(thumbX - thumbHalf, thumbY - thumbHalf, thumbX + thumbHalf, thumbY + thumbHalf);
    r.FillRoundedRect(thumbRect, 3.5f, thumbColor);

    // Label with hover brightness
    if (!label_.empty()) {
        D2D1_RECT_F textRect = D2D1::RectF(bounds_.left + trackW + 12.0f, bounds_.top, bounds_.right, bounds_.bottom);
        D2D1_COLOR_F labelCol = anim::LerpColor(theme.text, D2D1::ColorF(1.0f, 1.0f, 1.0f), h * 0.5f);
        r.DrawString(label_, textRect, labelCol, 12.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    }
}

} // namespace aero
