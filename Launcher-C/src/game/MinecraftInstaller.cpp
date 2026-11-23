#include "MinecraftInstaller.h"
#include "MinecraftMeta.h"
#include "../net/HttpClient.h"
#include "../net/Downloader.h"
#include "../core/Config.h"
#include "../core/StringUtils.h"
#include "../core/Logger.h"
#include <windows.h>
#include <shlobj.h>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <thread>
#include <atomic>

namespace aero {

namespace fs = std::filesystem;

bool MinecraftInstaller::InstallVanilla(
    const std::wstring& gameDir,
    const std::string& versionId,
    ProgressCallback onProgress,
    std::string* outError
) {
    if (onProgress) onProgress(0.05f, L"Поиск метаданных версии...");

    std::string targetVersion = versionId;
    if (targetVersion.starts_with("AeroVisual-")) targetVersion = targetVersion.substr(std::string("AeroVisual-").size());

    auto manifestList = MinecraftMeta::FetchVanillaManifest();
    std::string metaUrl;
    for (const auto& v : manifestList) {
        if (v.id == targetVersion) {
            metaUrl = v.url;
            break;
        }
    }

    if (metaUrl.empty()) {
        if (outError) *outError = "Версия не найдена в манифесте Mojang: " + targetVersion;
        return false;
    }

    if (onProgress) onProgress(0.1f, L"Загрузка конфигурации версии...");
    auto metaResp = HttpClient::Get(metaUrl);
    if (!metaResp.isSuccess) {
        if (outError) *outError = "Не удалось загрузить манифест версии: " + metaResp.error;
        return false;
    }

    fs::path verDir = fs::path(gameDir) / L"versions" / Utf8ToWide(targetVersion);
    std::error_code ec;
    fs::create_directories(verDir, ec);

    fs::path verJsonPath = verDir / Utf8ToWide(targetVersion + ".json");
    {
        std::ofstream f(verJsonPath);
        if (f.is_open()) {
            f << metaResp.body;
        }
    }

    return VerifyAndRepair(gameDir, targetVersion, onProgress, outError);
}

bool MinecraftInstaller::InstallFabric(
    const std::wstring& gameDir,
    const std::string& mcVersion,
    const std::string& loaderVersion,
    ProgressCallback onProgress,
    std::string* outError
) {
    std::string baseMcVersion = mcVersion;


    if (onProgress) onProgress(0.05f, L"Проверка базовой версии Vanilla...");
    if (!InstallVanilla(gameDir, baseMcVersion, onProgress, outError)) {
        return false;
    }

    if (onProgress) onProgress(0.3f, L"Загрузка профиля Fabric Loader...");
    std::string loader = loaderVersion;
    if (loader.empty()) {
        auto resp = HttpClient::Get(Config::MinecraftMeta::FabricLoader + "/" + baseMcVersion);
        if (resp.isSuccess && resp.json.is_array() && !resp.json.empty()) {
            if (resp.json[0].contains("loader") && resp.json[0]["loader"].is_object()) {
                loader = resp.json[0]["loader"].value("version", "");
            } else {
                loader = resp.json[0].value("version", "");
            }
        }
        if (loader.empty()) {
            auto globalResp = HttpClient::Get(Config::MinecraftMeta::FabricLoader);
            if (globalResp.isSuccess && globalResp.json.is_array() && !globalResp.json.empty()) {
                loader = globalResp.json[0].value("version", "");
            }
        }
        if (loader.empty()) {
            if (baseMcVersion.rfind("1.14", 0) == 0 || baseMcVersion.rfind("1.15", 0) == 0 || baseMcVersion.rfind("1.16", 0) == 0) {
                loader = "0.14.25";
            } else {
                loader = "0.16.10";
            }
        }
    }

    std::string profileUrl = "https://meta.fabricmc.net/v2/versions/loader/" + baseMcVersion + "/" + loader + "/profile/json";
    auto profResp = HttpClient::Get(profileUrl);
    if (!profResp.isSuccess) {
        if (outError) *outError = "Не удалось загрузить профиль Fabric: " + profResp.error;
        return false;
    }

    std::string fabricVerId = "fabric-loader-" + loader + "-" + baseMcVersion;
    fs::path verDir = fs::path(gameDir) / L"versions" / Utf8ToWide(fabricVerId);
    std::error_code ec;
    fs::create_directories(verDir, ec);

    fs::path verJsonPath = verDir / Utf8ToWide(fabricVerId + ".json");
    {
        std::ofstream f(verJsonPath);
        if (f.is_open()) {
            f << profResp.body;
        }
    }

    // If caller requested a custom alias (e.g. 1.21.11), create that version folder inheriting from fabricVerId
    if (mcVersion != baseMcVersion) {
        std::string customVerId = "fabric-loader-" + loader + "-" + mcVersion;
        fs::path customVerDir = fs::path(gameDir) / L"versions" / Utf8ToWide(customVerId);
        fs::create_directories(customVerDir, ec);
        fs::path customJsonPath = customVerDir / Utf8ToWide(customVerId + ".json");
        try {
            nlohmann::json aliasProf = nlohmann::json::parse(profResp.body);
            aliasProf["id"] = customVerId;
            aliasProf["inheritsFrom"] = fabricVerId;
            std::ofstream fAlias(customJsonPath);
            if (fAlias.is_open()) {
                fAlias << aliasProf.dump(2);
            }
        } catch (...) {}
    }

    return VerifyAndRepair(gameDir, fabricVerId, onProgress, outError);
}

bool MinecraftInstaller::VerifyAndRepair(
    const std::wstring& gameDir,
    const std::string& versionId,
    ProgressCallback onProgress,
    std::string* outError
) {
    Downloader::ResetCancel();
    fs::path verDir = fs::path(gameDir) / L"versions" / Utf8ToWide(versionId);
    fs::path verJsonPath = verDir / Utf8ToWide(versionId + ".json");

    if (!fs::exists(verJsonPath)) {
        if (outError) *outError = "Манифест версии не найден: " + versionId;
        return false;
    }

    VersionManifest manifest = MinecraftMeta::LoadVersionJson(verJsonPath.wstring());
    std::string baseVersionId = manifest.inheritsFrom.empty() ? manifest.id : manifest.inheritsFrom;

    // If parent version missing, automatically install parent vanilla core
    if (!manifest.inheritsFrom.empty()) {
        fs::path parentVerDir = fs::path(gameDir) / L"versions" / Utf8ToWide(manifest.inheritsFrom);
        fs::path parentJsonPath = parentVerDir / Utf8ToWide(manifest.inheritsFrom + ".json");
        if (!fs::exists(parentJsonPath)) {
            if (onProgress) onProgress(0.2f, L"Загрузка базовой версии " + Utf8ToWide(manifest.inheritsFrom) + L"...");
            InstallVanilla(gameDir, manifest.inheritsFrom, onProgress, nullptr);
            manifest = MinecraftMeta::LoadVersionJson(verJsonPath.wstring());
            baseVersionId = manifest.inheritsFrom.empty() ? manifest.id : manifest.inheritsFrom;
        }
    }

    // Client jar download if needed
    fs::path clientJarPath = fs::path(gameDir) / L"versions" / Utf8ToWide(baseVersionId) / Utf8ToWide(baseVersionId + ".jar");
    if ((!fs::exists(clientJarPath) || fs::file_size(clientJarPath) == 0) && !manifest.clientJarUrl.empty()) {
        if (onProgress) onProgress(0.35f, L"Загрузка client.jar...");
        DownloadOptions opt;
        opt.url = manifest.clientJarUrl;
        opt.destPath = clientJarPath.wstring();
        opt.expectedSha1 = manifest.clientJarSha1;
        if (!Downloader::DownloadFile(opt, outError)) {
            return false;
        }
    }

    // Libraries download & verification
    size_t totalLibs = manifest.libraries.size();
    fs::path libRootDir = fs::path(gameDir) / L"libraries";
    fs::path nativesDir = verDir / Utf8ToWide(versionId + "-natives");
    std::error_code ec;
    fs::create_directories(nativesDir, ec);

    for (size_t i = 0; i < totalLibs; ++i) {
        const auto& lib = manifest.libraries[i];
        fs::path libPath = libRootDir / Utf8ToWide(lib.path);

        if ((!fs::exists(libPath) || fs::file_size(libPath) == 0) && !lib.url.empty()) {
            float progress = 0.40f + 0.30f * (static_cast<float>(i) / (std::max)(size_t(1), totalLibs));
            if (onProgress) {
                onProgress(progress, L"Библиотека: " + Utf8ToWide(lib.name));
            }

            DownloadOptions opt;
            opt.url = lib.url;
            opt.destPath = libPath.wstring();
            opt.expectedSha1 = lib.sha1;
            Downloader::DownloadFile(opt, nullptr);
        }

        // Extract natives
        if (lib.isNative && fs::exists(libPath) && fs::file_size(libPath) > 0) {
            Downloader::ExtractZip(libPath.wstring(), nativesDir.wstring());
        }
    }

    // Assets Index & Objects download/repair
    if (!manifest.assetIndexId.empty()) {
        fs::path assetsDir = fs::path(gameDir) / L"assets";
        fs::path indexesDir = assetsDir / L"indexes";
        fs::path objectsDir = assetsDir / L"objects";
        fs::create_directories(indexesDir, ec);
        fs::create_directories(objectsDir, ec);

        fs::path indexJsonPath = indexesDir / Utf8ToWide(manifest.assetIndexId + ".json");
        wchar_t appDataBuf[MAX_PATH] = { 0 };
        SHGetFolderPathW(nullptr, CSIDL_APPDATA, nullptr, 0, appDataBuf);
        std::wstring appDataEnv = appDataBuf;

        if (!fs::exists(indexJsonPath) || fs::file_size(indexJsonPath) == 0) {
            bool copied = false;
            if (!appDataEnv.empty()) {
                std::vector<fs::path> candidateIndexes = {
                    fs::path(appDataEnv) / L".festvisuals" / L"assets" / L"indexes",
                    fs::path(appDataEnv) / L".minecraft" / L"assets" / L"indexes"
                };
                for (const auto& cDir : candidateIndexes) {
                    if (!fs::exists(cDir)) continue;
                    for (const auto& f : fs::directory_iterator(cDir, ec)) {
                        if (f.is_regular_file() && f.file_size(ec) > 1000) {
                            std::string fn = f.path().filename().string();
                            if (fn == manifest.assetIndexId + ".json" ||
                                fn.find(manifest.id) != std::string::npos ||
                                (!manifest.inheritsFrom.empty() && fn.find(manifest.inheritsFrom) != std::string::npos)) {
                                fs::copy_file(f.path(), indexJsonPath, fs::copy_options::overwrite_existing, ec);
                                copied = true;
                                break;
                            }
                        }
                    }
                    if (copied) break;
                }
            }

            if (!copied && !manifest.assetIndexUrl.empty()) {
                if (onProgress) onProgress(0.72f, L"Загрузка индекса ресурсов Minecraft...");
                DownloadOptions opt;
                opt.url = manifest.assetIndexUrl;
                opt.destPath = indexJsonPath.wstring();
                opt.expectedSha1 = manifest.assetIndexSha1;
                Downloader::DownloadFile(opt, nullptr);
            }
        }

        // Verify and repair individual asset objects
        if (fs::exists(indexJsonPath) && fs::file_size(indexJsonPath) > 0) {
            try {
                std::ifstream fIndex(indexJsonPath);
                nlohmann::json idx = nlohmann::json::parse(fIndex);
                if (idx.contains("objects") && idx["objects"].is_object()) {
                    struct AssetTask {
                        std::string hash;
                        size_t size = 0;
                    };
                    std::vector<AssetTask> missingAssets;

                    fs::path festObjects = !appDataEnv.empty() ? (fs::path(appDataEnv) / L".festvisuals" / L"assets" / L"objects") : fs::path();
                    bool hasFest = !festObjects.empty() && fs::exists(festObjects);
                    fs::path mcObjects = !appDataEnv.empty() ? (fs::path(appDataEnv) / L".minecraft" / L"assets" / L"objects") : fs::path();
                    bool hasMc = !mcObjects.empty() && fs::exists(mcObjects);

                    for (auto& [objName, objData] : idx["objects"].items()) {
                        std::string hash = objData.value("hash", "");
                        size_t size = objData.value("size", 0ULL);
                        if (hash.length() < 2) continue;

                        std::string prefix = hash.substr(0, 2);
                        fs::path targetObj = objectsDir / Utf8ToWide(prefix) / Utf8ToWide(hash);
                        if (!fs::exists(targetObj) || (size > 0 && fs::file_size(targetObj) != size)) {
                            // Instant local copy from known sibling cache
                            bool foundLocal = false;
                            if (hasFest) {
                                fs::path sib = festObjects / Utf8ToWide(prefix) / Utf8ToWide(hash);
                                if (fs::exists(sib) && (size == 0 || fs::file_size(sib) == size)) {
                                    fs::create_directories(targetObj.parent_path(), ec);
                                    fs::copy_file(sib, targetObj, fs::copy_options::overwrite_existing, ec);
                                    foundLocal = true;
                                }
                            }
                            if (!foundLocal && hasMc) {
                                fs::path sib = mcObjects / Utf8ToWide(prefix) / Utf8ToWide(hash);
                                if (fs::exists(sib) && (size == 0 || fs::file_size(sib) == size)) {
                                    fs::create_directories(targetObj.parent_path(), ec);
                                    fs::copy_file(sib, targetObj, fs::copy_options::overwrite_existing, ec);
                                    foundLocal = true;
                                }
                            }
                            if (!foundLocal) {
                                missingAssets.push_back({ hash, size });
                            }
                        }
                    }

                    // Concurrently download any remaining missing assets
                    if (!missingAssets.empty()) {
                        size_t totalMissing = missingAssets.size();
                        std::atomic<size_t> completedCount{ 0 };
                        std::atomic<size_t> nextIndex{ 0 };

                        if (onProgress) {
                            onProgress(0.80f, L"Докачка ресурсов игры (" + std::to_wstring(totalMissing) + L" шт.)...");
                        }

                        size_t numThreads = (std::min)(size_t(8), (std::max)(size_t(2), totalMissing));
                        std::vector<std::thread> workers;
                        for (size_t t = 0; t < numThreads; ++t) {
                            workers.emplace_back([&, totalMissing]() {
                                while (!Downloader::IsCancelled()) {
                                    size_t idx = nextIndex.fetch_add(1);
                                    if (idx >= totalMissing) break;

                                    const auto& task = missingAssets[idx];
                                    std::string prefix = task.hash.substr(0, 2);
                                    fs::path objFile = objectsDir / Utf8ToWide(prefix) / Utf8ToWide(task.hash);
                                    std::error_code localEc;
                                    fs::create_directories(objFile.parent_path(), localEc);

                                    DownloadOptions opt;
                                    opt.url = Config::MinecraftMeta::AssetsRoot + "/" + prefix + "/" + task.hash;
                                    opt.destPath = objFile.wstring();
                                    opt.expectedSha1 = task.hash;
                                    Downloader::DownloadFile(opt, nullptr);

                                    size_t done = completedCount.fetch_add(1) + 1;
                                    if (done % 30 == 0 && onProgress) {
                                        float p = 0.80f + 0.18f * (static_cast<float>(done) / totalMissing);
                                        onProgress(p, L"Ресурсы игры: " + std::to_wstring(done) + L"/" + std::to_wstring(totalMissing));
                                    }
                                }
                            });
                        }
                        for (auto& w : workers) {
                            if (w.joinable()) w.join();
                        }
                    }
                }
            } catch (...) {}
        }
    }

    if (onProgress) onProgress(1.0f, L"Проверка и докачка файлов завершена!");
    return true;
}

bool MinecraftInstaller::InstallFabricApi(
    const std::wstring& targetModsDir,
    const std::string& mcVersion,
    ProgressCallback onProgress,
    std::string* outError
) {
    fs::path modsPath(targetModsDir);
    std::error_code ec;
    fs::create_directories(modsPath, ec);

    if (onProgress) onProgress(0.1f, L"Поиск Fabric API на Modrinth...");

    std::string targetMc = mcVersion;


    std::string queryUrl = "https://api.modrinth.com/v2/project/fabric-api/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22" + targetMc + "%22%5D";
    auto resp = HttpClient::Get(queryUrl);
    if (!resp.isSuccess || !resp.json.is_array() || resp.json.empty()) {
        if (outError) *outError = "Fabric API для " + mcVersion + " не найден";
        return false;
    }

    const auto& ver = resp.json[0];
    if (!ver.contains("files") || !ver["files"].is_array() || ver["files"].empty()) {
        if (outError) *outError = "Файлы Fabric API не найдены";
        return false;
    }

    std::string downloadUrl;
    std::string fileName;
    for (const auto& f : ver["files"]) {
        if (f.value("primary", false)) {
            downloadUrl = f.value("url", "");
            fileName = f.value("filename", "");
            break;
        }
    }
    if (downloadUrl.empty()) {
        downloadUrl = ver["files"][0].value("url", "");
        fileName = ver["files"][0].value("filename", "");
    }

    if (downloadUrl.empty() || fileName.empty()) {
        if (outError) *outError = "Ссылка на скачивание Fabric API не найдена";
        return false;
    }

    fs::path targetJar = modsPath / Utf8ToWide(fileName);
    if (fs::exists(targetJar) && fs::file_size(targetJar) > 1000) {
        if (onProgress) onProgress(1.0f, L"Fabric API уже установлен");
        return true;
    }

    // Clean older fabric-api versions to avoid DuplicateModsException
    try {
        for (const auto& entry : fs::directory_iterator(modsPath)) {
            if (!entry.is_regular_file()) continue;
            std::string name = entry.path().filename().string();
            std::string lower = name;
            std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
            if (lower.rfind("fabric-api", 0) == 0 && (lower.ends_with(".jar") || lower.ends_with(".disabled")) && name != fileName) {
                fs::remove(entry.path(), ec);
            }
        }
    } catch (...) {}

    if (onProgress) onProgress(0.5f, L"Скачивание Fabric API: " + Utf8ToWide(fileName));

    DownloadOptions opt;
    opt.url = downloadUrl;
    opt.destPath = targetJar.wstring();
    return Downloader::DownloadFile(opt, outError);
}

bool MinecraftInstaller::InstallAeroMod(
    const std::wstring& targetModsDir,
    const std::string& mcVersion,
    bool /*hasSubscription*/,
    const std::string& token,
    ProgressCallback onProgress,
    std::string* outError,
    bool onlyIfChanged,
    bool* updated
) {
    if (updated) *updated = false;
    fs::path modsPath(targetModsDir);
    std::error_code ec;
    fs::create_directories(modsPath, ec);

    // Map base MC version for Fabric API
    std::string engineVersion = mcVersion;

    if (token.size() != 64 || token.find_first_not_of("0123456789abcdef") != std::string::npos) {
        if (outError) *outError = "Войдите в Aero ID для загрузки AeroVisuals Free или Premium.";
        return false;
    }
    if (mcVersion.find_first_not_of("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-_") != std::string::npos) {
        if (outError) *outError = "Некорректная версия Minecraft.";
        return false;
    }
    const std::string aeroModVer = mcVersion;
    const auto endpoint = Config::Client::ModMeta;
    const auto response = HttpClient::ApiRequest(endpoint + (mcVersion.empty() ? "" : "?version=" + mcVersion), "GET", "", token);
    if (!response.isSuccess) {
        if (outError) *outError = response.error.empty() ? "Не удалось получить сведения о релизе визуалов." : response.error;
        return false;
    }
    const auto metadata = response.json.contains("data") ? response.json["data"] : response.json;
    if (!metadata.is_object() || !metadata.value("available", false)) {
        if (outError) *outError = "Сборка для выбранной версии Minecraft ещё не опубликована.";
        return false;
    }
    auto downloadUrl = metadata.value("downloadUrl", metadata.value("fileUrl", std::string{}));
    if (downloadUrl.starts_with("/") && !downloadUrl.starts_with("//")) downloadUrl = Config::DefaultBaseUrl + downloadUrl;
    if (!downloadUrl.starts_with(Config::DefaultBaseUrl + "/")) {
        if (outError) *outError = "Сервер вернул недопустимый адрес загрузки.";
        return false;
    }
    std::vector<std::string> downloadUrls{ downloadUrl };
    const auto expectedSha256 = metadata.contains("sha256") && metadata["sha256"].is_string() ? metadata["sha256"].get<std::string>() : std::string{};
    const auto remoteVersion = metadata.value("version", std::string{});
    const auto expectedPlan = metadata.value("edition",metadata.value("plan",std::string("free"))=="free"?std::string("free"):std::string("premium"));
    if(expectedPlan!="free"&&expectedPlan!="premium"){
        if(outError)*outError="Сервер вернул неизвестную редакцию AeroVisuals.";
        return false;
    }
    const auto cleanupOtherEditions = [&](const std::string& keptName) {
    try {
        for (const auto& entry : fs::directory_iterator(modsPath)) {
            if (!entry.is_regular_file()) continue;
            std::string name = entry.path().filename().string();
            std::string lower = name;
            std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
            if ((lower.rfind("aerovisual", 0) == 0 || lower.rfind("aero-visual", 0) == 0) &&
                (lower.ends_with(".jar") || lower.ends_with(".disabled")) && name != keptName) {
                fs::remove(entry.path(), ec);
            }
        }
    } catch (...) {}

    };
    const auto remember = [&](const fs::path& artifact, const std::string& version) {
        cleanupOtherEditions(WideToUtf8(artifact.filename().wstring()));
        nlohmann::json receipt{{"filename", WideToUtf8(artifact.filename().wstring())}, {"plan", expectedPlan},
            {"minecraftVersion", mcVersion}, {"version", version}, {"sha256", Downloader::ComputeSha256(artifact.wstring())}};
        std::ofstream record(modsPath / L".aero-visuals.json");
        record << receipt.dump(2);
    };
    if (onlyIfChanged) {
        bool knownVersion = false;
        try {
            std::ifstream record(modsPath / L".aero-visuals.json");
            nlohmann::json installed;
            if (record) {
                record >> installed;
                const auto name = fs::path(Utf8ToWide(installed.value("filename", std::string{})));
                const auto file = modsPath / name;
                if (!name.empty() && name == name.filename() && installed.value("plan", "") == expectedPlan &&
                    installed.value("minecraftVersion", "") == mcVersion && fs::is_regular_file(file)) {
                    const auto version = installed.value("version", std::string{});
                    knownVersion = !version.empty();
                    const auto hash = Downloader::ComputeSha256(file.wstring());
                    if ((!knownVersion || version == remoteVersion) && !expectedSha256.empty() && hash == expectedSha256) {
                        remember(file, remoteVersion);
                        return true;
                    }
                    if (knownVersion && version == remoteVersion && expectedSha256.empty() && hash == installed.value("sha256", "")) return true;
                    // A temporary older server catalog must not downgrade an installed release.
                    auto numbers = [](const std::string& value) {
                        std::vector<unsigned> result;
                        std::istringstream in(value);
                        unsigned number;
                        while (in >> number) { result.push_back(number); if (in.peek() != '.') break; in.get(); }
                        result.resize(4);
                        return result;
                    };
                    if (knownVersion && !remoteVersion.empty() && numbers(version) > numbers(remoteVersion) && hash == installed.value("sha256", "")) return true;
                }
            }
        } catch (...) {}
        // Adopt an existing current JAR without downloading it again, including installations from older launchers.
        if (!knownVersion && !expectedSha256.empty()) {
            for (const auto& entry : fs::directory_iterator(modsPath)) {
                if (!entry.is_regular_file() || entry.path().extension() != L".jar") continue;
                if (metadata.contains("size") && metadata["size"].is_number_unsigned() && entry.file_size() != metadata["size"].get<uint64_t>()) continue;
                if (Downloader::ComputeSha256(entry.path().wstring()) == expectedSha256) { remember(entry.path(), remoteVersion); return true; }
            }
        }
    }
    if (onProgress) onProgress(0.3f, expectedPlan == "premium" ? L"Загрузка Aero Visuals (Premium)..." : L"Загрузка Aero Visuals (Free)...");
    // 1. Install Fabric API dependency
#ifndef AERO_TESTING
    std::string fApiErr;
    if (!InstallFabricApi(targetModsDir, engineVersion, onProgress, &fApiErr)) {
        Logger::Warn("[Install] Предупреждение установки Fabric API: " + fApiErr);
    }
#endif

    std::string serverFileName = metadata.value("filename", std::string{});
    fs::path tempJar = modsPath / L"aerovisual.download.tmp";

    bool downloadOk = false;
    std::string lastErr;

    for (const auto& url : downloadUrls) {
        DownloadOptions opt;
        opt.url = url;
        opt.destPath = tempJar.wstring();
        opt.token = token;
        opt.expectedSha256 = expectedSha256;
        opt.outFileName = &serverFileName;
        opt.onProgress = [onProgress](size_t downloaded, size_t total) {
            if (onProgress && total > 0) {
                float p = 0.3f + 0.65f * (static_cast<float>(downloaded) / static_cast<float>(total));
                onProgress(p, L"Загрузка мода Aero Visuals...");
            }
        };

        std::string dErr;
        if (Downloader::DownloadFile(opt, &dErr)) {
            char signature[4]{};
            std::ifstream jar(tempJar, std::ios::binary);
            jar.read(signature, 4);
            const bool validJar = jar.gcount() == 4 && signature[0] == 'P' && signature[1] == 'K' && signature[2] == 3 && signature[3] == 4;
            jar.close();
            if (validJar) {
                downloadOk = true;
                break;
            } else {
                lastErr = "Сервер вернул файл, который не является JAR-архивом.";
                if (fs::exists(tempJar)) fs::remove(tempJar, ec);
            }
        } else {
            lastErr = dErr;
            if (fs::exists(tempJar)) fs::remove(tempJar, ec);
        }
    }

    if (!downloadOk) {
        if (outError) *outError = lastErr.empty() ? "Не удалось загрузить файл мода Aero" : lastErr;
        return false;
    }

    const auto releaseVersion = !remoteVersion.empty() && remoteVersion.find_first_not_of("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._+-") == std::string::npos ? remoteVersion : std::string("1.0");
    // Determine final target file name
    std::string finalName;
    if (!serverFileName.empty() && serverFileName.ends_with(".jar")) {
        finalName = WideToUtf8(fs::path(Utf8ToWide(serverFileName)).filename().wstring());
    } else {
        finalName = (expectedPlan == "premium")
            ? ("aerovisuals-" + aeroModVer + "-" + releaseVersion + ".jar")
            : ("aerovisual-free-" + aeroModVer + ".jar");
    }

    fs::path targetJar = modsPath / Utf8ToWide(finalName);

    fs::rename(tempJar, targetJar, ec);
    if (ec) {
        ec.clear();
        fs::copy_file(tempJar, targetJar, fs::copy_options::overwrite_existing, ec);
        if (ec) {
            if (outError) *outError = "Не удалось сохранить мод: " + ec.message();
            return false;
        }
        fs::remove(tempJar, ec);
    }
    remember(targetJar, remoteVersion);
    if (updated) *updated = true;
    Logger::Info("[Install] Мод Aero Visuals успешно установлен: " + finalName);
    if (onProgress) {
        onProgress(1.0f, L"Визуалы Aero успешно установлены!");
    }
    return true;
}

} // namespace aero
