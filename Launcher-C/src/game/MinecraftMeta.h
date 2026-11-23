#pragma once
#include <string>
#include <vector>
#include <nlohmann/json.hpp>

namespace aero {

struct VersionEntry {
    std::string id;
    std::string type; // release | snapshot
    std::string url;
    std::string releaseTime;
};

struct FabricGameEntry {
    std::string version;
    bool stable = false;
};

struct LibraryInfo {
    std::string name;
    std::string path;
    std::string url;
    std::string sha1;
    size_t size = 0;
    bool isNative = false;
};

struct VersionManifest {
    int javaMajor = 0;
    std::string id;
    std::string inheritsFrom;
    std::string mainClass;
    std::string minecraftArguments;
    nlohmann::json arguments;
    std::string clientJarUrl;
    std::string clientJarSha1;
    size_t clientJarSize = 0;
    std::string assetIndexId;
    std::string assetIndexUrl;
    std::string assetIndexSha1;
    std::vector<LibraryInfo> libraries;
};

class MinecraftMeta {
public:
    static std::vector<VersionEntry> FetchVanillaManifest();
    static std::vector<FabricGameEntry> FetchFabricGameVersions();
    static std::vector<std::string> FetchFabricVersions();
    static void InvalidateRamCache();
    static VersionManifest LoadVersionJson(const std::wstring& jsonPath);

    static std::string MavenCoordinateToPath(const std::string& coordinate);
};

} // namespace aero
