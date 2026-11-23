#pragma once
#include <d2d1.h>
#include <vector>
#include <memory>
#include "../render/D2DRenderer.h"
#include "../core/Window.h"

namespace aero {

class UIElement {
public:
    virtual ~UIElement() = default;

    virtual void Update(float /*dt*/) {}
    virtual void Render(D2DRenderer& /*r*/) {}

    virtual bool OnMouseMove(float x, float y) {
        bool wasHovered = isHovered_;
        isHovered_ = Contains(x, y);
        return isHovered_ != wasHovered;
    }

    virtual bool OnMouseButton(float x, float y, MouseBtn btn, bool down) {
        if (!isVisible_ || !isEnabled_) return false;
        if (Contains(x, y)) {
            if (btn == MouseBtn::Left) {
                isPressed_ = down;
                if (down) isFocused_ = true;
            }
            return true;
        } else {
            if (down) isFocused_ = false;
            isPressed_ = false;
        }
        return false;
    }

    virtual bool OnMouseWheel(float /*x*/, float /*y*/, float /*delta*/) { return false; }
    virtual bool OnChar(wchar_t /*ch*/) { return false; }
    virtual bool OnKey(int /*vk*/, bool /*down*/) { return false; }

    bool Contains(float x, float y) const {
        return x >= bounds_.left && x <= bounds_.right && y >= bounds_.top && y <= bounds_.bottom;
    }

    void SetBounds(const D2D1_RECT_F& bounds) { bounds_ = bounds; }
    void SetBounds(float x, float y, float w, float h) { bounds_ = D2D1::RectF(x, y, x + w, y + h); }
    const D2D1_RECT_F& GetBounds() const { return bounds_; }
    float GetWidth() const { return bounds_.right - bounds_.left; }
    float GetHeight() const { return bounds_.bottom - bounds_.top; }

    void SetVisible(bool v) { isVisible_ = v; }
    bool IsVisible() const { return isVisible_; }

    void SetEnabled(bool e) { isEnabled_ = e; }
    bool IsEnabled() const { return isEnabled_; }

    bool IsHovered() const { return isHovered_; }
    bool IsFocused() const { return isFocused_; }
    void SetFocused(bool f) { isFocused_ = f; }

protected:
    D2D1_RECT_F bounds_{ 0, 0, 0, 0 };
    bool isVisible_ = true;
    bool isEnabled_ = true;
    bool isHovered_ = false;
    bool isPressed_ = false;
    bool isFocused_ = false;
};

} // namespace aero
