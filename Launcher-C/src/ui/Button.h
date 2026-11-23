#pragma once
#include "UIElement.h"
#include <string>
#include <functional>
#include "../core/Animation.h"

namespace aero {

enum class ButtonStyle {
    Default,
    Primary,
    Danger,
    Play,
    Ghost,
    Icon
};

class Button : public UIElement {
public:
    Button(const std::wstring& text = L"", ButtonStyle style = ButtonStyle::Default);

    void SetText(const std::wstring& text) { text_ = text; }
    const std::wstring& GetText() const { return text_; }

    void SetIcon(const std::string& iconName) { icon_ = iconName; }
    void SetStyle(ButtonStyle style) { style_ = style; }

    void SetOnClick(std::function<void()> onClick) { onClick_ = onClick; }

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnKey(int vk, bool down) override;

private:
    std::wstring text_;
    std::string icon_;
    ButtonStyle style_ = ButtonStyle::Default;
    std::function<void()> onClick_;

    AnimatedFloat hoverAnim_{ 0.0f, 16.0f };
};

} // namespace aero
