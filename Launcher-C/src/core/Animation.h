#pragma once
#include <d2d1.h>
#include <algorithm>
#include <cmath>

namespace aero {

namespace anim {

inline float Clamp(float val, float minVal, float maxVal) {
    if (val < minVal) return minVal;
    if (val > maxVal) return maxVal;
    return val;
}

inline float Lerp(float a, float b, float t) {
    return a + (b - a) * Clamp(t, 0.0f, 1.0f);
}

inline D2D1_COLOR_F LerpColor(const D2D1_COLOR_F& a, const D2D1_COLOR_F& b, float t) {
    t = Clamp(t, 0.0f, 1.0f);
    return D2D1::ColorF(
        a.r + (b.r - a.r) * t,
        a.g + (b.g - a.g) * t,
        a.b + (b.b - a.b) * t,
        a.a + (b.a - a.a) * t
    );
}

// Frame-rate independent exponential decay smoothing
inline float ExpDecay(float current, float target, float decaySpeed, float dt) {
    return target + (current - target) * std::exp(-decaySpeed * dt);
}

inline float EaseInOutQuad(float t) {
    t = Clamp(t, 0.0f, 1.0f);
    return t < 0.5f ? 2.0f * t * t : 1.0f - std::pow(-2.0f * t + 2.0f, 2.0f) / 2.0f;
}

inline float EaseOutCubic(float t) {
    t = Clamp(t, 0.0f, 1.0f) - 1.0f;
    return t * t * t + 1.0f;
}

inline float EaseOutBack(float t, float s = 1.70158f) {
    t = Clamp(t, 0.0f, 1.0f) - 1.0f;
    return (t * t * ((s + 1.0f) * t + s) + 1.0f);
}

} // namespace anim

class AnimatedFloat {
public:
    AnimatedFloat(float initial = 0.0f, float speed = 15.0f)
        : current_(initial), target_(initial), speed_(speed) {}

    void SetTarget(float target) { target_ = target; }
    void SnapTo(float value) { current_ = value; target_ = value; }
    void SetSpeed(float speed) { speed_ = speed; }

    void Update(float dt) {
        current_ = anim::ExpDecay(current_, target_, speed_, dt);
        if (std::abs(current_ - target_) < 0.0005f) {
            current_ = target_;
        }
    }

    float Get() const { return current_; }
    float GetTarget() const { return target_; }
    bool IsSettled() const { return current_ == target_; }

private:
    float current_;
    float target_;
    float speed_;
};

// Physics-based Spring Float for natural bouncy physical feedback
class SpringFloat {
public:
    SpringFloat(float initial = 0.0f, float tension = 180.0f, float friction = 14.0f)
        : current_(initial), target_(initial), velocity_(0.0f), tension_(tension), friction_(friction) {}

    void SetTarget(float target) { target_ = target; }
    void SnapTo(float value) { current_ = value; target_ = value; velocity_ = 0.0f; }

    void Update(float dt) {
        if (dt > 0.05f) dt = 0.05f; // prevent instability on lag spikes
        float force = -tension_ * (current_ - target_) - friction_ * velocity_;
        velocity_ += force * dt;
        current_ += velocity_ * dt;
        if (std::abs(current_ - target_) < 0.001f && std::abs(velocity_) < 0.005f) {
            current_ = target_;
            velocity_ = 0.0f;
        }
    }

    float Get() const { return current_; }
    float GetTarget() const { return target_; }
    bool IsSettled() const { return current_ == target_ && std::abs(velocity_) < 0.001f; }

private:
    float current_;
    float target_;
    float velocity_;
    float tension_;
    float friction_;
};

} // namespace aero
