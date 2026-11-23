#pragma once
#include "../ui/UIElement.h"
#include "../ui/Button.h"
#include "../ui/TextInput.h"
#include "../ui/ComboBox.h"
#include "../ui/Checkbox.h"
#include "../ui/ProgressBar.h"
#include "../ui/ScrollView.h"
#include "../game/MinecraftMeta.h"
#include <vector>
#include <string>
#include <mutex>
#include <future>

namespace aero {

class VersionsView : public UIElement {
public:
    VersionsView();

    void Resize(float width, float height);
    void RefreshData();

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

private:
    void UpdateRowBounds();
    void UpdatePickerBounds();
    void OpenVersionPicker(bool forAero);
    void CloseVersionPicker();
    void FilterPickerVersions();
    void PopulatePickerTiles();
    void InstallAeroBuild();
    void RestoreAeroBuild();
    void ActivateAeroBuild();
    void InstallCustomBuild();
    void VerifyCustomBuild();
    void FetchRemoteManifestAsync();
    void FetchAeroCatalogAsync(bool refresh = false);
    bool ApplyAeroCatalog();
    void FetchFabricLoadersAsync(const std::string& mcVersion);

    // 1. Official Aero Visuals Stack Card
    std::string lunMcVer_ = "1.21.11";
    Button btnLunPick_{ L"Версия", ButtonStyle::Ghost };
    Button btnLunInstall_{ L"Установить", ButtonStyle::Primary };
    Button btnLunRestore_{ L"Проверить", ButtonStyle::Ghost };
    Button btnLunActivate_{ L"Выбрать", ButtonStyle::Ghost };
    ProgressBar progressLun_;
    std::wstring lunHint_ = L"Выбери версию Minecraft и установи визуальный стек.";
    bool isLunBusy_ = false;

    // 2. Add Custom Build Card (Vanilla / Fabric)
    std::string customKind_ = "vanilla"; // "vanilla", "fabric"
    std::string customMcVer_ = "1.21.4";
    std::string customLoaderVer_ = "0.19.5";
    Button btnKindVanilla_{ L"Vanilla", ButtonStyle::Primary };
    Button btnKindFabric_{ L"Fabric", ButtonStyle::Default };
    Button btnRefreshRemote_{ L"↻", ButtonStyle::Ghost };
    Button btnCustomPick_{ L"", ButtonStyle::Ghost };
    ComboBox comboCustomLoader_;
    Checkbox checkSnapshots_{ L"Включить снимки" };
    Button btnCustomInstall_{ L"Установить", ButtonStyle::Primary };
    Button btnCustomVerify_{ L"Проверить", ButtonStyle::Ghost };
    ProgressBar progressCustom_;
    std::wstring customHint_ = L"Выбери тип загрузчика и версию.";
    std::wstring customHealth_ = L"Состояние сборки не проверено";
    bool isCustomBusy_ = false;

    std::mutex loaderMutex_;
    std::vector<std::pair<std::string, bool>> fabricLoadersList_;
    bool needUpdateLoadersCombo_ = false;

    // 3. Installed Rows
    struct InstalledRow {
        std::string id;
        std::string versionId;
        std::wstring name;
        std::wstring pathStr;
        std::wstring loader;
        std::wstring counters;
        bool isActive = false;
        AnimatedFloat hoverAnim{ 0.0f, 15.0f };
        Button btnSelect{ L"Использовать", ButtonStyle::Primary };
        Button btnFolder{ L"Папка", ButtonStyle::Ghost };
        Button btnMods{ L"Моды", ButtonStyle::Ghost };
        Button btnClone{ L"Клонировать", ButtonStyle::Ghost };
        Button btnRename{ L"Переименовать", ButtonStyle::Ghost };
        Button btnRepair{ L"Починить", ButtonStyle::Ghost };
        Button btnDelete{ L"Удалить", ButtonStyle::Danger };
    };

    std::vector<InstalledRow> installedRows_;
    ScrollView scrollView_;
    float animTime_ = 0.0f;
    bool needRefresh_ = false;

    // 4. Version Picker Modal
    bool isPickerOpen_ = false;
    bool pickerForAero_ = true;
    TextInput inputPickerSearch_{ L"Поиск версии…" };
    Button btnPickerClose_{ L"×", ButtonStyle::Ghost };

    struct VersionTile {
        std::string id;
        std::wstring tag;
        std::wstring sub;
        bool isSnapshot = false;
        AnimatedFloat hoverAnim{ 0.0f, 15.0f };
        D2D1_RECT_F bounds = D2D1::RectF(0, 0, 0, 0);
    };

    std::vector<VersionTile> allPickerVersions_;
    std::vector<VersionTile> filteredPickerVersions_;
    ScrollView pickerScrollView_;

    std::vector<VersionEntry> remoteManifest_;
    std::vector<FabricGameEntry> remoteFabricGames_;
    std::vector<std::string> aeroSupportedVersions_;
    std::mutex remoteMutex_;
    bool isManifestLoaded_ = false;
    bool isFabricGamesLoaded_ = false;
    bool isAeroInfoLoaded_ = false;
    bool aeroCatalogPremium_ = false;
    std::wstring aeroCatalogMessage_;
    struct AeroCatalog {
        std::vector<std::string> versions;
        bool valid = false;
        bool premium = false;
    };
    std::string aeroCatalogToken_ = "#unloaded";
    std::future<AeroCatalog> aeroCatalogRequest_;
    bool aeroCatalogRefreshQueued_ = false;
    bool isFetchingManifest_ = false;
    bool needRefreshPicker_ = false;

    // 5. Modal Rename Dialog
    bool isRenaming_ = false;
    std::string renameInstId_;
    TextInput inputRename_{ L"" };
    Button btnRenameSave_{ L"Сохранить", ButtonStyle::Primary };
    Button btnRenameCancel_{ L"Отмена", ButtonStyle::Ghost };
};

} // namespace aero
