#pragma once
#include <string>
#include <functional>
#include "MinecraftMeta.h"

namespace aero {

class MinecraftInstaller {
public:
    using ProgressCallback = std::function<void(float progress, const std::wstring& status)>;

    static bool InstallVanilla(
        const std::wstring& gameDir,
        const std::string& versionId,
        ProgressCallback onProgress,
        std::string* outError = nullptr
    );

    static bool InstallFabric(
        const std::wstring& gameDir,
        const std::string& mcVersion,
        const std::string& loaderVersion,
        ProgressCallback onProgress,
        std::string* outError = nullptr
    );

    static bool VerifyAndRepair(
        const std::wstring& gameDir,
        const std::string& versionId,
        ProgressCallback onProgress,
        std::string* outError = nullptr
    );

    static bool InstallFabricApi(
        const std::wstring& targetModsDir,
        const std::string& mcVersion,
        ProgressCallback onProgress,
        std::string* outError = nullptr
    );

    static bool InstallAeroMod(
        const std::wstring& targetModsDir,
        const std::string& mcVersion,
        bool hasSubscription,
        const std::string& token,
        ProgressCallback onProgress,
        std::string* outError = nullptr,
        bool onlyIfChanged = false,
        bool* updated = nullptr
    );
};

} // namespace aero
