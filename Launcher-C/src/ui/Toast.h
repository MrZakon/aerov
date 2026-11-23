#pragma once
#include <string>
#include <mutex>
#include <d2d1.h>
#include "../render/D2DRenderer.h"
#include "../core/Animation.h"

namespace aero {

enum class ToastKind {
    Info,
    Success,
    Error
};

class Toast {
public:
    static Toast& Instance();

    void Show(const std::wstring& message, ToastKind kind = ToastKind::Info, float duration = 3.5f);
    void Update(float dt);
    void Render(D2DRenderer& r, float windowWidth, float windowHeight);

private:
    Toast() = default;

    std::mutex mutex_;
    std::wstring message_;
    ToastKind kind_ = ToastKind::Info;
    float timer_ = 0.0f;
    bool isVisible_ = false;
    AnimatedFloat alphaAnim_{ 0.0f, 12.0f };
    SpringFloat slideAnim_{ 0.0f, 220.0f, 15.0f };
};

} // namespace aero
