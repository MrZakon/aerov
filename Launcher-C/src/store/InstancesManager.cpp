#include "InstancesManager.h"
#include "SettingsManager.h"
#include "../core/StringUtils.h"
#include <windows.h>
#include <algorithm>
#include <fstream>
#include <nlohmann/json.hpp>

namespace aero {

namespace fs = std::filesystem;

static int CountValidFiles(const fs::path& dir) {
    std::error_code ec;
    if (!fs::exists(dir, ec) || !fs::is_directory(dir, ec)) return 0;
    int count = 0;
    for (const auto& entry : fs::directory_iterator(dir, ec)) {
        if (ec) break;
        if (entry.is_regular_file(ec)) {
            if (entry.path().extension() != L".part") count++;
        }
    }
    return count;
}

static bool IsInstanceFolderName(const std::string& name) {
    return !name.empty() && name.back() != '.' && name.back() != ' ' &&
        name.find_first_of("\\/:*?\"<>|") == std::string::npos &&
        std::none_of(name.begin(), name.end(), [](unsigned char ch) { return ch < 32; });
}

InstancesManager& InstancesManager::Instance() {
    static InstancesManager s_instance;
    return s_instance;
}

void InstancesManager::ScanInstances(const std::wstring& gameDir) {
    std::lock_guard mutation(mutationMutex_);
    ScanInstancesLocked(gameDir);
}

void InstancesManager::ScanInstancesLocked(const std::wstring& gameDir) {
    if (gameDir.empty()) {
        std::lock_guard catalog(catalogMutex_);
        instances_.clear();
        return;
    }
    std::vector<InstanceInfo> scanned;
    try {
        fs::path instRoot = fs::path(gameDir) / L"instances";
        std::error_code ec;
        if (!fs::exists(instRoot, ec)) {
            fs::create_directories(instRoot, ec);
        }

        if (fs::exists(instRoot, ec)) {
            for (const auto& entry : fs::directory_iterator(instRoot, ec)) {
                if (ec) break;
                if (!entry.is_directory(ec)) continue;

                InstanceInfo info;
                info.path = entry.path().wstring();
                info.name = WideToUtf8(entry.path().filename().wstring());
                info.id = info.name;

                // Read instance.json if exists
                fs::path metaFile = entry.path() / L"instance.json";
                if (fs::exists(metaFile, ec)) {
                    try {
                        std::ifstream f(metaFile);
                        nlohmann::json j;
                        f >> j;
                        info.loader = j.value("loader", "Fabric");
                        info.mcVersion = j.value("mc", "1.21.4");
                        info.versionId = j.value("versionId", "");
                    } catch (...) {}
                } else {
                    // Deduce from name (e.g. "Fabric 1.21.4")
                    if (info.name.find("Fabric") != std::string::npos) info.loader = "Fabric";
                    else if (info.name.find("Forge") != std::string::npos) info.loader = "Forge";
                    else info.loader = "Vanilla";

                    size_t spaceIdx = info.name.find(' ');
                    if (spaceIdx != std::string::npos) {
                        info.mcVersion = info.name.substr(spaceIdx + 1);
                    } else {
                        info.mcVersion = "1.21.4";
                    }
                }

                info.modCount = CountValidFiles(entry.path() / L"mods");
                info.resourcePackCount = CountValidFiles(entry.path() / L"resourcepacks");
                info.shaderPackCount = CountValidFiles(entry.path() / L"shaderpacks");
                info.screenshotCount = CountValidFiles(entry.path() / L"screenshots");

                scanned.push_back(std::move(info));
            }
        }
    } catch (...) {}

    std::sort(scanned.begin(), scanned.end(), [](const InstanceInfo& left, const InstanceInfo& right) {
        return left.id < right.id;
    });
    std::lock_guard catalog(catalogMutex_);
    instances_ = std::move(scanned);
}

std::vector<InstanceInfo> InstancesManager::GetInstances() const {
    std::lock_guard catalog(catalogMutex_);
    return instances_;
}

std::optional<InstanceInfo> InstancesManager::GetInstance(const std::string& id) const {
    std::lock_guard catalog(catalogMutex_);
    for (const auto& inst : instances_) {
        if (inst.id == id) return inst;
    }
    return std::nullopt;
}

std::optional<InstanceInfo> InstancesManager::GetActiveInstance() const {
    const auto& settings = SettingsManager::Instance().Get();
    const auto activeId = settings.activeInstanceId;
    const auto verId = settings.selectedVersionId;
    std::lock_guard catalog(catalogMutex_);
    for (const auto& inst : instances_) {
        if (inst.id == activeId || inst.name == activeId) return inst;
    }
    if (!verId.empty()) {
        for (const auto& inst : instances_) {
            if (inst.id == verId || inst.name == verId || inst.versionId == verId) return inst;
        }
    }
    if (instances_.empty()) return std::nullopt;
    return instances_.front();
}

bool InstancesManager::SelectInstance(const std::string& id) {
    std::lock_guard mutation(mutationMutex_);
    const auto instance = GetInstance(id);
    if (!instance) return false;
    auto& store = SettingsManager::Instance();
    auto& settings = store.Get();
    settings.activeInstanceId = instance->id;
    settings.selectedVersionId = instance->versionId.empty() ? instance->name : instance->versionId;
    settings.modMcVersion = instance->mcVersion;
    settings.contentTargetId = instance->id;
    store.Save();
    return true;
}

InstanceInfo InstancesManager::CreateInstance(const std::wstring& gameDir, const std::string& name, const std::string& loader, const std::string& mcVersion, const std::string& versionId) {
    std::lock_guard mutation(mutationMutex_);
    return CreateInstanceLocked(gameDir, name, loader, mcVersion, versionId);
}

InstanceInfo InstancesManager::CreateInstanceLocked(const std::wstring& gameDir, const std::string& name,
    const std::string& loader, const std::string& mcVersion, const std::string& versionId) {
    fs::path instDir = fs::path(gameDir) / L"instances" / Utf8ToWide(name);
    std::error_code ec;
    fs::create_directories(instDir / L"mods", ec);
    fs::create_directories(instDir / L"resourcepacks", ec);
    fs::create_directories(instDir / L"shaderpacks", ec);
    fs::create_directories(instDir / L"saves", ec);
    fs::create_directories(instDir / L"screenshots", ec);
    fs::create_directories(instDir / L"config", ec);

    nlohmann::json j;
    j["name"] = name;
    j["loader"] = loader;
    j["mc"] = mcVersion;
    j["versionId"] = versionId;

    {
        std::ofstream f(instDir / L"instance.json");
        if (f) f << j.dump(2);
    }

    InstanceInfo info;
    info.id = name;
    info.name = name;
    info.path = instDir.wstring();
    info.loader = loader;
    info.mcVersion = mcVersion;
    info.versionId = versionId;

    {
        std::lock_guard catalog(catalogMutex_);
        const auto existing = std::lower_bound(instances_.begin(), instances_.end(), info.id,
            [](const InstanceInfo& instance, const std::string& id) { return instance.id < id; });
        if (existing == instances_.end() || existing->id != info.id) instances_.insert(existing, info);
        else *existing = info;
    }
    SettingsManager::Instance().Get().activeInstanceId = info.id;
    SettingsManager::Instance().Save();

    return info;
}

bool InstancesManager::DeleteInstance(const std::string& id) {
    std::lock_guard mutation(mutationMutex_);
    const auto removed = GetInstance(id);
    if (!removed) return false;
    const auto previouslyActive = GetActiveInstance();

    std::error_code ec;
    fs::remove_all(removed->path, ec);
    if (ec) return false;

    {
        std::lock_guard catalog(catalogMutex_);
        std::erase_if(instances_, [&](const InstanceInfo& instance) { return instance.id == id; });
    }

    auto& store = SettingsManager::Instance();
    auto& settings = store.Get();
    const bool removedActive = previouslyActive && previouslyActive->id == removed->id;
    if (removedActive) settings.activeInstanceId.clear();
    const auto active = GetActiveInstance();
    if (removedActive) {
        settings.activeInstanceId = active ? active->id : "";
        settings.selectedVersionId = active ? (active->versionId.empty() ? active->name : active->versionId) : "";
        settings.modMcVersion = active ? active->mcVersion : "";
    }
    if (settings.contentTargetId == removed->id) settings.contentTargetId = active ? active->id : "";
    // Version profiles, libraries and assets can be shared by many instances.
    store.Save();
    return true;
}

bool InstancesManager::CloneInstance(const std::string& id, const std::string& newName) {
    std::lock_guard mutation(mutationMutex_);
    if (!IsInstanceFolderName(newName)) return false;
    const auto src = GetInstance(id);
    if (!src) return false;

    fs::path srcPath(src->path);
    fs::path dstPath = srcPath.parent_path() / Utf8ToWide(newName);
    std::error_code ec;
    if (fs::exists(dstPath, ec)) return false;

    fs::copy(srcPath, dstPath, fs::copy_options::recursive, ec);
    if (ec) return false;

    fs::path metaFile = dstPath / L"instance.json";
    nlohmann::json j;
    j["name"] = newName;
    j["loader"] = src->loader;
    j["mc"] = src->mcVersion;
    j["versionId"] = src->versionId;

    std::ofstream f(metaFile);
    if (f.is_open()) {
        f << j.dump(2);
    }

    ScanInstancesLocked(srcPath.parent_path().parent_path().wstring());
    return true;
}

bool InstancesManager::RenameInstance(const std::string& id, const std::string& newName) {
    std::lock_guard mutation(mutationMutex_);
    if (!IsInstanceFolderName(newName)) return false;
    const auto src = GetInstance(id);
    if (!src) return false;

    fs::path srcPath(src->path);
    fs::path dstPath = srcPath.parent_path() / Utf8ToWide(newName);
    std::error_code ec;
    if (fs::exists(dstPath, ec)) return false;

    fs::rename(srcPath, dstPath, ec);
    if (ec) return false;

    fs::path metaFile = dstPath / L"instance.json";
    nlohmann::json j;
    j["name"] = newName;
    j["loader"] = src->loader;
    j["mc"] = src->mcVersion;
    j["versionId"] = src->versionId;

    std::ofstream f(metaFile);
    if (f.is_open()) {
        f << j.dump(2);
    }

    auto& s = SettingsManager::Instance().Get();
    bool changed = false;
    if (s.activeInstanceId == id) {
        s.activeInstanceId = newName;
        changed = true;
    }
    if (s.selectedVersionId == id) {
        s.selectedVersionId = newName;
        changed = true;
    }
    if (s.contentTargetId == id) {
        s.contentTargetId = newName;
        changed = true;
    }
    if (changed) {
        SettingsManager::Instance().Save();
    }
    ScanInstancesLocked(srcPath.parent_path().parent_path().wstring());
    return true;
}

std::vector<ScreenshotInfo> InstancesManager::GetScreenshots(const std::wstring& instanceDir) {
    std::vector<ScreenshotInfo> res;
    if (instanceDir.empty()) return res;

    try {
        fs::path scDir = fs::path(instanceDir) / L"screenshots";
        std::error_code ec;
        if (!fs::exists(scDir, ec) || !fs::is_directory(scDir, ec)) return res;

        for (const auto& entry : fs::directory_iterator(scDir, ec)) {
            if (ec) break;
            if (entry.is_regular_file(ec)) {
                const auto ext = entry.path().extension();
                if (ext == L".png" || ext == L".jpg") {
                    ScreenshotInfo sc;
                    sc.filePath = entry.path().wstring();
                    sc.fileName = WideToUtf8(entry.path().filename().wstring());
                    sc.fileSize = static_cast<int64_t>(entry.file_size(ec));
                    auto ftime = entry.last_write_time(ec);
                    sc.modifiedTime = ftime.time_since_epoch().count();
                    res.push_back(sc);
                }
            }
        }

        std::sort(res.begin(), res.end(), [](const ScreenshotInfo& a, const ScreenshotInfo& b) {
            return a.modifiedTime > b.modifiedTime;
        });
    } catch (...) {}

    return res;
}

ImportResult InstancesManager::ImportContentFile(const std::wstring& sourcePath, const std::string& instanceId) {
    ImportResult res;
    std::error_code ec;
    fs::path src(sourcePath);
    if (!fs::exists(src, ec) || !fs::is_regular_file(src, ec)) {
        res.errorMessage = "Файл не найден";
        return res;
    }

    res.fileName = src.filename().wstring();
    std::string lowerName = WideToUtf8(res.fileName);
    for (auto& c : lowerName) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));

    std::string kind = "mod";
    std::wstring folder = L"mods";

    if (lowerName.ends_with(".jar") || lowerName.ends_with(".litemod")) {
        kind = "mod";
        folder = L"mods";
    } else if (lowerName.ends_with(".zip")) {
        bool isShader = (lowerName.find("shader") != std::string::npos ||
                         lowerName.find("bsl") != std::string::npos ||
                         lowerName.find("complementary") != std::string::npos ||
                         lowerName.find("sildur") != std::string::npos ||
                         lowerName.find("seus") != std::string::npos ||
                         lowerName.find("iris") != std::string::npos ||
                         lowerName.find("chocapic") != std::string::npos ||
                         lowerName.find("astralux") != std::string::npos ||
                         lowerName.find("solas") != std::string::npos);

        if (!isShader) {
            std::ifstream zf(src, std::ios::binary);
            if (zf.is_open()) {
                std::vector<char> buf(65536);
                zf.read(buf.data(), buf.size());
                std::streamsize bytes = zf.gcount();
                std::string header(buf.data(), bytes);
                if (header.find("shaders/") != std::string::npos || header.find("shaders\\") != std::string::npos) {
                    isShader = true;
                }
            }
        }

        if (isShader) {
            kind = "shader";
            folder = L"shaderpacks";
        } else {
            kind = "resourcepack";
            folder = L"resourcepacks";
        }
    } else {
        res.errorMessage = "Неподдерживаемый формат файла (поддерживаются .jar и .zip)";
        return res;
    }

    std::lock_guard mutation(mutationMutex_);
    auto inst = instanceId.empty() ? GetActiveInstance() : GetInstance(instanceId);
    if (!inst && !instanceId.empty()) {
        res.errorMessage = "Выбранная сборка недоступна";
        return res;
    }

    auto& store = SettingsManager::Instance();
    auto& settings = store.Get();
    if (!inst) {
        const auto version = settings.modMcVersion.empty() ? "1.21.4" : settings.modMcVersion;
        inst = CreateInstanceLocked(settings.gameDir, "Aero Visuals " + version, "Fabric", version, "AeroVisual-" + version);
        settings.selectedVersionId = inst->versionId;
        settings.contentTargetId = inst->id;
        store.Save();
    }
    const auto instDir = fs::path(inst->path);
    const auto instName = inst->name;
    if (!fs::is_directory(instDir, ec)) {
        res.errorMessage = "Папка выбранной сборки недоступна";
        return res;
    }
    fs::path destDir = instDir / folder;
    fs::create_directories(destDir, ec);

    fs::path destPath = destDir / src.filename();
    fs::copy_file(src, destPath, fs::copy_options::overwrite_existing, ec);
    if (ec) {
        res.errorMessage = "Ошибка копирования файла: " + ec.message();
        return res;
    }

    res.success = true;
    res.kind = kind;
    res.targetFolder = folder;
    res.destPath = destPath.wstring();
    res.instanceName = instName;

    ScanInstancesLocked(settings.gameDir);

    return res;
}

} // namespace aero

