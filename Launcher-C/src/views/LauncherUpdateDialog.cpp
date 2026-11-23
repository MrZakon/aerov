#include "LauncherUpdateDialog.h"

#include <algorithm>
#include <utility>

namespace aero {

LauncherUpdateDialog::LauncherUpdateDialog(std::function<void()> accept, std::function<void()> dismiss) {
    SetVisible(false);
    download_.SetOnClick(std::move(accept));
    later_.SetOnClick(std::move(dismiss));
}

void LauncherUpdateDialog::Resize(float width, float height) {
    width_ = width;
    height_ = height;
    const float cardWidth = std::min(464.0f, width - 32.0f);
    SetBounds((width - cardWidth) / 2, (height - 252.0f) / 2, cardWidth, 252);
    download_.SetBounds(bounds_.right - 196, bounds_.bottom - 64, 172, 40);
    later_.SetBounds(bounds_.right - 292, bounds_.bottom - 64, 88, 40);
}

void LauncherUpdateDialog::Show(const std::wstring& installed, const std::wstring& available) {
    installed_ = installed;
    available_ = available;
    later_.SetFocused(true);
    download_.SetFocused(false);
    SetVisible(true);
}

void LauncherUpdateDialog::Update(float dt) {
    later_.Update(dt);
    download_.Update(dt);
}

void LauncherUpdateDialog::Render(D2DRenderer& r) {
    const auto& theme = Theme::Instance();
    const auto card = bounds_;
    const float left = card.left + 24;
    const float right = card.right - 24;
    const float top = card.top;
    const float middle = (left + right) / 2;
    const auto row = [&](float y, float height) {
        return D2D1::RectF(left, top + y, right, top + y + height);
    };

    r.FillRect(D2D1::RectF(0, 0, width_, height_), Color::FromHex(0x080c0e, 0.73f));
    r.FillRoundedRect(card, 16, theme.surface);
    r.DrawRoundedRect(card, 16, theme.line);
    r.DrawString(L"Aero Launcher", row(24, 16), theme.muted, 12);
    r.DrawString(L"Доступно обновление", row(44, 32), theme.text, 25, FontStyle::Bold);
    r.DrawString(L"После скачивания откроется установщик", row(80, 20), theme.muted, 14);

    r.FillRoundedRect(row(112, 60), 8, theme.bg);
    r.DrawRoundedRect(row(112, 60), 8, theme.line);
    r.DrawString(L"Установлена", D2D1::RectF(left + 16, top + 120, middle - 20, top + 136), theme.muted, 12);
    r.DrawString(installed_, D2D1::RectF(left + 16, top + 140, middle - 20, top + 164), theme.text, 16, FontStyle::Mono);
    r.DrawLine(D2D1::Point2F(middle - 3, top + 137), D2D1::Point2F(middle + 2, top + 142), theme.muted, 1.5f);
    r.DrawLine(D2D1::Point2F(middle + 2, top + 142), D2D1::Point2F(middle - 3, top + 147), theme.muted, 1.5f);
    r.DrawString(L"Доступна", D2D1::RectF(middle + 24, top + 120, right - 16, top + 136), theme.muted, 12);
    r.DrawString(available_, D2D1::RectF(middle + 24, top + 140, right - 16, top + 164), theme.text, 16, FontStyle::Mono);

    later_.Render(r);
    download_.Render(r);
}

bool LauncherUpdateDialog::OnMouseMove(float x, float y) {
    later_.OnMouseMove(x, y);
    download_.OnMouseMove(x, y);
    return true;
}

bool LauncherUpdateDialog::OnMouseButton(float x, float y, MouseBtn button, bool down) {
    if (button == MouseBtn::Left && down && (later_.Contains(x, y) || download_.Contains(x, y))) {
        later_.SetFocused(later_.Contains(x, y));
        download_.SetFocused(download_.Contains(x, y));
    }
    later_.OnMouseButton(x, y, button, down);
    if (IsVisible()) download_.OnMouseButton(x, y, button, down);
    return true;
}

bool LauncherUpdateDialog::OnKey(int key, bool down) {
    if (!down && key == VK_ESCAPE) {
        later_.SetFocused(true);
        later_.OnKey(VK_RETURN, true);
    } else if (down && (key == VK_TAB || key == VK_LEFT || key == VK_RIGHT)) {
        const bool focusDownload = key == VK_RIGHT || (key == VK_TAB && later_.IsFocused());
        later_.SetFocused(!focusDownload);
        download_.SetFocused(focusDownload);
    } else if (!down && !later_.OnKey(key, true)) {
        download_.OnKey(key, true);
    }
    return true;
}

} // namespace aero
