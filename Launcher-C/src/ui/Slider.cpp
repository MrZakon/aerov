#include "Slider.h"
#include "../render/Theme.h"
#include <algorithm>
#include <cmath>

namespace aero {

Slider::Slider(float minVal, float maxVal, float curVal, float step)
    : min_(minVal), max_(maxVal), step_(step), value_(curVal) {
    SetValue(curVal);
}

void Slider::SetRange(float minVal, float maxVal, float step) {
    min_ = minVal;
    max_ = (std::max)(minVal + 0.001f, maxVal);
    step_ = (std::max)(0.0001f, step);
    SetValue(value_);
}

void Slider::SetValue(float val) {
    float clamped = (std::clamp)(val, min_, max_);
    if (step_ > 0.0f) {
        clamped = min_ + std::round((clamped - min_) / step_) * step_;
        clamped = (std::clamp)(clamped, min_, max_);
    }
    value_ = clamped;

    float w = bounds_.right - bounds_.left;
    if (w > 20.0f) {
        float ratio = (value_ - min_) / (max_ - min_);
        float targetX = bounds_.left + ratio * w;
        thumbAnimX_.SetTarget(targetX);
    }
}

void Slider::UpdateValueFromMouse(float mouseX) {
    float w = bounds_.right - bounds_.left;
    if (w <= 0.0f) return;

    float ratio = (std::clamp)(mouseX - bounds_.left, 0.0f, w) / w;
    float rawVal = min_ + ratio * (max_ - min_);

    float newVal = rawVal;
    if (step_ > 0.0f) {
        newVal = min_ + std::round((rawVal - min_) / step_) * step_;
        newVal = (std::clamp)(newVal, min_, max_);
    }

    if (newVal != value_) {
        value_ = newVal;
        if (onChanged_) {
            onChanged_(value_);
        }
    }

    float targetX = bounds_.left + ((value_ - min_) / (max_ - min_)) * w;
    thumbAnimX_.SetTarget(targetX);
}

void Slider::Update(float dt) {
    float w = bounds_.right - bounds_.left;
    if (w > 0.0f) {
        float ratio = (value_ - min_) / (max_ - min_);
        float targetX = bounds_.left + ratio * w;
        thumbAnimX_.SetTarget(targetX);
    }

    thumbAnimX_.Update(dt);
    hoverScale_.Update(dt);
    activeGlow_.Update(dt);

    if (isDragging_) {
        hoverScale_.SetTarget(1.22f);
        activeGlow_.SetTarget(1.0f);
    } else if (isHovered_) {
        hoverScale_.SetTarget(1.12f);
        activeGlow_.SetTarget(0.7f);
    } else {
        hoverScale_.SetTarget(1.0f);
        activeGlow_.SetTarget(0.0f);
    }
}

bool Slider::OnMouseMove(float x, float y) {
    isHovered_ = (x >= bounds_.left && x <= bounds_.right && y >= bounds_.top - 8.0f && y <= bounds_.bottom + 8.0f);
    if (isDragging_) {
        UpdateValueFromMouse(x);
        return true;
    }
    return isHovered_;
}

bool Slider::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (btn != MouseBtn::Left) return false;

    if (down) {
        if (x >= bounds_.left - 6.0f && x <= bounds_.right + 6.0f && y >= bounds_.top - 12.0f && y <= bounds_.bottom + 12.0f) {
            isDragging_ = true;
            UpdateValueFromMouse(x);
            return true;
        }
    } else {
        if (isDragging_) {
            isDragging_ = false;
            return true;
        }
    }
    return false;
}

void Slider::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    float centerY = (bounds_.top + bounds_.bottom) * 0.5f;
    float trackH = 8.0f;

    D2D1_RECT_F trackBg = D2D1::RectF(bounds_.left, centerY - trackH * 0.5f, bounds_.right, centerY + trackH * 0.5f);

    // Track background
    r.FillRoundedRect(trackBg, 4.0f, Color::FromRgba(32, 35, 32, 0.90f));
    r.DrawRoundedRect(trackBg, 4.0f, Color::FromRgba(32, 35, 32, 0.65f), 1.0f);

    // Filled portion
    float curThumbX = (std::clamp)(thumbAnimX_.Get(), bounds_.left, bounds_.right);
    if (curThumbX > bounds_.left + 1.0f) {
        D2D1_RECT_F trackFill = D2D1::RectF(bounds_.left, centerY - trackH * 0.5f, curThumbX, centerY + trackH * 0.5f);
        r.FillGradientRoundedRect(trackFill, 4.0f, theme.accent2, theme.accent1, false);

        // Subtle glow under active track when hovered/dragging
        float glowA = activeGlow_.Get();
        if (glowA > 0.05f) {
            D2D1_RECT_F glowRect = D2D1::RectF(bounds_.left, centerY - trackH, curThumbX, centerY + trackH);
            r.DrawRoundedRect(glowRect, 6.0f, D2D1::ColorF(theme.accent2.r, theme.accent2.g, theme.accent2.b, 0.25f * glowA), 2.0f);
        }
    }

    // Thumb halo / shadow
    float hScale = hoverScale_.Get();
    float thumbR = 8.5f * hScale;

    float glowA = activeGlow_.Get();
    if (glowA > 0.05f) {
        D2D1_RECT_F haloRect = D2D1::RectF(curThumbX - thumbR - 6.0f, centerY - thumbR - 6.0f, curThumbX + thumbR + 6.0f, centerY + thumbR + 6.0f);
        r.FillRoundedRect(haloRect, thumbR + 6.0f, D2D1::ColorF(theme.accent2.r, theme.accent2.g, theme.accent2.b, 0.30f * glowA));
    }

    // Thumb knob
    D2D1_RECT_F thumbRect = D2D1::RectF(curThumbX - thumbR, centerY - thumbR, curThumbX + thumbR, centerY + thumbR);
    r.FillRoundedRect(thumbRect, thumbR, Color::FromHex(0xffffff));
    r.DrawRoundedRect(thumbRect, thumbR, theme.accent1, 2.0f);
}

} // namespace aero
