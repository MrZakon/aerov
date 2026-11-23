#include "ModrinthClient.h"
#include "../net/HttpClient.h"
#include "../net/Downloader.h"
#include "../core/Config.h"
#include "../core/StringUtils.h"
#include <windows.h>
#include <sstream>
#include <filesystem>

namespace aero {

namespace fs = std::filesystem;

static std::string UrlEncode(const std::string& value) {
    std::ostringstream escaped;
    escaped.fill('0');
    escaped << std::hex;
    for (char c : value) {
        if (isalnum(static_cast<unsigned char>(c)) || c == '-' || c == '_' || c == '.' || c == '~') {
            escaped << c;
        } else {
            escaped << '%' << std::uppercase << int(static_cast<unsigned char>(c)) << std::nouppercase;
        }
    }
    return escaped.str();
}

static std::string MapToModrinthMcVersion(const std::string& ver) {
    if (ver.empty() || ver == "all" || ver == "Все версии") return "";
    if (ver.rfind("26.", 0) == 0 || ver == "1.21.11") return "1.21.4";
    return ver;
}

std::vector<WorkshopItem> ModrinthClient::Search(
    const std::string& query,
    const std::string& kind,
    const std::string& mcVersion,
    const std::string& loader,
    const std::string& category,
    int limit,
    const std::string& sortIndex
) {
    std::vector<WorkshopItem> results;

    std::string projectType = "mod";
    if (kind == "resourcepack") projectType = "resourcepack";
    else if (kind == "shader") projectType = "shader";

    nlohmann::json facets = nlohmann::json::array();
    facets.push_back(nlohmann::json::array({ "project_type:" + projectType }));

    std::string targetMc = MapToModrinthMcVersion(mcVersion);
    if (!targetMc.empty()) {
        facets.push_back(nlohmann::json::array({ "versions:" + targetMc }));
    }

    if (!loader.empty() && loader != "auto" && loader != "any" && projectType == "mod") {
        std::string l = loader;
        for (auto& c : l) c = static_cast<char>(tolower(c));
        facets.push_back(nlohmann::json::array({ "categories:" + l }));
    }

    if (!category.empty() && category != "all") {
        facets.push_back(nlohmann::json::array({ "categories:" + category }));
    }

    std::string idx = sortIndex.empty() ? "downloads" : sortIndex;
    std::string url = Config::Modrinth::Api + "/search?limit=" + std::to_string(limit) +
        "&index=" + idx + "&facets=" + UrlEncode(facets.dump());

    if (!query.empty()) {
        url += "&query=" + UrlEncode(query);
    }

    auto resp = HttpClient::Get(url);
    if (!resp.isSuccess || !resp.json.contains("hits")) {
        return results;
    }

    for (const auto& hit : resp.json["hits"]) {
        WorkshopItem item;
        item.id = hit.value("project_id", "");
        item.slug = hit.value("slug", "");
        item.title = hit.value("title", "");
        item.description = hit.value("description", "");
        item.iconUrl = hit.value("icon_url", "");
        item.author = hit.value("author", "");
        item.downloads = hit.value("downloads", 0);
        item.follows = hit.value("follows", 0);

        if (hit.contains("categories") && hit["categories"].is_array()) {
            for (const auto& cat : hit["categories"]) {
                item.categories.push_back(cat.get<std::string>());
            }
        }
        results.push_back(item);
    }

    return results;
}

std::vector<WorkshopVersionFile> ModrinthClient::GetProjectFiles(
    const std::string& projectId,
    const std::string& mcVersion,
    const std::string& loader
) {
    std::vector<WorkshopVersionFile> files;
    std::string url = Config::Modrinth::Api + "/project/" + projectId + "/version";

    std::vector<std::string> params;
    if (!loader.empty() && loader != "auto") {
        std::string l = loader;
        for (auto& c : l) c = static_cast<char>(tolower(c));
        nlohmann::json loadersArr = nlohmann::json::array({ l });
        params.push_back("loaders=" + UrlEncode(loadersArr.dump()));
    }
    std::string targetMc = MapToModrinthMcVersion(mcVersion);
    if (!targetMc.empty()) {
        nlohmann::json versArr = nlohmann::json::array({ targetMc });
        params.push_back("game_versions=" + UrlEncode(versArr.dump()));
    }

    if (!params.empty()) {
        url += "?";
        for (size_t i = 0; i < params.size(); ++i) {
            if (i > 0) url += "&";
            url += params[i];
        }
    }

    auto resp = HttpClient::Get(url);
    if (!resp.isSuccess || !resp.json.is_array() || resp.json.empty()) {
        return files;
    }

    // Pick first matching version release
    for (const auto& v : resp.json) {
        if (!v.contains("files") || !v["files"].is_array()) continue;

        for (const auto& f : v["files"]) {
            WorkshopVersionFile file;
            file.url = f.value("url", "");
            file.filename = f.value("filename", "");
            file.isPrimary = f.value("primary", false);
            file.size = f.value("size", 0ULL);
            if (f.contains("hashes") && f["hashes"].is_object()) {
                file.sha1 = f["hashes"].value("sha1", "");
            }
            if (!file.url.empty()) {
                files.push_back(file);
            }
        }
        if (!files.empty()) break; // got the latest compatible release
    }

    return files;
}

bool ModrinthClient::InstallItem(
    const std::string& projectId,
    const std::wstring& targetDir,
    const std::string& mcVersion,
    const std::string& loader,
    std::function<void(size_t, size_t)> onProgress,
    std::string* outError
) {
    auto files = GetProjectFiles(projectId, mcVersion, loader);
    if (files.empty()) {
        if (outError) *outError = "Не найден подходящий файл для этой версии";
        return false;
    }

    const auto* targetFile = &files[0];
    for (const auto& f : files) {
        if (f.isPrimary) {
            targetFile = &f;
            break;
        }
    }

    fs::path destPath = fs::path(targetDir) / Utf8ToWide(targetFile->filename);

    DownloadOptions opt;
    opt.url = targetFile->url;
    opt.destPath = destPath.wstring();
    opt.expectedSha1 = targetFile->sha1;
    opt.onProgress = onProgress;

    return Downloader::DownloadFile(opt, outError);
}

} // namespace aero
