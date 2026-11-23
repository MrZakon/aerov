#pragma once
#include <string>
#include <vector>
#include <functional>
#include <windows.h>
#include <atomic>
#include <thread>
#include "../store/AccountManager.h"
#include "../store/SettingsManager.h"

namespace aero {

struct JavaInstallation {
    std::wstring path;
    int majorVersion = 0;
    std::wstring description;
};

class GameLauncher {
public:
    static GameLauncher& Instance();

    static std::vector<JavaInstallation> ScanJavaInstallations(bool forceRefresh = false);
    static std::string DiagnoseExitCode(int exitCode, const std::vector<std::string>& logLines);
    static std::string GetAeroGuardToken();
    static std::string ResolveVersionFolder(const std::wstring& gameDir, const std::string& versionId);

    bool Launch(
        const std::string& versionId,
        const Account& account,
        const std::wstring& instanceDir,
        std::function<void(const std::string& line, bool isError)> onLog,
        std::function<void(int exitCode, const std::string& diag)> onExit,
        std::string* outError = nullptr,
        std::function<void(const std::wstring&, float)> onPreparation = {}
    );

    bool IsRunning() const { return isRunning_.load(); }
    void TerminateGame();

private:
    GameLauncher() = default;
    ~GameLauncher();

    void MonitorProcess(
        HANDLE hProcess,
        HANDLE hPipeRead,
        std::function<void(const std::string&, bool)> onLog,
        std::function<void(int, const std::string&)> onExit
    );

    std::atomic<bool> isRunning_{ false };
    std::atomic<HANDLE> hCurrentProcess_{ nullptr };
    std::thread monitorThread_;
};

} // namespace aero
