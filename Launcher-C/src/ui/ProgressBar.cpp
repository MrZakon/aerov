#include "ProgressBar.h"

namespace aero {

ProgressBar::ProgressBar() = default;

void ProgressBar::SetProgress(float progress) {
    targetProgress_ = anim::Clamp(progress, 0.0f, 1.0f);
    currentProgress_.SetTarget(targetProgress_);
}

void ProgressBar::Update(float dt) {
    currentProgress_.Update(dt);
    shimmerPhase_ += dt * 1.8f;
    if (shimmerPhase_ > 2.0f) shimmerPhase_ -= 2.0f;
}

void ProgressBar::Render(D2DRenderer& r) {
    if (!isVisible_) return;

    auto& theme = Theme::Instance();

    // Track background
    float trackHeight = 6.0f;
    float trackY = bounds_.bottom - trackHeight;
    D2D1_RECT_F trackRect = D2D1::RectF(bounds_.left, trackY, bounds_.right, bounds_.bottom);

    r.FillRoundedRect(trackRect, 3.0f, Color::FromRgba(32, 35, 32, 0.75f));
    r.DrawRoundedRect(trackRect, 3.0f, Color::FromRgba(207, 189, 154, 0.12f), 1.0f);

    // Fill
    float fillProgress = currentProgress_.Get();
    if (fillProgress > 0.005f) {
        float fillWidth = (bounds_.right - bounds_.left) * fillProgress;
        D2D1_RECT_F fillRect = D2D1::RectF(bounds_.left, trackY, bounds_.left + fillWidth, bounds_.bottom);
        r.FillGradientRoundedRect(fillRect, 3.0f, theme.accent1, theme.accent2, false);

        // Animated shimmer highlight wave across the filled part
        if (fillProgress < 0.99f) {
            float sPos = (bounds_.right - bounds_.left) * (shimmerPhase_ * 0.6f);
            float sLeft = bounds_.left + sPos - 30.0f;
            float sRight = bounds_.left + sPos + 30.0f;
            if (sRight > bounds_.left && sLeft < fillRect.right) {
                sLeft = anim::Clamp(sLeft, bounds_.left, fillRect.right);
                sRight = anim::Clamp(sRight, bounds_.left, fillRect.right);
                if (sRight > sLeft) {
                    D2D1_RECT_F shimRect = D2D1::RectF(sLeft, trackY, sRight, bounds_.bottom);
                    r.FillRoundedRect(shimRect, 3.0f, Color::FromRgba(255, 255, 255, 0.35f));
                }
            }
        }

        // Glowing tip marker at current progress point (clean rounded rect)
        float tipX = fillRect.right;
        D2D1_RECT_F tipRect = D2D1::RectF(tipX - 1.5f, trackY, tipX + 1.5f, bounds_.bottom);
        r.FillRoundedRect(tipRect, 1.0f, D2D1::ColorF(1.0f, 1.0f, 1.0f));
    }

    // Optional status text above bar
    if (!text_.empty() && (bounds_.bottom - bounds_.top > 14.0f)) {
        D2D1_RECT_F textRect = D2D1::RectF(bounds_.left, bounds_.top, bounds_.right, trackY - 4.0f);
        r.DrawString(text_, textRect, theme.text, 11.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    }
}

} // namespace aero
