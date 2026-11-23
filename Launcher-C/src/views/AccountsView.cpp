#include "AccountsView.h"
#include "../store/AccountManager.h"
#include "../auth/AeroAuth.h"
#include "../auth/MicrosoftAuth.h"
#include "../core/HWID.h"
#include "../ui/Toast.h"
#include "../core/StringUtils.h"
#include <shellapi.h>
#include <thread>
#include <utility>

namespace aero {

AccountsView::AccountsView() {
    btnAddOffline_.SetOnClick([this]() { AddOfflineAccount(); });
    btnRedeem_.SetOnClick([this]() { RedeemKey(); });

    btnMsLogin_.SetOnClick([this] {
        if (microsoft_.Start()) Resize(GetWidth(), GetHeight());
    });

    btnCopyMsCode_.SetOnClick([this]() {
        const auto wCode = Utf8ToWide(microsoft_.Code());
        if (!wCode.empty() && OpenClipboard(nullptr)) {
            EmptyClipboard();
            HGLOBAL hg = GlobalAlloc(GMEM_MOVEABLE, (wCode.size() + 1) * sizeof(wchar_t));
            if (hg) {
                memcpy(GlobalLock(hg), wCode.c_str(), (wCode.size() + 1) * sizeof(wchar_t));
                GlobalUnlock(hg);
                SetClipboardData(CF_UNICODETEXT, hg);
            }
            CloseClipboard();
            Toast::Instance().Show(L"Код скопирован в буфер обмена!", ToastKind::Success);
        }
    });

    btnCancelMs_.SetOnClick([this]() {
        CancelSignIn();
        Toast::Instance().Show(L"Вход Microsoft отменён", ToastKind::Info);
    });

    btnCopyHwid_.SetOnClick([]() {
        std::wstring hwid = Utf8ToWide(HWID::Get());
        if (OpenClipboard(nullptr)) {
            EmptyClipboard();
            HGLOBAL hg = GlobalAlloc(GMEM_MOVEABLE, (hwid.size() + 1) * sizeof(wchar_t));
            if (hg) {
                memcpy(GlobalLock(hg), hwid.c_str(), (hwid.size() + 1) * sizeof(wchar_t));
                GlobalUnlock(hg);
                SetClipboardData(CF_UNICODETEXT, hg);
            }
            CloseClipboard();
            Toast::Instance().Show(L"HWID скопирован в буфер обмена!", ToastKind::Success);
        }
    });

    RefreshData();
}

void AccountsView::CancelSignIn() {
    if (!microsoft_.IsActive()) return;
    microsoft_.Cancel();
    Resize(GetWidth(), GetHeight());
}

void AccountsView::AddOfflineAccount() {
    std::string nick = WideToUtf8(inputNick_.GetText());
    if (nick.empty()) {
        Toast::Instance().Show(L"Введите никнейм игрока", ToastKind::Error);
        return;
    }
    AccountManager::Instance().AddOfflineAccount(nick);
    inputNick_.SetText(L"");
    Toast::Instance().Show(L"Офлайн-аккаунт добавлен", ToastKind::Success);
    needRefresh_ = true;
}

void AccountsView::RedeemKey() {
    if (redeemRequest_.valid()) return;
    auto key = WideToUtf8(inputRedeem_.GetText());
    const auto start = key.find_first_not_of(" \r\n\t");
    if (start == std::string::npos) {
        Toast::Instance().Show(L"Введите ключ активации", ToastKind::Error);
        return;
    }
    key = key.substr(start, key.find_last_not_of(" \r\n\t") - start + 1);
    redeemToken_ = AeroAuth::Instance().GetOnlineToken();
    if (redeemToken_.empty()) {
        Toast::Instance().Show(L"Войдите в Aero ID перед активацией ключа", ToastKind::Error);
        return;
    }
    redeemRequest_ = std::async(std::launch::async, [key = std::move(key), token = redeemToken_] {
        return AeroAuth::Redeem(key, token);
    });
    btnRedeem_.SetText(L"Проверяем…");
    btnRedeem_.SetEnabled(false);
    inputRedeem_.SetEnabled(false);
}

void AccountsView::PollRedeem() {
    if (!redeemRequest_.valid() || redeemRequest_.wait_for(std::chrono::seconds(0)) != std::future_status::ready) return;

    std::string error;
    try { error = redeemRequest_.get(); }
    catch (const std::exception&) { error = "Не удалось активировать ключ"; }
    btnRedeem_.SetText(L"Активировать");
    btnRedeem_.SetEnabled(true);
    inputRedeem_.SetEnabled(true);

    const auto completedToken = std::exchange(redeemToken_, {});
    if (AeroAuth::Instance().GetSession().token != completedToken) {
        inputRedeem_.SetText(L"");
        return;
    }
    if (!error.empty()) {
        Toast::Instance().Show(Utf8ToWide(error), ToastKind::Error);
        return;
    }
    inputRedeem_.SetText(L"");
    Toast::Instance().Show(L"Ключ активирован", ToastKind::Success);
    if (onRedeemed_) onRedeemed_();
}

void AccountsView::RefreshData() {
    rows_.clear();
    const auto& accounts = AccountManager::Instance().GetAccounts();
    auto* active = AccountManager::Instance().GetActiveAccount();

    for (const auto& acc : accounts) {
        AccountRow row;
        row.id = acc.id;
        row.username = Utf8ToWide(acc.username);
        row.type = acc.type;
        row.isActive = (active && active->id == acc.id);

        if (acc.type == AccountType::Microsoft) {
            row.typeLabel = acc.accessToken.empty() && acc.refreshToken.empty()
                ? L"Требуется вход Microsoft" : L"Лицензия Microsoft";
        } else {
            row.typeLabel = L"Офлайн";
        }

        std::string idCopy = acc.id;
        row.btnSelect.SetOnClick([this, idCopy]() {
            AccountManager::Instance().SetActiveAccount(idCopy);
            Toast::Instance().Show(L"Активный аккаунт изменён", ToastKind::Info);
            needRefresh_ = true;
        });

        row.btnDelete.SetOnClick([this, idCopy]() {
            const bool removed = AccountManager::Instance().RemoveAccount(idCopy);
            Toast::Instance().Show(removed ? L"Профиль удалён" : L"Не удалось удалить профиль с диска",
                removed ? ToastKind::Info : ToastKind::Error);
            needRefresh_ = true;
        });

        rows_.push_back(row);
    }

    scrollView_.SetContentHeight(rows_.size() * 64.0f + 20.0f);
    UpdateRowBounds();
}

void AccountsView::UpdateRowBounds() {
    float leftW = 380.0f;
    float startX = GetWidth() - 416.0f;
    float startY = 96.0f;
    float rightX = 36.0f;
    float rightW = GetWidth() - 476.0f;

    for (size_t i = 0; i < rows_.size(); ++i) {
        float itemY = startY + 50.0f + i * 64.0f;
        rows_[i].btnSelect.SetBounds(rightX + rightW - 146.0f, itemY + 12.0f, 96.0f, 32.0f);
        rows_[i].btnDelete.SetBounds(rightX + rightW - 42.0f, itemY + 12.0f, 32.0f, 32.0f);
    }
}

void AccountsView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);

    float leftW = 380.0f;
    float startX = GetWidth() - 416.0f;
    float startY = 96.0f;

    // Card 1: Offline nick
    inputNick_.SetBounds(startX + 20.0f, startY + 54.0f, leftW - 150.0f, 40.0f);
    btnAddOffline_.SetBounds(startX + leftW - 120.0f, startY + 54.0f, 100.0f, 40.0f);

    btnMsLogin_.SetBounds(startX + 20.0f, startY + 112.0f, leftW - 40.0f, 38.0f);

    btnCopyMsCode_.SetBounds(startX + 20.0f, startY + 202.0f, leftW - 140.0f, 36.0f);
    btnCancelMs_.SetBounds(startX + leftW - 110.0f, startY + 202.0f, 90.0f, 36.0f);

    // Card 2: Redeem
    float card2Y = startY + (microsoft_.IsActive() ? 260.0f : 176.0f);
    inputRedeem_.SetBounds(startX + 20.0f, card2Y + 52.0f, leftW - 150.0f, 40.0f);
    btnRedeem_.SetBounds(startX + leftW - 120.0f, card2Y + 52.0f, 100.0f, 40.0f);
    btnCopyHwid_.SetBounds(startX + leftW - 120.0f, card2Y + 98.0f, 100.0f, 24.0f);

    float rightX = 36.0f;
    float rightW = GetWidth() - 476.0f;
    float rightH = height - startY - 60.0f;

    scrollView_.SetBounds(rightX, startY + 46.0f, rightW, rightH);
    UpdateRowBounds();
}

void AccountsView::Update(float dt) {
    if (const auto result = microsoft_.Update()) {
        Resize(GetWidth(), GetHeight());
        if (result->error.empty()) {
            const auto& profile = result->profile;
            const bool saved = AccountManager::Instance().AddMicrosoftAccount(profile.nickname, profile.uuid, profile.accessToken,
                profile.refreshToken, profile.expiresAt);
            Toast::Instance().Show(saved ? L"Профиль Minecraft добавлен: " + Utf8ToWide(profile.nickname)
                : L"Вход выполнен. Сохранить профиль на диске не удалось.", saved ? ToastKind::Success : ToastKind::Error);
            RefreshData();
        } else {
            Toast::Instance().Show(Utf8ToWide(result->error), ToastKind::Error);
        }
    }

    if (needRefresh_) {
        needRefresh_ = false;
        RefreshData();
    }

    inputNick_.Update(dt);
    btnAddOffline_.Update(dt);
    btnMsLogin_.Update(dt);
    if (microsoft_.IsActive()) {
        btnCopyMsCode_.Update(dt);
        btnCancelMs_.Update(dt);
    }
    inputRedeem_.Update(dt);
    btnRedeem_.Update(dt);
    btnCopyHwid_.Update(dt);
    scrollView_.Update(dt);

    for (auto& row : rows_) {
        row.hoverAnim.Update(dt);
        row.btnSelect.Update(dt);
        row.btnDelete.Update(dt);
    }
}

bool AccountsView::OnMouseMove(float x, float y) {
    bool handled = false;
    handled |= inputNick_.OnMouseMove(x, y);
    handled |= btnAddOffline_.OnMouseMove(x, y);
    handled |= btnMsLogin_.OnMouseMove(x, y);
    if (microsoft_.IsActive()) {
        handled |= btnCopyMsCode_.OnMouseMove(x, y);
        handled |= btnCancelMs_.OnMouseMove(x, y);
    }
    handled |= inputRedeem_.OnMouseMove(x, y);
    handled |= btnRedeem_.OnMouseMove(x, y);
    handled |= btnCopyHwid_.OnMouseMove(x, y);

    float scroll = scrollView_.GetScrollOffset();
    float sy = y + scroll;

    for (size_t i = 0; i < rows_.size(); ++i) {
        auto& row = rows_[i];
        float itemY = 24.0f + 50.0f + i * 64.0f;
        bool hovered = (x >= scrollView_.GetBounds().left && x <= scrollView_.GetBounds().right && sy >= itemY && sy <= itemY + 54.0f);
        row.hoverAnim.SetTarget(hovered ? 1.0f : 0.0f);

        handled |= row.btnSelect.OnMouseMove(x, sy);
        handled |= row.btnDelete.OnMouseMove(x, sy);
    }

    handled |= scrollView_.OnMouseMove(x, y);
    return handled;
}

bool AccountsView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (inputNick_.OnMouseButton(x, y, btn, down)) return true;
    if (btnAddOffline_.OnMouseButton(x, y, btn, down)) return true;
    if (btnMsLogin_.OnMouseButton(x, y, btn, down)) return true;
    if (microsoft_.IsActive()) {
        if (btnCopyMsCode_.OnMouseButton(x, y, btn, down)) return true;
        if (btnCancelMs_.OnMouseButton(x, y, btn, down)) return true;
    }
    if (inputRedeem_.OnMouseButton(x, y, btn, down)) return true;
    if (btnRedeem_.OnMouseButton(x, y, btn, down)) return true;
    if (btnCopyHwid_.OnMouseButton(x, y, btn, down)) return true;

    // Check row buttons with scroll offset FIRST before ScrollView!
    D2D1_RECT_F sBounds = scrollView_.GetBounds();
    if (x >= sBounds.left && x <= sBounds.right && y >= sBounds.top && y <= sBounds.bottom) {
        float scroll = scrollView_.GetScrollOffset();
        float sy = y + scroll;

        for (auto& row : rows_) {
            if (row.btnDelete.OnMouseButton(x, sy, btn, down)) return true;
            if (!row.isActive && row.btnSelect.OnMouseButton(x, sy, btn, down)) return true;
        }
    }

    return scrollView_.OnMouseButton(x, y, btn, down);
}

bool AccountsView::OnMouseWheel(float x, float y, float delta) {
    return scrollView_.OnMouseWheel(x, y, delta);
}

bool AccountsView::OnChar(wchar_t ch) {
    if (inputNick_.IsFocused()) return inputNick_.OnChar(ch);
    if (inputRedeem_.IsFocused()) return inputRedeem_.OnChar(ch);
    return false;
}

bool AccountsView::OnKey(int vk, bool down) {
    if (down && vk == VK_RETURN) {
        if (inputNick_.IsFocused()) {
            AddOfflineAccount();
            return true;
        }
        if (inputRedeem_.IsFocused()) {
            RedeemKey();
            return true;
        }
    }
    if (inputNick_.IsFocused()) return inputNick_.OnKey(vk, down);
    if (inputRedeem_.IsFocused()) return inputRedeem_.OnKey(vk, down);
    return false;
}

void AccountsView::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    float leftW = 380.0f;
    float startX = GetWidth() - 416.0f;
    float startY = 96.0f;

    r.DrawString(L"Игровые профили",D2D1::RectF(36,28,600,76),theme.text,32,FontStyle::Bold);
    // Left card 1: Add offline & MS
    float card1H = microsoft_.IsActive() ? 248.0f : 160.0f;
    D2D1_RECT_F card1 = D2D1::RectF(startX, startY, startX + leftW, startY + card1H);
    r.FillRoundedRect(card1, 8.0f, theme.surface);
    r.DrawRoundedRect(card1, 8.0f, theme.line, 1.0f);

    r.DrawString(L"Добавить игровой профиль", D2D1::RectF(startX + 20.0f, startY + 16.0f, startX + leftW - 20, startY + 42.0f), theme.text, 16.0f, FontStyle::Bold);

    inputNick_.Render(r);
    btnAddOffline_.Render(r);

    r.DrawLine(D2D1::Point2F(startX + 20.0f, startY + 102.0f), D2D1::Point2F(startX + leftW - 20.0f, startY + 102.0f), theme.line, 1.0f);

    if (!microsoft_.IsActive()) {
        btnMsLogin_.Render(r);
    } else {
        const auto& uCode = microsoft_.Code();
        const auto& sText = microsoft_.Status();

        r.DrawString(Utf8ToWide(sText), D2D1::RectF(startX + 20.0f, startY + 108.0f, startX + leftW - 20.0f, startY + 130.0f), theme.muted, 11);

        // Display user code prominently in 6px rounded rectangle
        D2D1_RECT_F codeBox = D2D1::RectF(startX + 20.0f, startY + 132.0f, startX + leftW - 20.0f, startY + 192.0f);
        r.FillRoundedRect(codeBox, 6.0f, theme.panel);
        r.DrawRoundedRect(codeBox, 6.0f, theme.line);

        std::wstring codeDisplay = uCode.empty() ? L"Ожидание кода..." : Utf8ToWide(uCode);
        r.DrawString(codeDisplay, codeBox, theme.mint, 18.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

        btnCopyMsCode_.Render(r);
        btnCancelMs_.Render(r);
    }

    // Left card 2: Redeem key
    float card2Y = startY + (microsoft_.IsActive() ? 260.0f : 176.0f);
    float card2H = 132.0f;
    D2D1_RECT_F card2 = D2D1::RectF(startX, card2Y, startX + leftW, card2Y + card2H);
    r.FillRoundedRect(card2, 8.0f, theme.surface);
    r.DrawRoundedRect(card2, 8.0f, theme.line, 1.0f);

    r.DrawString(L"Активировать подписку", D2D1::RectF(startX + 20.0f, card2Y + 14.0f, startX + leftW - 20.0f, card2Y + 40.0f), theme.text, 16.0f, FontStyle::Bold);

    inputRedeem_.Render(r);
    btnRedeem_.Render(r);

    std::wstring hwidDisplay = L"HWID: " + Utf8ToWide(HWID::Get());
    r.DrawString(hwidDisplay, D2D1::RectF(startX + 20.0f, card2Y + 102.0f, startX + leftW - 130.0f, card2Y + 124.0f), Color::FromHex(0x929d86), 8.5f);
    btnCopyHwid_.Render(r);

    // Right list
    float rightX = 36.0f;
    float rightW = GetWidth() - 476.0f;

    r.DrawString(L"Сохранённые", D2D1::RectF(rightX, startY + 12.0f, rightX + 220.0f, startY + 36.0f), theme.text, 16.0f, FontStyle::Bold);

    std::wstring countText = std::to_wstring(rows_.size()) + L" аккаунтов";
    D2D1_RECT_F countChip = D2D1::RectF(rightX + rightW - 110.0f, startY + 12.0f, rightX + rightW, startY + 32.0f);
    r.DrawString(countText, countChip, theme.muted, 12, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_TRAILING);

    scrollView_.BeginRender(r);

    if (rows_.empty()) {
        r.DrawString(L"Пока нет добавленных ников. Добавь ник или лицензию в панели справа.", D2D1::RectF(rightX, startY + 60.0f, rightX + rightW, startY + 120.0f), theme.muted, 12.0f);
    }

    for (size_t i = 0; i < rows_.size(); ++i) {
        auto& row = rows_[i];
        float h = row.hoverAnim.Get();
        float itemY = startY + 50.0f + i * 64.0f;
        D2D1_RECT_F itemRect = D2D1::RectF(rightX, itemY, rightX + rightW, itemY + 54.0f);

        const auto bgCol = row.isActive ? theme.panel2 : anim::LerpColor(theme.surface, theme.panel, h);
        const auto borderCol = theme.line;

        r.FillRoundedRect(itemRect, 8.0f, bgCol);
        r.DrawRoundedRect(itemRect, 8.0f, borderCol, (row.isActive || h > 0.1f) ? 1.5f : 1.0f);

        // Avatar icon
        D2D1_RECT_F avRect = D2D1::RectF(rightX + 12.0f, itemY + 9.0f, rightX + 48.0f, itemY + 45.0f);
        r.FillRoundedRect(avRect, 6.0f, theme.panel2);
        std::wstring initial = row.username.empty() ? L"P" : row.username.substr(0, 1);
        r.DrawString(initial, avRect, theme.text, 16.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

        // Username
        r.DrawString(row.username, D2D1::RectF(rightX + 60.0f, itemY + 9.0f, rightX + rightW - 160.0f, itemY + 28.0f), theme.text, 13.0f, FontStyle::Bold);

        const auto typeLabel = row.isActive ? row.typeLabel + L"  ·  Выбран" : row.typeLabel;
        r.DrawString(typeLabel, D2D1::RectF(rightX + 60, itemY + 29, rightX + rightW - 56, itemY + 50), theme.muted, 11);

        if (!row.isActive) row.btnSelect.Render(r);

        row.btnDelete.Render(r);
    }

    scrollView_.EndRender(r);
}

} // namespace aero

