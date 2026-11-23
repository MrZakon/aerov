#pragma once

#include "../ui/Button.h"

namespace aero {

class LauncherUpdateDialog final : public UIElement {
public:
    LauncherUpdateDialog(std::function<void()> accept, std::function<void()> dismiss);

    void Resize(float width, float height);
    void Show(const std::wstring& installed, const std::wstring& available);
    void Hide() { SetVisible(false); }
    void Update(float dt) override;
    void Render(D2DRenderer& renderer) override;
    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn button, bool down) override;
    bool OnKey(int key, bool down) override;

private:
    Button later_{L"Позже"};
    Button download_{L"Скачать обновление", ButtonStyle::Primary};
    std::wstring installed_;
    std::wstring available_;
    float width_ = 0;
    float height_ = 0;
};

} // namespace aero
