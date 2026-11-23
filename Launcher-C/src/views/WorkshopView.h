#pragma once
#include "../ui/UIElement.h"
#include "../ui/TextInput.h"
#include "../ui/Button.h"
#include "../ui/ComboBox.h"
#include "../ui/Checkbox.h"
#include "../ui/ScrollView.h"
#include "../workshop/ModrinthClient.h"
#include <vector>
#include <string>
#include <mutex>
#include <filesystem>

namespace aero {

class WorkshopView : public UIElement {
public:
    WorkshopView();

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
    void UpdateCardBounds();
    void DoSearch();
    void InstallItem(const WorkshopItem& item);
    void RefreshInstalled();
    void ImportFile();
    void ToggleMod(const std::string& filename, bool currentlyEnabled);
    void DeleteInstalledFile(const std::string& kind, const std::string& filename);
    void OpenModModal(const WorkshopItem& item);
    void CloseModModal();
    void SyncVersionsAndLoaders();
    std::filesystem::path GetIconsCacheDir();

    // Top Header
    Button btnImportFile_{ L"Импортировать файл", ButtonStyle::Ghost };

    // Toolbar (.workshop-toolbar)
    // Row 1: Kind segmented
    Button btnTypeMod_{ L"Моды", ButtonStyle::Primary };
    Button btnTypeRP_{ L"Ресурспаки", ButtonStyle::Default };
    Button btnTypeShader_{ L"Шейдеры", ButtonStyle::Default };

    // Row 2: Search + Sort
    TextInput inputSearch_{ L"Поиск по мастерской…" };
    Button btnSearch_{ L"Найти", ButtonStyle::Primary };
    ComboBox comboSort_;

    // Row 3: Minecraft Version + Loader
    ComboBox comboMc_;
    ComboBox comboLoader_;

    // Row 4: Categories
    Button btnCatAll_{ L"Все", ButtonStyle::Primary };
    Button btnCatOpt_{ L"Оптимизация", ButtonStyle::Default };
    Button btnCatGfx_{ L"Графика", ButtonStyle::Default };
    Button btnCatUtil_{ L"Утилиты", ButtonStyle::Default };

    // Installed panel controls
    Button btnOpenInstalledDir_{ L"Открыть папку", ButtonStyle::Ghost };

    std::string activeKind_ = "mod";
    std::string activeCategory_ = "all";
    std::string activeSort_ = "downloads";
    std::string activeLoader_ = "auto";
    std::string activeMc_ = "";

    struct CardUI {
        WorkshopItem item;
        AnimatedFloat hoverAnim{ 0.0f, 15.0f };
        Button btnInstall{ L"Установить", ButtonStyle::Primary };
        Button btnDetails{ L"Подробнее", ButtonStyle::Ghost };
    };

    struct InstalledFileItem {
        std::string filename;
        std::wstring displayName;
        std::wstring sizeStr;
        std::string kind; // "mod", "resourcepack", "shader"
        bool isEnabled = true;
        Checkbox toggle;
        Button btnDelete{ L"Удалить", ButtonStyle::Danger };
    };

    std::vector<CardUI> cards_;
    std::vector<InstalledFileItem> installedFiles_;
    ScrollView scrollView_;
    bool isLoading_ = false;
    float animTime_ = 0.0f;

    std::mutex cardsMutex_;
    std::vector<WorkshopItem> pendingItems_;
    bool hasPendingItems_ = false;
    bool needRefreshInstalled_ = false;

    // Mod details modal (#mod-modal)
    bool isModModalOpen_ = false;
    WorkshopItem activeModalMod_;
    Button btnModModalClose_{ L"×", ButtonStyle::Ghost };
    Button btnModModalWeb_{ L"Страница мода ↗", ButtonStyle::Ghost };
    Button btnModModalInstall_{ L"Установить", ButtonStyle::Primary };
};

} // namespace aero
