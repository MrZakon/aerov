#pragma once
#include "../ui/UIElement.h"
#include "../ui/Button.h"
#include "../ui/ComboBox.h"
#include "../ui/ProgressBar.h"
#include "../ui/ScrollView.h"
#include "../store/AccountManager.h"
#include <functional>
#include <string>
#include <vector>
#include <mutex>
#include <future>
#include <optional>
#include <array>

namespace aero {

class PlayView : public UIElement {
public:
    PlayView();
    ~PlayView() override;

    void Resize(float width, float height);
    void RefreshData();

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

    void SetOnNavigate(std::function<void(const std::string&)> cb) { onNavigate_ = cb; }
    void SetOnLog(std::function<void(const std::string&, bool)> cb) { onLog_ = cb; }
    void SetCheckingVisualUpdates(bool value) { checkingVisualUpdates_ = value; }
    bool IsPreparingGame() const { return launchWorker_.valid(); }
    void DrainLaunchMessages();

private:
    std::function<void(const std::string&, bool)> onLog_;
    void StartLaunch();
    std::array<UIElement*, 4> LaunchControls();

    // Scroll
    ScrollView scrollView_;

    // Launch configuration controls
    ComboBox comboAccounts_;
    ComboBox comboVersions_;
    std::vector<std::string> versionIds_;
    Button btnQuickManageVer_{ L"Библиотека", ButtonStyle::Ghost };
    Button btnPlay_{ L"Запустить Minecraft", ButtonStyle::Play };

    // Game state
    struct LaunchStatus {
        void Report(std::wstring text, float progress = -1);
        void Log(std::string text, bool error);
        std::mutex mutex;
        std::wstring status = L"Игра не запущена";
        float progress = -1;
        std::vector<std::pair<std::string, bool>> logs;
        std::optional<std::pair<Account, std::string>> renewedAccount;
    };
    std::shared_ptr<LaunchStatus> launch_ = std::make_shared<LaunchStatus>();
    std::future<void> launchWorker_;
    bool checkingVisualUpdates_ = false;

    // Navigation callback
    std::function<void(const std::string&)> onNavigate_;

};

} // namespace aero
