#pragma once

#include <windows.h>
#include <atomic>
#include <cstdint>
#include <filesystem>
#include <future>
#include <optional>
#include <string>

namespace aero {

class LauncherUpdate {
public:
    LauncherUpdate();
    ~LauncherUpdate();

    // Returns true when the installer has started and the launcher should exit.
    bool Poll(HWND owner, bool gameActive);
    std::wstring OfferedVersion() const;
    void Dismiss() { offer_.reset(); }
    void Accept(const std::filesystem::path& appData);

private:
    struct Release {
        std::string version;
        std::string sha256;
        std::uint64_t size;
    };

    static std::optional<Release> Check();
    std::filesystem::path Download(const Release& release, const std::filesystem::path& appData);

    std::atomic<bool> cancelled_{false};
    std::future<std::optional<Release>> check_;
    std::future<std::filesystem::path> download_;
    std::optional<Release> offer_;
};

} // namespace aero
