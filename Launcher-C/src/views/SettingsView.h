#pragma once
#include "../ui/UIElement.h"
#include "../ui/TextInput.h"
#include "../ui/Button.h"
#include "../ui/Checkbox.h"
#include "../ui/Slider.h"
#include "../ui/ComboBox.h"
#include "../game/GameLauncher.h"
#include <functional>

namespace aero {

class SettingsView : public UIElement {
public:
    SettingsView();

    void Resize(float width, float height);
    void RefreshData();

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

    void SetOnReRunSetup(std::function<void()> cb) { onReRunSetup_ = cb; }

private:
    void SaveSettings();
    void SetRamValue(int gb);

    // Tabs
    Button btnTabGeneral_{ L"Основные", ButtonStyle::Ghost };
    Button btnTabJvm_{ L"Память и Java", ButtonStyle::Ghost };
    Button btnTabTheme_{ L"Оформление", ButtonStyle::Ghost };
    int activeTab_ = 0;

    // Theme buttons
    Button btnThemeEmerald_{ L"Emerald", ButtonStyle::Default };
    Button btnThemeViolet_{ L"Violet", ButtonStyle::Default };
    Button btnThemeCyan_{ L"Cyan", ButtonStyle::Default };
    Button btnThemeAmber_{ L"Amber", ButtonStyle::Default };

    // Toggles
    Checkbox checkStarfield_{ L"Анимированное звёздное поле" };
    Checkbox checkDiscord_{ L"Интеграция с Discord RPC" };
    Checkbox checkGlow_{ L"Фоновое свечение элементов" };
    Checkbox checkSounds_{ L"Звуковые эффекты интерфейса" };

    // Inputs, Combobox & Slider
    TextInput inputGameDir_{ L"Папка игры" };
    ComboBox comboJava_;
    std::vector<JavaInstallation> detectedJavas_;
    TextInput inputJavaPath_{ L"Путь к java.exe (пусто = авто)" };
    Slider sliderRam_{ 1.0f, 16.0f, 4.0f, 1.0f };
    TextInput inputJvmArgs_{ L"Аргументы JVM" };

    // JVM quick presets
    Button btnJvmPerf_{ L"Производительность", ButtonStyle::Default };
    Button btnJvmStable_{ L"Стабильность", ButtonStyle::Default };
    Button btnJvmReset_{ L"Сбросить аргументы", ButtonStyle::Ghost };

    Button btnSave_{ L"Сохранить настройки", ButtonStyle::Primary };
    Button btnBrowseDir_{ L"Выбрать", ButtonStyle::Default };
    Button btnOpenDir_{ L"Открыть", ButtonStyle::Ghost };
    Button btnBrowseJava_{ L"Обзор…", ButtonStyle::Default };
    Button btnReRunSetup_{ L"Пройти начальную настройку заново", ButtonStyle::Default };

    SpringFloat tabPillY_{ 74.0f, 240.0f, 18.0f };
    SpringFloat tabContentOffset_{ 0.0f, 260.0f, 18.0f };
    SpringFloat dispRamVal_{ 4.0f, 220.0f, 18.0f };
    float animTime_ = 0.0f;
    int totalRamGb_ = 16;
    int curRamGb_ = 4;

    std::function<void()> onReRunSetup_;
};

} // namespace aero
