#pragma once
#include "../ui/UIElement.h"
#include "../ui/Button.h"
#include "../ui/TextInput.h"
#include "../core/Animation.h"
#include "../auth/MicrosoftAuth.h"
#include <functional>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>

namespace aero {

enum class SetupStep {
    Account = 0,
    Ram = 1,
    Final = 2
};

class InitialSetupView : public UIElement {
public:
    InitialSetupView();

    void Resize(float width, float height);
    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

    void SetOnFinish(std::function<void()> cb) { onFinish_ = cb; }
    void SyncWithAuth();

private:
    void SetStep(SetupStep step);
    void ApplySettingsAndFinish();

    SetupStep currentStep_ = SetupStep::Account;
    AnimatedFloat stepAnimOffset_{ 0.0f, 18.0f };
    AnimatedFloat finalFadeIn_{ 0.0f, 4.0f };
    float animTime_ = 0.0f;
    float textRevealTimer_ = 0.0f;

    // --- STEP 1: ACCOUNT ---
    enum class AccountMode {
        Offline,
        Microsoft
    };
    AccountMode accountMode_ = AccountMode::Offline;
    Button btnOptOffline_{ L"Офлайн никнейм", ButtonStyle::Primary };
    Button btnOptMicrosoft_{ L"Лицензия Microsoft", ButtonStyle::Default };
    TextInput inputNick_{ L"Steve" };
    Button btnStartMsAuth_{ L"Войти через Microsoft ↗", ButtonStyle::Primary };
    Button btnCopyMsCode_{ L"Скопировать код", ButtonStyle::Ghost };
    Button btnCancelMs_{ L"Отмена", ButtonStyle::Ghost };
    Button btnNextFromAccount_{ L"Далее к выбору памяти →", ButtonStyle::Primary };

    // Microsoft Auth state
    MicrosoftDeviceFlow microsoft_;
    std::string msAuthSuccessNick_;

    // --- STEP 2: RAM (ОЗУ) ---
    int totalRamGb_ = 16;
    int selectedRamGb_ = 4;
    Button btnRam2_{ L"2 ГБ", ButtonStyle::Default };
    Button btnRam4_{ L"4 ГБ", ButtonStyle::Default };
    Button btnRam6_{ L"6 ГБ", ButtonStyle::Default };
    Button btnRam8_{ L"8 ГБ", ButtonStyle::Default };
    Button btnRam12_{ L"12 ГБ", ButtonStyle::Default };
    Button btnRam16_{ L"16 ГБ", ButtonStyle::Default };
    Button btnRamRec_{ L"Рекомендуемый", ButtonStyle::Ghost };
    Button btnBackToAccount_{ L"← Назад", ButtonStyle::Ghost };
    Button btnNextFromRam_{ L"Завершить настройку →", ButtonStyle::Primary };

    // --- STEP 3: FINAL GREETING ---
    std::wstring greetingText1_ = L"Привет. Завершилась настройка лаунчера.";
    std::wstring greetingText2_ = L"Можешь выбирать версию и начать играть.";
    Button btnFinish_{ L"НАЧАТЬ ИГРАТЬ →", ButtonStyle::Play };

    std::function<void()> onFinish_;
};

} // namespace aero
