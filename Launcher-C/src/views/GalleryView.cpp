#include "GalleryView.h"
#include "../store/SettingsManager.h"
#include "../core/StringUtils.h"
#include <shellapi.h>
#include <filesystem>

namespace aero {

namespace fs = std::filesystem;

GalleryView::GalleryView() {
    btnOpenFolder_.SetOnClick([]() {
        auto& settings = SettingsManager::Instance().Get();
        fs::path scDir = fs::path(settings.gameDir) / L"screenshots";
        std::error_code ec;
        fs::create_directories(scDir, ec);
        ShellExecuteW(nullptr, L"open", scDir.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
    });

    RefreshData();
}

void GalleryView::RefreshData() {
    auto& settings = SettingsManager::Instance().Get();
    auto list = InstancesManager::Instance().GetScreenshots(settings.gameDir);
    cards_.clear();
    for (const auto& s : list) {
        ScreenshotCard c;
        c.info = s;
        cards_.push_back(c);
    }

    int rows = static_cast<int>((cards_.size() + 3) / 4);
    scrollView_.SetContentHeight(rows * 160.0f + 50.0f);
}

void GalleryView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);

    float startX = 36.0f;
    float startY = 24.0f;
    float contentW = width - 72.0f;

    btnOpenFolder_.SetBounds(startX + contentW - 220.0f, startY, 220.0f, 38.0f);

    float listY = startY + 50.0f;
    float listH = height - listY - 20.0f;
    scrollView_.SetBounds(startX, listY, contentW, listH);
}

void GalleryView::Update(float dt) {
    animTime_ += dt;
    btnOpenFolder_.Update(dt);
    scrollView_.Update(dt);
    for (auto& c : cards_) {
        c.hoverAnim.Update(dt);
    }
}

bool GalleryView::OnMouseMove(float x, float y) {
    bool handled = false;
    handled |= btnOpenFolder_.OnMouseMove(x, y);
    handled |= scrollView_.OnMouseMove(x, y);

    float startX = 36.0f;
    float startY = 24.0f + 50.0f;
    float contentW = bounds_.right - 72.0f;
    float cardW = (contentW - 45.0f) / 4.0f;
    float cardH = 140.0f;
    float scrollOffset = scrollView_.GetScrollOffset();

    for (size_t i = 0; i < cards_.size(); ++i) {
        int col = static_cast<int>(i % 4);
        int row = static_cast<int>(i / 4);
        float cx = startX + col * (cardW + 15.0f);
        float cy = startY + row * (cardH + 15.0f) - scrollOffset;

        bool hovered = (x >= cx && x <= cx + cardW && y >= cy && y <= cy + cardH);
        cards_[i].hoverAnim.SetTarget(hovered ? 1.0f : 0.0f);
    }

    return handled;
}

bool GalleryView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (btnOpenFolder_.OnMouseButton(x, y, btn, down)) return true;

    // Check screenshot card clicks FIRST
    if (btn == MouseBtn::Left && !down) {
        float startX = 36.0f;
        float startY = 24.0f + 50.0f;
        float contentW = bounds_.right - 72.0f;
        float cardW = (contentW - 45.0f) / 4.0f;
        float cardH = 140.0f;
        float scrollOffset = scrollView_.GetScrollOffset();

        for (size_t i = 0; i < cards_.size(); ++i) {
            int col = static_cast<int>(i % 4);
            int row = static_cast<int>(i / 4);
            float cx = startX + col * (cardW + 15.0f);
            float cy = startY + row * (cardH + 15.0f) - scrollOffset;

            if (x >= cx && x <= cx + cardW && y >= cy && y <= cy + cardH) {
                ShellExecuteW(nullptr, L"open", cards_[i].info.filePath.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
                return true;
            }
        }
    }

    return scrollView_.OnMouseButton(x, y, btn, down);
}

bool GalleryView::OnMouseWheel(float x, float y, float delta) {
    return scrollView_.OnMouseWheel(x, y, delta);
}

void GalleryView::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    float startX = 36.0f;
    float startY = 24.0f;
    float contentW = bounds_.right - 72.0f;

    r.DrawString(L"Галерея", D2D1::RectF(startX, startY, startX + contentW - 240.0f, startY + 30.0f), theme.text, 18.0f, FontStyle::Bold);
    btnOpenFolder_.Render(r);

    if (cards_.empty()) {
        r.DrawString(L"Скриншотов пока нет. Нажмите F2 в игре, чтобы сделать снимок.", D2D1::RectF(startX, startY + 120.0f, startX + contentW, startY + 160.0f), theme.muted, 13.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_CENTER);
        return;
    }

    scrollView_.BeginRender(r);

    float listY = startY + 50.0f;
    float cardW = (contentW - 45.0f) / 4.0f;
    float cardH = 140.0f;

    for (size_t i = 0; i < cards_.size(); ++i) {
        auto& c = cards_[i];
        float h = c.hoverAnim.Get();
        int col = static_cast<int>(i % 4);
        int row = static_cast<int>(i / 4);
        float cx = startX + col * (cardW + 15.0f);
        float cy = listY + row * (cardH + 15.0f) - h * 3.5f;

        D2D1_RECT_F itemRect = D2D1::RectF(cx, cy, cx + cardW, cy + cardH);

        // Hover glow
        if (h > 0.05f) {
            D2D1_RECT_F haloRect = D2D1::RectF(itemRect.left - 2.0f, itemRect.top - 2.0f, itemRect.right + 2.0f, itemRect.bottom + 2.0f);
            r.DrawRoundedRect(haloRect, 8.0f, Color::FromRgba(240, 183, 130, 0.16f * h), 1.5f);
        }

        r.FillRoundedRect(itemRect, 8.0f, Color::FromHex(0x252b24));
        r.DrawRoundedRect(itemRect, 8.0f, anim::LerpColor(theme.line, theme.accent2, h * 0.7f), h > 0.1f ? 1.5f : 1.0f);

        // Thumbnail image
        ID2D1Bitmap* bmp = r.LoadBitmapFromFile(c.info.filePath);
        if (bmp) {
            D2D1_RECT_F imgRect = D2D1::RectF(cx + 2.0f, cy + 2.0f, cx + cardW - 2.0f, cy + cardH - 36.0f);
            r.PushClip(imgRect);
            r.DrawBitmap(bmp, imgRect);
            r.PopClip();
        } else {
            D2D1_RECT_F phRect = D2D1::RectF(cx, cy, cx + cardW, cy + cardH - 36.0f);
            r.DrawIcon("gallery", phRect, theme.muted, 1.5f);
        }

        // Overlay caption
        D2D1_RECT_F captionRect = D2D1::RectF(cx + 8.0f, cy + cardH - 32.0f, cx + cardW - 8.0f, cy + cardH - 8.0f);
        r.DrawString(Utf8ToWide(c.info.fileName), captionRect, anim::LerpColor(theme.text, D2D1::ColorF(1.0f, 1.0f, 1.0f), h * 0.5f), 10.0f, FontStyle::Bold);

        // Hover view badge
        if (h > 0.05f) {
            D2D1_RECT_F badge = D2D1::RectF(cx + cardW - 65.0f, cy + 8.0f, cx + cardW - 8.0f, cy + 28.0f);
            r.FillRoundedRect(badge, 6.0f, Color::FromRgba(32, 35, 32, 0.85f * h));
            r.DrawRoundedRect(badge, 6.0f, Color::FromRgba(240, 183, 130, 0.40f * h), 1.0f);
            r.DrawString(L"Открыть ↗", badge, theme.accent2, 8.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
        }
    }

    scrollView_.EndRender(r);
}

} // namespace aero

