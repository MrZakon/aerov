#pragma once
#include "UIElement.h"
#include <string>
#include "../core/Animation.h"

namespace aero {

class ProgressBar : public UIElement {
public:
    ProgressBar();

    void SetProgress(float progress); // 0.0f .. 1.0f
    float GetProgress() const { return targetProgress_; }

    void SetText(const std::wstring& text) { text_ = text; }
    const std::wstring& GetText() const { return text_; }

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

private:
    float targetProgress_ = 0.0f;
    std::wstring text_;
    AnimatedFloat currentProgress_{ 0.0f, 10.0f };
    float shimmerPhase_ = 0.0f;
};

} // namespace aero
