#pragma once
#include "UIElement.h"
#include <string>
#include <functional>
#include "../core/Animation.h"

namespace aero {

class TextInput : public UIElement {
public:
    TextInput(const std::wstring& placeholder = L"");

    void SetText(const std::wstring& text);
    const std::wstring& GetText() const { return text_; }

    void SetPlaceholder(const std::wstring& placeholder) { placeholder_ = placeholder; }
    void SetPassword(bool isPassword) { isPassword_ = isPassword; }

    void SetOnChange(std::function<void(const std::wstring&)> cb) { onChange_ = cb; }
    void SetOnEnter(std::function<void()> cb) { onEnter_ = cb; }

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

private:
    std::wstring text_;
    std::wstring placeholder_;
    bool isPassword_ = false;

    int cursor_ = 0;
    float blinkTimer_ = 0.0f;
    bool showCaret_ = true;
    bool selectAll_ = false;

    std::function<void(const std::wstring&)> onChange_;
    std::function<void()> onEnter_;

    AnimatedFloat focusAnim_{ 0.0f, 15.0f };
    AnimatedFloat hoverAnim_{ 0.0f, 15.0f };
};

} // namespace aero
