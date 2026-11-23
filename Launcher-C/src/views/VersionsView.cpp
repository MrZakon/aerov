#include "../game/AeroCore.h"
#include "../render/AeroDesign.h"
#include "VersionsView.h"
#include "../store/SettingsManager.h"
#include "../store/InstancesManager.h"
#include "../auth/AeroAuth.h"
#include "../game/MinecraftInstaller.h"
#include "../game/MinecraftMeta.h"
#include "../net/HttpClient.h"
#include "../ui/Toast.h"
#include "../core/Config.h"
#include "../core/StringUtils.h"
#include <shellapi.h>
#include <thread>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <algorithm>
#include <regex>

namespace aero {

namespace fs = std::filesystem;

namespace {
std::optional<InstanceInfo> FindAeroBuild(const std::string& minecraftVersion) {
    auto& library = InstancesManager::Instance();
    const auto expected = "AeroVisual-" + minecraftVersion;
    const auto matches = [&](const InstanceInfo& build) {
        return build.loader != "AeroCore" && (build.versionId == expected ||
            (build.mcVersion == minecraftVersion && build.name.starts_with("Aero Visuals ")));
    };
    auto active = library.GetActiveInstance();
    if (active && matches(*active)) return active;
    const auto builds = library.GetInstances();
    const auto first = std::find_if(builds.begin(), builds.end(), matches);
    if (first == builds.end()) return std::nullopt;
    return *first;
}
} // namespace

VersionsView::VersionsView() {
    auto& s = SettingsManager::Instance().Get();
    if (!s.modMcVersion.empty()) lunMcVer_ = s.modMcVersion;
    else s.modMcVersion = lunMcVer_;

    // 1. Official Aero Visuals Stack Card
    btnLunPick_.SetOnClick([this]() { OpenVersionPicker(true); });
    btnLunInstall_.SetOnClick([this]() { InstallAeroBuild(); });
    btnLunRestore_.SetOnClick([this]() { RestoreAeroBuild(); });
    btnLunActivate_.SetOnClick([this]() { ActivateAeroBuild(); });

    // 2. Custom Build Card
    btnKindVanilla_.SetOnClick([this]() {
        customKind_ = "vanilla";
        btnKindVanilla_.SetStyle(ButtonStyle::Primary);
        btnKindFabric_.SetStyle(ButtonStyle::Default);
        customHint_ = L"Чистая версия Minecraft без модификаций.";
        if (isPickerOpen_ && !pickerForAero_) {
            PopulatePickerTiles();
            UpdatePickerBounds();
        }
        Resize(bounds_.right, bounds_.bottom);
    });

    btnKindFabric_.SetOnClick([this]() {
        customKind_ = "fabric";
        btnKindVanilla_.SetStyle(ButtonStyle::Default);
        btnKindFabric_.SetStyle(ButtonStyle::Primary);
        customHint_ = L"Современный быстрый загрузчик модов Fabric.";
        FetchFabricLoadersAsync(customMcVer_);
        if (isPickerOpen_ && !pickerForAero_) {
            PopulatePickerTiles();
            UpdatePickerBounds();
        }
        Resize(bounds_.right, bounds_.bottom);
    });

    btnRefreshRemote_.SetOnClick([this]() {
        Toast::Instance().Show(L"Обновление версий и загрузчиков...", ToastKind::Info);
        MinecraftMeta::InvalidateRamCache();
        {
            std::lock_guard<std::mutex> lock(remoteMutex_);
            remoteManifest_.clear();
            remoteFabricGames_.clear();
            isManifestLoaded_ = false;
            isFabricGamesLoaded_ = false;
        }
        FetchAeroCatalogAsync(true);
        FetchRemoteManifestAsync();
        FetchFabricLoadersAsync(customMcVer_);
        RefreshData();
    });

    btnCustomPick_.SetOnClick([this]() { if (customKind_ != "aerocore") OpenVersionPicker(false); });
    btnCustomInstall_.SetOnClick([this]() { InstallCustomBuild(); });
    btnCustomVerify_.SetOnClick([this]() { VerifyCustomBuild(); });

    std::vector<std::wstring> loaderItems = {
        L"Fabric 0.19.5 (Stable)",
        L"Fabric 0.19.4",
        L"Fabric 0.19.3",
        L"Fabric 0.19.2",
        L"Fabric 0.18.6 (Stable)",
        L"Fabric 0.18.4 (Stable)",
        L"Fabric 0.17.3 (Stable)",
        L"Fabric 0.16.14 (Stable)"
    };
    comboCustomLoader_.SetItems(loaderItems);
    comboCustomLoader_.SetSelectedIndex(0);
    customLoaderVer_ = "0.19.5";

    comboCustomLoader_.SetOnSelect([this](int /*idx*/, const std::wstring& item) {
        std::string s = WideToUtf8(item);
        std::regex verRegex(R"(\b\d+\.\d+\.\d+\b)");
        std::smatch match;
        if (std::regex_search(s, match, verRegex)) {
            customLoaderVer_ = match.str();
        } else {
            size_t pos = s.find(' ');
            if (pos != std::string::npos) {
                customLoaderVer_ = s.substr(pos + 1);
            }
        }
    });

    // Version Picker Modal
    btnPickerClose_.SetOnClick([this]() { CloseVersionPicker(); });

    // Modal Rename Dialog
    btnRenameSave_.SetOnClick([this]() {
        if (!isRenaming_) return;
        std::string newName = WideToUtf8(inputRename_.GetText());
        if (!newName.empty()) {
            if (InstancesManager::Instance().RenameInstance(renameInstId_, newName)) {
                Toast::Instance().Show(L"Сборка переименована: " + Utf8ToWide(newName), ToastKind::Success);
                RefreshData();
            } else {
                Toast::Instance().Show(L"Ошибка переименования сборки", ToastKind::Error);
            }
        }
        isRenaming_ = false;
    });

    btnRenameCancel_.SetOnClick([this]() {
        isRenaming_ = false;
    });

    RefreshData();
    FetchAeroCatalogAsync();
    FetchRemoteManifestAsync();
    FetchFabricLoadersAsync(customMcVer_);
}

void VersionsView::RefreshData() {
    auto& settings = SettingsManager::Instance().Get();
    InstancesManager::Instance().ScanInstances(settings.gameDir);

    installedRows_.clear();
    const auto& instances = InstancesManager::Instance().GetInstances();
    const auto activeInst = InstancesManager::Instance().GetActiveInstance();

    for (const auto& inst : instances) {
        InstalledRow row;
        row.id = inst.id;
        row.versionId = inst.versionId;
        row.name = Utf8ToWide(inst.name.empty() ? inst.id : inst.name);
        row.pathStr = inst.path;
        row.loader = Utf8ToWide(inst.loader.empty() ? "Vanilla" : inst.loader);
        row.isActive = (activeInst && activeInst->id == inst.id);

        row.counters = L"Модов: " + std::to_wstring(inst.modCount) +
                       L" · Ресурспаков: " + std::to_wstring(inst.resourcePackCount) +
                       L" · Шейдеров: " + std::to_wstring(inst.shaderPackCount);

        std::string instId = inst.id;
        std::wstring instName = row.name;
        std::wstring instPath = inst.path;

        row.btnSelect.SetOnClick([this, instId, instName]() {
            if (InstancesManager::Instance().SelectInstance(instId)) {
                Toast::Instance().Show(L"Выбрана сборка: " + instName, ToastKind::Success);
            } else {
                Toast::Instance().Show(L"Сборка больше недоступна", ToastKind::Error);
            }
            needRefresh_ = true;
        });

        row.btnFolder.SetOnClick([instPath]() {
            ShellExecuteW(nullptr, L"open", instPath.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        });

        row.btnMods.SetOnClick([instPath]() {
            fs::path p = fs::path(instPath) / L"mods";
            std::error_code ec;
            fs::create_directories(p, ec);
            ShellExecuteW(nullptr, L"open", p.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        });

        row.btnClone.SetOnClick([this, instId, instName]() {
            std::string srcName = WideToUtf8(instName);
            std::string cloneName = srcName + " (Копия)";
            int counter = 2;
            while (InstancesManager::Instance().GetInstance(cloneName).has_value()) {
                cloneName = srcName + " (Копия " + std::to_string(counter++) + ")";
            }
            if (InstancesManager::Instance().CloneInstance(instId, cloneName)) {
                Toast::Instance().Show(L"Сборка клонирована: " + Utf8ToWide(cloneName), ToastKind::Success);
                needRefresh_ = true;
            } else {
                Toast::Instance().Show(L"Ошибка клонирования сборки", ToastKind::Error);
            }
        });

        row.btnRename.SetOnClick([this, instId, instName]() {
            isRenaming_ = true;
            renameInstId_ = instId;
            inputRename_.SetText(instName);
            inputRename_.SetFocused(true);
        });

        row.btnRepair.SetOnClick([this, inst]() {
            if (isCustomBusy_ || isLunBusy_) return;
            isCustomBusy_ = true;
            customHint_ = L"Проверка и восстановление файлов сборки...";
            Toast::Instance().Show(L"Запущена проверка файлов...", ToastKind::Info);

            std::thread([this, inst]() {
                auto& s = SettingsManager::Instance().Get();
                auto progressCb = [this](float p, const std::wstring& st) {
                    progressCustom_.SetProgress(p);
                    customHint_ = st;
                };

                std::string err;
                bool ok = inst.versionId == aerocore::VersionId ? aerocore::Install(s.gameDir, progressCb, &err) : MinecraftInstaller::VerifyAndRepair(s.gameDir, inst.versionId.empty() ? inst.mcVersion : inst.versionId, progressCb, &err);
                if (!ok && inst.versionId != aerocore::VersionId) {
                    ok = MinecraftInstaller::InstallVanilla(s.gameDir, inst.mcVersion, progressCb, &err);
                }

                isCustomBusy_ = false;
                progressCustom_.SetProgress(1.0f);
                Toast::Instance().Show(ok ? L"Сборка проверена и готова к игре!" : L"Ошибка проверки файлов", ok ? ToastKind::Success : ToastKind::Error);
                needRefresh_ = true;
            }).detach();
        });

        row.btnDelete.SetOnClick([this, instId, instName]() {
            if (InstancesManager::Instance().DeleteInstance(instId)) {
                Toast::Instance().Show(L"Сборка \"" + instName + L"\" удалена", ToastKind::Info);
                needRefresh_ = true;
            } else {
                Toast::Instance().Show(L"Не удалось удалить сборку", ToastKind::Error);
            }
        });

        installedRows_.push_back(row);
    }

    btnCustomPick_.SetText(L"");
    btnLunPick_.SetText(L"Версия");

    UpdateRowBounds();
}

void VersionsView::UpdatePickerBounds() {
    float width = bounds_.right - bounds_.left;
    float height = bounds_.bottom - bounds_.top;
    float pW = (std::min)(width - 60.0f, 650.0f);
    float pH = (std::min)(height - 80.0f, 540.0f);
    float pX = (width - pW) * 0.5f;
    float pY = (height - pH) * 0.5f;

    btnPickerClose_.SetBounds(pX + pW - 46.0f, pY + 16.0f, 28.0f, 28.0f);
    inputPickerSearch_.SetBounds(pX + 24.0f, pY + 74.0f, pW - 48.0f, 36.0f);
    pickerScrollView_.SetBounds(pX + 24.0f, pY + 120.0f, pW - 48.0f, pH - 136.0f);
    FilterPickerVersions();
}

void VersionsView::PopulatePickerTiles() {
    allPickerVersions_.clear();

    if (pickerForAero_) {
        const auto aeroSession = AeroAuth::Instance().GetSession();
        bool hasSub = aeroSession.verified && aeroSession.hasSubscription;

        if (isAeroInfoLoaded_) hasSub = aeroCatalogPremium_;
        for (auto entry = aeroSupportedVersions_.rbegin(); entry != aeroSupportedVersions_.rend(); ++entry) {
            const auto& v = *entry;
            VersionTile t;
            t.id = v;
            t.tag = L"Aero " + Utf8ToWide(v);
            t.sub = hasSub ? L"Fabric + Aero Visuals (Premium)" : L"Fabric + Aero Visuals (Free)";
            t.isSnapshot = false;
            allPickerVersions_.push_back(t);
        }
    } else {
        bool showSnapshots = checkSnapshots_.IsChecked();

        if (customKind_ == "fabric") {
            std::vector<FabricGameEntry> games;
            {
                std::lock_guard<std::mutex> lock(remoteMutex_);
                games = remoteFabricGames_;
            }

            if (!games.empty()) {
                for (const auto& g : games) {
                    if (!showSnapshots && !g.stable) {
                        continue;
                    }
                    VersionTile t;
                    t.id = g.version;
                    t.tag = Utf8ToWide(g.version);
                    t.sub = g.stable ? L"Fabric Release" : L"Fabric Snapshot";
                    t.isSnapshot = !g.stable;
                    allPickerVersions_.push_back(t);
                }
            } else {
                std::vector<std::string> fallbackReleases = {
                    "26.3", "26.2", "26.1.2", "1.21.11", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
                    "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
                    "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
                    "1.18.2", "1.18.1", "1.18",
                    "1.17.1", "1.17",
                    "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
                    "1.15.2", "1.14.4", "1.13.2", "1.12.2"
                };
                for (const auto& r : fallbackReleases) {
                    VersionTile t;
                    t.id = r;
                    t.tag = Utf8ToWide(r);
                    t.sub = L"Fabric Release";
                    t.isSnapshot = false;
                    allPickerVersions_.push_back(t);
                }
            }
        } else {
            std::vector<VersionEntry> manifest;
            {
                std::lock_guard<std::mutex> lock(remoteMutex_);
                manifest = remoteManifest_;
            }

            if (!manifest.empty()) {
                for (const auto& entry : manifest) {
                    bool isRel = (entry.type == "release");
                    if (!showSnapshots && !isRel) {
                        continue;
                    }
                    VersionTile t;
                    t.id = entry.id;
                    t.tag = Utf8ToWide(entry.id);
                    t.sub = isRel ? L"Vanilla Release" : L"Vanilla Snapshot";
                    t.isSnapshot = !isRel;
                    allPickerVersions_.push_back(t);
                }
            } else {
                std::vector<std::string> fallbackReleases = {
                    "26.3", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
                    "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
                    "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
                    "1.18.2", "1.18.1", "1.18",
                    "1.17.1", "1.17",
                    "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
                    "1.15.2", "1.15.1", "1.15",
                    "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
                    "1.13.2", "1.13.1", "1.13",
                    "1.12.2", "1.12.1", "1.12",
                    "1.11.2", "1.11.1", "1.11",
                    "1.10.2", "1.10.1", "1.10",
                    "1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
                    "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8",
                    "1.7.10", "1.7.9", "1.7.8", "1.7.7", "1.7.6", "1.7.5", "1.7.4", "1.7.2",
                    "1.6.4", "1.6.2", "1.6.1",
                    "1.5.2", "1.5.1",
                    "1.4.7", "1.4.6", "1.4.5", "1.4.4", "1.4.2",
                    "1.3.2", "1.3.1",
                    "1.2.5", "1.2.4", "1.2.3", "1.2.2", "1.2.1",
                    "1.1", "1.0.0"
                };
                for (const auto& r : fallbackReleases) {
                    VersionTile t;
                    t.id = r;
                    t.tag = Utf8ToWide(r);
                    t.sub = L"Vanilla Release";
                    t.isSnapshot = false;
                    allPickerVersions_.push_back(t);
                }
            }
        }
    }
}

void VersionsView::OpenVersionPicker(bool forAero) {
    isPickerOpen_ = true;
    pickerForAero_ = forAero;
    inputPickerSearch_.SetText(L"");
    inputPickerSearch_.SetFocused(true);
    if (forAero) FetchAeroCatalogAsync();

    PopulatePickerTiles();
    UpdatePickerBounds();
    if (forAero) return;

    bool needFetch = false;
    {
        std::lock_guard<std::mutex> lock(remoteMutex_);
        if (customKind_ == "fabric") {
            needFetch = !isFabricGamesLoaded_ && remoteFabricGames_.empty();
        } else {
            needFetch = !isManifestLoaded_ && remoteManifest_.empty();
        }
    }
    if (needFetch) {
        FetchRemoteManifestAsync();
    }
}

void VersionsView::CloseVersionPicker() {
    isPickerOpen_ = false;
}

void VersionsView::FilterPickerVersions() {
    filteredPickerVersions_.clear();
    std::wstring query = inputPickerSearch_.GetText();
    std::transform(query.begin(), query.end(), query.begin(), ::towlower);

    for (const auto& item : allPickerVersions_) {
        if (query.empty()) {
            filteredPickerVersions_.push_back(item);
        } else {
            std::wstring idW = Utf8ToWide(item.id);
            std::transform(idW.begin(), idW.end(), idW.begin(), ::towlower);
            if (idW.find(query) != std::wstring::npos || item.tag.find(query) != std::wstring::npos) {
                filteredPickerVersions_.push_back(item);
            }
        }
    }

    D2D1_RECT_F pBounds = pickerScrollView_.GetBounds();
    float gridW = pBounds.right - pBounds.left;
    float colW = (gridW - 3 * 8.0f) / 4.0f;
    float tileH = 88.0f;

    for (size_t i = 0; i < filteredPickerVersions_.size(); ++i) {
        int col = static_cast<int>(i % 4);
        int row = static_cast<int>(i / 4);
        float tx = pBounds.left + col * (colW + 8.0f);
        float ty = pBounds.top + 4.0f + row * (tileH + 8.0f);
        filteredPickerVersions_[i].bounds = D2D1::RectF(tx, ty, tx + colW, ty + tileH);
    }

    int totalRows = static_cast<int>((filteredPickerVersions_.size() + 3) / 4);
    pickerScrollView_.SetContentHeight(totalRows * (tileH + 8.0f) + 16.0f);
}

void VersionsView::InstallAeroBuild() {
    if (isLunBusy_) return;
    const auto authToken = AeroAuth::Instance().GetOnlineToken();
    if (authToken.empty()) {
        Toast::Instance().Show(L"Войдите в Aero ID в лаунчере для установки AeroVisuals.", ToastKind::Error);
        return;
    }
    if (!isAeroInfoLoaded_ || aeroCatalogToken_ != authToken) {
        FetchAeroCatalogAsync();
        Toast::Instance().Show(L"Список релизов загружается.", ToastKind::Info);
        return;
    }
    if (aeroSupportedVersions_.empty()) {
        Toast::Instance().Show(aeroCatalogMessage_, ToastKind::Error);
        return;
    }
    if (std::find(aeroSupportedVersions_.begin(), aeroSupportedVersions_.end(), lunMcVer_) == aeroSupportedVersions_.end()) {
        lunMcVer_ = aeroSupportedVersions_.back();
    }
    isLunBusy_ = true;
    progressLun_.SetProgress(0.05f);
    lunHint_ = L"Установка сборки Aero Visuals " + Utf8ToWide(lunMcVer_) + L"…";
    Toast::Instance().Show(L"Установка сборки Aero Visuals...", ToastKind::Info);

    std::string ver = lunMcVer_;
    std::thread([this, ver, authToken]() {
        auto& s = SettingsManager::Instance().Get();
        auto progressCb = [this](float p, const std::wstring& st) {
            progressLun_.SetProgress(p);
            lunHint_ = st;
        };

        std::string err;
        // Resolve the live Free/Premium edition before downloading Minecraft or changing files.
        const auto access = HttpClient::ApiRequest(Config::Client::ModMeta + "?version=" + ver, "GET", "", authToken);
        if (!access.isSuccess) {
            progressLun_.SetProgress(0.0f);
            lunHint_ = access.error.empty() ? L"Нет доступного релиза AeroVisuals для этой версии Minecraft." : Utf8ToWide(access.error);
            Toast::Instance().Show(lunHint_, ToastKind::Error);
            isLunBusy_ = false;
            needRefresh_ = true;
            return;
        }
        const auto accessData=access.json.contains("data")?access.json["data"]:access.json;
        const bool premium=accessData.value("edition",accessData.value("plan",std::string("free"))=="free"?std::string("free"):std::string("premium"))=="premium";
        std::string baseVer = ver;
        bool ok = MinecraftInstaller::InstallFabric(s.gameDir, baseVer, "", progressCb, &err);
        if (ok) {
            std::string aeroVersionId = "AeroVisual-" + ver;
            fs::path lunVerDir = fs::path(s.gameDir) / L"versions" / Utf8ToWide(aeroVersionId);
            std::error_code ec;
            fs::create_directories(lunVerDir, ec);

            std::string fabricVerId = "";
            fs::path fabricJsonPath;
            for (const auto& entry : fs::directory_iterator(fs::path(s.gameDir) / L"versions", ec)) {
                if (entry.is_directory()) {
                    std::string fn = entry.path().filename().string();
                    if (fn.rfind("fabric-loader-", 0) == 0 && (fn.ends_with("-" + ver) || fn.ends_with("-" + baseVer))) {
                        fabricVerId = fn;
                        fabricJsonPath = entry.path() / Utf8ToWide(fn + ".json");
                        break;
                    }
                }
            }

            fs::path lunJsonPath = lunVerDir / Utf8ToWide(aeroVersionId + ".json");
            if (fs::exists(fabricJsonPath)) {
                try {
                    std::ifstream fin(fabricJsonPath);
                    nlohmann::json prof = nlohmann::json::parse(fin);
                    prof["id"] = aeroVersionId;
                    prof["inheritsFrom"] = fabricVerId;
                    std::ofstream fout(lunJsonPath);
                    fout << prof.dump(2);
                } catch (...) {}
            }


            std::string instName = (premium?"Aero Visuals Premium ":"Aero Visuals Free ") + ver;
            auto inst = InstancesManager::Instance().CreateInstance(s.gameDir, instName, "Fabric", ver, aeroVersionId);
            fs::path modsDir = fs::path(inst.path) / L"mods";
            fs::create_directories(modsDir, ec);

            bool modOk = MinecraftInstaller::InstallAeroMod(modsDir.wstring(), ver, premium, authToken, progressCb, &err);
            if (!modOk) {
                progressLun_.SetProgress(0.0f);
                lunHint_ = L"Ошибка загрузки мода: " + Utf8ToWide(err);
                Toast::Instance().Show(lunHint_, ToastKind::Error);
                isLunBusy_ = false;
                needRefresh_ = true;
                return;
            }

            s.selectedVersionId = aeroVersionId;
            s.activeInstanceId = inst.id;
            s.modMcVersion = ver;
            SettingsManager::Instance().Save();

            progressLun_.SetProgress(1.0f);
            lunHint_ = L"Сборка «" + Utf8ToWide(instName) + L"» готова к запуску";
            Toast::Instance().Show(L"Сборка Aero Visuals установлена и активирована!", ToastKind::Success);
        } else {
            progressLun_.SetProgress(0.0f);
            lunHint_ = L"Ошибка установки: " + Utf8ToWide(err);
            Toast::Instance().Show(lunHint_, ToastKind::Error);
        }

        isLunBusy_ = false;
        needRefresh_ = true;
    }).detach();
}

void VersionsView::RestoreAeroBuild() {
    if (isLunBusy_) return;
    const auto authToken = AeroAuth::Instance().GetOnlineToken();
    if (authToken.empty()) {
        Toast::Instance().Show(L"Войдите в Aero ID в лаунчере для установки AeroVisuals.", ToastKind::Error);
        return;
    }
    const auto target = FindAeroBuild(lunMcVer_);
    if (!target) {
        Toast::Instance().Show(L"Установите AeroVisuals для выбранной версии Minecraft", ToastKind::Info);
        return;
    }
    isLunBusy_ = true;
    progressLun_.SetProgress(0.2f);
    lunHint_ = L"Восстановление файлов визуала и проверка Fabric...";
    Toast::Instance().Show(L"Восстановление визуала и проверка Fabric...", ToastKind::Info);

    std::string ver = lunMcVer_;
    const auto modsDir = fs::path(target->path) / L"mods";
    std::thread([this, ver, authToken, modsDir]() {
        auto& s = SettingsManager::Instance().Get();
        auto progressCb = [this](float p, const std::wstring& st) {
            progressLun_.SetProgress(p);
            lunHint_ = st;
        };

        std::string err;
        // Resolve the live Free/Premium edition before downloading Minecraft or changing files.
        const auto access = HttpClient::ApiRequest(Config::Client::ModMeta + "?version=" + ver, "GET", "", authToken);
        if (!access.isSuccess) {
            progressLun_.SetProgress(0.0f);
            lunHint_ = access.error.empty() ? L"Нет доступного релиза AeroVisuals для этой версии Minecraft." : Utf8ToWide(access.error);
            Toast::Instance().Show(lunHint_, ToastKind::Error);
            isLunBusy_ = false;
            needRefresh_ = true;
            return;
        }
        const auto accessData=access.json.contains("data")?access.json["data"]:access.json;
        const bool premium=accessData.value("edition",accessData.value("plan",std::string("free"))=="free"?std::string("free"):std::string("premium"))=="premium";
        std::string baseVer = ver;
        bool ok = MinecraftInstaller::InstallFabric(s.gameDir, baseVer, "", progressCb, &err);
        if (ok) {
            std::string aeroVersionId = "AeroVisual-" + ver;
            fs::path lunVerDir = fs::path(s.gameDir) / L"versions" / Utf8ToWide(aeroVersionId);
            std::error_code ec;
            fs::create_directories(lunVerDir, ec);

            std::string fabricVerId = "";
            fs::path fabricJsonPath;
            for (const auto& entry : fs::directory_iterator(fs::path(s.gameDir) / L"versions", ec)) {
                if (entry.is_directory()) {
                    std::string fn = entry.path().filename().string();
                    if (fn.rfind("fabric-loader-", 0) == 0 && (fn.ends_with("-" + ver) || fn.ends_with("-" + baseVer))) {
                        fabricVerId = fn;
                        fabricJsonPath = entry.path() / Utf8ToWide(fn + ".json");
                        break;
                    }
                }
            }

            fs::path lunJsonPath = lunVerDir / Utf8ToWide(aeroVersionId + ".json");
            if (fs::exists(fabricJsonPath)) {
                try {
                    std::ifstream fin(fabricJsonPath);
                    nlohmann::json prof = nlohmann::json::parse(fin);
                    prof["id"] = aeroVersionId;
                    prof["inheritsFrom"] = fabricVerId;
                    std::ofstream fout(lunJsonPath);
                    fout << prof.dump(2);
                } catch (...) {}
            }

            bool modOk = MinecraftInstaller::InstallAeroMod(modsDir.wstring(), ver, premium, authToken, progressCb, &err);
            if (!modOk) {
                progressLun_.SetProgress(0.0f);
                lunHint_ = L"Ошибка восстановления мода: " + Utf8ToWide(err);
                Toast::Instance().Show(lunHint_, ToastKind::Error);
                isLunBusy_ = false;
                needRefresh_ = true;
                return;
            }

            progressLun_.SetProgress(1.0f);
            lunHint_ = L"Визуал успешно восстановлен и проверен!";
            Toast::Instance().Show(lunHint_, ToastKind::Success);
        } else {
            progressLun_.SetProgress(0.0f);
            lunHint_ = L"Ошибка восстановления: " + Utf8ToWide(err);
            Toast::Instance().Show(lunHint_, ToastKind::Error);
        }

        isLunBusy_ = false;
        needRefresh_ = true;
    }).detach();
}

void VersionsView::ActivateAeroBuild() {
    const auto instance = FindAeroBuild(lunMcVer_);
    if (!instance || !InstancesManager::Instance().SelectInstance(instance->id)) {
        Toast::Instance().Show(L"Установите AeroVisuals для выбранной версии Minecraft", ToastKind::Info);
        return;
    }
    Toast::Instance().Show(L"Выбрана сборка: " + Utf8ToWide(instance->name), ToastKind::Success);
    needRefresh_ = true;
}

void VersionsView::InstallCustomBuild() {
    if (isCustomBusy_) return;
    isCustomBusy_ = true;
    progressCustom_.SetProgress(0.05f);
    customHint_ = L"Установка " + Utf8ToWide(customKind_) + L" " + Utf8ToWide(customMcVer_) + L"…";
    Toast::Instance().Show(customHint_, ToastKind::Info);

    std::string kind = customKind_;
    std::string mc = customMcVer_;
    std::string loaderVer = customLoaderVer_;

    std::thread([this, kind, mc, loaderVer]() {
        auto& s = SettingsManager::Instance().Get();
        auto progressCb = [this](float p, const std::wstring& st) {
            progressCustom_.SetProgress(p);
            customHint_ = st;
        };

        std::string err;
        bool ok = false;
        std::string versionId;

        if (kind == "aerocore") {
            ok = aerocore::Install(s.gameDir, progressCb, &err);
            versionId = aerocore::VersionId;
        } else if (kind == "fabric") {
            ok = MinecraftInstaller::InstallFabric(s.gameDir, mc, loaderVer, progressCb, &err);
            versionId = "fabric-loader-" + loaderVer + "-" + mc;
        } else {
            ok = MinecraftInstaller::InstallVanilla(s.gameDir, mc, progressCb, &err);
            versionId = mc;
        }

        if (ok) {
            std::string instName = (kind == "aerocore" ? "AeroCore " : kind == "fabric" ? "Fabric " : "Minecraft ") + mc;
            auto inst = InstancesManager::Instance().CreateInstance(s.gameDir, instName, kind == "aerocore" ? "AeroCore" : kind == "fabric" ? "Fabric" : "Vanilla", mc, versionId);

            s.selectedVersionId = versionId;
            s.activeInstanceId = inst.id;
            SettingsManager::Instance().Save();

            progressCustom_.SetProgress(1.0f);
            customHint_ = L"Сборка «" + Utf8ToWide(instName) + L"» готова к игре";
            Toast::Instance().Show(L"Сборка успешно установлена!", ToastKind::Success);
        } else {
            progressCustom_.SetProgress(0.0f);
            customHint_ = L"Ошибка установки: " + Utf8ToWide(err);
            Toast::Instance().Show(customHint_, ToastKind::Error);
        }

        isCustomBusy_ = false;
        needRefresh_ = true;
    }).detach();
}

void VersionsView::VerifyCustomBuild() {
    if (isCustomBusy_) return;
    isCustomBusy_ = true;
    progressCustom_.SetProgress(0.15f);
    customHint_ = L"Проверка файлов сборки...";
    Toast::Instance().Show(L"Проверка сборки...", ToastKind::Info);

    std::string mc = customMcVer_;
    bool native = customKind_ == "aerocore";
    std::thread([this, mc, native]() {
        auto& s = SettingsManager::Instance().Get();
        auto progressCb = [this](float p, const std::wstring& st) {
            progressCustom_.SetProgress(p);
            customHint_ = st;
        };

        std::string err;
        bool ok = native ? aerocore::Install(s.gameDir, progressCb, &err) : MinecraftInstaller::VerifyAndRepair(s.gameDir, mc, progressCb, &err);
        progressCustom_.SetProgress(1.0f);
        customHealth_ = ok ? L"Все файлы в порядке" : L"Обнаружены недостающие файлы";
        customHint_ = ok ? L"Сборка проверена: все файлы целы" : L"Рекомендуется нажать «Установить»";
        Toast::Instance().Show(customHealth_, ok ? ToastKind::Success : ToastKind::Error);

        isCustomBusy_ = false;
    }).detach();
}

void VersionsView::UpdateRowBounds() {
    float startX = 36.0f;
    float contentW = (bounds_.right > 72.0f) ? (bounds_.right - 72.0f) : 700.0f;
    float scrollStartY = 68.0f;
    float cardsY = scrollStartY + 8.0f;
    float cardH = 345.0f;
    float panelY = cardsY + cardH + 24.0f;
    float listStartY = panelY + 54.0f;

    // Installed rows buttons
    for (size_t i = 0; i < installedRows_.size(); ++i) {
        float itemY = listStartY + i * 72.0f;
        float btnY = itemY + 17.0f;
        float btnH = 30.0f;

        float curX = startX + contentW - 14.0f;

        curX -= 64.0f;
        installedRows_[i].btnDelete.SetBounds(curX, btnY, 64.0f, btnH);
        curX -= 6.0f;

        curX -= 70.0f;
        installedRows_[i].btnRepair.SetBounds(curX, btnY, 70.0f, btnH);
        curX -= 6.0f;

        curX -= 104.0f;
        installedRows_[i].btnRename.SetBounds(curX, btnY, 104.0f, btnH);
        curX -= 6.0f;

        curX -= 92.0f;
        installedRows_[i].btnClone.SetBounds(curX, btnY, 92.0f, btnH);
        curX -= 6.0f;

        curX -= 52.0f;
        installedRows_[i].btnMods.SetBounds(curX, btnY, 52.0f, btnH);
        curX -= 6.0f;

        curX -= 56.0f;
        installedRows_[i].btnFolder.SetBounds(curX, btnY, 56.0f, btnH);

        if (!installedRows_[i].isActive) {
            curX -= 6.0f;
            curX -= 96.0f;
            installedRows_[i].btnSelect.SetBounds(curX, btnY, 96.0f, btnH);
        }
    }

    float totalH = listStartY + (installedRows_.empty() ? 60.0f : installedRows_.size() * 72.0f) + 40.0f - scrollStartY;
    scrollView_.SetContentHeight(totalH);
}

void VersionsView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);

    float startX = 36.0f;
    float contentW = width - 72.0f;
    float scrollStartY = 68.0f;
    float scrollH = height - scrollStartY - 16.0f;

    scrollView_.SetBounds(startX, scrollStartY, contentW, scrollH);

    float cardsY = scrollStartY + 8.0f;
    float cardW = (contentW - 16.0f) * 0.5f;

    // 1. Official Aero Card
    float c1X = startX;
    float btnY1 = cardsY + 170.0f;
    const int actionColumns = cardW < 350.0f ? 2 : 4;
    const float actionWidth = (cardW - 36.0f - 8.0f * (actionColumns - 1)) / actionColumns;
    Button* actions[] = {&btnLunPick_, &btnLunInstall_, &btnLunRestore_, &btnLunActivate_};
    for (int i = 0; i < 4; ++i) {
        actions[i]->SetBounds(c1X + 18.0f + (i % actionColumns) * (actionWidth + 8.0f),
            btnY1 + (i / actionColumns) * 42.0f, actionWidth, 34.0f);
    }
    const float progressY = cardsY + (actionColumns == 2 ? 258.0f : 216.0f);
    progressLun_.SetBounds(c1X + 18.0f, progressY, cardW - 36.0f, 6.0f);

    // 2. Custom Build Card
    float c2X = startX + cardW + 16.0f;
    btnRefreshRemote_.SetBounds(c2X + cardW - 46.0f, cardsY + 14.0f, 28.0f, 28.0f);

    float segW = (cardW - 36.0f - 8.0f) / 2.0f;
    btnKindVanilla_.SetBounds(c2X + 18.0f, cardsY + 48.0f, segW, 32.0f);
    btnKindFabric_.SetBounds(c2X + 18.0f + segW + 8.0f, cardsY + 48.0f, segW, 32.0f);

    float pRowW = (customKind_ != "fabric") ? (cardW - 36.0f) : ((cardW - 44.0f) * 0.5f);
    btnCustomPick_.SetBounds(c2X + 18.0f, cardsY + 176.0f, pRowW, 34.0f);
    comboCustomLoader_.SetBounds(c2X + 18.0f + pRowW + 8.0f, cardsY + 176.0f, pRowW, 34.0f);

    checkSnapshots_.SetBounds(c2X + 18.0f, cardsY + 218.0f, 150.0f, 20.0f);

    btnCustomInstall_.SetBounds(c2X + 18.0f, cardsY + 246.0f, 100.0f, 34.0f);
    btnCustomVerify_.SetBounds(c2X + 126.0f, cardsY + 246.0f, 96.0f, 34.0f);
    progressCustom_.SetBounds(c2X + 18.0f, cardsY + 288.0f, cardW - 36.0f, 6.0f);

    UpdateRowBounds();

    // Modal Version Picker sizing
    UpdatePickerBounds();

    // Modal Rename sizing
    float rW = 380.0f;
    float rH = 170.0f;
    float rX = (width - rW) * 0.5f;
    float rY = (height - rH) * 0.5f;
    inputRename_.SetBounds(rX + 20.0f, rY + 60.0f, rW - 40.0f, 38.0f);
    btnRenameSave_.SetBounds(rX + rW - 190.0f, rY + 114.0f, 85.0f, 32.0f);
    btnRenameCancel_.SetBounds(rX + rW - 95.0f, rY + 114.0f, 75.0f, 32.0f);
}

void VersionsView::Update(float dt) {
    const bool accountChanged = aeroCatalogToken_ != AeroAuth::Instance().GetOnlineToken();
    if (accountChanged) FetchAeroCatalogAsync();
    bool refreshPicker = ApplyAeroCatalog() || accountChanged;
    {
        std::lock_guard<std::mutex> lock(remoteMutex_);
        if (needRefreshPicker_) {
            needRefreshPicker_ = false;
            refreshPicker = true;
        }
    }
    if (refreshPicker && isPickerOpen_) {
        PopulatePickerTiles();
        UpdatePickerBounds();
    }
    animTime_ += dt;
    if (needRefresh_) {
        needRefresh_ = false;
        RefreshData();
    }

    if (isPickerOpen_) {
        inputPickerSearch_.Update(dt);
        btnPickerClose_.Update(dt);
        pickerScrollView_.Update(dt);
        for (auto& t : filteredPickerVersions_) {
            if (t.hoverAnim.Get() > 0.001f || t.hoverAnim.GetTarget() > 0.001f) {
                t.hoverAnim.Update(dt);
            }
        }
        return;
    }

    if (isRenaming_) {
        inputRename_.Update(dt);
        btnRenameSave_.Update(dt);
        btnRenameCancel_.Update(dt);
        return;
    }

    btnLunPick_.Update(dt);
    btnLunInstall_.Update(dt);
    btnLunRestore_.Update(dt);
    btnLunActivate_.Update(dt);
    progressLun_.Update(dt);

    btnKindVanilla_.Update(dt);
    btnKindFabric_.Update(dt);
    btnRefreshRemote_.Update(dt);
    btnCustomPick_.Update(dt);
    comboCustomLoader_.Update(dt);
    checkSnapshots_.Update(dt);
    btnCustomInstall_.Update(dt);
    btnCustomVerify_.Update(dt);
    progressCustom_.Update(dt);

    bool updateLoaders = false;
    std::vector<std::pair<std::string, bool>> loadersCopy;
    {
        std::lock_guard<std::mutex> lock(loaderMutex_);
        if (needUpdateLoadersCombo_) {
            needUpdateLoadersCombo_ = false;
            updateLoaders = true;
            loadersCopy = fabricLoadersList_;
        }
    }

    if (updateLoaders && !loadersCopy.empty()) {
        std::vector<std::wstring> items;
        size_t count = (std::min)(loadersCopy.size(), size_t(16));
        int selectedIdx = 0;
        bool foundCurrent = false;

        for (size_t i = 0; i < count; ++i) {
            const auto& p = loadersCopy[i];
            std::wstring label = L"Fabric " + Utf8ToWide(p.first) + (p.second ? L" (Stable)" : L"");
            items.push_back(label);
            if (p.first == customLoaderVer_) {
                selectedIdx = static_cast<int>(i);
                foundCurrent = true;
            }
        }

        comboCustomLoader_.SetItems(items);
        if (!foundCurrent) {
            comboCustomLoader_.SetSelectedIndex(0);
            customLoaderVer_ = loadersCopy[0].first;
        } else {
            comboCustomLoader_.SetSelectedIndex(selectedIdx);
        }
    }


    scrollView_.Update(dt);

    for (auto& row : installedRows_) {
        row.hoverAnim.Update(dt);
        row.btnSelect.Update(dt);
        row.btnFolder.Update(dt);
        row.btnMods.Update(dt);
        row.btnClone.Update(dt);
        row.btnRename.Update(dt);
        row.btnRepair.Update(dt);
        row.btnDelete.Update(dt);
    }
}

bool VersionsView::OnMouseMove(float x, float y) {
    if (isPickerOpen_) {
        bool handled = btnPickerClose_.OnMouseMove(x, y);
        handled |= inputPickerSearch_.OnMouseMove(x, y);

        D2D1_RECT_F pBounds = pickerScrollView_.GetBounds();
        if (x >= pBounds.left && x <= pBounds.right && y >= pBounds.top && y <= pBounds.bottom) {
            float sy = y + pickerScrollView_.GetScrollOffset();
            for (auto& t : filteredPickerVersions_) {
                if (t.bounds.bottom < sy - 20.0f || t.bounds.top > sy + 20.0f) {
                    if (t.hoverAnim.GetTarget() > 0.0f) t.hoverAnim.SetTarget(0.0f);
                    continue;
                }
                bool hov = (x >= t.bounds.left && x <= t.bounds.right && sy >= t.bounds.top && sy <= t.bounds.bottom);
                t.hoverAnim.SetTarget(hov ? 1.0f : 0.0f);
            }
        }
        handled |= pickerScrollView_.OnMouseMove(x, y);
        return handled;
    }

    if (isRenaming_) {
        bool handled = btnRenameSave_.OnMouseMove(x, y);
        handled |= btnRenameCancel_.OnMouseMove(x, y);
        handled |= inputRename_.OnMouseMove(x, y);
        return handled;
    }

    D2D1_RECT_F sBounds = scrollView_.GetBounds();
    bool inScroll = (x >= sBounds.left && x <= sBounds.right && y >= sBounds.top && y <= sBounds.bottom);
    float scroll = scrollView_.GetScrollOffset();
    float sy = y + scroll;

    bool handled = false;
    if (inScroll) {
        // Module cards buttons inside scrollview
        handled |= btnLunPick_.OnMouseMove(x, sy);
        handled |= btnLunInstall_.OnMouseMove(x, sy);
        handled |= btnLunRestore_.OnMouseMove(x, sy);
        handled |= btnLunActivate_.OnMouseMove(x, sy);

        handled |= btnKindVanilla_.OnMouseMove(x, sy);
        handled |= btnKindFabric_.OnMouseMove(x, sy);
        handled |= btnRefreshRemote_.OnMouseMove(x, sy);
        handled |= btnCustomPick_.OnMouseMove(x, sy);
        handled |= comboCustomLoader_.OnMouseMove(x, sy);
        handled |= checkSnapshots_.OnMouseMove(x, sy);
        handled |= btnCustomInstall_.OnMouseMove(x, sy);
        handled |= btnCustomVerify_.OnMouseMove(x, sy);

        // Installed rows
        float startX = 36.0f;
        float contentW = (bounds_.right > 72.0f) ? (bounds_.right - 72.0f) : 700.0f;
        float scrollStartY = 68.0f;
        float cardsY = scrollStartY + 8.0f;
        float cardH = 300.0f;
        float panelY = cardsY + cardH + 18.0f;
        float listStartY = panelY + 54.0f;

        for (size_t i = 0; i < installedRows_.size(); ++i) {
            auto& row = installedRows_[i];
            float itemY = listStartY + i * 72.0f;
            bool hovered = (x >= startX && x <= startX + contentW && sy >= itemY && sy <= itemY + 64.0f);
            row.hoverAnim.SetTarget(hovered ? 1.0f : 0.0f);

            if (!row.isActive) handled |= row.btnSelect.OnMouseMove(x, sy);
            handled |= row.btnFolder.OnMouseMove(x, sy);
            handled |= row.btnMods.OnMouseMove(x, sy);
            handled |= row.btnClone.OnMouseMove(x, sy);
            handled |= row.btnRename.OnMouseMove(x, sy);
            handled |= row.btnRepair.OnMouseMove(x, sy);
            handled |= row.btnDelete.OnMouseMove(x, sy);
        }
    }

    handled |= scrollView_.OnMouseMove(x, y);
    return handled;
}

bool VersionsView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (isPickerOpen_) {
        if (btnPickerClose_.OnMouseButton(x, y, btn, down)) return true;
        if (inputPickerSearch_.OnMouseButton(x, y, btn, down)) return true;

        D2D1_RECT_F pBounds = pickerScrollView_.GetBounds();
        if (x >= pBounds.left && x <= pBounds.right && y >= pBounds.top && y <= pBounds.bottom) {
            float sy = y + pickerScrollView_.GetScrollOffset();
            for (const auto& t : filteredPickerVersions_) {
                if (t.bounds.bottom < sy || t.bounds.top > sy) continue;
                if (x >= t.bounds.left && x <= t.bounds.right && sy >= t.bounds.top && sy <= t.bounds.bottom) {
                    if (down && btn == MouseBtn::Left) {
                        std::string chosen = t.id;
                        if (pickerForAero_) {
                            lunMcVer_ = chosen;
                            auto& s = SettingsManager::Instance().Get();
                            s.modMcVersion = chosen;
                            SettingsManager::Instance().Save();
                            btnLunPick_.SetText(L"Версия");
                        } else {
                            customMcVer_ = chosen;
                            btnCustomPick_.SetText(L"");
                            if (customKind_ == "fabric") {
                                FetchFabricLoadersAsync(customMcVer_);
                            }
                        }
                        CloseVersionPicker();
                        return true;
                    }
                }
            }
        }
        if (pickerScrollView_.OnMouseButton(x, y, btn, down)) return true;
        return true;
    }

    if (isRenaming_) {
        if (btnRenameSave_.OnMouseButton(x, y, btn, down)) return true;
        if (btnRenameCancel_.OnMouseButton(x, y, btn, down)) return true;
        if (inputRename_.OnMouseButton(x, y, btn, down)) return true;
        return true;
    }

    D2D1_RECT_F sBounds = scrollView_.GetBounds();
    bool inScroll = (x >= sBounds.left && x <= sBounds.right && y >= sBounds.top && y <= sBounds.bottom);
    float scroll = scrollView_.GetScrollOffset();
    float sy = y + scroll;

    if (inScroll) {
        // Module cards buttons inside scrollview
        if (btnLunPick_.OnMouseButton(x, sy, btn, down)) return true;
        if (btnLunInstall_.OnMouseButton(x, sy, btn, down)) return true;
        if (btnLunRestore_.OnMouseButton(x, sy, btn, down)) return true;
        if (btnLunActivate_.OnMouseButton(x, sy, btn, down)) return true;

        if (btnKindVanilla_.OnMouseButton(x, sy, btn, down)) return true;
        if (btnKindFabric_.OnMouseButton(x, sy, btn, down)) return true;
        if (btnRefreshRemote_.OnMouseButton(x, sy, btn, down)) return true;
        if (btnCustomPick_.OnMouseButton(x, sy, btn, down)) return true;
        if (customKind_ == "fabric" && comboCustomLoader_.OnMouseButton(x, sy, btn, down)) return true;
        if (checkSnapshots_.OnMouseButton(x, sy, btn, down)) {
            RefreshData();
            return true;
        }
        if (btnCustomInstall_.OnMouseButton(x, sy, btn, down)) return true;
        if (btnCustomVerify_.OnMouseButton(x, sy, btn, down)) return true;

        // Installed rows buttons
        for (auto& row : installedRows_) {
            if (!row.isActive && row.btnSelect.OnMouseButton(x, sy, btn, down)) return true;
            if (row.btnFolder.OnMouseButton(x, sy, btn, down)) return true;
            if (row.btnMods.OnMouseButton(x, sy, btn, down)) return true;
            if (row.btnClone.OnMouseButton(x, sy, btn, down)) return true;
            if (row.btnRename.OnMouseButton(x, sy, btn, down)) return true;
            if (row.btnRepair.OnMouseButton(x, sy, btn, down)) return true;
            if (row.btnDelete.OnMouseButton(x, sy, btn, down)) return true;
        }
    }

    return scrollView_.OnMouseButton(x, y, btn, down);
}

bool VersionsView::OnMouseWheel(float x, float y, float delta) {
    if (isPickerOpen_) {
        return pickerScrollView_.OnMouseWheel(x, y, delta);
    }
    if (comboCustomLoader_.IsExpanded() && comboCustomLoader_.OnMouseWheel(x, y, delta)) {
        return true;
    }
    return scrollView_.OnMouseWheel(x, y, delta);
}

bool VersionsView::OnChar(wchar_t ch) {
    if (isPickerOpen_) {
        bool res = inputPickerSearch_.OnChar(ch);
        FilterPickerVersions();
        return res;
    }
    if (isRenaming_) {
        return inputRename_.OnChar(ch);
    }
    return false;
}

bool VersionsView::OnKey(int vk, bool down) {
    if (isPickerOpen_) {
        if (down && vk == VK_ESCAPE) {
            CloseVersionPicker();
            return true;
        }
        bool res = inputPickerSearch_.OnKey(vk, down);
        FilterPickerVersions();
        return res;
    }

    if (isRenaming_) {
        if (down && vk == VK_ESCAPE) {
            isRenaming_ = false;
            return true;
        }
        if (down && vk == VK_RETURN) {
            std::string newName = WideToUtf8(inputRename_.GetText());
            if (!newName.empty()) {
                if (InstancesManager::Instance().RenameInstance(renameInstId_, newName)) {
                    Toast::Instance().Show(L"Сборка переименована: " + Utf8ToWide(newName), ToastKind::Success);
                    needRefresh_ = true;
                } else {
                    Toast::Instance().Show(L"Ошибка переименования сборки", ToastKind::Error);
                }
            }
            isRenaming_ = false;
            return true;
        }
        return inputRename_.OnKey(vk, down);
    }
    return false;
}

void VersionsView::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    float startX = 36.0f;
    float contentW = bounds_.right - 72.0f;

    // View Header
    r.DrawString(L"Библиотека сборок", D2D1::RectF(startX, 28.0f, startX + 300.0f, 52.0f), theme.text, 20.0f, FontStyle::Bold);
    r.DrawString(L"Установка и управление версиями Minecraft", D2D1::RectF(startX + 260.0f, 32.0f, startX + contentW - 200.0f, 52.0f), theme.muted, 11.0f);

    // Live status chip (top right)
    D2D1_RECT_F liveDot = D2D1::RectF(startX + contentW - 190.0f, 34.0f, startX + contentW - 184.0f, 40.0f);
    r.FillRoundedRect(liveDot, 2.0f, theme.mint);
    r.DrawString(L"Выбор версии Minecraft", D2D1::RectF(startX + contentW - 178.0f, 28.0f, startX + contentW, 46.0f), theme.mint, 10.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

    scrollView_.BeginRender(r);

    float scrollStartY = 68.0f;
    float cardsY = scrollStartY + 8.0f;
    float cardW = (contentW - 16.0f) * 0.5f;
    float cardH = 345.0f;

    // ------------------------------------------------------------------------------------------------
    // CARD 1: OFFICIAL AERO VISUAL STACK (module-card--aero)
    // ------------------------------------------------------------------------------------------------
    float c1X = startX;
    D2D1_RECT_F c1Rect = D2D1::RectF(c1X, cardsY, c1X + cardW, cardsY + cardH);

    r.FillRoundedRect(c1Rect, 12.0f, Color::FromRgba(32, 35, 32, 0.94f));
    r.DrawRoundedRect(c1Rect, 12.0f, Color::FromRgba(240, 183, 130, 0.22f), 1.0f);

    // Head

    const auto aeroSession = AeroAuth::Instance().GetSession();
    bool hasSub = aeroSession.verified && aeroSession.hasSubscription;
    if (isAeroInfoLoaded_) hasSub = aeroCatalogPremium_;
    std::wstring lunTitle = hasSub?L"Aero Visuals Premium":L"Aero Visuals Free";
    r.DrawString(lunTitle, D2D1::RectF(c1X + 18.0f, cardsY + 26.0f, c1X + cardW - 80.0f, cardsY + 48.0f), theme.text, 15.0f, FontStyle::Bold);

    // Lock badge (#lun-locked)
    D2D1_RECT_F lockBadge = D2D1::RectF(c1X + cardW - 65.0f, cardsY + 16.0f, c1X + cardW - 18.0f, cardsY + 36.0f);
    r.FillRoundedRect(lockBadge, 4.0f, hasSub ? Color::FromRgba(178, 199, 142, 0.15f) : Color::FromRgba(251, 191, 36, 0.15f));
    r.DrawRoundedRect(lockBadge, 4.0f, hasSub ? Color::FromRgba(178, 199, 142, 0.40f) : Color::FromRgba(251, 191, 36, 0.40f), 1.0f);
    r.DrawString(hasSub ? L"PREMIUM" : L"FREE", lockBadge, hasSub ? theme.mint : Color::FromHex(0xfacc6a), 8.5f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

    // Art box (.module-art.art.art--aero)
    D2D1_RECT_F art1 = D2D1::RectF(c1X + 18.0f, cardsY + 54.0f, c1X + cardW - 18.0f, cardsY + 134.0f);
    aero::Landscape(r, art1);
    r.DrawRoundedRect(art1, 12.0f, Color::FromRgba(240, 183, 130, 0.18f), 1.0f);

    // Single tag at bottom-left (#lun-art-tag)
    std::wstring lunTagStr = L"Aero " + Utf8ToWide(lunMcVer_);
    D2D1_RECT_F lunArtTagRect = D2D1::RectF(art1.left + 14.0f, art1.bottom - 32.0f, art1.right - 14.0f, art1.bottom - 8.0f);
    r.DrawString(lunTagStr, lunArtTagRect, Color::FromHex(0xf4f1e8), 16.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

    // Meta line (.module-meta)
    float meta1Y = cardsY + 144.0f;
    std::wstring lunMcStr = L"Minecraft " + Utf8ToWide(lunMcVer_);
    r.DrawString(lunMcStr, D2D1::RectF(c1X + 18.0f, meta1Y, c1X + 200.0f, meta1Y + 18.0f), Color::FromHex(0xb4bdaa), 10.5f, FontStyle::Regular);
    r.DrawString(L"Загрузчик Fabric", D2D1::RectF(c1X + cardW - 160.0f, meta1Y, c1X + cardW - 18.0f, meta1Y + 18.0f), Color::FromHex(0xb4bdaa), 10.5f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_TRAILING);

    // Action buttons
    btnLunPick_.Render(r);
    btnLunInstall_.Render(r);
    btnLunRestore_.Render(r);
    btnLunActivate_.Render(r);

    // Progress & hint
    progressLun_.Render(r);
    const float hintY = progressLun_.GetBounds().bottom + 10.0f;
    r.DrawString(lunHint_, D2D1::RectF(c1X + 18.0f, hintY, c1X + cardW - 18.0f, hintY + 42.0f), theme.muted, 11.0f);

    // ------------------------------------------------------------------------------------------------
    // CARD 2: ADD CUSTOM BUILD (module-card: VANILLA / FABRIC)
    // ------------------------------------------------------------------------------------------------
    float c2X = startX + cardW + 16.0f;
    D2D1_RECT_F c2Rect = D2D1::RectF(c2X, cardsY, c2X + cardW, cardsY + cardH);

    r.FillRoundedRect(c2Rect, 12.0f, Color::FromRgba(32, 35, 32, 0.94f));
    r.DrawRoundedRect(c2Rect, 12.0f, theme.line, 1.0f);

    // Head
    r.DrawString(L"Добавить сборку", D2D1::RectF(c2X + 18.0f, cardsY + 26.0f, c2X + cardW - 80.0f, cardsY + 48.0f), theme.text, 15.0f, FontStyle::Bold);
    btnRefreshRemote_.Render(r);

    // Segmented loader buttons
    btnKindVanilla_.Render(r);
    btnKindFabric_.Render(r);

    // Art box (.module-art.art)
    D2D1_RECT_F art2 = D2D1::RectF(c2X + 18.0f, cardsY + 87.0f, c2X + cardW - 18.0f, cardsY + 167.0f);
    D2D1_COLOR_F a2Col1 = (customKind_ == "fabric") ? Color::FromHex(0x123f3d) : Color::FromHex(0x252b24);
    D2D1_COLOR_F a2Col2 = (customKind_ == "fabric") ? Color::FromHex(0x1d5c56) : Color::FromHex(0x252b24);
    r.FillGradientRoundedRect(art2, 12.0f, a2Col1, a2Col2, false);
    r.DrawRoundedRect(art2, 12.0f, theme.line, 1.0f);

    // Single tag at bottom-left (#ver-art-tag)
    std::wstring kTagStr = Utf8ToWide(customKind_ == "aerocore" ? "AeroCore 26.3" : customKind_ == "fabric" ? "Fabric" : "Vanilla");
    D2D1_RECT_F verArtTagRect = D2D1::RectF(art2.left + 14.0f, art2.bottom - 32.0f, art2.right - 14.0f, art2.bottom - 8.0f);
    r.DrawString(kTagStr, verArtTagRect, Color::FromHex(0xf4f1e8), 16.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

    // Version picker button & loader version combo (.picker-row)
    btnCustomPick_.Render(r);
    D2D1_RECT_F pBtnRect = btnCustomPick_.GetBounds();
    r.DrawString(L"Версия Minecraft", D2D1::RectF(pBtnRect.left + 12.0f, pBtnRect.top, pBtnRect.left + 120.0f, pBtnRect.bottom), Color::FromHex(0xb4bdaa), 10.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    r.DrawString(Utf8ToWide(customMcVer_), D2D1::RectF(pBtnRect.left + 120.0f, pBtnRect.top, pBtnRect.right - 12.0f, pBtnRect.bottom), Color::FromHex(0xb4bdaa), 12.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_TRAILING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

    if (customKind_ == "fabric") {
        comboCustomLoader_.Render(r);
    }

    checkSnapshots_.Render(r);

    btnCustomInstall_.Render(r);
    btnCustomVerify_.Render(r);

    progressCustom_.Render(r);
    r.DrawString(customHint_, D2D1::RectF(c2X + 18.0f, cardsY + 300.0f, c2X + cardW - 18.0f, cardsY + 316.0f), Color::FromHex(0x929d86), 10.0f);
    r.DrawString(customHealth_, D2D1::RectF(c2X + 18.0f, cardsY + 318.0f, c2X + cardW - 18.0f, cardsY + 334.0f), Color::FromHex(0x929d86), 9.5f);

    // ------------------------------------------------------------------------------------------------
    // PANEL: УСТАНОВЛЕННЫЕ ВЕРСИИ
    // ------------------------------------------------------------------------------------------------
    float panelY = cardsY + cardH + 24.0f;
    float listStartY = panelY + 54.0f;

    // Section head
    r.DrawString(L"Установленные версии", D2D1::RectF(startX, panelY, startX + 300.0f, panelY + 22.0f), theme.text, 16.0f, FontStyle::Bold);
    r.DrawString(L"Управляй локальными копиями, модами и структурой инстансов.", D2D1::RectF(startX, panelY + 24.0f, startX + 450.0f, panelY + 42.0f), theme.muted, 11.0f);

    // AERO ACTIVE pill if active instance is Aero
    const auto activeInst = InstancesManager::Instance().GetActiveInstance();
    bool isLunActive = activeInst && ((activeInst->name.find("Aero") != std::string::npos || activeInst->name.find("Aero") != std::string::npos) || activeInst->name.find("aero") != std::string::npos);
    if (isLunActive) {
        D2D1_RECT_F lunPill = D2D1::RectF(startX + contentW - 130.0f, panelY + 4.0f, startX + contentW, panelY + 26.0f);
        r.FillRoundedRect(lunPill, 4.0f, Color::FromRgba(178, 199, 142, 0.15f));
        r.DrawRoundedRect(lunPill, 4.0f, Color::FromRgba(178, 199, 142, 0.40f), 1.0f);
        r.DrawString(L"AERO ACTIVE", lunPill, theme.mint, 9.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    }

    // Installed rows
    if (installedRows_.empty()) {
        D2D1_RECT_F emptyBox = D2D1::RectF(startX, listStartY, startX + contentW, listStartY + 70.0f);
        r.FillRoundedRect(emptyBox, 8.0f, Color::FromRgba(32, 36, 30, 0.5f));
        r.DrawRoundedRect(emptyBox, 8.0f, theme.line, 1.0f);
        r.DrawString(L"Пока не установлено ни одной сборки", emptyBox, theme.muted, 12.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    } else {
        for (size_t i = 0; i < installedRows_.size(); ++i) {
            auto& row = installedRows_[i];
            float itemY = listStartY + i * 72.0f;
            float itemH = 64.0f;

            D2D1_RECT_F rowRect = D2D1::RectF(startX, itemY, startX + contentW, itemY + itemH);

            float h = row.hoverAnim.Get();
            D2D1_COLOR_F rowBg = row.isActive ? Color::FromHex(0x252b24) : anim::LerpColor(Color::FromHex(0x252b24), Color::FromHex(0x252b24), h);
            r.FillRoundedRect(rowRect, 8.0f, rowBg);

            D2D1_COLOR_F borderCol = row.isActive ? Color::FromRgba(178, 199, 142, 0.50f) : anim::LerpColor(theme.line, theme.accent2, h * 0.5f);
            r.DrawRoundedRect(rowRect, 8.0f, borderCol, row.isActive ? 1.4f : 1.0f);

            // Left details: align directly from left padding (matching Node.js .row__main)
            float textX = startX + 16.0f;

            // Line 1: Name + loader badge + active badge
            r.DrawString(row.name, D2D1::RectF(textX, itemY + 8.0f, textX + 320.0f, itemY + 28.0f), theme.text, 13.0f, FontStyle::Bold);

            // Loader pill
            float pillLeft = textX + r.MeasureString(row.name, 13.0f, FontStyle::Bold).width + 12.0f;
            D2D1_RECT_F lPill = D2D1::RectF(pillLeft, itemY + 8.0f, pillLeft + 54.0f, itemY + 26.0f);
            r.FillRoundedRect(lPill, 4.0f, Color::FromRgba(207, 189, 154, 0.10f));
            r.DrawRoundedRect(lPill, 4.0f, Color::FromRgba(207, 189, 154, 0.22f), 1.0f);
            r.DrawString(row.loader, lPill, Color::FromHex(0xb4bdaa), 9.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

            // Active pill
            if (row.isActive) {
                D2D1_RECT_F aPill = D2D1::RectF(pillLeft + 60.0f, itemY + 8.0f, pillLeft + 124.0f, itemY + 26.0f);
                r.FillRoundedRect(aPill, 4.0f, Color::FromRgba(178, 199, 142, 0.18f));
                r.DrawRoundedRect(aPill, 4.0f, Color::FromRgba(178, 199, 142, 0.40f), 1.0f);
                r.DrawString(L"Выбрана", aPill, theme.mint, 9.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
            }

            // Line 2: version ID & dir
            std::wstring sub1 = Utf8ToWide(row.versionId) + L" · " + row.pathStr;
            r.DrawString(sub1, D2D1::RectF(textX, itemY + 28.0f, startX + contentW - 460.0f, itemY + 43.0f), theme.muted, 10.5f);

            // Line 3: counters
            r.DrawString(row.counters, D2D1::RectF(textX, itemY + 44.0f, startX + contentW - 460.0f, itemY + 58.0f), Color::FromHex(0x929d86), 10.0f);

            // Render row buttons
            if (!row.isActive) row.btnSelect.Render(r);
            row.btnFolder.Render(r);
            row.btnMods.Render(r);
            row.btnClone.Render(r);
            row.btnRename.Render(r);
            row.btnRepair.Render(r);
            row.btnDelete.Render(r);
        }
    }

    // Dropdown inside custom card must render on top
    if (customKind_ == "fabric") {
        comboCustomLoader_.RenderDropdown(r);
    }

    scrollView_.EndRender(r);

    // ------------------------------------------------------------------------------------------------
    // VERSION PICKER MODAL (modal__card--picker)
    // ------------------------------------------------------------------------------------------------
    if (isPickerOpen_) {
        // Dim background overlay completely blocking background
        r.FillRoundedRect(D2D1::RectF(0, 0, bounds_.right, bounds_.bottom), 0.0f, Color::FromRgba(32, 35, 32, 0.88f));

        float width = bounds_.right - bounds_.left;
        float height = bounds_.bottom - bounds_.top;
        float pW = (std::min)(width - 60.0f, 650.0f);
        float pH = (std::min)(height - 80.0f, 540.0f);
        float pX = (width - pW) * 0.5f;
        float pY = (height - pH) * 0.5f;

        D2D1_RECT_F pRect = D2D1::RectF(pX, pY, pX + pW, pY + pH);
        r.FillGradientRoundedRect(pRect, 14.0f, Color::FromHex(0x252b24), Color::FromHex(0x252b24), false);
        r.DrawRoundedRect(pRect, 14.0f, Color::FromRgba(240, 183, 130, 0.40f), 1.0f);

        // Head
        r.DrawString(L"SELECT / VERSION", D2D1::RectF(pX + 24.0f, pY + 16.0f, pX + 300.0f, pY + 30.0f), theme.accent2, 9.0f, FontStyle::Bold);
        std::wstring pTitle = pickerForAero_ ? L"Версия Aero Visuals" : (customKind_ == "fabric" ? L"Выбор версии Fabric" : L"Выбор версии Minecraft");
        bool isFetching = pickerForAero_ && aeroCatalogRequest_.valid() && !isAeroInfoLoaded_;
        if (!pickerForAero_) {
            std::lock_guard<std::mutex> lock(remoteMutex_);
            isFetching = isFetchingManifest_;
        }
        if (isFetching) {
            pTitle += L" (Загрузка списка...)";
        } else {
            pTitle += L" (" + std::to_wstring(filteredPickerVersions_.size()) + L" версий)";
        }
        r.DrawString(pTitle, D2D1::RectF(pX + 24.0f, pY + 28.0f, pX + pW - 60.0f, pY + 54.0f), theme.text, 16.0f, FontStyle::Bold);

        btnPickerClose_.Render(r);
        inputPickerSearch_.Render(r);

        if (pickerForAero_ && filteredPickerVersions_.empty()) {
            std::wstring message = aeroCatalogMessage_;
            if (isFetching) message = L"Загрузка версий AeroVisuals…";
            else if (!allPickerVersions_.empty()) message = L"По запросу ничего не найдено.";
            r.DrawString(message, D2D1::RectF(pX + 24.0f, pY + 130.0f, pX + pW - 24.0f, pY + 200.0f), theme.text, 13.0f, FontStyle::Regular);
        }
        pickerScrollView_.BeginRender(r);

        float scrollOffset = pickerScrollView_.GetScrollOffset();
        D2D1_RECT_F pvBounds = pickerScrollView_.GetBounds();
        float viewH = pvBounds.bottom - pvBounds.top;
        float visibleTop = pvBounds.top + scrollOffset - 20.0f;
        float visibleBottom = pvBounds.top + scrollOffset + viewH + 20.0f;

        for (auto& t : filteredPickerVersions_) {
            if (t.bounds.bottom < visibleTop || t.bounds.top > visibleBottom) {
                continue;
            }
            bool isActive = pickerForAero_ ? (t.id == lunMcVer_) : (t.id == customMcVer_);
            float h = t.hoverAnim.Get();

            D2D1_COLOR_F tileBg = isActive
                ? Color::FromRgba(240, 183, 130, 0.12f)
                : anim::LerpColor(Color::FromHex(0x252b24), Color::FromHex(0x252b24), h);
            D2D1_COLOR_F tileBorder = isActive
                ? Color::FromRgba(240, 183, 130, 0.70f)
                : anim::LerpColor(theme.line, theme.accent2, h * 0.6f);

            r.FillRoundedRect(t.bounds, 6.0f, tileBg);
            r.DrawRoundedRect(t.bounds, 6.0f, tileBorder, (isActive || h > 0.05f) ? 1.5f : 1.0f);

            // Preview art (tile__art)
            D2D1_RECT_F artRect = D2D1::RectF(t.bounds.left + 5.0f, t.bounds.top + 5.0f, t.bounds.right - 5.0f, t.bounds.top + 42.0f);
            D2D1_COLOR_F a1;
            D2D1_COLOR_F bdr;
            if (pickerForAero_) {
                a1 = Color::FromRgba(207, 189, 154, 0.60f);
                bdr = Color::FromRgba(240, 183, 130, 0.50f);
            } else if (t.isSnapshot) {
                a1 = Color::FromRgba(239, 68, 68, 0.55f);
                bdr = Color::FromRgba(248, 113, 113, 0.50f);
            } else if (customKind_ == "fabric") {
                a1 = Color::FromRgba(56, 189, 248, 0.55f);
                bdr = Color::FromRgba(207, 189, 154, 0.50f);
            } else {
                a1 = Color::FromRgba(16, 185, 129, 0.55f);
                bdr = Color::FromRgba(52, 211, 153, 0.50f);
            }
            D2D1_COLOR_F a2 = Color::FromRgba(32, 35, 32, 0.90f);
            r.FillGradientRoundedRect(artRect, 4.0f, a1, a2, false);
            r.DrawRoundedRect(artRect, 4.0f, bdr, 1.0f);

            r.DrawString(t.tag, artRect, Color::FromHex(0xb4bdaa), 9.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

            // Name
            D2D1_RECT_F nameRect = D2D1::RectF(t.bounds.left + 6.0f, t.bounds.top + 45.0f, t.bounds.right - 6.0f, t.bounds.top + 64.0f);
            r.DrawString(Utf8ToWide(t.id), nameRect, theme.text, 11.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

            // Subtitle
            D2D1_RECT_F subRect = D2D1::RectF(t.bounds.left + 6.0f, t.bounds.top + 64.0f, t.bounds.right - 6.0f, t.bounds.bottom - 4.0f);
            r.DrawString(t.sub, subRect, theme.muted, 8.5f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
        }

        pickerScrollView_.EndRender(r);
    }

    // ------------------------------------------------------------------------------------------------
    // MODAL RENAME DIALOG
    // ------------------------------------------------------------------------------------------------
    if (isRenaming_) {
        r.FillRoundedRect(D2D1::RectF(0, 0, bounds_.right, bounds_.bottom), 0.0f, Color::FromRgba(32, 35, 32, 0.75f));

        float rW = 380.0f;
        float rH = 170.0f;
        float rX = (bounds_.right - rW) * 0.5f;
        float rY = (bounds_.bottom - rH) * 0.5f;

        D2D1_RECT_F mRect = D2D1::RectF(rX, rY, rX + rW, rY + rH);
        r.FillRoundedRect(mRect, 8.0f, Color::FromHex(0x252b24));
        r.DrawRoundedRect(mRect, 8.0f, Color::FromRgba(240, 183, 130, 0.40f), 1.0f);

        r.DrawString(L"ACTION / INPUT", D2D1::RectF(rX + 20.0f, rY + 16.0f, rX + 300.0f, rY + 30.0f), theme.accent2, 9.0f, FontStyle::Bold);
        r.DrawString(L"Переименование сборки", D2D1::RectF(rX + 20.0f, rY + 30.0f, rX + 340.0f, rY + 52.0f), theme.text, 15.0f, FontStyle::Bold);

        inputRename_.Render(r);
        btnRenameSave_.Render(r);
        btnRenameCancel_.Render(r);
    }
}

void VersionsView::FetchAeroCatalogAsync(bool refresh) {
    const auto token = AeroAuth::Instance().GetOnlineToken();
    if (aeroCatalogToken_ != token) {
        aeroCatalogToken_ = token;
        aeroCatalogRefreshQueued_ = false;
        aeroCatalogRequest_ = {};
        aeroSupportedVersions_.clear();
        isAeroInfoLoaded_ = false;
        aeroCatalogMessage_.clear();
    }
    if (aeroCatalogRequest_.valid()) {
        aeroCatalogRefreshQueued_ = aeroCatalogRefreshQueued_ || refresh;
        return;
    }

    std::promise<AeroCatalog> result;
    aeroCatalogRequest_ = result.get_future();
    std::thread([token, result = std::move(result)]() mutable {
        AeroCatalog catalog;
        try {
            const auto response = HttpClient::ApiRequest(Config::Client::Info, "GET", "", token);
            if (response.isSuccess && response.json.contains("data")) {
                const auto& data = response.json["data"];
                if (data.is_object()) {
                    catalog.premium = data.value("hasSubscription", false);
                    const auto key = catalog.premium ? "premiumMcVersions" : "freeMcVersions";
                    const auto list = data.contains("availableMcVersions") ? data["availableMcVersions"]
                        : data.contains(key) ? data[key] : data.value("mcVersions", nlohmann::json::array());
                    catalog.valid = list.is_array();
                    for (const auto& item : list) if (catalog.valid && item.is_string()) {
                        auto version = item.get<std::string>();
                        if (!version.empty() && std::find(catalog.versions.begin(), catalog.versions.end(), version) == catalog.versions.end()) {
                            catalog.versions.push_back(std::move(version));
                        }
                    }
                }
            }
        } catch (const std::exception&) {
            catalog.valid = false;
        }
        result.set_value(std::move(catalog));
    }).detach();
}

bool VersionsView::ApplyAeroCatalog() {
    if (!aeroCatalogRequest_.valid()
        || aeroCatalogRequest_.wait_for(std::chrono::seconds(0)) != std::future_status::ready) return false;

    auto catalog = aeroCatalogRequest_.get();
    if (catalog.valid) {
        aeroSupportedVersions_ = std::move(catalog.versions);
        aeroCatalogPremium_ = catalog.premium;
        isAeroInfoLoaded_ = true;
        aeroCatalogMessage_ = catalog.premium ? L"Пока нет опубликованных Premium-версий." : L"Пока нет опубликованных Free-версий.";
    } else {
        aeroCatalogMessage_ = L"Не удалось загрузить версии. Попробуйте обновить список.";
    }
    if (aeroCatalogRefreshQueued_) {
        aeroCatalogRefreshQueued_ = false;
        FetchAeroCatalogAsync();
    }
    return true;
}

void VersionsView::FetchRemoteManifestAsync() {
    {
        std::lock_guard<std::mutex> lock(remoteMutex_);
        if (isFetchingManifest_) return;
        isFetchingManifest_ = true;
    }
    std::thread([this]() {
        auto manifest = MinecraftMeta::FetchVanillaManifest();
        auto fabricGames = MinecraftMeta::FetchFabricGameVersions();
        {
            std::lock_guard<std::mutex> lock(remoteMutex_);
            if (!manifest.empty()) { remoteManifest_ = std::move(manifest); isManifestLoaded_ = true; }
            if (!fabricGames.empty()) { remoteFabricGames_ = std::move(fabricGames); isFabricGamesLoaded_ = true; }
            isFetchingManifest_ = false;
            needRefreshPicker_ = true;
        }
    }).detach();
}

void VersionsView::FetchFabricLoadersAsync(const std::string& mcVersion) {
    std::thread([this, mcVersion]() {
        std::vector<std::pair<std::string, bool>> results;

        // 1. Version-specific Fabric Meta endpoint
        if (!mcVersion.empty()) {
            std::string url = "https://meta.fabricmc.net/v2/versions/loader/" + mcVersion;
            auto resp = HttpClient::Get(url);
            if (resp.isSuccess && resp.json.is_array() && !resp.json.empty()) {
                for (const auto& item : resp.json) {
                    if (item.contains("loader") && item["loader"].is_object()) {
                        std::string ver = item["loader"].value("version", "");
                        bool stable = item["loader"].value("stable", false);
                        if (!ver.empty()) {
                            results.emplace_back(ver, stable);
                        }
                    } else if (item.contains("version")) {
                        std::string ver = item.value("version", "");
                        bool stable = item.value("stable", false);
                        if (!ver.empty()) {
                            results.emplace_back(ver, stable);
                        }
                    }
                }
            }
        }

        // 2. Global Fabric Meta loaders endpoint fallback
        if (results.empty()) {
            auto globalResp = HttpClient::Get("https://meta.fabricmc.net/v2/versions/loader");
            if (globalResp.isSuccess && globalResp.json.is_array() && !globalResp.json.empty()) {
                for (const auto& item : globalResp.json) {
                    std::string ver = item.value("version", "");
                    bool stable = item.value("stable", false);
                    if (!ver.empty()) {
                        results.emplace_back(ver, stable);
                    }
                }
            }
        }

        // 3. Fallback to latest stable loaders if offline
        if (results.empty()) {
            results = {
                { "0.19.5", true },
                { "0.19.4", false },
                { "0.19.3", false },
                { "0.19.2", false },
                { "0.18.6", true },
                { "0.18.4", true },
                { "0.17.3", true },
                { "0.16.14", true }
            };
        }

        {
            std::lock_guard<std::mutex> lock(loaderMutex_);
            fabricLoadersList_ = std::move(results);
            needUpdateLoadersCombo_ = true;
        }
    }).detach();
}

} // namespace aero

