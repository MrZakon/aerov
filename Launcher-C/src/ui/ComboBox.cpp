#include "ComboBox.h"
#include <algorithm>

namespace aero {

ComboBox::ComboBox(const std::vector<std::wstring>& items)
    : items_(items) {}

void ComboBox::SetItems(const std::vector<std::wstring>& items) {
    CloseDropdown();
    items_ = items;
    if (selectedIndex_ < 0 || selectedIndex_ >= static_cast<int>(items_.size())) {
        selectedIndex_ = items_.empty() ? -1 : 0;
    }
    scrollY_ = 0.0f;
    targetScrollY_ = 0.0f;
}

void ComboBox::RevealItem(int index) {
    const float itemH = GetHeight();
    const float top = index * itemH;
    const float bottom = top + itemH;
    const float visibleH = (std::min)(itemH * static_cast<float>(items_.size()), maxVisibleHeight_);
    if (top < targetScrollY_) targetScrollY_ = top;
    if (bottom > targetScrollY_ + visibleH) targetScrollY_ = bottom - visibleH;
}

void ComboBox::OpenDropdown() {
    if (items_.empty()) return;
    isExpanded_ = true;
    hoveredItemIndex_ = (std::max)(0, selectedIndex_);
    RevealItem(hoveredItemIndex_);
    scrollY_ = targetScrollY_;
}

void ComboBox::CloseDropdown() {
    isExpanded_ = false;
    hoveredItemIndex_ = -1;
}

void ComboBox::SelectItem(int index) {
    selectedIndex_ = index;
    CloseDropdown();
    if (onSelect_) onSelect_(selectedIndex_, items_[selectedIndex_]);
}

bool ComboBox::OnKey(int vk, bool down) {
    if (!down || !isVisible_ || !isEnabled_ || items_.empty() || (!isFocused_ && !isExpanded_)) return false;
    if (!isExpanded_) {
        if (vk != VK_RETURN && vk != VK_SPACE && vk != VK_UP && vk != VK_DOWN) return false;
        OpenDropdown();
        return true;
    }
    if (vk == VK_ESCAPE) {
        CloseDropdown();
        return true;
    }

    const int last = static_cast<int>(items_.size()) - 1;
    int next = hoveredItemIndex_ < 0 ? (std::max)(0, selectedIndex_) : hoveredItemIndex_;
    switch (vk) {
    case VK_UP: next = (std::max)(0, next - 1); break;
    case VK_DOWN: next = (std::min)(last, next + 1); break;
    case VK_HOME: next = 0; break;
    case VK_END: next = last; break;
    case VK_RETURN:
    case VK_SPACE:
        SelectItem(next);
        return true;
    default: return false;
    }
    hoveredItemIndex_ = next;
    RevealItem(next);
    return true;
}

void ComboBox::SetSelectedIndex(int index) {
    if (index >= 0 && index < static_cast<int>(items_.size())) {
        selectedIndex_ = index;
    }
}

std::wstring ComboBox::GetSelectedItem() const {
    if (selectedIndex_ >= 0 && selectedIndex_ < static_cast<int>(items_.size())) {
        return items_[selectedIndex_];
    }
    return L"";
}

void ComboBox::Update(float dt) {
    expandAnim_.SetTarget(isExpanded_ ? 1.0f : 0.0f);
    expandAnim_.Update(dt);

    if (isExpanded_) {
        float itemH = bounds_.bottom - bounds_.top;
        float totalH = static_cast<float>(items_.size()) * itemH;
        float maxScroll = (std::max)(0.0f, totalH - maxVisibleHeight_);
        targetScrollY_ = anim::Clamp(targetScrollY_, 0.0f, maxScroll);
        scrollY_ += (targetScrollY_ - scrollY_) * (std::min)(1.0f, dt * 18.0f);
    } else {
        scrollY_ = 0.0f;
        targetScrollY_ = 0.0f;
    }
}

bool ComboBox::OnMouseMove(float x, float y) {
    UIElement::OnMouseMove(x, y);

    if (isExpanded_) {
        float itemH = bounds_.bottom - bounds_.top;
        float dropTop = bounds_.bottom + 4.0f;
        float totalH = static_cast<float>(items_.size()) * itemH;
        float visibleH = (std::min)(totalH, maxVisibleHeight_);

        hoveredItemIndex_ = -1;
        if (x >= bounds_.left && x <= bounds_.right && y >= dropTop && y <= dropTop + visibleH) {
            float relY = (y - dropTop) + scrollY_;
            int idx = static_cast<int>(relY / itemH);
            if (idx >= 0 && idx < static_cast<int>(items_.size())) {
                hoveredItemIndex_ = idx;
            }
        }
        return true;
    }
    return false;
}

bool ComboBox::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (!isVisible_ || !isEnabled_) return false;

    if (btn == MouseBtn::Left && down) {
        if (Contains(x, y)) {
            isFocused_ = true;
            if (isExpanded_) CloseDropdown();
            else OpenDropdown();
            return true;
        }

        if (isExpanded_) {
            float itemH = bounds_.bottom - bounds_.top;
            float dropTop = bounds_.bottom + 4.0f;
            float totalH = static_cast<float>(items_.size()) * itemH;
            float visibleH = (std::min)(totalH, maxVisibleHeight_);

            if (x >= bounds_.left && x <= bounds_.right && y >= dropTop && y <= dropTop + visibleH) {
                float relY = (y - dropTop) + scrollY_;
                int clickedIndex = static_cast<int>(relY / itemH);
                if (clickedIndex >= 0 && clickedIndex < static_cast<int>(items_.size())) {
                    SelectItem(clickedIndex);
                    return true;
                }
            } else {
                CloseDropdown();
                return true;
            }
        }
    }
    return false;
}

bool ComboBox::OnMouseWheel(float x, float y, float delta) {
    if (!isExpanded_) return false;

    float dropTop = bounds_.bottom + 4.0f;
    float itemH = bounds_.bottom - bounds_.top;
    float totalH = static_cast<float>(items_.size()) * itemH;
    float visibleH = (std::min)(totalH, maxVisibleHeight_);

    if (x >= bounds_.left && x <= bounds_.right && y >= dropTop && y <= dropTop + visibleH) {
        float maxScroll = (std::max)(0.0f, totalH - maxVisibleHeight_);
        if (maxScroll > 0.0f) {
            targetScrollY_ -= delta * 36.0f;
            targetScrollY_ = anim::Clamp(targetScrollY_, 0.0f, maxScroll);
            return true;
        }
    }
    return false;
}

void ComboBox::Render(D2DRenderer& r) {
    if (!isVisible_) return;

    auto& theme = Theme::Instance();
    const bool active = isEnabled_ && (isFocused_ || isExpanded_);
    const auto bg = isEnabled_ && (isHovered_ || isExpanded_) ? theme.panel2 : theme.panel;
    const auto border = active ? theme.lineHi : theme.line;

    r.FillRoundedRect(bounds_, 6.0f, bg);
    r.DrawRoundedRect(bounds_, 6.0f, border, active ? 1.5f : 1.0f);

    // Selected text
    D2D1_RECT_F textRect = D2D1::RectF(bounds_.left + 12.0f, bounds_.top, bounds_.right - 30.0f, bounds_.bottom);
    std::wstring label = GetSelectedItem();
    r.DrawString(label, textRect, isEnabled_ ? theme.text : theme.muted, 12.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

    // Arrow icon
    float t = expandAnim_.Get();
    float arrowX = bounds_.right - 18.0f;
    float arrowY = (bounds_.top + bounds_.bottom) * 0.5f;
    float arrowDir = (t - 0.5f) * 4.0f;
    D2D1_COLOR_F arrowCol = (isHovered_ || isExpanded_) ? theme.accent2 : theme.muted;
    r.DrawLine(D2D1::Point2F(arrowX - 4.0f, arrowY - arrowDir), D2D1::Point2F(arrowX, arrowY + arrowDir), arrowCol, 1.5f);
    r.DrawLine(D2D1::Point2F(arrowX, arrowY + arrowDir), D2D1::Point2F(arrowX + 4.0f, arrowY - arrowDir), arrowCol, 1.5f);
}

void ComboBox::RenderDropdown(D2DRenderer& r) {
    float t = expandAnim_.Get();
    if (!isVisible_ || t < 0.01f || items_.empty()) return;

    auto& theme = Theme::Instance();
    float itemH = bounds_.bottom - bounds_.top;
    float dropTop = bounds_.bottom + 4.0f;
    float totalH = static_cast<float>(items_.size()) * itemH;
    float visibleH = (std::min)(totalH, maxVisibleHeight_);
    float currentH = visibleH * t;

    D2D1_RECT_F dropRect = D2D1::RectF(bounds_.left, dropTop, bounds_.right, dropTop + currentH);

    // Dropdown container background & shadow
    r.FillRoundedRect(dropRect, 6.0f, theme.surface);
    r.DrawRoundedRect(dropRect, 6.0f, theme.lineHi);

    r.PushClip(dropRect);

    float scrollOffset = scrollY_;

    for (size_t i = 0; i < items_.size(); ++i) {
        float top = dropTop + i * itemH - scrollOffset;
        float bottom = top + itemH;

        if (bottom < dropTop || top > dropTop + currentH) continue;

        D2D1_RECT_F itemRect = D2D1::RectF(bounds_.left + 4.0f, top + 2.0f, bounds_.right - 4.0f, bottom - 2.0f);

        bool isSel = (static_cast<int>(i) == selectedIndex_);
        bool isHov = (static_cast<int>(i) == hoveredItemIndex_);

        if (isSel) {
            r.FillRoundedRect(itemRect, 6.0f, Color::FromRgba(240, 183, 130, 0.22f));
            r.DrawRoundedRect(itemRect, 6.0f, Color::FromRgba(240, 183, 130, 0.30f), 1.0f);
        } else if (isHov) {
            r.FillRoundedRect(itemRect, 6.0f, Color::FromRgba(255, 255, 255, 0.07f));
        }

        D2D1_RECT_F textRect = D2D1::RectF(bounds_.left + 14.0f, top, bounds_.right - 14.0f, bottom);
        D2D1_COLOR_F itemColor = isSel ? theme.accent2 : (isHov ? D2D1::ColorF(1.0f, 1.0f, 1.0f) : theme.text);
        r.DrawString(items_[i], textRect, itemColor, 12.0f, isSel ? FontStyle::Bold : FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

        if (isSel) {
            float dotX = bounds_.right - 18.0f;
            float dotY = (top + bottom) * 0.5f;
            D2D1_RECT_F dotRect = D2D1::RectF(dotX - 3.0f, dotY - 3.0f, dotX + 3.0f, dotY + 3.0f);
            r.FillRoundedRect(dotRect, 2.0f, theme.mint);
        }
    }

    // Scrollbar indicator if items overflow
    if (totalH > visibleH && currentH >= visibleH * 0.5f) {
        float trackH = currentH - 8.0f;
        float thumbH = (std::max)(18.0f, (visibleH / totalH) * trackH);
        float thumbY = dropTop + 4.0f + (scrollOffset / (totalH - visibleH)) * (trackH - thumbH);
        D2D1_RECT_F thumbRect = D2D1::RectF(bounds_.right - 6.0f, thumbY, bounds_.right - 2.0f, thumbY + thumbH);
        r.FillRoundedRect(thumbRect, 2.0f, Color::FromRgba(240, 183, 130, 0.50f));
    }

    r.PopClip();
}

} // namespace aero
