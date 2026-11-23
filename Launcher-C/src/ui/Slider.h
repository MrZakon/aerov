#pragma once
#include "UIElement.h"
#include "../core/Animation.h"
#include <functional>
#include <string>

namespace aero {

class Slider : public UIElement {
public:
    Slider(float minVal = 1.0f, float maxVal = 16.0f, float curVal = 4.0f, float step = 1.0f);

    void SetRange(float minVal, float maxVal, float step = 1.0f);
    void SetValue(float val);
    float GetValue() const { return value_; }

    void SetOnChanged(std::function<void(float)> cb) { onChanged_ = cb; }

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;

private:
    void UpdateValueFromMouse(float mouseX);

    float min_ = 1.0f;
    float max_ = 16.0f;
    float step_ = 1.0f;
    float value_ = 4.0f;

    bool isDragging_ = false;
    bool isHovered_ = false;

    SpringFloat thumbAnimX_{ 0.0f, 260.0f, 22.0f };
    SpringFloat hoverScale_{ 1.0f, 320.0f, 20.0f };
    SpringFloat activeGlow_{ 0.0f, 240.0f, 18.0f };

    std::function<void(float)> onChanged_;
};

} // namespace aero
