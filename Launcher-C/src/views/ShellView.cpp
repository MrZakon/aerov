#include "ShellView.h"
#include "../core/Config.h"
#include "../store/AccountManager.h"
#include "../store/InstancesManager.h"
#include "../auth/AeroAuth.h"
#include "../ui/Toast.h"
#include "../core/StringUtils.h"
#include "../game/MinecraftInstaller.h"
#include "../game/GameLauncher.h"
#include <shellapi.h>
#include <algorithm>

namespace aero {

ShellView::ShellView() {
    btnNavPlay_.SetIcon("play");
    btnNavPlay_.SetOnClick([this]() { SetActiveTab(ViewTab::Play); });

    btnNavAccounts_.SetIcon("accounts");
    btnNavAccounts_.SetOnClick([this]() { SetActiveTab(ViewTab::Accounts); });

    btnNavVersions_.SetIcon("versions");
    btnNavVersions_.SetOnClick([this]() { SetActiveTab(ViewTab::Versions); });

    btnNavWorkshop_.SetIcon("workshop");
    btnNavWorkshop_.SetOnClick([this]() { SetActiveTab(ViewTab::Workshop); });

    btnNavGallery_.SetIcon("gallery");
    btnNavGallery_.SetOnClick([this]() { SetActiveTab(ViewTab::Gallery); });

    btnNavConsole_.SetIcon("console");
    btnNavConsole_.SetOnClick([this]() { SetActiveTab(ViewTab::Console); });

    btnNavSettings_.SetIcon("settings");
    btnNavSettings_.SetOnClick([this]() { SetActiveTab(ViewTab::Settings); });

    btnSocialTg_.SetOnClick([]() {
        ShellExecuteW(nullptr, L"open", L"https://aeroproject.space/visuals/support.html", nullptr, nullptr, SW_SHOWNORMAL);
    });

    btnSocialSite_.SetOnClick([]() {
        ShellExecuteW(nullptr, L"open", L"https://aeroproject.space/visuals/", nullptr, nullptr, SW_SHOWNORMAL);
    });

    btnLogout_.SetOnClick([this]() {
        accountsView_.CancelSignIn();
        auto& auth = AeroAuth::Instance();
        auto session = auth.GetSession();
        auth.Logout();
        if (!session.isOffline && !session.token.empty()) {
            revocations_.push_back(std::async(std::launch::async, [token = std::move(session.token)] {
                AeroAuth::Revoke(token);
            }));
        }
        AccountManager::Instance().ClearAeroAccount();
        Toast::Instance().Show(L"Вы вышли из учетной записи", ToastKind::Info);
        if (onLogout_) onLogout_();
    });

    playView_.SetOnNavigate([this](const std::string& v) {
        if (v == "versions") {
            SetActiveTab(ViewTab::Versions);
        } else if (v == "workshop") {
            SetActiveTab(ViewTab::Workshop);
        } else if (v == "accounts") {
            SetActiveTab(ViewTab::Accounts);
        }
    });

    playView_.SetOnLog([this](const std::string& line, bool isErr) {
        consoleView_.AppendLog(line, isErr);
    });

    accountsView_.SetOnRedeemed([this] { if (onAccountChanged_) onAccountChanged_(); });
    SetActiveTab(ViewTab::Play);
}

namespace {
constexpr float kContentTop = 136.0f;
constexpr float kFooterHeight = 36.0f;
}

std::array<Button*, 7> ShellView::Navigation() {
    return {&btnNavPlay_, &btnNavAccounts_, &btnNavVersions_, &btnNavWorkshop_,
        &btnNavGallery_, &btnNavConsole_, &btnNavSettings_};
}

UIElement& ShellView::ActiveView() {
    switch (activeTab_) {
    case ViewTab::Play: return playView_;
    case ViewTab::Versions: return versionsView_;
    case ViewTab::Workshop: return workshopView_;
    case ViewTab::Gallery: return galleryView_;
    case ViewTab::Accounts: return accountsView_;
    case ViewTab::Console: return consoleView_;
    case ViewTab::Settings: return settingsView_;
    }
    return playView_;
}

void ShellView::SetActiveTab(ViewTab tab) {
    if (activeTab_ == ViewTab::Accounts && tab != activeTab_) accountsView_.CancelSignIn();
    activeTab_ = tab;
    contentMouseDown_ = false;
    switch (tab) {
    case ViewTab::Play: playView_.RefreshData(); break;
    case ViewTab::Versions: versionsView_.RefreshData(); break;
    case ViewTab::Workshop: workshopView_.RefreshData(); break;
    case ViewTab::Gallery: galleryView_.RefreshData(); break;
    case ViewTab::Accounts: accountsView_.RefreshData(); break;
    case ViewTab::Settings: settingsView_.RefreshData(); break;
    case ViewTab::Console: break;
    }
}

void ShellView::RefreshData() {
    playView_.RefreshData();
    accountsView_.RefreshData();
    versionsView_.RefreshData();
    galleryView_.RefreshData();
    settingsView_.RefreshData();
    CheckVisualUpdates();
}

bool ShellView::IsGameActive() const {
    return playView_.IsPreparingGame() || GameLauncher::Instance().IsRunning();
}

void ShellView::CheckVisualUpdates() {
    if (visualUpdates_.valid() || !SettingsManager::Instance().Get().autoUpdateMod || GameLauncher::Instance().IsRunning() || playView_.IsPreparingGame()) return;
    const auto session = AeroAuth::Instance().GetSession();
    if (!session.verified) return;
    const auto& token = session.token;
    if (checkedVisualAccount_ == token) return;
    checkedVisualAccount_ = token;
    std::vector<InstanceInfo> builds;
    for (const auto& instance : InstancesManager::Instance().GetInstances()) {
        if (instance.loader == "AeroCore" || instance.mcVersion.empty()) continue;
        if (instance.versionId.starts_with("AeroVisual-") || instance.name.find("Aero Visuals") != std::string::npos || instance.name.find("Aero Free") != std::string::npos) builds.push_back(instance);
    }
    if (builds.empty()) return;
    const bool premium = session.hasSubscription && !token.empty();
    playView_.SetCheckingVisualUpdates(true);
    consoleView_.AppendLog("[Update] Проверка обновлений визуалов на AeroProject…", false);
    visualUpdates_ = std::async(std::launch::async, [builds, token, premium] {
        VisualUpdateResult result;
        for (const auto& instance : builds) {
            std::string error;
            bool updated = false;
            try {
                if (!MinecraftInstaller::InstallAeroMod((std::filesystem::path(instance.path) / L"mods").wstring(), instance.mcVersion, premium, token, {}, &error, true, &updated))
                    result.errors.push_back(instance.name + ": " + error);
                else if (updated) result.updated.push_back(instance.name);
            } catch (const std::exception& e) { result.errors.push_back(instance.name + ": " + e.what()); }
        }
        return result;
    });
}




void ShellView::AppendLog(const std::string& line, bool isError) {
    consoleView_.AppendLog(line, isError);
}

void ShellView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);
    const auto nav = Navigation();
    const float navWidth = (width - 64) / nav.size();
    for (size_t i = 0; i < nav.size(); ++i) {
        nav[i]->SetBounds(32 + static_cast<float>(i) * navWidth, 80, navWidth - 6, 40);
    }
    btnSocialTg_.SetBounds(width - 132, height - 31, 28, 24);
    btnSocialSite_.SetBounds(width - 98, height - 31, 32, 24);
    btnLogout_.SetBounds(width - 300, height - 31, 150, 24);

    const float w = width;
    const float h = (std::max)(0.0f, height - kContentTop - kFooterHeight);
    playView_.Resize(w, h);
    accountsView_.Resize(w, h);
    versionsView_.Resize(w, h);
    workshopView_.Resize(w, h);
    galleryView_.Resize(w, h);
    consoleView_.Resize(w, h);
    settingsView_.Resize(w, h);
}

void ShellView::Update(float dt) {
    playView_.DrainLaunchMessages();
    accountsView_.PollRedeem();
    std::erase_if(revocations_, [](const auto& request) {
        return request.wait_for(std::chrono::seconds(0)) == std::future_status::ready;
    });
    if (visualUpdates_.valid() && visualUpdates_.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
        try {
            const auto result = visualUpdates_.get();
            for (const auto& error : result.errors) consoleView_.AppendLog("[Update] " + error, true);
            for (const auto& name : result.updated) consoleView_.AppendLog("[Update] Обновлены визуалы: " + name, false);
            if (!result.updated.empty()) Toast::Instance().Show(L"Визуалы обновлены: " + std::to_wstring(result.updated.size()) + L" сборок", ToastKind::Success);
            else if (result.errors.empty()) consoleView_.AppendLog("[Update] Установлены актуальные версии. Повторная загрузка не требуется.", false);
        } catch (const std::exception& e) { consoleView_.AppendLog("[Update] " + std::string(e.what()), true); }
        playView_.SetCheckingVisualUpdates(false);
    }
    CheckVisualUpdates();
    for (auto* button : Navigation()) button->Update(dt);
    for (auto* button : {&btnSocialTg_, &btnSocialSite_, &btnLogout_}) button->Update(dt);
    ActiveView().Update(dt);
}

bool ShellView::InContent(float x, float y) const {
    return x >= 0 && x <= bounds_.right && y >= kContentTop && y < bounds_.bottom - kFooterHeight;
}

bool ShellView::OnMouseMove(float x, float y) {
    bool handled = false;
    for (auto* button : Navigation()) handled |= button->OnMouseMove(x, y);
    for (auto* button : {&btnSocialTg_, &btnSocialSite_, &btnLogout_}) {
        if (button->IsVisible()) handled |= button->OnMouseMove(x, y);
    }
    return ActiveView().OnMouseMove(x, y - kContentTop) || handled;
}

bool ShellView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    const bool inContent = InContent(x, y);
    if (!down && contentMouseDown_) {
        contentMouseDown_ = false;
        return ActiveView().OnMouseButton(x, y - kContentTop, btn, false);
    }
    if (down && inContent) {
        contentMouseDown_ = true;
        return ActiveView().OnMouseButton(x, y - kContentTop, btn, true);
    }

    bool handled = false;
    for (auto* button : Navigation()) handled |= button->OnMouseButton(x, y, btn, down);
    for (auto* button : {&btnSocialTg_, &btnSocialSite_, &btnLogout_}) {
        handled |= button->OnMouseButton(x, y, btn, down);
    }
    return handled;
}

bool ShellView::OnMouseWheel(float x, float y, float delta) {
    return InContent(x, y) && ActiveView().OnMouseWheel(x, y - kContentTop, delta);
}

bool ShellView::OnChar(wchar_t ch) {
    return ActiveView().OnChar(ch);
}

bool ShellView::OnKey(int vk, bool down) {
    if (down && (GetKeyState(VK_CONTROL) & 0x8000) && vk >= '1' && vk <= '7') {
        SetActiveTab(static_cast<ViewTab>(vk - '1'));
        return true;
    }
    return ActiveView().OnKey(vk, down);
}

void ShellView::Render(D2DRenderer& r) {
    const auto& t = Theme::Instance();
    const float w = bounds_.right;
    const float h = bounds_.bottom;
    const float footer = h - kFooterHeight;
    r.FillRect(D2D1::RectF(0, kContentTop, w, footer), t.bg);
    r.FillRect(D2D1::RectF(0, 60, w, kContentTop), t.surface);
    r.FillRect(D2D1::RectF(0, footer, w, h), t.surface);
    r.DrawLine(D2D1::Point2F(32, kContentTop - 1), D2D1::Point2F(w - 32, kContentTop - 1), t.line);
    const auto nav = Navigation();
    for (size_t i = 0; i < nav.size(); ++i) {
        if (i == static_cast<size_t>(activeTab_)) {
            const auto b = nav[i]->GetBounds();
            r.FillRoundedRect(b, 8, t.panel2);
            r.FillRoundedRect(D2D1::RectF(b.left + 20, b.bottom + 8, b.right - 20, b.bottom + 11), 1.5f, t.accent1);
        }
        nav[i]->Render(r);
    }

    const auto session = AeroAuth::Instance().GetSession();
    const auto name = session.username.empty() ? L"Aero ID" : Utf8ToWide(session.username);
    const auto status = session.isOffline ? std::wstring(L"Локальный режим")
        : session.verified ? L"Aero ID  ·  " + Utf8ToWide(session.planLabel)
        : authChecking_ ? std::wstring(L"Восстанавливаем вход в Aero ID") : std::wstring(L"Повторим вход автоматически");
    r.DrawString(L"Aero Launcher " + Config::AppVersion + L"  /  " + name + L"  ·  " + status,
        D2D1::RectF(32, h - 26, w - 312, h - 9), t.muted, 10);
    btnLogout_.Render(r);

    btnSocialTg_.Render(r);
    btnSocialSite_.Render(r);

    const auto target = r.GetRenderTarget();
    D2D1_MATRIX_3X2_F original;
    target->GetTransform(&original);
    r.PushClip(D2D1::RectF(0, kContentTop, w, footer));
    target->SetTransform(D2D1::Matrix3x2F::Translation(0, kContentTop) * original);
    ActiveView().Render(r);
    target->SetTransform(original);
    r.PopClip();
}

void ShellView::HandleDropFiles(const std::vector<std::wstring>& files) {
    if (files.empty()) return;

    int successCount = 0;
    ImportResult lastRes;

    for (const auto& f : files) {
        auto res = InstancesManager::Instance().ImportContentFile(f);
        if (res.success) {
            successCount++;
            lastRes = res;
        }
    }

    if (successCount == 1) {
        std::wstring catName = (lastRes.kind == "shader") ? L"Шейдеры" : ((lastRes.kind == "resourcepack") ? L"Ресурспак" : L"Мод");
        Toast::Instance().Show(catName + L" «" + lastRes.fileName + L"» установлен в " + Utf8ToWide(lastRes.instanceName), ToastKind::Success);
    } else if (successCount > 1) {
        Toast::Instance().Show(L"Успешно установлено файлов: " + std::to_wstring(successCount) + L" в " + Utf8ToWide(lastRes.instanceName), ToastKind::Success);
    } else {
        Toast::Instance().Show(L"Не удалось импортировать файлы (поддерживаются .jar и .zip)", ToastKind::Error);
    }

    if (successCount > 0) {
        RefreshData();
    }
}

} // namespace aero

