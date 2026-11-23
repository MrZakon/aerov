#pragma once
#include "UIElement.h"
#include <vector>
#include <memory>
#include "../core/Animation.h"

namespace aero {

class ScrollView : public UIElement {
public:
    ScrollView();

    void SetContentHeight(float contentHeight) { contentHeight_ = contentHeight; }
    float GetContentHeight() const { return contentHeight_; }
    float GetScrollOffset() const { return scrollY_.Get(); }

    void ResetScroll() { scrollY_.SnapTo(0.0f); targetScrollY_ = 0.0f; }
    void ScrollToBottom(bool instant = false);
    void Reveal(float top, float bottom);
    bool IsAtBottom(float tolerance = 25.0f) const;

    void Update(float dt) override;
    void BeginRender(D2DRenderer& r);
    void EndRender(D2DRenderer& r);

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;

    bool IsDraggingScrollbar() const { return isDraggingScrollbar_; }

private:
    float contentHeight_ = 0.0f;
    float targetScrollY_ = 0.0f;
    AnimatedFloat scrollY_{ 0.0f, 15.0f };
    AnimatedFloat scrollbarOpacity_{ 0.0f, 8.0f };
    float scrollbarTimer_ = 0.0f;
    D2D1_MATRIX_3X2_F savedTransform_ = D2D1::Matrix3x2F::Identity();

    bool isDraggingScrollbar_ = false;
    bool isThumbHovered_ = false;
    float dragStartY_ = 0.0f;
    float dragStartScrollY_ = 0.0f;
};

} // namespace aero
