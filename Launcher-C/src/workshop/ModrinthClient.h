#pragma once
#include <string>
#include <vector>
#include <functional>
#include <nlohmann/json.hpp>

namespace aero {

struct WorkshopItem {
    std::string id;
    std::string slug;
    std::string title;
    std::string description;
    std::string iconUrl;
    std::string author;
    int downloads = 0;
    int follows = 0;
    std::vector<std::string> categories;
    bool isInstalled = false;
};

struct WorkshopVersionFile {
    std::string url;
    std::string filename;
    std::string sha1;
    size_t size = 0;
    bool isPrimary = false;
};

class ModrinthClient {
public:
    static std::vector<WorkshopItem> Search(
        const std::string& query = "",
        const std::string& kind = "mod", // mod | resourcepack | shader
        const std::string& mcVersion = "",
        const std::string& loader = "",
        const std::string& category = "all",
        int limit = 24,
        const std::string& sortIndex = "downloads"
    );

    static std::vector<WorkshopVersionFile> GetProjectFiles(
        const std::string& projectId,
        const std::string& mcVersion = "",
        const std::string& loader = ""
    );

    static bool InstallItem(
        const std::string& projectId,
        const std::wstring& targetDir,
        const std::string& mcVersion = "",
        const std::string& loader = "",
        std::function<void(size_t, size_t)> onProgress = nullptr,
        std::string* outError = nullptr
    );
};

} // namespace aero
