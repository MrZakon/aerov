#include "Toast.h"

namespace aero {

Toast& Toast::Instance() {
    static Toast s_instance;
    return s_instance;
}

void Toast::Show(const std::wstring& message, ToastKind kind, float duration) {
    std::lock_guard<std::mutex> lock(mutex_);
    message_ = message;
    kind_ = kind;
    timer_ = duration;
    isVisible_ = true;
    alphaAnim_.SnapTo(0.0f);
    alphaAnim_.SetTarget(1.0f);
    slideAnim_.SnapTo(0.0f);
    slideAnim_.SetTarget(1.0f);
}

void Toast::Update(float dt) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (isVisible_) {
        timer_ -= dt;
        if (timer_ <= 0.0f) {
            isVisible_ = false;
            alphaAnim_.SetTarget(0.0f);
            slideAnim_.SetTarget(0.0f);
        }
    }
    alphaAnim_.Update(dt);
    slideAnim_.Update(dt);
}

void Toast::Render(D2DRenderer& r, float windowWidth, float windowHeight) {
    std::wstring msg;
    ToastKind kind = ToastKind::Info;
    float alpha = 0.0f;
    float slideY = 0.0f;

    {
        std::lock_guard<std::mutex> lock(mutex_);
        alpha = alphaAnim_.Get();
        if (alpha < 0.01f || message_.empty()) return;
        msg = message_;
        kind = kind_;
        slideY = (1.0f - slideAnim_.Get()) * 24.0f;
    }

    auto& theme = Theme::Instance();

    // Measure toast size
    D2D1_SIZE_F textSize = r.MeasureString(msg, 12.0f, FontStyle::Regular, 380.0f);
    float toastW = textSize.width + 36.0f;
    float toastH = textSize.height + 26.0f;

    float posX = windowWidth - toastW - 24.0f;
    float posY = windowHeight - toastH - 24.0f + slideY;

    D2D1_RECT_F toastRect = D2D1::RectF(posX, posY, posX + toastW, posY + toastH);

    // Glowing halo
    D2D1_COLOR_F glowCol = (kind == ToastKind::Error)
        ? Color::FromRgba(251, 113, 133, alpha * 0.35f)
        : ((kind == ToastKind::Success)
            ? Color::FromRgba(178, 199, 142, alpha * 0.35f)
            : Color::FromRgba(240, 183, 130, alpha * 0.30f));

    D2D1_RECT_F glowRect = D2D1::RectF(toastRect.left - 3.0f, toastRect.top - 3.0f, toastRect.right + 3.0f, toastRect.bottom + 3.0f);
    r.DrawRoundedRect(glowRect, 8.0f, glowCol, 1.2f);

    D2D1_COLOR_F bg = Color::FromHex(0x252b24, alpha * 0.96f);
    D2D1_COLOR_F border = (kind == ToastKind::Error)
        ? Color::FromRgba(251, 113, 133, alpha * 0.8f)
        : ((kind == ToastKind::Success)
            ? Color::FromRgba(178, 199, 142, alpha * 0.8f)
            : Color::FromRgba(240, 183, 130, alpha * 0.6f));

    r.FillRoundedRect(toastRect, 8.0f, bg);
    r.DrawRoundedRect(toastRect, 8.0f, border, 1.0f);

    D2D1_COLOR_F textColor = (kind == ToastKind::Error) ? Color::FromHex(0xffadba) : theme.text;
    textColor.a = alpha;

    D2D1_RECT_F textRect = D2D1::RectF(toastRect.left + 18.0f, toastRect.top, toastRect.right - 18.0f, toastRect.bottom);
    r.DrawString(msg, textRect, textColor, 12.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
}

} // namespace aero
