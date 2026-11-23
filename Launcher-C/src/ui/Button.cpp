#include "Button.h"

namespace aero {

Button::Button(const std::wstring& text, ButtonStyle style)
    : text_(text), style_(style) {}

void Button::Update(float dt) {
    hoverAnim_.SetTarget(isHovered_ ? 1.0f : 0.0f);
    hoverAnim_.Update(dt);

}

bool Button::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (!isVisible_ || !isEnabled_) return false;

    if (Contains(x, y)) {
        if (btn == MouseBtn::Left) {
            if (down) {
                isPressed_ = true;
            } else {
                if (isPressed_) {
                    isPressed_ = false;
                    if (onClick_) {
                        onClick_();
                    }
                }
            }
        }
        return true;
    } else {
        isPressed_ = false;
    }
    return false;
}

bool Button::OnKey(int vk, bool down) {
    if (!down || !isVisible_ || !isEnabled_ || !isFocused_ || !onClick_) return false;
    if (vk != VK_RETURN && vk != VK_SPACE) return false;
    onClick_();
    return true;
}

void Button::Render(D2DRenderer& r) {
    if(!isVisible_) return;
    auto& t=Theme::Instance();
    float hover=hoverAnim_.Get();
    auto b=bounds_;
    if(isPressed_) { b.top+=1; b.bottom+=1; }
    bool primary=style_==ButtonStyle::Primary || style_==ButtonStyle::Play;
    auto fg=t.text;
    if(primary) {
        auto bg=t.accent1;
        bg.r=anim::Clamp(bg.r+hover*.06f,0.f,1.f);
        bg.g=anim::Clamp(bg.g+hover*.06f,0.f,1.f);
        bg.b=anim::Clamp(bg.b+hover*.06f,0.f,1.f);
        if(!isEnabled_) bg=t.panel2;
        r.FillRoundedRect(b,9,bg);
        fg=t.bg;
    } else if(style_==ButtonStyle::Danger) {
        r.FillRoundedRect(b,8,Color::FromRgba(214,112,100,.12f+hover*.1f));
        fg=t.red;
    } else if(style_==ButtonStyle::Ghost) {
        if(hover>.01f) r.FillRoundedRect(b,8,Color::FromRgba(200,210,180,hover*.07f));
        fg=hover>.2f?t.text:t.muted;
    } else {
        r.FillRoundedRect(b,8,hover>.2f?t.panel2:t.panel);
        r.DrawRoundedRect(b,8,hover>.2f?t.lineHi:t.line);
    }
    if(!isEnabled_) fg=t.muted;
    float cy=(b.top+b.bottom)*.5f;
    if(style_==ButtonStyle::Icon) {
        if(!icon_.empty()) r.DrawIcon(icon_,D2D1::RectF((b.left+b.right)*.5f-7,cy-7,(b.left+b.right)*.5f+7,cy+7),fg,1.5f);
    } else if(!icon_.empty()) {
        float size=primary?17.f:15.f;
        r.DrawIcon(icon_,D2D1::RectF(b.left+16,cy-size*.5f,b.left+16+size,cy+size*.5f),fg,1.6f);
        r.DrawString(text_,D2D1::RectF(b.left+42,b.top,b.right-12,b.bottom),fg,primary?13.f:12.f,primary?FontStyle::Bold:FontStyle::Regular,DWRITE_TEXT_ALIGNMENT_LEADING,DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    } else {
        r.DrawString(text_,b,fg,primary?13.f:12.f,primary?FontStyle::Bold:FontStyle::Regular,DWRITE_TEXT_ALIGNMENT_CENTER,DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    }
    if (isFocused_ && isEnabled_) r.DrawRoundedRect(b, 8, t.lineHi, 1.5f);
}

} // namespace aero
