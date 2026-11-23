#pragma once
#include "../ui/UIElement.h"
#include "../ui/Button.h"
#include "../core/Window.h"

namespace aero {

class TitleBarView : public UIElement {
public:
    TitleBarView(Window& window);

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;

    void Resize(float width);

private:
    Window& window_;
    Button btnMin_{ L"", ButtonStyle::Icon };
    Button btnMax_{ L"", ButtonStyle::Icon };
    Button btnClose_{ L"", ButtonStyle::Icon };
    float animTime_ = 0.0f;
};

} // namespace aero
