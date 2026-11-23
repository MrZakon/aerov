#pragma once
#include <string>
#include <vector>
#include <filesystem>
#include <mutex>
#include <optional>

namespace aero {

struct InstanceInfo {
    std::string id;
    std::string name;
    std::wstring path;
    std::string loader;
    std::string mcVersion;
    std::string versionId;
    int modCount = 0;
    int resourcePackCount = 0;
    int shaderPackCount = 0;
    int screenshotCount = 0;
};

struct ScreenshotInfo {
    std::wstring filePath;
    std::string fileName;
    int64_t fileSize = 0;
    int64_t modifiedTime = 0;
};

struct ImportResult {
    bool success = false;
    std::string kind; // "mod", "resourcepack", "shader"
    std::wstring targetFolder; // L"mods", L"resourcepacks", L"shaderpacks"
    std::wstring fileName;
    std::wstring destPath;
    std::string instanceName;
    std::string errorMessage;
};

class InstancesManager {
public:
    static InstancesManager& Instance();

    void ScanInstances(const std::wstring& gameDir);
    std::vector<InstanceInfo> GetInstances() const;

    std::optional<InstanceInfo> GetInstance(const std::string& id) const;
    std::optional<InstanceInfo> GetActiveInstance() const;
    bool SelectInstance(const std::string& id);

    InstanceInfo CreateInstance(const std::wstring& gameDir, const std::string& name, const std::string& loader, const std::string& mcVersion, const std::string& versionId);
    bool DeleteInstance(const std::string& id);
    bool CloneInstance(const std::string& id, const std::string& newName);
    bool RenameInstance(const std::string& id, const std::string& newName);

    std::vector<ScreenshotInfo> GetScreenshots(const std::wstring& instanceDir);
    ImportResult ImportContentFile(const std::wstring& sourcePath, const std::string& instanceId = "");

private:
    InstancesManager() = default;
    void ScanInstancesLocked(const std::wstring& gameDir);
    InstanceInfo CreateInstanceLocked(const std::wstring& gameDir, const std::string& name,
        const std::string& loader, const std::string& mcVersion, const std::string& versionId);
    std::mutex mutationMutex_;
    mutable std::mutex catalogMutex_;
    std::vector<InstanceInfo> instances_;
};

} // namespace aero
