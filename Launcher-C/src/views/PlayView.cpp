#include "PlayView.h"
#include "../auth/AeroAuth.h"
#include "../auth/MicrosoftAuth.h"
#include "../core/StringUtils.h"
#include "../game/AeroCore.h"
#include "../game/GameLauncher.h"
#include "../game/MinecraftInstaller.h"
#include "../net/Downloader.h"
#include "../store/AccountManager.h"
#include "../store/InstancesManager.h"
#include "../store/SettingsManager.h"
#include "../ui/Toast.h"
#include "../render/AeroDesign.h"
#include <algorithm>
#include <filesystem>
#include <regex>

namespace aero {

namespace fs = std::filesystem;

namespace {

struct PlayLayout {
    D2D1_RECT_F art;
    D2D1_RECT_F panel;
    float end;
};

PlayLayout Layout(float w) {
    if (w >= 1000) {
        return {D2D1::RectF(32, 92, w - 410, 424),
            D2D1::RectF(w - 386, 92, w - 32, 488), 570};
    }
    return {D2D1::RectF(32, 92, w - 32, 424),
        D2D1::RectF(32, 444, w - 32, 840), 864};
}

bool IsAero(const InstanceInfo& instance) {
    return instance.versionId.starts_with("AeroVisual-") ||
        instance.name.find("Aero Visuals") != std::string::npos ||
        instance.name.find("Aero Free") != std::string::npos;
}

std::string MinecraftVersion(const InstanceInfo& instance) {
    if (!instance.mcVersion.empty()) return instance.mcVersion;
    static const std::regex version(R"((\d+\.\d+(?:\.\d+)?))");
    std::smatch match;
    return std::regex_search(instance.versionId, match, version) ? match[1].str() : std::string{};
}

bool NeedsMicrosoftRefresh(const Account& account) {
    if (account.type != AccountType::Microsoft) return false;
    if (account.accessToken.empty() || account.accessToken == "microsoft_license") return true;
    const auto expiry = account.expiresAt > 100000000000LL ? account.expiresAt / 1000 : account.expiresAt;
    if (expiry == 0) return !account.refreshToken.empty() || account.accessToken.size() < 32;
    const auto now = std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    return expiry <= now + 120;
}

std::string MinecraftIdentity(std::string uuid) {
    std::erase(uuid, '-');
    std::transform(uuid.begin(), uuid.end(), uuid.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });
    return uuid;
}

} // namespace

PlayView::PlayView() {
    btnQuickManageVer_.SetOnClick([this]() { if (onNavigate_) onNavigate_("versions"); });
    btnPlay_.SetOnClick([this]() { StartLaunch(); });

    comboAccounts_.SetOnSelect([](int index, const std::wstring&) {
        const auto& accounts = AccountManager::Instance().GetAccounts();
        if (index >= 0 && static_cast<size_t>(index) < accounts.size()) {
            AccountManager::Instance().SetActiveAccount(accounts[index].id);
        }
    });

    comboVersions_.SetOnSelect([this](int index, const std::wstring&) {
        if (index >= 0 && static_cast<size_t>(index) < versionIds_.size() &&
            !InstancesManager::Instance().SelectInstance(versionIds_[index])) {
            launch_->Report(L"Сборка больше недоступна. Выберите другую.");
        }
    });
}

PlayView::~PlayView() {
    if (launchWorker_.valid() && launchWorker_.wait_for(std::chrono::seconds(0)) != std::future_status::ready) {
        Downloader::CancelAll();
        launchWorker_.wait();
    }
}

void PlayView::LaunchStatus::Report(std::wstring text, float value) {
    std::lock_guard lock(mutex);
    status = std::move(text);
    progress = value;
}

void PlayView::LaunchStatus::Log(std::string text, bool error) {
    std::lock_guard lock(mutex);
    if (logs.size() < 1000) logs.emplace_back(std::move(text), error);
}

void PlayView::DrainLaunchMessages() {
    std::vector<std::pair<std::string, bool>> logs;
    std::optional<std::pair<Account, std::string>> renewed;
    {
        std::lock_guard lock(launch_->mutex);
        logs.swap(launch_->logs);
        renewed.swap(launch_->renewedAccount);
    }
    if (renewed) {
        auto& store = AccountManager::Instance();
        const auto& [account, precedingToken] = *renewed;
        const auto& accounts = store.GetAccounts();
        const auto current = std::find_if(accounts.begin(), accounts.end(), [&](const Account& candidate) {
            return candidate.id == account.id && candidate.accessToken == precedingToken;
        });
        if (current != accounts.end()) store.UpdateAccount(account);
    }
    if (onLog_) for (const auto& [line, error] : logs) onLog_(line, error);
    if (launchWorker_.valid() && launchWorker_.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
        try { launchWorker_.get(); }
        catch (const std::exception& error) {
            launch_->Report(L"Не удалось подготовить Minecraft");
            if (onLog_) onLog_(error.what(), true);
        }
    }
}

void PlayView::RefreshData() {
    auto& store = AccountManager::Instance();
    auto& settings = SettingsManager::Instance().Get();
    const auto& accounts = store.GetAccounts();
    auto* active = store.GetActiveAccount();
    if (!active && !accounts.empty()) {
        store.SetActiveAccount(accounts.front().id);
        active = store.GetActiveAccount();
    }

    std::vector<std::wstring> profiles;
    int profileIndex = 0;
    for (size_t i = 0; i < accounts.size(); ++i) {
        const auto& account = accounts[i];
        profiles.push_back(Utf8ToWide(account.username) +
            (account.type == AccountType::Microsoft ? L"  ·  Microsoft" : L"  ·  локальный"));
        if (active && active->id == account.id) profileIndex = static_cast<int>(i);
    }
    if (profiles.empty()) profiles.emplace_back(L"Нет игрового профиля");
    comboAccounts_.SetItems(profiles);
    comboAccounts_.SetSelectedIndex(profileIndex);
    comboAccounts_.SetEnabled(!accounts.empty());

    auto& library = InstancesManager::Instance();
    library.ScanInstances(settings.gameDir);
    const auto instances = library.GetInstances();
    const auto activeBuild = library.GetActiveInstance();
    std::vector<std::wstring> builds;
    versionIds_.clear();
    int selected = 0;
    for (size_t i = 0; i < instances.size(); ++i) {
        const auto& instance = instances[i];
        builds.push_back(Utf8ToWide(instance.name.empty() ? instance.versionId : instance.name));
        versionIds_.push_back(instance.id);
        if (activeBuild && instance.id == activeBuild->id) selected = static_cast<int>(i);
    }
    if (builds.empty()) builds.emplace_back(L"Нет установленных сборок");
    comboVersions_.SetItems(builds);
    comboVersions_.SetSelectedIndex(selected);
    comboVersions_.SetEnabled(!instances.empty());
}

void PlayView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);
    scrollView_.SetBounds(bounds_);
    const auto layout = Layout(width);
    const auto panel = layout.panel;
    const float x = panel.left + 24;
    const float w = panel.right - x - 24;
    const float y = panel.top;
    comboAccounts_.SetBounds(x, y + 92, w, 44);
    comboVersions_.SetBounds(x, y + 182, w, 44);
    btnQuickManageVer_.SetBounds(panel.right - 132, y + 234, 108, 28);
    btnPlay_.SetBounds(x, y + 284, w, 56);
    scrollView_.SetContentHeight(layout.end);
}

void PlayView::Update(float dt) {
    scrollView_.Update(dt);
    comboAccounts_.Update(dt);
    comboVersions_.Update(dt);
    btnQuickManageVer_.Update(dt);
    btnPlay_.Update(dt);
    const bool running = GameLauncher::Instance().IsRunning();
    const bool preparing = launchWorker_.valid();
    btnPlay_.SetEnabled(!checkingVisualUpdates_ && !running && !preparing);
    btnPlay_.SetText(checkingVisualUpdates_ ? L"Проверяем обновления…"
        : running ? L"Minecraft запущен"
        : preparing ? L"Подготовка к запуску…"
        : versionIds_.empty() ? L"Открыть библиотеку"
        : !AccountManager::Instance().GetActiveAccount() ? L"Добавить профиль" : L"Запустить Minecraft");
}

std::array<UIElement*, 4> PlayView::LaunchControls() {
    return {&comboAccounts_, &btnQuickManageVer_, &comboVersions_, &btnPlay_};
}

bool PlayView::OnMouseMove(float x, float y) {
    const float py = y + scrollView_.GetScrollOffset();
    if (comboAccounts_.IsExpanded()) return comboAccounts_.OnMouseMove(x, py);
    if (comboVersions_.IsExpanded()) return comboVersions_.OnMouseMove(x, py);
    bool handled = false;
    for (auto* element : LaunchControls()) {
        handled |= element->OnMouseMove(x, py);
    }
    return handled;
}

bool PlayView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    const float py = y + scrollView_.GetScrollOffset();
    if (comboAccounts_.IsExpanded() && comboAccounts_.OnMouseButton(x, py, btn, down)) return true;
    if (comboVersions_.IsExpanded() && comboVersions_.OnMouseButton(x, py, btn, down)) return true;
    const auto controls = LaunchControls();
    if (btn == MouseBtn::Left && down) {
        for (auto* element : controls)
            element->SetFocused(element->IsEnabled() && element->Contains(x, py));
    }
    for (auto* element : controls) {
        if (element->OnMouseButton(x, py, btn, down)) return true;
    }
    return scrollView_.OnMouseButton(x, y, btn, down);
}

bool PlayView::OnMouseWheel(float x, float y, float delta) {
    const float py = y + scrollView_.GetScrollOffset();
    if (comboAccounts_.IsExpanded()) return comboAccounts_.OnMouseWheel(x, py, delta);
    if (comboVersions_.IsExpanded()) return comboVersions_.OnMouseWheel(x, py, delta);
    return scrollView_.OnMouseWheel(x, y, delta);
}

bool PlayView::OnChar(wchar_t) { return false; }

bool PlayView::OnKey(int vk, bool down) {
    if (down && vk == VK_TAB) {
        comboAccounts_.CloseDropdown();
        comboVersions_.CloseDropdown();
        std::vector<UIElement*> controls;
        for (auto* element : LaunchControls()) {
            if (element->IsEnabled()) controls.push_back(element);
            else element->SetFocused(false);
        }
        if (controls.empty()) return true;
        const auto focused = std::find_if(controls.begin(), controls.end(), [](UIElement* element) {
            return element->IsFocused();
        });
        const bool reverse = (GetKeyState(VK_SHIFT) & 0x8000) != 0;
        const auto count = controls.size();
        const auto current = focused == controls.end() ? (reverse ? 0 : count - 1)
            : static_cast<size_t>(focused - controls.begin());
        for (auto* element : controls) element->SetFocused(false);
        auto* target = controls[(current + (reverse ? count - 1 : 1)) % count];
        target->SetFocused(true);
        const auto bounds = target->GetBounds();
        scrollView_.Reveal(bounds.top - 8.0f, bounds.bottom + 8.0f);
        return true;
    }
    if (comboAccounts_.IsExpanded()) return comboAccounts_.OnKey(vk, down);
    if (comboVersions_.IsExpanded()) return comboVersions_.OnKey(vk, down);
    for (auto* element : LaunchControls()) {
        if (element->IsFocused() && element->IsEnabled()) return element->OnKey(vk, down);
    }
    if (down && vk == VK_RETURN && btnPlay_.IsEnabled()) {
        StartLaunch();
        return true;
    }
    return false;
}

void PlayView::StartLaunch() {
    if (checkingVisualUpdates_ || launchWorker_.valid() || GameLauncher::Instance().IsRunning()) return;
    const auto index = comboVersions_.GetSelectedIndex();
    if (index < 0 || static_cast<size_t>(index) >= versionIds_.size()) {
        if (onNavigate_) onNavigate_("versions");
        return;
    }
    auto& library = InstancesManager::Instance();
    const auto selectedBuild = library.GetInstance(versionIds_[index]);
    if (!selectedBuild) {
        RefreshData();
        launch_->Report(L"Сборка больше недоступна. Выберите другую.");
        return;
    }
    const auto* account = AccountManager::Instance().GetActiveAccount();
    if (!account) {
        if (onNavigate_) onNavigate_("accounts");
        return;
    }
    const auto instance = *selectedBuild;
    const auto session = AeroAuth::Instance().GetSession();
    const bool aero = instance.loader != "AeroCore" && IsAero(instance);
    if (aero && !session.verified) {
        launch_->Report(session.isOffline ? L"Для AeroVisuals нужен вход в Aero ID" : L"Проверьте связь с Aero ID");
        return;
    }
    const auto gameDir = SettingsManager::Instance().Get().gameDir;
    if (SettingsManager::Instance().Get().activeInstanceId != instance.id && !library.SelectInstance(instance.id)) return;
    Downloader::ResetCancel();
    launch_->Report(L"Подготовка к запуску");
    launchWorker_ = std::async(std::launch::async, [state = launch_, instance, account = *account, session, gameDir, aero]() mutable {
        const fs::path mods = fs::path(instance.path) / L"mods";
        const auto& target = instance.versionId.empty() ? instance.name : instance.versionId;
        const auto version = MinecraftVersion(instance);
        const auto progress = [state](float amount, const std::wstring& status) { state->Report(status, amount); };
        const auto reportFailure = [state](std::wstring status, const std::string& detail) {
            state->Log(detail, true);
            state->Report(std::move(status));
        };
        std::string error;
        if (NeedsMicrosoftRefresh(account)) {
            state->Report(L"Обновляем сессию Microsoft");
            MicrosoftProfile profile;
            if (!MicrosoftAuth::RefreshProfile(account.refreshToken, profile, &error) ||
                MinecraftIdentity(profile.uuid) != MinecraftIdentity(account.uuid)) {
                if (error.empty()) error = "Microsoft вернул другой профиль Minecraft";
                reportFailure(Utf8ToWide(error), error);
                return;
            }
            const auto precedingToken = account.accessToken;
            account.username = std::move(profile.nickname);
            account.uuid = std::move(profile.uuid);
            account.accessToken = std::move(profile.accessToken);
            account.refreshToken = std::move(profile.refreshToken);
            account.expiresAt = profile.expiresAt;
            std::lock_guard lock(state->mutex);
            state->renewedAccount.emplace(account, precedingToken);
        }
        if (target == aerocore::VersionId) {
            if (!aerocore::Ready(gameDir) && !aerocore::Install(gameDir, progress, &error)) {
                reportFailure(L"Не удалось подготовить AeroCore", error);
                return;
            }
        } else {
            const bool fabric = instance.loader == "Fabric" || instance.loader == "fabric" || aero ||
                target.starts_with("fabric-loader-");
            const auto install = [&] {
                if (version.empty()) {
                    error = "Не удалось определить версию Minecraft для " + instance.name;
                    return false;
                }
                return fabric ? MinecraftInstaller::InstallFabric(gameDir, version, "", progress, &error)
                    : MinecraftInstaller::InstallVanilla(gameDir, version, progress, &error);
            };
            auto resolved = GameLauncher::ResolveVersionFolder(gameDir, target);
            const auto versionDir = fs::path(gameDir) / L"versions" / Utf8ToWide(resolved);
            if (!fs::exists(versionDir / Utf8ToWide(resolved + ".json"))) {
                if (!install()) {
                    reportFailure(L"Не удалось установить Minecraft", error);
                    return;
                }
                resolved = GameLauncher::ResolveVersionFolder(gameDir, target);
            }
            state->Report(L"Проверка игровых файлов");
            if (!MinecraftInstaller::VerifyAndRepair(gameDir, resolved, progress, &error)) {
                if (Downloader::IsCancelled() || !install()) {
                    reportFailure(L"Не удалось восстановить файлы Minecraft", error);
                    return;
                }
                resolved = GameLauncher::ResolveVersionFolder(gameDir, target);
                if (!MinecraftInstaller::VerifyAndRepair(gameDir, resolved, progress, &error)) {
                    reportFailure(L"Проверка файлов Minecraft не прошла", error);
                    return;
                }
            }
            if (fabric) {
                std::error_code ec;
                fs::create_directories(mods, ec);
                bool hasFabricApi = false;
                for (const auto& file : fs::directory_iterator(mods, ec)) {
                    if (!file.is_regular_file(ec)) continue;
                    auto name = WideToUtf8(file.path().filename().wstring());
                    std::transform(name.begin(), name.end(), name.begin(), [](unsigned char c) {
                        return static_cast<char>(std::tolower(c));
                    });
                    if (name.starts_with("fabric-api") && (name.ends_with(".jar") || name.ends_with(".disabled")) &&
                        file.file_size(ec) > 1000 && !ec) {
                        hasFabricApi = true;
                        break;
                    }
                }
                if (!hasFabricApi && !MinecraftInstaller::InstallFabricApi(mods.wstring(), version, progress, &error)) {
                    state->Log("[Fabric API] " + error, true);
                }
            }
            if (aero) {
                state->Report(L"Проверка AeroVisuals");
                if (!MinecraftInstaller::InstallAeroMod(mods.wstring(), version, session.hasSubscription,
                    session.token, progress, &error, true)) {
                    reportFailure(L"Не удалось подготовить AeroVisuals", error);
                    return;
                }
            }
        }

        if (Downloader::IsCancelled()) return;
        state->Report(L"Запуск Minecraft");
        const bool launched = GameLauncher::Instance().Launch(target, account, instance.path,
            [state](const std::string& line, bool error) { state->Log(line, error); },
            [state](int exitCode, const std::string& diagnostic) {
                state->Report(exitCode == 0 ? L"Игра завершена" : Utf8ToWide(diagnostic));
            }, &error, [state](const std::wstring& status, float value) { state->Report(status, value); });
        if (!launched) reportFailure(L"Не удалось запустить Minecraft", error);
    });
}

void PlayView::Render(D2DRenderer& r) {
    const auto& t = Theme::Instance();
    const float w = GetWidth();
    const auto layout = Layout(w);
    const auto art = layout.art;
    const auto panel = layout.panel;
    const auto x = panel.left + 24;
    const auto y = panel.top;
    const auto right = panel.right - 24;
    scrollView_.BeginRender(r);

    r.DrawString(L"ИГРА НАЧИНАЕТСЯ ЗДЕСЬ", D2D1::RectF(32, 20, w - 32, 38), t.accent1, 10, FontStyle::Bold);
    r.DrawString(L"Исследуй дальше.", D2D1::RectF(32, 42, w - 32, 82), t.text, 30, FontStyle::Bold);
    aero::Landscape(r, art);
    r.DrawString(L"A E R O  /  M I N E C R A F T", D2D1::RectF(art.left + 26, 118, art.right - 26, 138),
        Color::FromHex(0xf6e4c8), 10, FontStyle::Bold);
    r.DrawString(L"Больше, чем\nпросто блоки.", D2D1::RectF(art.left + 26, 286, art.right - 24, 382),
        Color::FromHex(0xfff5e5), 34, FontStyle::Bold);
    r.DrawString(L"Твой следующий мир уже ждёт.", D2D1::RectF(art.left + 26, 388, art.right - 24, 410),
        Color::FromHex(0xc4c9b8), 12);
    if (w >= 1000) {
        const float divider = (art.left + art.right) * 0.5f;
        aero::Panel(r, D2D1::RectF(art.left, 440, divider - 6, 550));
        aero::Panel(r, D2D1::RectF(divider + 6, 440, art.right, 550));
        r.DrawString(std::to_wstring(InstancesManager::Instance().GetInstances().size()),
            D2D1::RectF(art.left + 20, 452, divider - 20, 492), t.text, 28, FontStyle::Bold);
        r.DrawString(L"Сборок в библиотеке", D2D1::RectF(art.left + 20, 505, divider - 20, 530), t.muted, 11);
        r.DrawString(L"Modrinth", D2D1::RectF(divider + 26, 458, art.right - 16, 490), t.text, 20, FontStyle::Bold);
        r.DrawString(L"Моды и шейдеры — в Мастерской",
            D2D1::RectF(divider + 26, 505, art.right - 16, 540), t.muted, 11);
    }

    aero::Panel(r, panel);
    r.DrawString(L"БЫСТРЫЙ ЗАПУСК", D2D1::RectF(x, y + 22, right, y + 38), t.accent1, 10, FontStyle::Bold);
    r.DrawString(L"Настрой свой полёт", D2D1::RectF(x, y + 44, right, y + 72), t.text, 20, FontStyle::Bold);
    r.DrawString(L"01   ИГРОВОЙ ПРОФИЛЬ", D2D1::RectF(x, y + 74, right, y + 90), t.muted, 9, FontStyle::Bold);
    r.DrawString(L"02   ВЕРСИЯ И СБОРКА", D2D1::RectF(x, y + 162, right, y + 180), t.muted, 9, FontStyle::Bold);
    r.DrawString(versionIds_.empty() ? L"Добавь первую сборку в библиотеке." : L"Выбери сборку и отправляйся в игру.",
        D2D1::RectF(x, y + 242, panel.right - 140, y + 279), t.muted, 10);

    comboAccounts_.Render(r);
    comboVersions_.Render(r);
    btnQuickManageVer_.Render(r);
    btnPlay_.Render(r);

    std::wstring status;
    float percent;
    {
        std::lock_guard lock(launch_->mutex);
        status = launch_->status;
        percent = launch_->progress;
    }
    if (GameLauncher::Instance().IsRunning()) status = L"Minecraft запущен";
    else if (checkingVisualUpdates_) status = L"Получаем версии AeroVisuals";
    else if (versionIds_.empty()) status = L"Установите первую сборку из библиотеки";
    r.DrawString(status, D2D1::RectF(x, y + 354, right, y + 382), t.muted, 11, FontStyle::Regular,
        DWRITE_TEXT_ALIGNMENT_CENTER);
    if (launchWorker_.valid() && percent >= 0) {
        r.FillRoundedRect(D2D1::RectF(x, y + 382, right, y + 386), 2, t.panel2);
        const float end = x + (right - x) * std::clamp(percent, 0.0f, 1.0f);
        if (end > x) r.FillRoundedRect(D2D1::RectF(x, y + 382, end, y + 386), 2, t.accent1);
    }

    comboAccounts_.RenderDropdown(r);
    comboVersions_.RenderDropdown(r);
    scrollView_.EndRender(r);
}

} // namespace aero
