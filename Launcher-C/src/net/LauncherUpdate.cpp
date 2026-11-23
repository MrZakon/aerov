#include "LauncherUpdate.h"
#include "Downloader.h"
#include "HttpClient.h"
#include "../core/Config.h"
#include "../core/Logger.h"
#include "../core/StringUtils.h"
#include "../ui/Toast.h"

#include <objbase.h>
#include <shellapi.h>
#include <algorithm>
#include <array>
#include <charconv>
#include <chrono>
#include <string_view>

namespace aero {
namespace {

using VersionParts = std::array<unsigned, 4>;

std::optional<VersionParts> ParseVersion(std::string_view text) {
    VersionParts version{};
    for (auto& part : version) {
        const auto dot = text.find('.');
        const auto component = text.substr(0, dot);
        if (component.empty()) return std::nullopt;
        const auto end = component.data() + component.size();
        const auto result = std::from_chars(component.data(), end, part);
        if (result.ec != std::errc{} || result.ptr != end) return std::nullopt;
        if (dot == std::string_view::npos) return version;
        text.remove_prefix(dot + 1);
    }
    return std::nullopt;
}

} // namespace

LauncherUpdate::LauncherUpdate() : check_(std::async(std::launch::async, &LauncherUpdate::Check)) {}

LauncherUpdate::~LauncherUpdate() {
    cancelled_.store(true);
}

std::optional<LauncherUpdate::Release> LauncherUpdate::Check() {
    const auto response = HttpClient::ApiRequest(Config::Client::LauncherMeta + "?platform=windows");
    if (!response.isSuccess || !response.json.contains("data")) return std::nullopt;
    const auto& data = response.json["data"];
    if (!data.is_object() || !data.value("available", false) || data.value("kind", "") != "launcher" ||
        data.value("platform", "") != "windows" || !data.contains("size") || !data["size"].is_number_integer()) {
        return std::nullopt;
    }

    const auto local = ParseVersion(WideToUtf8(Config::AppVersion));
    const auto version = data.value("version", std::string{});
    const auto remote = ParseVersion(version);
    if (!local || !remote || *remote <= *local) return std::nullopt;

    const auto sha256 = data.value("sha256", std::string{});
    const auto size = data["size"].get<std::uint64_t>();
    constexpr std::uint64_t maxInstallerSize = 1024ULL * 1024 * 1024;
    if (size == 0 || size > maxInstallerSize || sha256.size() != 64 ||
        !std::all_of(sha256.begin(), sha256.end(), [](char c) {
            return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        })) return std::nullopt;

    return Release{version, sha256, size};
}

std::filesystem::path LauncherUpdate::Download(const Release& release, const std::filesystem::path& appData) {
    GUID id{};
    wchar_t idString[40]{};
    if (appData.empty() || FAILED(CoCreateGuid(&id)) || !StringFromGUID2(id, idString, 40)) return {};

    const auto folder = appData / L"updates" / idString;
    const auto installer = folder / (L"Aero Launcher Setup " + Utf8ToWide(release.version) + L".exe");
    DownloadOptions options;
    options.url = Config::DefaultBaseUrl + Config::Client::LauncherFile + "?platform=windows";
    options.destPath = installer.wstring();
    options.expectedSize = release.size;
    options.expectedSha256 = release.sha256;
    options.isCancelled = [this] { return cancelled_.load(); };
    options.publicArtifact = true;
    if (Downloader::DownloadFile(options)) return installer;

    std::error_code ec;
    std::filesystem::remove(installer.wstring() + L".part", ec);
    std::filesystem::remove(folder, ec);
    return {};
}

std::wstring LauncherUpdate::OfferedVersion() const {
    return offer_ ? Utf8ToWide(offer_->version) : std::wstring{};
}

void LauncherUpdate::Accept(const std::filesystem::path& appData) {
    if (!offer_) return;
    auto release = std::move(*offer_);
    Dismiss();
    try {
        download_ = std::async(std::launch::async, &LauncherUpdate::Download, this,
            std::move(release), appData);
        Toast::Instance().Show(L"Скачиваю обновление Aero Launcher…");
    } catch (const std::exception& error) {
        Logger::Warn(std::string("[LauncherUpdate] ") + error.what());
        Toast::Instance().Show(L"Не удалось начать загрузку обновления.", ToastKind::Error);
    }
}

bool LauncherUpdate::Poll(HWND owner, bool gameActive) {
    const auto ready = [](const auto& job) {
        return job.valid() && job.wait_for(std::chrono::seconds(0)) == std::future_status::ready;
    };
    auto& toast = Toast::Instance();
    try {
        if (!gameActive && ready(check_)) offer_ = check_.get();
        if (ready(download_)) {
            const auto installer = download_.get();
            if (installer.empty()) {
                toast.Show(L"Не удалось скачать или проверить обновление.", ToastKind::Error);
            } else if (gameActive) {
                Logger::Info("[LauncherUpdate] " + WideToUtf8(installer.wstring()));
                toast.Show(L"Установщик скачан. Закройте Minecraft и перезапустите лаунчер.");
            } else {
                const auto result = ShellExecuteW(owner, L"open", installer.c_str(), nullptr,
                    installer.parent_path().c_str(), SW_SHOWNORMAL);
                if (reinterpret_cast<INT_PTR>(result) > 32) return true;
                toast.Show(L"Не удалось открыть установщик Aero Launcher.", ToastKind::Error);
            }
        }
    } catch (const std::exception& error) {
        Logger::Warn(std::string("[LauncherUpdate] ") + error.what());
        toast.Show(L"Не удалось проверить или скачать обновление.", ToastKind::Error);
    }
    return false;
}

} // namespace aero
