#pragma once
#include "UIElement.h"
#include <string>
#include <functional>
#include "../core/Animation.h"

namespace aero {

class Checkbox : public UIElement {
public:
    Checkbox(const std::wstring& label = L"", bool checked = false);

    void SetChecked(bool checked);
    bool IsChecked() const { return isChecked_; }

    void SetLabel(const std::wstring& label) { label_ = label; }
    void SetOnToggle(std::function<void(bool)> cb) { onToggle_ = cb; }

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;

private:
    std::wstring label_;
    bool isChecked_ = false;
    std::function<void(bool)> onToggle_;
    SpringFloat switchAnim_{ 0.0f, 260.0f, 18.0f };
    AnimatedFloat hoverAnim_{ 0.0f, 15.0f };
};

} // namespace aero
