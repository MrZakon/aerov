#include "ScrollView.h"

namespace aero {

ScrollView::ScrollView() = default;

void ScrollView::ScrollToBottom(bool instant) {
    float viewH = bounds_.bottom - bounds_.top;
    float maxScroll = (std::max)(0.0f, contentHeight_ - viewH);
    targetScrollY_ = maxScroll;
    if (instant) {
        scrollY_.SnapTo(maxScroll);
    }
}

void ScrollView::Reveal(float top, float bottom) {
    const float viewport = GetHeight();
    const float offset = scrollY_.Get();
    const float next = top < offset ? top : (std::max)(offset, bottom - viewport);
    targetScrollY_ = anim::Clamp(next, 0.0f, (std::max)(0.0f, contentHeight_ - viewport));
    scrollY_.SnapTo(targetScrollY_);
}

bool ScrollView::IsAtBottom(float tolerance) const {
    float viewH = bounds_.bottom - bounds_.top;
    float maxScroll = (std::max)(0.0f, contentHeight_ - viewH);
    if (maxScroll <= 0.0f) return true;
    return (maxScroll - targetScrollY_) <= tolerance;
}

void ScrollView::Update(float dt) {
    float viewH = bounds_.bottom - bounds_.top;
    float maxScroll = (std::max)(0.0f, contentHeight_ - viewH);
    targetScrollY_ = anim::Clamp(targetScrollY_, 0.0f, maxScroll);

    scrollY_.SetTarget(targetScrollY_);
    scrollY_.Update(dt);

    if (isDraggingScrollbar_ || isThumbHovered_) {
        scrollbarTimer_ = 1.0f;
        scrollbarOpacity_.SetTarget(1.0f);
    } else if (scrollbarTimer_ > 0.0f) {
        scrollbarTimer_ -= dt;
        scrollbarOpacity_.SetTarget(1.0f);
    } else {
        scrollbarOpacity_.SetTarget(isHovered_ ? 0.6f : 0.0f);
    }
    scrollbarOpacity_.Update(dt);
}

bool ScrollView::OnMouseMove(float x, float y) {
    float viewH = bounds_.bottom - bounds_.top;
    float maxScroll = (std::max)(0.0f, contentHeight_ - viewH);

    if (isDraggingScrollbar_) {
        if (maxScroll > 0.0f && viewH > 20.0f) {
            float thumbH = (std::max)(28.0f, (viewH / contentHeight_) * viewH);
            float thumbRange = viewH - thumbH;
            if (thumbRange > 0.0f) {
                float deltaY = y - dragStartY_;
                float scrollDelta = (deltaY / thumbRange) * maxScroll;
                targetScrollY_ = anim::Clamp(dragStartScrollY_ + scrollDelta, 0.0f, maxScroll);
                scrollY_.SnapTo(targetScrollY_);
            }
        }
        scrollbarTimer_ = 2.0f;
        return true;
    }

    if (contentHeight_ > viewH && viewH > 20.0f) {
        bool inTrack = (x >= bounds_.right - 14.0f && x <= bounds_.right + 4.0f && y >= bounds_.top && y <= bounds_.bottom);
        isThumbHovered_ = inTrack;
        if (isThumbHovered_) {
            scrollbarTimer_ = 1.5f;
        }
    } else {
        isThumbHovered_ = false;
    }

    return false;
}

bool ScrollView::OnMouseWheel(float x, float y, float delta) {
    if (!Contains(x, y)) return false;

    float viewH = bounds_.bottom - bounds_.top;
    float maxScroll = (std::max)(0.0f, contentHeight_ - viewH);
    if (maxScroll <= 0.0f) return false;

    targetScrollY_ -= delta * 45.0f;
    targetScrollY_ = anim::Clamp(targetScrollY_, 0.0f, maxScroll);
    scrollbarTimer_ = 1.5f;
    return true;
}

bool ScrollView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (btn != MouseBtn::Left) return false;

    if (!down) {
        if (isDraggingScrollbar_) {
            isDraggingScrollbar_ = false;
            scrollbarTimer_ = 1.5f;
            return true;
        }
        return false;
    }

    float viewH = bounds_.bottom - bounds_.top;
    float maxScroll = (std::max)(0.0f, contentHeight_ - viewH);
    if (maxScroll <= 0.0f || viewH <= 20.0f) return false;

    // Check click in scrollbar hit zone (right 14px)
    if (x >= bounds_.right - 14.0f && x <= bounds_.right + 4.0f && y >= bounds_.top && y <= bounds_.bottom) {
        isDraggingScrollbar_ = true;
        scrollbarTimer_ = 2.0f;
        dragStartY_ = y;
        dragStartScrollY_ = targetScrollY_;

        float thumbH = (std::max)(28.0f, (viewH / contentHeight_) * viewH);
        float thumbRange = viewH - thumbH;
        float scrollFraction = (maxScroll > 0.0f) ? (targetScrollY_ / maxScroll) : 0.0f;
        float thumbY = bounds_.top + scrollFraction * thumbRange;

        // If clicked on track outside the thumb, immediately snap thumb center to clicked y
        if (y < thumbY || y > thumbY + thumbH) {
            float clickFraction = anim::Clamp((y - bounds_.top - thumbH * 0.5f) / thumbRange, 0.0f, 1.0f);
            targetScrollY_ = clickFraction * maxScroll;
            scrollY_.SnapTo(targetScrollY_);
            dragStartY_ = y;
            dragStartScrollY_ = targetScrollY_;
        }

        return true;
    }

    return false;
}

void ScrollView::BeginRender(D2DRenderer& r) {
    r.GetRenderTarget()->GetTransform(&savedTransform_);
    r.PushClip(bounds_);
    // Apply transform offset for contents composing with parent transform
    D2D1_MATRIX_3X2_F scrollTrans = D2D1::Matrix3x2F::Translation(0.0f, -scrollY_.Get());
    r.GetRenderTarget()->SetTransform(scrollTrans * savedTransform_);
}

void ScrollView::EndRender(D2DRenderer& r) {
    // Reset transform to parent transform
    r.GetRenderTarget()->SetTransform(savedTransform_);
    r.PopClip();

    // Render scrollbar thumb if content overflows
    float viewH = bounds_.bottom - bounds_.top;
    if (contentHeight_ > viewH && viewH > 20.0f) {
        float opacity = scrollbarOpacity_.Get();
        if (isDraggingScrollbar_ || isThumbHovered_) {
            opacity = 1.0f;
        }

        if (opacity > 0.02f) {
            float thumbH = (std::max)(28.0f, (viewH / contentHeight_) * viewH);
            float scrollFraction = scrollY_.Get() / (contentHeight_ - viewH);
            float thumbY = bounds_.top + scrollFraction * (viewH - thumbH);

            float barWidth = (isDraggingScrollbar_ || isThumbHovered_) ? 7.0f : 5.0f;
            D2D1_RECT_F thumbRect = D2D1::RectF(bounds_.right - barWidth - 2.0f, thumbY, bounds_.right - 2.0f, thumbY + thumbH);

            // Subtle track background on hover/drag
            if (isDraggingScrollbar_ || isThumbHovered_) {
                D2D1_RECT_F trackRect = D2D1::RectF(bounds_.right - 9.0f, bounds_.top + 2.0f, bounds_.right - 1.0f, bounds_.bottom - 2.0f);
                r.FillRoundedRect(trackRect, 4.0f, Color::FromRgba(240, 183, 130, 0.06f));
            }

            D2D1_COLOR_F barColor;
            if (isDraggingScrollbar_) {
                barColor = Color::FromHex(0xb4bdaa); // Active neon cyan when dragging
            } else if (isThumbHovered_) {
                barColor = Color::FromRgba(207, 189, 154, 0.85f);
            } else {
                barColor = Color::FromRgba(207, 189, 154, opacity * 0.50f);
            }

            r.FillRoundedRect(thumbRect, barWidth * 0.5f, barColor);
        }
    }
}

} // namespace aero
