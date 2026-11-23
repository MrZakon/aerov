#include "MinecraftMeta.h"
#include "../net/HttpClient.h"
#include "../core/Config.h"
#include "../core/StringUtils.h"
#include "../store/SettingsManager.h"
#include <fstream>
#include <sstream>
#include <filesystem>
#include <mutex>
#include <windows.h>

namespace aero {

namespace fs = std::filesystem;

static std::vector<VersionEntry> s_manifestRamCache;
static std::mutex s_manifestMutex;

static std::vector<FabricGameEntry> s_fabricGameRamCache;
static std::mutex s_fabricGameMutex;

void MinecraftMeta::InvalidateRamCache() {
    {
        std::lock_guard<std::mutex> lock(s_manifestMutex);
        s_manifestRamCache.clear();
    }
    {
        std::lock_guard<std::mutex> lock(s_fabricGameMutex);
        s_fabricGameRamCache.clear();
    }
}

std::vector<VersionEntry> MinecraftMeta::FetchVanillaManifest() {
    {
        std::lock_guard<std::mutex> lock(s_manifestMutex);
        if (!s_manifestRamCache.empty()) {
            return s_manifestRamCache;
        }
    }

    // Clean up any leftover disk cache file
    std::wstring appData = SettingsManager::GetAppDataAeroDir();
    fs::path cachePath = fs::path(appData) / L"cache" / L"version_manifest_v2.json";
    std::error_code ec;
    if (fs::exists(cachePath, ec)) {
        fs::remove(cachePath, ec);
    }

    // Fetch fresh manifest from primary or secondary Mojang endpoint into RAM
    auto resp = HttpClient::Get(Config::MinecraftMeta::VanillaManifest, "", 15000);
    if (!resp.isSuccess || !resp.json.contains("versions")) {
        resp = HttpClient::Get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json", "", 15000);
    }

    std::vector<VersionEntry> list;
    if (resp.isSuccess && resp.json.contains("versions") && resp.json["versions"].is_array()) {
        for (const auto& item : resp.json["versions"]) {
            VersionEntry entry;
            entry.id = item.value("id", "");
            entry.type = item.value("type", "release");
            entry.url = item.value("url", "");
            entry.releaseTime = item.value("releaseTime", "");
            if (!entry.id.empty()) {
                list.push_back(entry);
            }
        }
    }

    if (!list.empty()) {
        std::lock_guard<std::mutex> lock(s_manifestMutex);
        s_manifestRamCache = list;
    }

    return list;
}

std::vector<FabricGameEntry> MinecraftMeta::FetchFabricGameVersions() {
    {
        std::lock_guard<std::mutex> lock(s_fabricGameMutex);
        if (!s_fabricGameRamCache.empty()) {
            return s_fabricGameRamCache;
        }
    }

    auto resp = HttpClient::Get(Config::MinecraftMeta::FabricGame, "", 15000);
    std::vector<FabricGameEntry> list;
    if (resp.isSuccess && resp.json.is_array()) {
        for (const auto& item : resp.json) {
            FabricGameEntry entry;
            entry.version = item.value("version", "");
            entry.stable = item.value("stable", false);
            if (!entry.version.empty()) {
                list.push_back(entry);
            }
        }
    }

    if (!list.empty()) {
        std::lock_guard<std::mutex> lock(s_fabricGameMutex);
        s_fabricGameRamCache = list;
    }

    return list;
}

std::vector<std::string> MinecraftMeta::FetchFabricVersions() {
    auto games = FetchFabricGameVersions();
    std::vector<std::string> list;
    for (const auto& g : games) {
        if (g.stable) {
            list.push_back(g.version);
        }
    }
    return list;
}

std::string MinecraftMeta::MavenCoordinateToPath(const std::string& coordinate) {
    // group:artifact:version[:classifier][@ext]
    std::vector<std::string> parts;
    std::stringstream ss(coordinate);
    std::string item;
    while (std::getline(ss, item, ':')) {
        parts.push_back(item);
    }
    if (parts.size() < 3) return "";

    std::string group = parts[0];
    for (char& c : group) if (c == '.') c = '/';

    std::string artifact = parts[1];
    std::string version = parts[2];
    std::string classifier = "";
    std::string ext = "jar";

    if (parts.size() >= 4) {
        classifier = parts[3];
        size_t atIdx = classifier.find('@');
        if (atIdx != std::string::npos) {
            ext = classifier.substr(atIdx + 1);
            classifier = classifier.substr(0, atIdx);
        }
    } else {
        size_t atIdx = version.find('@');
        if (atIdx != std::string::npos) {
            ext = version.substr(atIdx + 1);
            version = version.substr(0, atIdx);
        }
    }

    std::string fileName = artifact + "-" + version;
    if (!classifier.empty()) {
        fileName += "-" + classifier;
    }
    fileName += "." + ext;

    return group + "/" + artifact + "/" + version + "/" + fileName;
}

static bool CheckRules(const nlohmann::json& rules) {
    if (!rules.is_array() || rules.empty()) return true;

    bool allow = false;
    for (const auto& rule : rules) {
        std::string action = rule.value("action", "allow");
        bool apply = true;

        if (rule.contains("os") && rule["os"].is_object()) {
            std::string osName = rule["os"].value("name", "");
            if (osName != "windows") {
                apply = false;
            }
        }

        if (apply) {
            allow = (action == "allow");
        }
    }
    return allow;
}

VersionManifest MinecraftMeta::LoadVersionJson(const std::wstring& jsonPath) {
    VersionManifest manifest;
    std::ifstream file{fs::path(jsonPath)};
    if (!file.is_open()) return manifest;

    try {
        nlohmann::json j;
        file >> j;

        manifest.id = j.value("id", "");
        if (j.contains("javaVersion") && j["javaVersion"].is_object()) manifest.javaMajor = j["javaVersion"].value("majorVersion", 0);
        manifest.inheritsFrom = j.value("inheritsFrom", "");
        manifest.mainClass = j.value("mainClass", "");
        manifest.minecraftArguments = j.value("minecraftArguments", "");
        if (j.contains("arguments")) {
            manifest.arguments = j["arguments"];
        }

        if (j.contains("downloads") && j["downloads"].is_object() && j["downloads"].contains("client")) {
            const auto& client = j["downloads"]["client"];
            manifest.clientJarUrl = client.value("url", "");
            manifest.clientJarSha1 = client.value("sha1", "");
            manifest.clientJarSize = client.value("size", 0ULL);
        }

        if (j.contains("assetIndex") && j["assetIndex"].is_object()) {
            const auto& ai = j["assetIndex"];
            manifest.assetIndexId = ai.value("id", "");
            manifest.assetIndexUrl = ai.value("url", "");
            manifest.assetIndexSha1 = ai.value("sha1", "");
        }

        if (j.contains("libraries") && j["libraries"].is_array()) {
            for (const auto& lib : j["libraries"]) {
                if (lib.contains("rules") && !CheckRules(lib["rules"])) {
                    continue;
                }

                LibraryInfo info;
                info.name = lib.value("name", "");

                // Check downloads artifact
                if (lib.contains("downloads") && lib["downloads"].is_object()) {
                    const auto& dl = lib["downloads"];
                    if (dl.contains("artifact") && dl["artifact"].is_object()) {
                        const auto& art = dl["artifact"];
                        info.path = art.value("path", "");
                        info.url = art.value("url", "");
                        info.sha1 = art.value("sha1", "");
                        info.size = art.value("size", 0ULL);
                    }

                    // Native classifiers for windows
                    if (dl.contains("classifiers") && dl["classifiers"].is_object()) {
                        const auto& cls = dl["classifiers"];
                        std::string nativeKey = "natives-windows";
                        if (!cls.contains(nativeKey) && cls.contains("natives-windows-64")) {
                            nativeKey = "natives-windows-64";
                        }
                        if (cls.contains(nativeKey)) {
                            const auto& nat = cls[nativeKey];
                            LibraryInfo natInfo;
                            natInfo.name = info.name + ":natives";
                            natInfo.path = nat.value("path", "");
                            natInfo.url = nat.value("url", "");
                            natInfo.sha1 = nat.value("sha1", "");
                            natInfo.size = nat.value("size", 0ULL);
                            natInfo.isNative = true;
                            manifest.libraries.push_back(natInfo);
                        }
                    }
                }

                // If path empty, resolve Maven coordinate
                if (info.path.empty() && !info.name.empty()) {
                    info.path = MavenCoordinateToPath(info.name);
                    std::string baseUrl = lib.value("url", "https://libraries.minecraft.net/");
                    if (!baseUrl.empty() && baseUrl.back() != '/') baseUrl += '/';
                    info.url = baseUrl + info.path;
                }

                if (!info.path.empty()) {
                    manifest.libraries.push_back(info);
                }
            }
        }

        // Recursively resolve and merge parent version libraries (inheritsFrom, e.g. Fabric -> Vanilla)
        if (!manifest.inheritsFrom.empty() && manifest.inheritsFrom != manifest.id) {
            fs::path currentPath(jsonPath);
            fs::path versionsDir = currentPath.parent_path().parent_path();
            fs::path parentJsonPath = versionsDir / Utf8ToWide(manifest.inheritsFrom) / Utf8ToWide(manifest.inheritsFrom + ".json");
            if (fs::exists(parentJsonPath) && parentJsonPath != currentPath) {
                VersionManifest parent = LoadVersionJson(parentJsonPath.wstring());
                if (!manifest.javaMajor) manifest.javaMajor = parent.javaMajor;
                if (manifest.clientJarUrl.empty()) manifest.clientJarUrl = parent.clientJarUrl;
                if (manifest.clientJarSha1.empty()) manifest.clientJarSha1 = parent.clientJarSha1;
                if (manifest.clientJarSize == 0) manifest.clientJarSize = parent.clientJarSize;
                if (manifest.assetIndexId.empty()) manifest.assetIndexId = parent.assetIndexId;
                if (manifest.assetIndexUrl.empty()) manifest.assetIndexUrl = parent.assetIndexUrl;
                if (manifest.assetIndexSha1.empty()) manifest.assetIndexSha1 = parent.assetIndexSha1;
                if (manifest.mainClass.empty()) manifest.mainClass = parent.mainClass;

                // Append parent libraries without duplicating existing library names or paths
                for (const auto& parentLib : parent.libraries) {
                    bool alreadyExists = false;
                    for (const auto& existingLib : manifest.libraries) {
                        if (existingLib.name == parentLib.name || (!existingLib.path.empty() && existingLib.path == parentLib.path)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        manifest.libraries.push_back(parentLib);
                    }
                }
            }
        }
    } catch (...) {}

    return manifest;
}

} // namespace aero
