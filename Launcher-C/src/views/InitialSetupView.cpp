#include "InitialSetupView.h"
#include "../store/AccountManager.h"
#include "../store/SettingsManager.h"
#include "../auth/AeroAuth.h"
#include "../auth/MicrosoftAuth.h"
#include "../ui/Toast.h"
#include "../core/StringUtils.h"
#include <windows.h>
#include <shellapi.h>
#include <thread>
#include <cmath>

namespace aero {

InitialSetupView::InitialSetupView() {
    // Detect system RAM
    MEMORYSTATUSEX memStatus = { sizeof(MEMORYSTATUSEX) };
    if (GlobalMemoryStatusEx(&memStatus)) {
        totalRamGb_ = static_cast<int>(memStatus.ullTotalPhys / (1024ULL * 1024 * 1024));
        selectedRamGb_ = (std::min)(8, (std::max)(2, totalRamGb_ / 2));
    }

    // Step 1: Account setup
    auto updateAccountButtons = [this]() {
        btnOptOffline_.SetStyle(accountMode_ == AccountMode::Offline ? ButtonStyle::Primary : ButtonStyle::Default);
        btnOptMicrosoft_.SetStyle(accountMode_ == AccountMode::Microsoft ? ButtonStyle::Primary : ButtonStyle::Default);
    };

    btnOptOffline_.SetOnClick([this, updateAccountButtons]() {
        microsoft_.Cancel();
        accountMode_ = AccountMode::Offline;
        updateAccountButtons();
    });

    btnOptMicrosoft_.SetOnClick([this, updateAccountButtons]() {
        accountMode_ = AccountMode::Microsoft;
        updateAccountButtons();
    });

    updateAccountButtons();

    btnStartMsAuth_.SetOnClick([this] {
        if (microsoft_.Start()) msAuthSuccessNick_.clear();
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

    btnCancelMs_.SetOnClick([this] {
        microsoft_.Cancel();
        Toast::Instance().Show(L"Вход Microsoft отменён", ToastKind::Info);
    });

    btnNextFromAccount_.SetOnClick([this]() {
        if (accountMode_ == AccountMode::Offline) {
            std::string nick = WideToUtf8(inputNick_.GetText());
            if (nick.empty()) {
                Toast::Instance().Show(L"Введите игровой никнейм перед продолжением", ToastKind::Error);
                return;
            }
        } else {
            auto* acc = AccountManager::Instance().GetActiveAccount();
            if (!acc || acc->type != AccountType::Microsoft) {
                Toast::Instance().Show(L"Войдите через Microsoft или переключитесь на Офлайн", ToastKind::Error);
                return;
            }
        }
        SetStep(SetupStep::Ram);
    });

    // Step 2: RAM setup
    auto updateRamButtons = [this]() {
        btnRam2_.SetStyle(selectedRamGb_ == 2 ? ButtonStyle::Primary : ButtonStyle::Default);
        btnRam4_.SetStyle(selectedRamGb_ == 4 ? ButtonStyle::Primary : ButtonStyle::Default);
        btnRam6_.SetStyle(selectedRamGb_ == 6 ? ButtonStyle::Primary : ButtonStyle::Default);
        btnRam8_.SetStyle(selectedRamGb_ == 8 ? ButtonStyle::Primary : ButtonStyle::Default);
        btnRam12_.SetStyle(selectedRamGb_ == 12 ? ButtonStyle::Primary : ButtonStyle::Default);
        btnRam16_.SetStyle(selectedRamGb_ == 16 ? ButtonStyle::Primary : ButtonStyle::Default);
    };

    btnRam2_.SetOnClick([this, updateRamButtons]() { selectedRamGb_ = 2; updateRamButtons(); });
    btnRam4_.SetOnClick([this, updateRamButtons]() { selectedRamGb_ = 4; updateRamButtons(); });
    btnRam6_.SetOnClick([this, updateRamButtons]() { selectedRamGb_ = 6; updateRamButtons(); });
    btnRam8_.SetOnClick([this, updateRamButtons]() { selectedRamGb_ = 8; updateRamButtons(); });
    btnRam12_.SetOnClick([this, updateRamButtons]() { selectedRamGb_ = 12; updateRamButtons(); });
    btnRam16_.SetOnClick([this, updateRamButtons]() { selectedRamGb_ = 16; updateRamButtons(); });

    btnRamRec_.SetOnClick([this, updateRamButtons]() {
        selectedRamGb_ = (std::min)(8, (std::max)(4, totalRamGb_ / 2));
        updateRamButtons();
    });

    updateRamButtons();

    btnBackToAccount_.SetOnClick([this]() {
        SetStep(SetupStep::Account);
    });

    btnNextFromRam_.SetOnClick([this]() {
        SetStep(SetupStep::Final);
    });

    // Step 3: Finish
    btnFinish_.SetOnClick([this]() {
        ApplySettingsAndFinish();
    });

    SetStep(SetupStep::Account);
}

void InitialSetupView::SetStep(SetupStep step) {
    currentStep_ = step;
    stepAnimOffset_.SnapTo(20.0f);
    stepAnimOffset_.SetTarget(0.0f);

    if (step == SetupStep::Final) {
        textRevealTimer_ = 0.0f;
        finalFadeIn_.SnapTo(0.0f);
        finalFadeIn_.SetTarget(1.0f);
    }
}

void InitialSetupView::SyncWithAuth() {
    microsoft_.Cancel();
    msAuthSuccessNick_.clear();
    SetStep(SetupStep::Account);
    accountMode_ = AccountMode::Offline;
    btnOptOffline_.SetStyle(ButtonStyle::Primary);
    btnOptMicrosoft_.SetStyle(ButtonStyle::Default);
}

void InitialSetupView::ApplySettingsAndFinish() {
    auto& settings = SettingsManager::Instance().Get();
    settings.memoryMax = selectedRamGb_;
    settings.initialSetupDone = true;

    if (accountMode_ == AccountMode::Offline) {
        std::string nick = WideToUtf8(inputNick_.GetText());
        if (!nick.empty()) {
            AccountManager::Instance().AddOfflineAccount(nick);
        }
    }

    SettingsManager::Instance().Save();

    if (onFinish_) {
        onFinish_();
    }
}

void InitialSetupView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);

    float cardW = 680.0f;
    float cardH = 480.0f;
    float cardX = (width - cardW) * 0.5f;
    float cardY = (height - cardH) * 0.5f;

    // Step 1: Account bounds (2 options: Offline, Microsoft)
    float optW = (cardW - 48.0f - 12.0f) * 0.5f;
    btnOptOffline_.SetBounds(cardX + 24.0f, cardY + 120.0f, optW, 44.0f);
    btnOptMicrosoft_.SetBounds(cardX + 24.0f + optW + 12.0f, cardY + 120.0f, optW, 44.0f);

    inputNick_.SetBounds(cardX + 24.0f, cardY + 196.0f, cardW - 48.0f, 44.0f);
    btnStartMsAuth_.SetBounds(cardX + 24.0f, cardY + 196.0f, cardW - 48.0f, 46.0f);
    btnCopyMsCode_.SetBounds(cardX + cardW - 24.0f - 160.0f, cardY + 216.0f, 140.0f, 38.0f);
    btnCancelMs_.SetBounds(cardX + cardW - 24.0f - 110.0f, cardY + 274.0f, 90.0f, 32.0f);

    btnNextFromAccount_.SetBounds(cardX + cardW - 270.0f, cardY + cardH - 64.0f, 246.0f, 44.0f);

    // Step 2: RAM bounds
    float ramBtnW = (cardW - 48.0f - 25.0f) / 6.0f;
    float ramStartY = cardY + 200.0f;
    btnRam2_.SetBounds(cardX + 24.0f, ramStartY, ramBtnW, 42.0f);
    btnRam4_.SetBounds(cardX + 24.0f + (ramBtnW + 5.0f), ramStartY, ramBtnW, 42.0f);
    btnRam6_.SetBounds(cardX + 24.0f + (ramBtnW + 5.0f) * 2, ramStartY, ramBtnW, 42.0f);
    btnRam8_.SetBounds(cardX + 24.0f + (ramBtnW + 5.0f) * 3, ramStartY, ramBtnW, 42.0f);
    btnRam12_.SetBounds(cardX + 24.0f + (ramBtnW + 5.0f) * 4, ramStartY, ramBtnW, 42.0f);
    btnRam16_.SetBounds(cardX + 24.0f + (ramBtnW + 5.0f) * 5, ramStartY, ramBtnW, 42.0f);

    btnRamRec_.SetBounds(cardX + 24.0f, ramStartY + 56.0f, 180.0f, 36.0f);

    btnBackToAccount_.SetBounds(cardX + 24.0f, cardY + cardH - 64.0f, 130.0f, 44.0f);
    btnNextFromRam_.SetBounds(cardX + cardW - 250.0f, cardY + cardH - 64.0f, 226.0f, 44.0f);

    // Step 3: Final bounds
    btnFinish_.SetBounds(cardX + (cardW - 260.0f) * 0.5f, cardY + cardH - 74.0f, 260.0f, 52.0f);
}

void InitialSetupView::Update(float dt) {
    animTime_ += dt;
    textRevealTimer_ += dt;
    stepAnimOffset_.Update(dt);
    finalFadeIn_.Update(dt);

    if (const auto result = microsoft_.Update()) {
        if (result->error.empty()) {
            const auto& profile = result->profile;
            const bool saved = AccountManager::Instance().AddMicrosoftAccount(profile.nickname, profile.uuid, profile.accessToken,
                profile.refreshToken, profile.expiresAt);
            msAuthSuccessNick_ = profile.nickname;
            Toast::Instance().Show(saved ? L"Профиль Minecraft добавлен"
                : L"Вход выполнен. Сохранить профиль на диске не удалось.", saved ? ToastKind::Success : ToastKind::Error);
        } else {
            Toast::Instance().Show(Utf8ToWide(result->error), ToastKind::Error);
        }
    }

    if (currentStep_ == SetupStep::Account) {
        btnOptOffline_.Update(dt);
        btnOptMicrosoft_.Update(dt);
        if (accountMode_ == AccountMode::Offline) {
            inputNick_.Update(dt);
        } else if (accountMode_ == AccountMode::Microsoft) {
            if (!microsoft_.IsActive() && msAuthSuccessNick_.empty()) {
                btnStartMsAuth_.Update(dt);
            } else if (microsoft_.IsActive() && msAuthSuccessNick_.empty()) {
                if (!microsoft_.Code().empty()) {
                    btnCopyMsCode_.Update(dt);
                }
                btnCancelMs_.Update(dt);
            }
        }
        btnNextFromAccount_.Update(dt);
    } else if (currentStep_ == SetupStep::Ram) {
        btnRam2_.Update(dt);
        btnRam4_.Update(dt);
        btnRam6_.Update(dt);
        btnRam8_.Update(dt);
        btnRam12_.Update(dt);
        btnRam16_.Update(dt);
        btnRamRec_.Update(dt);
        btnBackToAccount_.Update(dt);
        btnNextFromRam_.Update(dt);
    } else if (currentStep_ == SetupStep::Final) {
        btnFinish_.Update(dt);
    }
}

bool InitialSetupView::OnMouseMove(float x, float y) {
    if (currentStep_ == SetupStep::Account) {
        bool handled = false;
        handled |= btnOptOffline_.OnMouseMove(x, y);
        handled |= btnOptMicrosoft_.OnMouseMove(x, y);
        if (accountMode_ == AccountMode::Offline) {
            handled |= inputNick_.OnMouseMove(x, y);
        } else if (accountMode_ == AccountMode::Microsoft) {
            if (!microsoft_.IsActive() && msAuthSuccessNick_.empty()) {
                handled |= btnStartMsAuth_.OnMouseMove(x, y);
            } else if (microsoft_.IsActive() && msAuthSuccessNick_.empty()) {
                if (!microsoft_.Code().empty()) {
                    handled |= btnCopyMsCode_.OnMouseMove(x, y);
                }
                handled |= btnCancelMs_.OnMouseMove(x, y);
            }
        }
        handled |= btnNextFromAccount_.OnMouseMove(x, y);
        return handled;
    } else if (currentStep_ == SetupStep::Ram) {
        bool handled = false;
        handled |= btnRam2_.OnMouseMove(x, y);
        handled |= btnRam4_.OnMouseMove(x, y);
        handled |= btnRam6_.OnMouseMove(x, y);
        handled |= btnRam8_.OnMouseMove(x, y);
        handled |= btnRam12_.OnMouseMove(x, y);
        handled |= btnRam16_.OnMouseMove(x, y);
        handled |= btnRamRec_.OnMouseMove(x, y);
        handled |= btnBackToAccount_.OnMouseMove(x, y);
        handled |= btnNextFromRam_.OnMouseMove(x, y);
        return handled;
    } else if (currentStep_ == SetupStep::Final) {
        return btnFinish_.OnMouseMove(x, y);
    }
    return false;
}

bool InitialSetupView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (currentStep_ == SetupStep::Account) {
        if (btnOptOffline_.OnMouseButton(x, y, btn, down)) return true;
        if (btnOptMicrosoft_.OnMouseButton(x, y, btn, down)) return true;
        if (accountMode_ == AccountMode::Offline) {
            if (inputNick_.OnMouseButton(x, y, btn, down)) return true;
        } else if (accountMode_ == AccountMode::Microsoft) {
            if (!microsoft_.IsActive() && msAuthSuccessNick_.empty()) {
                if (btnStartMsAuth_.OnMouseButton(x, y, btn, down)) return true;
            } else if (microsoft_.IsActive() && msAuthSuccessNick_.empty()) {
                if (!microsoft_.Code().empty() && btnCopyMsCode_.OnMouseButton(x, y, btn, down)) return true;
                if (btnCancelMs_.OnMouseButton(x, y, btn, down)) return true;
            }
        }
        if (btnNextFromAccount_.OnMouseButton(x, y, btn, down)) return true;
    } else if (currentStep_ == SetupStep::Ram) {
        if (btnRam2_.OnMouseButton(x, y, btn, down)) return true;
        if (btnRam4_.OnMouseButton(x, y, btn, down)) return true;
        if (btnRam6_.OnMouseButton(x, y, btn, down)) return true;
        if (btnRam8_.OnMouseButton(x, y, btn, down)) return true;
        if (btnRam12_.OnMouseButton(x, y, btn, down)) return true;
        if (btnRam16_.OnMouseButton(x, y, btn, down)) return true;
        if (btnRamRec_.OnMouseButton(x, y, btn, down)) return true;
        if (btnBackToAccount_.OnMouseButton(x, y, btn, down)) return true;
        if (btnNextFromRam_.OnMouseButton(x, y, btn, down)) return true;
    } else if (currentStep_ == SetupStep::Final) {
        if (btnFinish_.OnMouseButton(x, y, btn, down)) return true;
    }
    return false;
}

bool InitialSetupView::OnChar(wchar_t ch) {
    if (currentStep_ == SetupStep::Account) {
        if (accountMode_ == AccountMode::Offline) {
            return inputNick_.OnChar(ch);
        }
    }
    return false;
}

bool InitialSetupView::OnKey(int vk, bool down) {
    if (currentStep_ == SetupStep::Account) {
        if (accountMode_ == AccountMode::Offline) {
            return inputNick_.OnKey(vk, down);
        }
    }
    return false;
}

void InitialSetupView::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    float cardW = 680.0f;
    float cardH = 480.0f;
    float cardX = (bounds_.right - cardW) * 0.5f;
    float cardY = (bounds_.bottom - cardH) * 0.5f;

    // Dark backdrop overlay with subtle blur
    r.FillRoundedRect(D2D1::RectF(0, 0, bounds_.right, bounds_.bottom), 0.0f, Color::FromRgba(32, 35, 32, 0.78f));

    // Outer glow halo behind main setup modal card
    float pulse = (std::sin(animTime_ * 2.8f) + 1.0f) * 0.5f;
    D2D1_RECT_F haloCard = D2D1::RectF(cardX - 4.0f, cardY - 4.0f, cardX + cardW + 4.0f, cardY + cardH + 4.0f);
    r.DrawRoundedRect(haloCard, 8.0f, Color::FromRgba(240, 183, 130, 0.18f + pulse * 0.14f), 1.5f);

    // Main setup modal card
    D2D1_RECT_F mCard = D2D1::RectF(cardX, cardY, cardX + cardW, cardY + cardH);
    r.FillGradientRoundedRect(mCard, 8.0f, Color::FromHex(0x252b24), Color::FromHex(0x252b24), false);
    r.DrawRoundedRect(mCard, 8.0f, Color::FromRgba(240, 183, 130, 0.32f), 1.0f);

    // Top Brand & Step Pills
    float headerY = cardY + 22.0f;
    r.DrawString(L"AERO / НАЧАЛЬНАЯ НАСТРОЙКА", D2D1::RectF(cardX + 24.0f, headerY, cardX + 350.0f, headerY + 14.0f), Color::FromHex(0x9ba58f), 9.0f, FontStyle::Bold);

    // Progress Step Dots / Pills (Step 1, Step 2, Step 3)
    float pillStartX = cardX + cardW - 200.0f;
    for (int i = 0; i < 3; ++i) {
        float px = pillStartX + i * 56.0f;
        D2D1_RECT_F stepPill = D2D1::RectF(px, headerY, px + 48.0f, headerY + 18.0f);
        bool isActive = static_cast<int>(currentStep_) == i;
        bool isPast = static_cast<int>(currentStep_) > i;

        if (isActive) {
            r.FillRoundedRect(stepPill, 4.0f, Color::FromRgba(178, 199, 142, 0.22f));
            r.DrawRoundedRect(stepPill, 4.0f, theme.mint, 1.2f);
        } else if (isPast) {
            r.FillRoundedRect(stepPill, 4.0f, Color::FromRgba(240, 183, 130, 0.20f));
            r.DrawRoundedRect(stepPill, 4.0f, theme.accent1, 1.0f);
        } else {
            r.FillRoundedRect(stepPill, 4.0f, Color::FromRgba(255, 255, 255, 0.04f));
            r.DrawRoundedRect(stepPill, 4.0f, theme.line, 1.0f);
        }
        std::wstring sTxt = L"0" + std::to_wstring(i + 1);
        r.DrawString(sTxt, stepPill, isActive ? theme.mint : (isPast ? theme.text : theme.muted), 9.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    }

    // Divider line
    r.DrawLine(D2D1::Point2F(cardX + 20.0f, headerY + 28.0f), D2D1::Point2F(cardX + cardW - 20.0f, headerY + 28.0f), theme.line, 1.0f);

    float contentTopY = cardY + 64.0f;

    // --- STEP 1: ACCOUNT ---
    if (currentStep_ == SetupStep::Account) {
        r.DrawString(L"Выбери способ входа в игру", D2D1::RectF(cardX + 24.0f, contentTopY, cardX + cardW - 24.0f, contentTopY + 32.0f), theme.text, 22.0f, FontStyle::Bold);
        r.DrawString(L"Ты сможешь играть на любых серверах или локально. Тип аккаунта можно сменить в любое время.", D2D1::RectF(cardX + 24.0f, contentTopY + 34.0f, cardX + cardW - 24.0f, contentTopY + 54.0f), theme.muted, 11.0f);

        btnOptOffline_.Render(r);
        btnOptMicrosoft_.Render(r);

        if (accountMode_ == AccountMode::Offline) {
            r.DrawString(L"Никнейм игрока", D2D1::RectF(cardX + 24.0f, cardY + 174.0f, cardX + 300.0f, cardY + 190.0f), Color::FromHex(0xc1c7b5), 11.0f, FontStyle::Bold);
            inputNick_.Render(r);
            r.DrawString(L"Бесплатный оффлайн-вход без лицензии. Введи свой игровой ник.", D2D1::RectF(cardX + 24.0f, cardY + 248.0f, cardX + cardW - 24.0f, cardY + 266.0f), Color::FromHex(0x929d86), 10.0f);
        } else {
            if (!microsoft_.IsActive() && msAuthSuccessNick_.empty()) {
                btnStartMsAuth_.Render(r);
                r.DrawString(L"Лаунчер сгенерирует разовый код и откроет страницу подтверждения microsoft.com/link в браузере.", D2D1::RectF(cardX + 24.0f, cardY + 252.0f, cardX + cardW - 24.0f, cardY + 276.0f), Color::FromHex(0x929d86), 10.5f);
            } else if (microsoft_.IsActive() && msAuthSuccessNick_.empty()) {
                D2D1_RECT_F codeBox = D2D1::RectF(cardX + 24.0f, cardY + 180.0f, cardX + cardW - 24.0f, cardY + 320.0f);
                r.FillRoundedRect(codeBox, 8.0f, Color::FromRgba(32, 35, 32, 0.85f));
                r.DrawRoundedRect(codeBox, 8.0f, Color::FromRgba(240, 183, 130, 0.40f), 1.0f);

                const auto& code = microsoft_.Code();
                const auto& status = microsoft_.Status();

                if (code.empty()) {
                    r.DrawString(L"Запрос одноразового кода авторизации Microsoft...", codeBox, theme.accent2, 13.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
                } else {
                    r.DrawString(Utf8ToWide(status), D2D1::RectF(cardX + 44.0f, cardY + 194.0f, cardX + cardW - 44.0f, cardY + 214.0f), theme.muted, 12.0f);

                    D2D1_RECT_F codeBadge = D2D1::RectF(cardX + 44.0f, cardY + 216.0f, cardX + 320.0f, cardY + 256.0f);
                    r.FillRoundedRect(codeBadge, 6.0f, Color::FromRgba(178, 199, 142, 0.12f));
                    r.DrawRoundedRect(codeBadge, 6.0f, theme.mint, 1.2f);
                    r.DrawString(Utf8ToWide(code), codeBadge, theme.mint, 22.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

                    btnCopyMsCode_.Render(r);

                }
                btnCancelMs_.Render(r);
            } else if (!msAuthSuccessNick_.empty()) {
                D2D1_RECT_F succBox = D2D1::RectF(cardX + 24.0f, cardY + 185.0f, cardX + cardW - 24.0f, cardY + 275.0f);
                r.FillRoundedRect(succBox, 8.0f, Color::FromRgba(16, 185, 129, 0.15f));
                r.DrawRoundedRect(succBox, 8.0f, Color::FromRgba(16, 185, 129, 0.50f), 1.2f);

                std::wstring succTitle = L"✓ Успешный вход: " + Utf8ToWide(msAuthSuccessNick_);
                r.DrawString(succTitle, D2D1::RectF(succBox.left + 20.0f, succBox.top + 16.0f, succBox.right - 20.0f, succBox.top + 42.0f), Color::FromHex(0x34d399), 16.0f, FontStyle::Bold);
                r.DrawString(L"Лицензия Minecraft привязана к лаунчеру. Нажмите кнопку «Далее», чтобы продолжить.", D2D1::RectF(succBox.left + 20.0f, succBox.top + 46.0f, succBox.right - 20.0f, succBox.bottom - 10.0f), theme.text, 11.0f);
            }
        }

        btnNextFromAccount_.Render(r);
    }
    // --- STEP 2: RAM (ОЗУ) ---
    else if (currentStep_ == SetupStep::Ram) {
        r.DrawString(L"Выделение оперативной памяти", D2D1::RectF(cardX + 24.0f, contentTopY, cardX + cardW - 24.0f, contentTopY + 32.0f), theme.text, 22.0f, FontStyle::Bold);
        r.DrawString(L"Укажи, сколько ОЗУ выделить для Minecraft. Больше памяти обеспечивает плавный FPS и поддержку шейдеров.", D2D1::RectF(cardX + 24.0f, contentTopY + 34.0f, cardX + cardW - 24.0f, contentTopY + 54.0f), theme.muted, 11.0f);

        // Current RAM big display card
        D2D1_RECT_F ramDisplay = D2D1::RectF(cardX + 24.0f, cardY + 120.0f, cardX + cardW - 24.0f, cardY + 180.0f);
        r.FillRoundedRect(ramDisplay, 8.0f, Color::FromRgba(32, 35, 32, 0.75f));
        r.DrawRoundedRect(ramDisplay, 8.0f, theme.line, 1.0f);

        std::wstring ramLabel = std::to_wstring(selectedRamGb_) + L" ГБ";
        r.DrawString(ramLabel, D2D1::RectF(ramDisplay.left + 20.0f, ramDisplay.top + 8.0f, ramDisplay.left + 160.0f, ramDisplay.bottom - 8.0f), theme.mint, 30.0f, FontStyle::Bold);

        std::wstring pcRamInfo = L"Всего в твоем ПК: " + std::to_wstring(totalRamGb_) + L" ГБ RAM";
        r.DrawString(pcRamInfo, D2D1::RectF(ramDisplay.left + 170.0f, ramDisplay.top + 12.0f, ramDisplay.right - 20.0f, ramDisplay.top + 32.0f), theme.text, 12.0f, FontStyle::Bold);
        r.DrawString(L"Рекомендуем 4–8 ГБ для визуалов Aero и стабильного фреймрейта", D2D1::RectF(ramDisplay.left + 170.0f, ramDisplay.top + 32.0f, ramDisplay.right - 20.0f, ramDisplay.bottom - 8.0f), Color::FromHex(0xb4bdaa), 10.0f);

        // Preset buttons
        btnRam2_.Render(r);
        btnRam4_.Render(r);
        btnRam6_.Render(r);
        btnRam8_.Render(r);
        btnRam12_.Render(r);
        btnRam16_.Render(r);
        btnRamRec_.Render(r);

        btnBackToAccount_.Render(r);
        btnNextFromRam_.Render(r);
    }
    // --- STEP 3: FINAL GREETING ---
    else if (currentStep_ == SetupStep::Final) {
        float fAlpha = finalFadeIn_.Get();

        // Glowing 3D Emblem in the center
        float cx = cardX + cardW * 0.5f;
        float emblemY = cardY + 130.0f;

        // Concentric pulsing neon rounded rectangles expanding outward (no ovals)
        for (int ring = 0; ring < 2; ++ring) {
            float rProg = std::fmod(animTime_ * 0.5f + ring * 0.5f, 1.0f);
            float rHalf = 32.0f + rProg * 28.0f;
            D2D1_RECT_F ripBox = D2D1::RectF(cx - rHalf, emblemY - rHalf, cx + rHalf, emblemY + rHalf);
            r.DrawRoundedRect(ripBox, 8.0f, Color::FromRgba(240, 183, 130, (1.0f - rProg) * 0.35f), 1.2f);
        }

        // Aura rect
        float auraHalf = 42.0f + pulse * 6.0f;
        D2D1_RECT_F auraBox = D2D1::RectF(cx - auraHalf, emblemY - auraHalf, cx + auraHalf, emblemY + auraHalf);
        r.FillRoundedRect(auraBox, 8.0f, Color::FromRgba(240, 183, 130, 0.15f + pulse * 0.08f));

        auto logoBox = D2D1::RectF(cx-30,emblemY-30,cx+30,emblemY+30);
        r.FillRoundedRect(logoBox,10,theme.panel);
        r.DrawRoundedRect(logoBox,10,theme.lineHi,1.5f);
        r.DrawString(L"A",logoBox,theme.text,28,FontStyle::Bold,DWRITE_TEXT_ALIGNMENT_CENTER,DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
        // Beautiful smooth typewriter / fade-in text
        // Exact user words:
        // "Привет. Завершилась настройка лаунчера."
        // "Можешь выбирать версию и начать играть."
        float text1Y = cardY + 210.0f;
        float text2Y = cardY + 252.0f;

        // Progressive character reveal or smooth fade-in
        size_t chars1 = (std::min)(greetingText1_.length(), static_cast<size_t>(textRevealTimer_ * 40.0f));
        std::wstring visible1 = greetingText1_.substr(0, chars1);

        size_t chars2 = 0;
        if (textRevealTimer_ > 1.0f) {
            chars2 = (std::min)(greetingText2_.length(), static_cast<size_t>((textRevealTimer_ - 1.0f) * 40.0f));
        }
        std::wstring visible2 = greetingText2_.substr(0, chars2);

        D2D1_COLOR_F tCol = D2D1::ColorF(0.93f, 0.95f, 1.0f, fAlpha);
        r.DrawString(visible1, D2D1::RectF(cardX + 24.0f, text1Y, cardX + cardW - 24.0f, text1Y + 36.0f), tCol, 22.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER);
        r.DrawString(visible2, D2D1::RectF(cardX + 24.0f, text2Y, cardX + cardW - 24.0f, text2Y + 36.0f), Color::FromRgba(240, 183, 130, fAlpha), 17.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER);

        // Subtitle note
        r.DrawString(
            L"Все параметры сохранены в профиль. Приятной игры!",
            D2D1::RectF(cardX + 24.0f, cardY + 310.0f, cardX + cardW - 24.0f, cardY + 334.0f),
            Color::FromRgba(207, 189, 154, fAlpha * 0.85f),
            12.0f,
            FontStyle::Regular,
            DWRITE_TEXT_ALIGNMENT_CENTER
        );

        // Finish button
        if (textRevealTimer_ > 0.8f) {
            btnFinish_.Render(r);
        }
    }
}

} // namespace aero

