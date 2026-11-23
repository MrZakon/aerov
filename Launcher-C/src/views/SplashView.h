#pragma once
#include "../render/D2DRenderer.h"
#include <d2d1.h>
#include <string>

namespace aero {

class SplashView {
public:
    SplashView();

    void Update(float dt);
    void Render(D2DRenderer& r, float width, float height);

    bool IsFinished() const { return isFinished_; }
    void Skip();

    float GetMainUiAlpha() const;
    float GetMainUiScale() const;
    float GetMainUiOffsetY() const;
    float GetSplashAlpha() const;

private:
    float animTime_ = 0.0f;
    bool isFinished_ = false;

    ID2D1Bitmap* logoBitmap_ = nullptr;
    bool isLogoLoaded_ = false;
};

} // namespace aero
