#include "TextInput.h"
#include <windows.h>

namespace aero {

TextInput::TextInput(const std::wstring& placeholder)
    : placeholder_(placeholder) {}

void TextInput::SetText(const std::wstring& text) {
    text_ = text;
    cursor_ = static_cast<int>(text_.length());
    if (onChange_) onChange_(text_);
}

void TextInput::Update(float dt) {
    focusAnim_.SetTarget(isFocused_ ? 1.0f : 0.0f);
    focusAnim_.Update(dt);
    hoverAnim_.SetTarget(isHovered_ ? 1.0f : 0.0f);
    hoverAnim_.Update(dt);

    if (isFocused_) {
        blinkTimer_ += dt;
        if (blinkTimer_ >= 0.5f) {
            blinkTimer_ = 0.0f;
            showCaret_ = !showCaret_;
        }
    } else {
        showCaret_ = false;
        blinkTimer_ = 0.0f;
    }
}

bool TextInput::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (!isVisible_ || !isEnabled_) return false;

    if (Contains(x, y)) {
        if (btn == MouseBtn::Left && down) {
            isFocused_ = true;
            showCaret_ = true;
            blinkTimer_ = 0.0f;
            selectAll_ = false;
            cursor_ = static_cast<int>(text_.length());
        }
        return true;
    } else {
        if (down) {
            isFocused_ = false;
            selectAll_ = false;
        }
    }
    return false;
}

bool TextInput::OnChar(wchar_t ch) {
    if (!isFocused_ || !isEnabled_) return false;

    if (ch >= 32) { // Printable character
        if (selectAll_) {
            text_.clear();
            cursor_ = 0;
            selectAll_ = false;
        }
        text_.insert(cursor_, 1, ch);
        cursor_++;
        showCaret_ = true;
        blinkTimer_ = 0.0f;
        if (onChange_) onChange_(text_);
        return true;
    }
    return false;
}

bool TextInput::OnKey(int vk, bool down) {
    if (!isFocused_ || !isEnabled_ || !down) return false;

    bool ctrl = (GetKeyState(VK_CONTROL) & 0x8000) != 0;

    if (ctrl && (vk == 'V' || vk == 'v')) {
        // Paste from clipboard
        if (selectAll_) {
            text_.clear();
            cursor_ = 0;
            selectAll_ = false;
        }
        if (OpenClipboard(nullptr)) {
            HANDLE hData = GetClipboardData(CF_UNICODETEXT);
            if (hData) {
                auto* pText = static_cast<wchar_t*>(GlobalLock(hData));
                if (pText) {
                    std::wstring pasted(pText);
                    text_.insert(cursor_, pasted);
                    cursor_ += static_cast<int>(pasted.length());
                    GlobalUnlock(hData);
                    if (onChange_) onChange_(text_);
                }
            }
            CloseClipboard();
        }
        return true;
    }

    if (ctrl && (vk == 'A' || vk == 'a')) {
        selectAll_ = true;
        cursor_ = static_cast<int>(text_.length());
        return true;
    }

    if (vk == VK_BACK) {
        if (selectAll_) {
            text_.clear();
            cursor_ = 0;
            selectAll_ = false;
            showCaret_ = true;
            blinkTimer_ = 0.0f;
            if (onChange_) onChange_(text_);
            return true;
        }
        if (cursor_ > 0 && !text_.empty()) {
            text_.erase(cursor_ - 1, 1);
            cursor_--;
            showCaret_ = true;
            blinkTimer_ = 0.0f;
            if (onChange_) onChange_(text_);
        }
        return true;
    }

    if (vk == VK_DELETE) {
        if (selectAll_) {
            text_.clear();
            cursor_ = 0;
            selectAll_ = false;
            showCaret_ = true;
            blinkTimer_ = 0.0f;
            if (onChange_) onChange_(text_);
            return true;
        }
        if (cursor_ < static_cast<int>(text_.length())) {
            text_.erase(cursor_, 1);
            showCaret_ = true;
            blinkTimer_ = 0.0f;
            if (onChange_) onChange_(text_);
        }
        return true;
    }

    if (vk == VK_RETURN) {
        if (onEnter_) onEnter_();
        return true;
    }

    if (vk == VK_LEFT) {
        selectAll_ = false;
        if (cursor_ > 0) cursor_--;
        showCaret_ = true;
        blinkTimer_ = 0.0f;
        return true;
    }

    if (vk == VK_RIGHT) {
        if (cursor_ < static_cast<int>(text_.length())) cursor_++;
        showCaret_ = true;
        blinkTimer_ = 0.0f;
        return true;
    }

    if (vk == VK_HOME) {
        cursor_ = 0;
        showCaret_ = true;
        blinkTimer_ = 0.0f;
        return true;
    }

    if (vk == VK_END) {
        cursor_ = static_cast<int>(text_.length());
        showCaret_ = true;
        blinkTimer_ = 0.0f;
        return true;
    }

    return false;
}

void TextInput::Render(D2DRenderer& r) {
    if (!isVisible_) return;

    auto& theme = Theme::Instance();
    float f = focusAnim_.Get();
    float h = hoverAnim_.Get();

    const auto bg = anim::LerpColor(theme.surface, theme.panel, f * 0.6f + h * 0.3f);
    const auto border = anim::LerpColor(theme.line, theme.lineHi, f);
    r.FillRoundedRect(bounds_, 6.0f, bg);
    r.DrawRoundedRect(bounds_, 6.0f, border, f > 0.01f ? 1.5f : 1.0f);

    D2D1_RECT_F textRect = D2D1::RectF(bounds_.left + 12.0f, bounds_.top, bounds_.right - 12.0f, bounds_.bottom);

    std::wstring displayStr;
    if (text_.empty()) {
        r.DrawString(placeholder_, textRect, theme.muted, 13.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
        // Caret when focused on empty input
        if (isFocused_ && showCaret_) {
            float caretX = textRect.left;
            float cy = (bounds_.top + bounds_.bottom) * 0.5f;
            r.DrawLine(D2D1::Point2F(caretX, cy - 8.0f), D2D1::Point2F(caretX, cy + 8.0f), theme.accent2, 1.5f);
        }
    } else {
        if (isPassword_) {
            displayStr = std::wstring(text_.length(), L'•');
        } else {
            displayStr = text_;
        }
        r.DrawString(displayStr, textRect, theme.text, 12.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

        // Caret
        if (isFocused_ && showCaret_) {
            std::wstring sub = displayStr.substr(0, cursor_);
            D2D1_SIZE_F subSize = r.MeasureString(sub, 12.0f, FontStyle::Regular);
            float caretX = textRect.left + subSize.width;
            float cy = (bounds_.top + bounds_.bottom) * 0.5f;
            r.DrawLine(D2D1::Point2F(caretX, cy - 8.0f), D2D1::Point2F(caretX, cy + 8.0f), theme.accent2, 1.5f);
        }
    }
}

} // namespace aero
