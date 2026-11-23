#pragma once
#include <string>
#include <filesystem>
#include <nlohmann/json.hpp>

namespace aero {

struct SettingsData {
    bool initialSetupDone = false;
    std::wstring gameDir;
    std::wstring javaPath;
    int memoryMin = 1;
    int memoryMax = 4;
    std::string jvmArgs = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -Dfile.encoding=UTF-8";
    int windowWidth = 1280;
    int windowHeight = 720;
    bool fullscreen = false;
    std::string afterLaunch = "hide"; // hide | close | keep
    bool autoUpdateMod = true;
    bool checkUpdatesOnStart = true;
    bool keepLoggedIn = true;

    std::string activeAccountId;
    std::string selectedVersionId;
    std::string activeInstanceId;
    std::string contentTargetId;
    std::string modMcVersion;
    std::string aeroVersionId;

    std::string themeAccent = "emerald";
    std::string uiScale = "100";
    bool bgGlow = true;
    bool soundEffects = true;
    bool starfield = false;

    bool discordRpc = true;
    std::string discordClientId = "1548006102392635522";
    int downloadThreads = 3;
    std::string assetsMirror = "mojang"; // mojang | bmclapi
};

class SettingsManager {
public:
    static SettingsManager& Instance();

    void Load();
    void Save();

    SettingsData& Get() { return data_; }
    const SettingsData& Get() const { return data_; }

    static std::wstring GetDefaultGameDir();
    static std::wstring GetAppDataAeroDir();

private:
    SettingsManager();
    std::wstring settingsFilePath_;
    SettingsData data_;
};

} // namespace aero
