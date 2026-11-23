#pragma once
#include <d2d1.h>
#include <vector>

namespace aero {

struct Star {
    float x;
    float y;
    float vx;
    float vy;
    float radius;
    bool isMint;
    float baseAlpha;
    float depth; // 0.3 (far) to 1.0 (near) for 3D parallax
};

struct Meteor {
    float x;
    float y;
    float vx;
    float vy;
    float length;
    float life;
    float maxLife;
    D2D1_COLOR_F color;
};

class Starfield {
public:
    Starfield();
    void Resize(float width, float height);
    void SetMousePos(float x, float y, bool active);
    void Update(float dt);
    void Render(ID2D1RenderTarget* target, ID2D1SolidColorBrush* brush);

    void SetEnabled(bool enabled) { enabled_ = enabled; }
    bool IsEnabled() const { return enabled_; }

private:
    void SpawnMeteor();

    float width_ = 1280.0f;
    float height_ = 800.0f;
    float mouseX_ = 640.0f;
    float mouseY_ = 400.0f;
    float targetMouseX_ = 640.0f;
    float targetMouseY_ = 400.0f;
    bool mouseActive_ = false;
    bool enabled_ = true;
    float animTime_ = 0.0f;
    float meteorTimer_ = 0.0f;

    std::vector<Star> stars_;
    std::vector<Meteor> meteors_;
};

} // namespace aero
