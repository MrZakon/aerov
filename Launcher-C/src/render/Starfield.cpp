#include "Starfield.h"
#include <random>
#include <cmath>

namespace aero {

Starfield::Starfield() {
    std::mt19937 rng(42);
    std::uniform_real_distribution<float> dist01(0.0f, 1.0f);
    std::uniform_real_distribution<float> distV(-0.25f, 0.25f);
    std::uniform_real_distribution<float> distR(0.6f, 2.4f);
    std::uniform_real_distribution<float> distD(0.3f, 1.0f);

    stars_.resize(85);
    for (auto& s : stars_) {
        s.x = dist01(rng) * width_;
        s.y = dist01(rng) * height_;
        s.depth = distD(rng);
        s.vx = distV(rng) * s.depth;
        s.vy = distV(rng) * s.depth;
        s.radius = distR(rng) * s.depth;
        s.isMint = dist01(rng) > 0.4f;
        s.baseAlpha = (dist01(rng) * 0.45f + 0.35f) * s.depth;
    }
}

void Starfield::Resize(float width, float height) {
    if (width <= 0 || height <= 0) return;
    float scaleX = width / width_;
    float scaleY = height / height_;
    width_ = width;
    height_ = height;
    for (auto& s : stars_) {
        s.x *= scaleX;
        s.y *= scaleY;
    }
}

void Starfield::SetMousePos(float x, float y, bool active) {
    targetMouseX_ = x;
    targetMouseY_ = y;
    mouseActive_ = active;
}

void Starfield::SpawnMeteor() {
    static std::mt19937 mrng(777);
    std::uniform_real_distribution<float> dX(width_ * 0.1f, width_ * 0.9f);
    std::uniform_real_distribution<float> dSpeed(450.0f, 750.0f);
    std::uniform_real_distribution<float> dLen(80.0f, 160.0f);
    std::uniform_real_distribution<float> dLife(0.8f, 1.4f);
    std::uniform_int_distribution<int> dCol(0, 1);

    Meteor m;
    m.x = dX(mrng);
    m.y = -20.0f;
    float angle = 1.15f; // ~65 degrees downward
    float spd = dSpeed(mrng);
    m.vx = std::cos(angle) * spd;
    m.vy = std::sin(angle) * spd;
    m.length = dLen(mrng);
    m.maxLife = dLife(mrng);
    m.life = m.maxLife;

    if (dCol(mrng) == 0) {
        m.color = D2D1::ColorF(0.43f, 0.91f, 1.0f); // Cyan
    } else {
        m.color = D2D1::ColorF(0.65f, 0.55f, 0.98f); // Violet
    }

    meteors_.push_back(m);
}

void Starfield::Update(float dt) {
    if (!enabled_) return;

    animTime_ += dt;
    meteorTimer_ += dt;

    // Periodically spawn a shooting star every 3.5 - 6 seconds
    if (meteorTimer_ > 4.2f && meteors_.size() < 2) {
        SpawnMeteor();
        meteorTimer_ = 0.0f;
    }

    // Smooth mouse inertia for cinematic parallax
    mouseX_ += (targetMouseX_ - mouseX_) * 3.5f * dt;
    mouseY_ += (targetMouseY_ - mouseY_) * 3.5f * dt;

    float speedMult = dt * 60.0f;
    float pOffsetX = (mouseX_ - width_ * 0.5f) / width_;
    float pOffsetY = (mouseY_ - height_ * 0.5f) / height_;

    for (auto& s : stars_) {
        // Natural drift + subtle 3D parallax
        s.x += (s.vx - pOffsetX * s.depth * 0.4f) * speedMult;
        s.y += (s.vy - pOffsetY * s.depth * 0.4f) * speedMult;

        if (s.x < 0.0f) s.x = width_;
        if (s.x > width_) s.x = 0.0f;
        if (s.y < 0.0f) s.y = height_;
        if (s.y > height_) s.y = 0.0f;
    }

    // Update meteors
    for (auto it = meteors_.begin(); it != meteors_.end(); ) {
        it->life -= dt;
        if (it->life <= 0.0f || it->x > width_ + 100.0f || it->y > height_ + 100.0f) {
            it = meteors_.erase(it);
        } else {
            it->x += it->vx * dt;
            it->y += it->vy * dt;
            ++it;
        }
    }
}

void Starfield::Render(ID2D1RenderTarget* target, ID2D1SolidColorBrush* brush) {
    if (!enabled_ || !target || !brush) return;

    const D2D1_COLOR_F mintColor = D2D1::ColorF(0.204f, 0.827f, 0.600f);
    const D2D1_COLOR_F violetColor = D2D1::ColorF(0.655f, 0.545f, 0.980f);

    size_t count = stars_.size();

    // 1. Constellations (connecting lines)
    for (size_t i = 0; i < count; ++i) {
        const auto& s = stars_[i];
        for (size_t j = i + 1; j < count; ++j) {
            const auto& s2 = stars_[j];
            float dx = s.x - s2.x;
            float dy = s.y - s2.y;
            float d = std::hypot(dx, dy);
            if (d < 85.0f) {
                D2D1_COLOR_F lineCol = s.isMint ? mintColor : violetColor;
                lineCol.a = (1.0f - d / 85.0f) * 0.14f * s.depth;
                brush->SetColor(lineCol);
                target->DrawLine(D2D1::Point2F(s.x, s.y), D2D1::Point2F(s2.x, s2.y), brush, 0.8f);
            }
        }
    }

    // 2. Stars with twinkle
    for (size_t i = 0; i < count; ++i) {
        const auto& s = stars_[i];
        D2D1_COLOR_F c = s.isMint ? mintColor : violetColor;
        float twinkle = 0.72f + 0.28f * std::sin(animTime_ * (2.0f + s.depth * 2.0f) + s.x * 0.03f + s.y * 0.03f);
        c.a = s.baseAlpha * twinkle;
        brush->SetColor(c);

        D2D1_ELLIPSE ellipse = D2D1::Ellipse(D2D1::Point2F(s.x, s.y), s.radius, s.radius);
        target->FillEllipse(ellipse, brush);
    }

    // 3. Shooting stars / Meteors
    for (const auto& m : meteors_) {
        float normLife = m.life / m.maxLife;
        float tailFade = std::sin(normLife * 3.14159f); // fade in and out smoothly

        float normVx = m.vx / std::hypot(m.vx, m.vy);
        float normVy = m.vy / std::hypot(m.vx, m.vy);

        D2D1_POINT_2F head = D2D1::Point2F(m.x, m.y);
        D2D1_POINT_2F tail = D2D1::Point2F(m.x - normVx * m.length, m.y - normVy * m.length);

        // Meteor glowing tail streak
        D2D1_COLOR_F streakCol = m.color;
        streakCol.a = tailFade * 0.75f;
        brush->SetColor(streakCol);
        target->DrawLine(tail, head, brush, 1.8f);

        // Glowing head spark
        D2D1_COLOR_F headCol = D2D1::ColorF(1.0f, 1.0f, 1.0f, tailFade * 0.95f);
        brush->SetColor(headCol);
        D2D1_ELLIPSE headEllipse = D2D1::Ellipse(head, 2.2f, 2.2f);
        target->FillEllipse(headEllipse, brush);
    }
}

} // namespace aero
