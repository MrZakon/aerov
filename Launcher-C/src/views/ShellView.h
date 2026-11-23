#pragma once
#include "../ui/UIElement.h"
#include "../ui/Button.h"
#include "PlayView.h"
#include "AccountsView.h"
#include "VersionsView.h"
#include "WorkshopView.h"
#include "GalleryView.h"
#include "ConsoleView.h"
#include "SettingsView.h"
#include <memory>
#include <vector>
#include <future>
#include <array>

namespace aero {

enum class ViewTab {
    Play,
    Accounts,
    Versions,
    Workshop,
    Gallery,
    Console,
    Settings
};

class ShellView : public UIElement {
public:
    ShellView();

    void Resize(float width, float height);
    void RefreshData();
    bool IsGameActive() const;

    void SetActiveTab(ViewTab tab);
    ViewTab GetActiveTab() const { return activeTab_; }

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

    void AppendLog(const std::string& line, bool isError);
    void HandleDropFiles(const std::vector<std::wstring>& files);
    void SetOnReRunSetup(std::function<void()> cb) { settingsView_.SetOnReRunSetup(cb); }
    void SetOnLogout(std::function<void()> cb) { onLogout_ = cb; }
    void SetOnAccountChanged(std::function<void()> cb) { onAccountChanged_ = std::move(cb); }
    void SetAuthChecking(bool value) { authChecking_ = value; }

private:
    struct VisualUpdateResult { std::vector<std::string> updated, errors; };
    void CheckVisualUpdates();
    std::future<VisualUpdateResult> visualUpdates_;
    std::vector<std::future<void>> revocations_;
    std::string checkedVisualAccount_ = "#unchecked";
    std::function<void()> onLogout_;
    std::function<void()> onAccountChanged_;
    std::array<Button*, 7> Navigation();
    UIElement& ActiveView();
    bool InContent(float x, float y) const;
    bool contentMouseDown_ = false;
    bool authChecking_ = false;
    ViewTab activeTab_ = ViewTab::Play;

    // Top navigation buttons
    Button btnNavPlay_{ L"Обзор", ButtonStyle::Ghost };
    Button btnNavAccounts_{ L"Профили", ButtonStyle::Ghost };
    Button btnNavVersions_{ L"Библиотека", ButtonStyle::Ghost };
    Button btnNavWorkshop_{ L"Мастерская", ButtonStyle::Ghost };
    Button btnNavGallery_{ L"Галерея", ButtonStyle::Ghost };
    Button btnNavConsole_{ L"Журнал", ButtonStyle::Ghost };
    Button btnNavSettings_{ L"Настройки", ButtonStyle::Ghost };

    // Header & bottom buttons
    Button btnSocialTg_{ L"TG", ButtonStyle::Default };
    Button btnSocialSite_{ L"WEB", ButtonStyle::Default };
    Button btnLogout_{ L"Выйти из аккаунта", ButtonStyle::Ghost };

    // Views
    PlayView playView_;
    AccountsView accountsView_;
    VersionsView versionsView_;
    WorkshopView workshopView_;
    GalleryView galleryView_;
    ConsoleView consoleView_;
    SettingsView settingsView_;

};

} // namespace aero
