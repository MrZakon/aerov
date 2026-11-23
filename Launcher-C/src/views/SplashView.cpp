#include "SplashView.h"
#include "../render/Theme.h"
#include "../core/Animation.h"
#include <cmath>
#include <filesystem>
#include <vector>

namespace aero {

static constexpr float kTransStartTime = 1.35f;
static constexpr float kExitDuration = 0.65f;
static constexpr float kTotalDuration = kTransStartTime + kExitDuration;

SplashView::SplashView() {
}

void SplashView::Update(float dt) {
    if (isFinished_) return;
    animTime_ += dt;
    if (animTime_ >= kTotalDuration) {
        isFinished_ = true;
    }
}

void SplashView::Skip() {
    if (animTime_ < kTransStartTime) {
        animTime_ = kTransStartTime;
    } else if (animTime_ < kTotalDuration) {
        animTime_ = kTotalDuration;
        isFinished_ = true;
    }
}

float SplashView::GetMainUiAlpha() const {
    if (isFinished_) return 1.0f;
    if (animTime_ < kTransStartTime) return 0.0f;
    float t = anim::Clamp((animTime_ - kTransStartTime) / kExitDuration, 0.0f, 1.0f);
    return anim::EaseOutCubic(t);
}

float SplashView::GetMainUiScale() const {
    if (isFinished_) return 1.0f;
    if (animTime_ < kTransStartTime) return 0.96f;
    float t = anim::Clamp((animTime_ - kTransStartTime) / kExitDuration, 0.0f, 1.0f);
    return 0.96f + 0.04f * anim::EaseOutCubic(t);
}

float SplashView::GetMainUiOffsetY() const {
    if (isFinished_) return 0.0f;
    if (animTime_ < kTransStartTime) return 18.0f;
    float t = anim::Clamp((animTime_ - kTransStartTime) / kExitDuration, 0.0f, 1.0f);
    return 18.0f * (1.0f - anim::EaseOutCubic(t));
}

float SplashView::GetSplashAlpha() const {
    if (isFinished_) return 0.0f;
    if (animTime_ < kTransStartTime) return 1.0f;
    float t = anim::Clamp((animTime_ - kTransStartTime) / kExitDuration, 0.0f, 1.0f);
    return 1.0f - anim::EaseInOutQuad(t);
}

void SplashView::Render(D2DRenderer& r, float width, float height) {
    if(isFinished_) return;
    float alpha=GetSplashAlpha();
    if(alpha<=.002f) return;
    auto& t=Theme::Instance();
    r.PushLayer(alpha);
    r.FillRect(D2D1::RectF(0,0,width,height),t.bg);
    float x=width*.5f, y=height*.5f;
    r.DrawLine(D2D1::Point2F(x-32,y-22),D2D1::Point2F(x,y-86),t.accent1,4);
    r.DrawLine(D2D1::Point2F(x,y-86),D2D1::Point2F(x+32,y-22),t.accent1,4);
    r.DrawLine(D2D1::Point2F(x-13,y-42),D2D1::Point2F(x+23,y-42),t.accent1,3);
    r.DrawString(L"A E R O",D2D1::RectF(x-200,y+4,x+200,y+64),t.text,38,FontStyle::Bold,DWRITE_TEXT_ALIGNMENT_CENTER);
    r.DrawString(L"ТВОЙ СЛЕДУЮЩИЙ МИР",D2D1::RectF(x-200,y+75,x+200,y+100),t.muted,10,FontStyle::Regular,DWRITE_TEXT_ALIGNMENT_CENTER);
    r.FillRoundedRect(D2D1::RectF(x-72,y+122,x+72,y+125),1.5f,t.panel2);
    float progress=anim::Clamp(animTime_/kTransStartTime,0.f,1.f);
    r.FillRoundedRect(D2D1::RectF(x-72,y+122,x-72+144*progress,y+125),1.5f,t.accent1);
    r.PopLayer();
}

} // namespace aero
