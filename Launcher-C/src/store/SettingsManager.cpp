#include "SettingsManager.h"
#include "../core/StringUtils.h"
#include <windows.h>
#include <shlobj.h>
#include <fstream>
#include <filesystem>

namespace aero {

namespace fs = std::filesystem;

SettingsManager::SettingsManager() {
    std::wstring appData = GetAppDataAeroDir();
    settingsFilePath_ = (fs::path(appData) / L"settings.json").wstring();
    data_.gameDir = GetDefaultGameDir();

    // Default recommended RAM based on system memory
    MEMORYSTATUSEX memStatus = { sizeof(MEMORYSTATUSEX) };
    if (GlobalMemoryStatusEx(&memStatus)) {
        int totalGb = static_cast<int>(memStatus.ullTotalPhys / (1024ULL * 1024 * 1024));
        data_.memoryMax = (std::min)(8, (std::max)(2, totalGb / 2));
    }
}

SettingsManager& SettingsManager::Instance() {
    static SettingsManager s_instance;
    return s_instance;
}

std::wstring SettingsManager::GetAppDataAeroDir() {
#ifdef AERO_TESTING
    fs::path testDir = fs::temp_directory_path() / L"aero-test-data" / std::to_wstring(GetCurrentProcessId());
    fs::create_directories(testDir);
    return testDir.wstring();
#endif
    wchar_t path[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_APPDATA, nullptr, 0, path))) {
        fs::path p = fs::path(path) / L"aero";
        std::error_code ec;
        fs::create_directories(p, ec);
        return p.wstring();
    }
    return L"";
}

std::wstring SettingsManager::GetDefaultGameDir() {
#ifdef AERO_TESTING
    fs::path testDir = fs::path(GetAppDataAeroDir()) / L"game";
    fs::create_directories(testDir);
    return testDir.wstring();
#endif
    wchar_t path[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_APPDATA, nullptr, 0, path))) {
        fs::path p = fs::path(path) / L".aero";
        std::error_code ec;
        fs::create_directories(p, ec);
        return p.wstring();
    }
    return L"";
}

void SettingsManager::Load() {
    std::ifstream file{fs::path(settingsFilePath_)};
    if (!file.is_open()) return;

    try {
        nlohmann::json j;
        file >> j;

        if (j.contains("initialSetupDone")) data_.initialSetupDone = j["initialSetupDone"].get<bool>();
        if (j.contains("gameDir")) {
            std::wstring gd = Utf8ToWide(j["gameDir"].get<std::string>());
            if ((gd.find(L"Р ") != std::wstring::npos || gd.find(L"Р") != std::wstring::npos) && !fs::exists(gd)) {
                gd = GetDefaultGameDir();
            } else if (!fs::exists(gd)) {
                std::error_code ec;
                fs::create_directories(gd, ec);
                if (ec) gd = GetDefaultGameDir();
            }
            data_.gameDir = gd;
        }
        if (j.contains("javaPath")) data_.javaPath = Utf8ToWide(j["javaPath"].get<std::string>());
        if (j.contains("memoryMin")) data_.memoryMin = j["memoryMin"].get<int>();
        if (j.contains("memoryMax")) data_.memoryMax = j["memoryMax"].get<int>();
        if (j.contains("jvmArgs")) data_.jvmArgs = j["jvmArgs"].get<std::string>();
        if (j.contains("width")) data_.windowWidth = j["width"].get<int>();
        if (j.contains("height")) data_.windowHeight = j["height"].get<int>();
        if (j.contains("fullscreen")) data_.fullscreen = j["fullscreen"].get<bool>();
        if (j.contains("afterLaunch")) data_.afterLaunch = j["afterLaunch"].get<std::string>();
        if (j.contains("autoUpdateMod")) data_.autoUpdateMod = j["autoUpdateMod"].get<bool>();
        if (j.contains("checkUpdatesOnStart")) data_.checkUpdatesOnStart = j["checkUpdatesOnStart"].get<bool>();
        if (j.contains("keepLoggedIn")) data_.keepLoggedIn = j["keepLoggedIn"].get<bool>();

        if (j.contains("activeAccountId")) data_.activeAccountId = j["activeAccountId"].get<std::string>();
        if (j.contains("selectedVersionId")) data_.selectedVersionId = j["selectedVersionId"].get<std::string>();
        if (j.contains("activeInstanceId")) data_.activeInstanceId = j["activeInstanceId"].get<std::string>();
        if (j.contains("contentTargetId")) data_.contentTargetId = j["contentTargetId"].get<std::string>();
        if (j.contains("modMcVersion")) data_.modMcVersion = j["modMcVersion"].get<std::string>();
        if (j.contains("aeroVersionId")) data_.aeroVersionId = j["aeroVersionId"].get<std::string>();

        if (j.contains("themeAccent")) data_.themeAccent = j["themeAccent"].get<std::string>();
        if (j.contains("uiScale")) data_.uiScale = j["uiScale"].get<std::string>();
        if (j.contains("bgGlow")) data_.bgGlow = j["bgGlow"].get<bool>();
        if (j.contains("soundEffects")) data_.soundEffects = j["soundEffects"].get<bool>();
        if (j.contains("starfield")) data_.starfield = j["starfield"].get<bool>();

        if (j.contains("discordRpc")) data_.discordRpc = j["discordRpc"].get<bool>();
        if (j.contains("discordClientId")) data_.discordClientId = j["discordClientId"].get<std::string>();
        if (j.contains("downloadThreads")) data_.downloadThreads = j["downloadThreads"].get<int>();
        if (j.contains("assetsMirror")) data_.assetsMirror = j["assetsMirror"].get<std::string>();
    } catch (...) {
        // Fallback to defaults
    }
}

void SettingsManager::Save() {
    nlohmann::json j;
    j["initialSetupDone"] = data_.initialSetupDone;
    j["gameDir"] = WideToUtf8(data_.gameDir);
    j["javaPath"] = WideToUtf8(data_.javaPath);
    j["memoryMin"] = data_.memoryMin;
    j["memoryMax"] = data_.memoryMax;
    j["jvmArgs"] = data_.jvmArgs;
    j["width"] = data_.windowWidth;
    j["height"] = data_.windowHeight;
    j["fullscreen"] = data_.fullscreen;
    j["afterLaunch"] = data_.afterLaunch;
    j["autoUpdateMod"] = data_.autoUpdateMod;
    j["checkUpdatesOnStart"] = data_.checkUpdatesOnStart;
    j["keepLoggedIn"] = data_.keepLoggedIn;

    j["activeAccountId"] = data_.activeAccountId;
    j["selectedVersionId"] = data_.selectedVersionId;
    j["activeInstanceId"] = data_.activeInstanceId;
    j["contentTargetId"] = data_.contentTargetId;
    j["modMcVersion"] = data_.modMcVersion;
    j["aeroVersionId"] = data_.aeroVersionId;

    j["themeAccent"] = data_.themeAccent;
    j["uiScale"] = data_.uiScale;
    j["bgGlow"] = data_.bgGlow;
    j["soundEffects"] = data_.soundEffects;
    j["starfield"] = data_.starfield;

    j["discordRpc"] = data_.discordRpc;
    j["discordClientId"] = data_.discordClientId;
    j["downloadThreads"] = data_.downloadThreads;
    j["assetsMirror"] = data_.assetsMirror;

    std::wstring tmpPath = settingsFilePath_ + L".tmp";
    std::ofstream file{fs::path(tmpPath)};
    if (file.is_open()) {
        file << j.dump(2);
        file.close();
        MoveFileExW(tmpPath.c_str(), settingsFilePath_.c_str(), MOVEFILE_REPLACE_EXISTING);
    }
}

} // namespace aero
