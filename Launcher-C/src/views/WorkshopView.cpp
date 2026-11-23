#include "WorkshopView.h"
#include "../store/SettingsManager.h"
#include "../store/InstancesManager.h"
#include "../net/Downloader.h"
#include "../ui/Toast.h"
#include "../core/StringUtils.h"
#include <shellapi.h>
#include <commdlg.h>
#include <thread>
#include <filesystem>
#include <algorithm>

namespace aero {

namespace fs = std::filesystem;

WorkshopView::WorkshopView() {
    btnImportFile_.SetOnClick([this]() { ImportFile(); });

    // Row 1: Segmented Kind
    btnTypeMod_.SetOnClick([this]() {
        activeKind_ = "mod";
        btnTypeMod_.SetStyle(ButtonStyle::Primary);
        btnTypeRP_.SetStyle(ButtonStyle::Default);
        btnTypeShader_.SetStyle(ButtonStyle::Default);
        DoSearch();
    });

    btnTypeRP_.SetOnClick([this]() {
        activeKind_ = "resourcepack";
        btnTypeMod_.SetStyle(ButtonStyle::Default);
        btnTypeRP_.SetStyle(ButtonStyle::Primary);
        btnTypeShader_.SetStyle(ButtonStyle::Default);
        DoSearch();
    });

    btnTypeShader_.SetOnClick([this]() {
        activeKind_ = "shader";
        btnTypeMod_.SetStyle(ButtonStyle::Default);
        btnTypeRP_.SetStyle(ButtonStyle::Default);
        btnTypeShader_.SetStyle(ButtonStyle::Primary);
        DoSearch();
    });

    // Row 2: Search + Sort
    btnSearch_.SetOnClick([this]() { DoSearch(); });

    comboSort_.SetItems({ L"Популярные", L"Обновлённые", L"Избранное" });
    comboSort_.SetSelectedIndex(0);
    comboSort_.SetOnSelect([this](int idx, const std::wstring&) {
        if (idx == 0) activeSort_ = "downloads";
        else if (idx == 1) activeSort_ = "updated";
        else activeSort_ = "follows";
        DoSearch();
    });

    comboMc_.SetOnSelect([this](int idx, const std::wstring& item) {
        if (idx == 0) {
            activeMc_ = "";
        } else {
            std::string s = WideToUtf8(item);
            if (s.rfind("MC ", 0) == 0) s = s.substr(3);
            size_t space = s.find(' ');
            if (space != std::string::npos) s = s.substr(0, space);
            activeMc_ = s;
        }
        DoSearch();
    });

    comboLoader_.SetItems({ L"Загрузчик: авто", L"Fabric" });
    comboLoader_.SetSelectedIndex(0);
    comboLoader_.SetOnSelect([this](int idx, const std::wstring&) {
        if (idx == 0) activeLoader_ = "auto";
        else activeLoader_ = "fabric";
        DoSearch();
    });

    // Row 4: Categories
    btnCatAll_.SetOnClick([this]() {
        activeCategory_ = "all";
        btnCatAll_.SetStyle(ButtonStyle::Primary);
        btnCatOpt_.SetStyle(ButtonStyle::Default);
        btnCatGfx_.SetStyle(ButtonStyle::Default);
        btnCatUtil_.SetStyle(ButtonStyle::Default);
        DoSearch();
    });

    btnCatOpt_.SetOnClick([this]() {
        activeCategory_ = "optimization";
        btnCatAll_.SetStyle(ButtonStyle::Default);
        btnCatOpt_.SetStyle(ButtonStyle::Primary);
        btnCatGfx_.SetStyle(ButtonStyle::Default);
        btnCatUtil_.SetStyle(ButtonStyle::Default);
        DoSearch();
    });

    btnCatGfx_.SetOnClick([this]() {
        activeCategory_ = "shaders";
        btnCatAll_.SetStyle(ButtonStyle::Default);
        btnCatOpt_.SetStyle(ButtonStyle::Default);
        btnCatGfx_.SetStyle(ButtonStyle::Primary);
        btnCatUtil_.SetStyle(ButtonStyle::Default);
        DoSearch();
    });

    btnCatUtil_.SetOnClick([this]() {
        activeCategory_ = "utility";
        btnCatAll_.SetStyle(ButtonStyle::Default);
        btnCatOpt_.SetStyle(ButtonStyle::Default);
        btnCatGfx_.SetStyle(ButtonStyle::Default);
        btnCatUtil_.SetStyle(ButtonStyle::Primary);
        DoSearch();
    });

    // Installed panel folder button
    btnOpenInstalledDir_.SetOnClick([this]() {
        const auto inst = InstancesManager::Instance().GetActiveInstance();
        fs::path p = (inst && !inst->path.empty()) ? fs::path(inst->path) : fs::path(SettingsManager::Instance().Get().gameDir);
        ShellExecuteW(nullptr, L"open", p.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
    });

    // Details modal buttons
    btnModModalClose_.SetOnClick([this]() { CloseModModal(); });
    btnModModalWeb_.SetOnClick([this]() {
        if (!activeModalMod_.slug.empty()) {
            std::string sub = (activeKind_ == "mod") ? "mod" : ((activeKind_ == "resourcepack") ? "resourcepack" : "shader");
            std::wstring url = L"https://modrinth.com/" + Utf8ToWide(sub) + L"/" + Utf8ToWide(activeModalMod_.slug);
            ShellExecuteW(nullptr, L"open", url.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        }
    });
    btnModModalInstall_.SetOnClick([this]() {
        InstallItem(activeModalMod_);
        CloseModModal();
    });

    SyncVersionsAndLoaders();
    RefreshInstalled();
    DoSearch();
}

fs::path WorkshopView::GetIconsCacheDir() {
    auto& s = SettingsManager::Instance().Get();
    fs::path base = s.gameDir.empty() ? fs::current_path() : fs::path(s.gameDir);
    fs::path dir = base / L"cache" / L"icons";
    std::error_code ec;
    fs::create_directories(dir, ec);
    return dir;
}

void WorkshopView::SyncVersionsAndLoaders() {
    const auto inst = InstancesManager::Instance().GetActiveInstance();
    std::string currentMc = inst ? inst->mcVersion : "";
    std::string currentLoader = inst ? inst->loader : "";
    std::transform(currentLoader.begin(), currentLoader.end(), currentLoader.begin(), [](unsigned char c) {
        return static_cast<char>(std::tolower(c));
    });

    std::vector<std::wstring> items = { L"Все версии" };
    std::vector<std::string> seen;

    if (!currentMc.empty()) {
        items.push_back(Utf8ToWide(currentMc) + L" (текущая)");
        seen.push_back(currentMc);
    }

    const auto& allInst = InstancesManager::Instance().GetInstances();
    for (const auto& i : allInst) {
        if (!i.mcVersion.empty() && std::find(seen.begin(), seen.end(), i.mcVersion) == seen.end()) {
            items.push_back(Utf8ToWide(i.mcVersion));
            seen.push_back(i.mcVersion);
        }
    }

    std::vector<std::string> standardReleases = {
        "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
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
    for (const auto& r : standardReleases) {
        if (std::find(seen.begin(), seen.end(), r) == seen.end()) {
            items.push_back(Utf8ToWide(r));
            seen.push_back(r);
        }
    }

    comboMc_.SetItems(items);

    if (!currentMc.empty()) {
        comboMc_.SetSelectedIndex(1);
        activeMc_ = currentMc;
    } else {
        comboMc_.SetSelectedIndex(0);
        activeMc_ = "";
    }

    if (currentLoader == "fabric") {
        comboLoader_.SetSelectedIndex(1);
        activeLoader_ = "fabric";
    } else if (currentLoader == "forge") {
        comboLoader_.SetSelectedIndex(2);
        activeLoader_ = "forge";
    } else {
        comboLoader_.SetSelectedIndex(0);
        activeLoader_ = "auto";
    }
}

void WorkshopView::OpenModModal(const WorkshopItem& item) {
    isModModalOpen_ = true;
    activeModalMod_ = item;
}

void WorkshopView::CloseModModal() {
    isModModalOpen_ = false;
}

void WorkshopView::RefreshData() {
    SyncVersionsAndLoaders();
    RefreshInstalled();
    DoSearch();
}

void WorkshopView::RefreshInstalled() {
    installedFiles_.clear();
    const auto inst = InstancesManager::Instance().GetActiveInstance();
    fs::path baseDir = (inst && !inst->path.empty()) ? fs::path(inst->path) : fs::path(SettingsManager::Instance().Get().gameDir);

    auto scanDir = [this](const fs::path& dir, const std::string& kind) {
        if (!fs::exists(dir)) return;
        std::error_code ec;
        for (const auto& entry : fs::directory_iterator(dir, ec)) {
            if (!entry.is_regular_file()) continue;
            std::string fn = entry.path().filename().string();
            std::string ext = entry.path().extension().string();
            std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });

            InstalledFileItem item;
            item.filename = fn;
            item.kind = kind;

            if (kind == "mod") {
                if (ext == ".jar") {
                    item.isEnabled = true;
                    item.displayName = entry.path().stem().wstring();
                } else if (fn.length() > 9 && fn.substr(fn.length() - 9) == ".disabled") {
                    item.isEnabled = false;
                    std::string stem = fn.substr(0, fn.length() - 9);
                    size_t dotPos = stem.rfind('.');
                    if (dotPos != std::string::npos) stem = stem.substr(0, dotPos);
                    item.displayName = Utf8ToWide(stem);
                } else {
                    continue;
                }
            } else if (kind == "resourcepack" || kind == "shader") {
                if (ext != ".zip") continue;
                item.isEnabled = true;
                item.displayName = entry.path().stem().wstring();
            } else {
                continue;
            }

            size_t sz = 0;
            std::error_code szEc;
            sz = entry.file_size(szEc);
            if (sz > 1024 * 1024) {
                wchar_t buf[32];
                swprintf_s(buf, L"%.1f МБ", sz / (1024.0 * 1024.0));
                item.sizeStr = buf;
            } else {
                wchar_t buf[32];
                swprintf_s(buf, L"%.0f КБ", sz / 1024.0);
                item.sizeStr = buf;
            }

            item.toggle.SetChecked(item.isEnabled);
            std::string capFn = fn;
            bool capEnabled = item.isEnabled;
            item.toggle.SetOnToggle([this, capFn, capEnabled](bool) {
                ToggleMod(capFn, capEnabled);
            });

            std::string capKind = kind;
            item.btnDelete.SetOnClick([this, capKind, capFn]() {
                DeleteInstalledFile(capKind, capFn);
            });

            installedFiles_.push_back(item);
        }
    };

    scanDir(baseDir / L"mods", "mod");
    scanDir(baseDir / L"resourcepacks", "resourcepack");
    scanDir(baseDir / L"shaderpacks", "shader");

    UpdateCardBounds();
}

void WorkshopView::ToggleMod(const std::string& filename, bool currentlyEnabled) {
    const auto inst = InstancesManager::Instance().GetActiveInstance();
    fs::path baseDir = (inst && !inst->path.empty()) ? fs::path(inst->path) : fs::path(SettingsManager::Instance().Get().gameDir);
    fs::path modsDir = baseDir / L"mods";

    fs::path src = modsDir / Utf8ToWide(filename);
    std::string newName;
    if (currentlyEnabled) {
        newName = filename + ".disabled";
    } else {
        if (filename.length() > 9 && filename.substr(filename.length() - 9) == ".disabled") {
            newName = filename.substr(0, filename.length() - 9);
        } else {
            newName = filename;
        }
    }
    fs::path dst = modsDir / Utf8ToWide(newName);
    std::error_code ec;
    fs::rename(src, dst, ec);
    if (!ec) {
        Toast::Instance().Show(currentlyEnabled ? L"Мод отключён (.disabled)" : L"Мод включён", ToastKind::Success);
        RefreshInstalled();
    } else {
        Toast::Instance().Show(L"Ошибка переключения статуса мода", ToastKind::Error);
    }
}

void WorkshopView::DeleteInstalledFile(const std::string& kind, const std::string& filename) {
    const auto inst = InstancesManager::Instance().GetActiveInstance();
    fs::path baseDir = (inst && !inst->path.empty()) ? fs::path(inst->path) : fs::path(SettingsManager::Instance().Get().gameDir);
    fs::path targetDir;
    if (kind == "resourcepack") targetDir = baseDir / L"resourcepacks";
    else if (kind == "shader") targetDir = baseDir / L"shaderpacks";
    else targetDir = baseDir / L"mods";

    fs::path p = targetDir / Utf8ToWide(filename);
    std::error_code ec;
    fs::remove(p, ec);
    if (!ec) {
        Toast::Instance().Show(L"Файл удалён", ToastKind::Info);
        RefreshInstalled();
    } else {
        Toast::Instance().Show(L"Не удалось удалить файл", ToastKind::Error);
    }
}

void WorkshopView::ImportFile() {
    OPENFILENAMEW ofn = { 0 };
    wchar_t szFile[MAX_PATH] = { 0 };
    ofn.lStructSize = sizeof(ofn);
    ofn.lpstrFile = szFile;
    ofn.nMaxFile = sizeof(szFile) / sizeof(wchar_t);
    ofn.lpstrFilter = L"Файлы Minecraft (*.jar;*.zip)\0*.jar;*.zip\0Все файлы (*.*)\0*.*\0";
    ofn.nFilterIndex = 1;
    ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST;

    if (GetOpenFileNameW(&ofn)) {
        auto res = InstancesManager::Instance().ImportContentFile(szFile);
        if (res.success) {
            std::wstring catName = (res.kind == "shader") ? L"Шейдеры" : ((res.kind == "resourcepack") ? L"Ресурспак" : L"Мод");
            Toast::Instance().Show(catName + L" «" + res.fileName + L"» установлен в " + Utf8ToWide(res.instanceName), ToastKind::Success);
            RefreshInstalled();
        } else {
            Toast::Instance().Show(Utf8ToWide(res.errorMessage.empty() ? "Ошибка импорта файла" : res.errorMessage), ToastKind::Error);
        }
    }
}

void WorkshopView::DoSearch() {
    if (isLoading_) return;
    isLoading_ = true;

    std::string q = WideToUtf8(inputSearch_.GetText());
    if (q == "Поиск по мастерской…") q = "";
    std::string kind = activeKind_;
    std::string cat = activeCategory_;
    std::string sort = activeSort_;
    std::string mc = activeMc_;
    std::string loader = (activeKind_ == "mod") ? (activeLoader_ == "auto" ? "" : activeLoader_) : "";

    std::thread([this, q, kind, cat, sort, mc, loader]() {
        auto res = ModrinthClient::Search(q, kind, mc, loader, cat, 24, sort);
        {
            std::lock_guard<std::mutex> lock(cardsMutex_);
            pendingItems_ = res;
            hasPendingItems_ = true;
        }
        isLoading_ = false;

        // Background download for mod icons
        fs::path cDir = GetIconsCacheDir();
        for (const auto& item : res) {
            if (!item.iconUrl.empty()) {
                fs::path iconFile = cDir / (Utf8ToWide(item.id) + L".png");
                if (!fs::exists(iconFile)) {
                    DownloadOptions opt;
                    opt.url = item.iconUrl;
                    opt.destPath = iconFile.wstring();
                    Downloader::DownloadFile(opt, nullptr);
                }
            }
        }
    }).detach();
}

void WorkshopView::InstallItem(const WorkshopItem& item) {
    auto& settings = SettingsManager::Instance().Get();
    const auto inst = InstancesManager::Instance().GetActiveInstance();

    fs::path targetDir;
    if (inst && !inst->path.empty()) {
        targetDir = fs::path(inst->path);
    } else {
        targetDir = fs::path(settings.gameDir);
    }

    if (activeKind_ == "resourcepack") targetDir /= L"resourcepacks";
    else if (activeKind_ == "shader") targetDir /= L"shaderpacks";
    else targetDir /= L"mods";

    std::error_code ec;
    fs::create_directories(targetDir, ec);

    Toast::Instance().Show(L"Скачивание «" + Utf8ToWide(item.title) + L"»...", ToastKind::Info);

    std::string mc = !activeMc_.empty() ? activeMc_ : (inst && !inst->mcVersion.empty() ? inst->mcVersion : "1.21.4");
    std::string loader = (activeLoader_ != "auto" && !activeLoader_.empty()) ? activeLoader_ : (inst && !inst->loader.empty() ? inst->loader : "fabric");

    std::thread([this, item, targetDir, mc, loader]() {
        std::string err;
        bool ok = ModrinthClient::InstallItem(item.id, targetDir.wstring(), mc, loader, nullptr, &err);
        if (ok) {
            Toast::Instance().Show(L"Установлено: «" + Utf8ToWide(item.title) + L"»", ToastKind::Success);
            needRefreshInstalled_ = true;
        } else {
            Toast::Instance().Show(L"Ошибка установки: " + Utf8ToWide(err.empty() ? "нет совместимых версий" : err), ToastKind::Error);
        }
    }).detach();
}

void WorkshopView::UpdateCardBounds() {
    float startX = 36.0f;
    float contentW = (bounds_.right > 72.0f) ? (bounds_.right - 72.0f) : 700.0f;
    float scrollStartY = 226.0f;
    float startY = scrollStartY + 4.0f;
    float cardW = (contentW - 22.0f) / 3.0f;
    float cardH = 145.0f;

    for (size_t i = 0; i < cards_.size(); ++i) {
        int col = static_cast<int>(i % 3);
        int row = static_cast<int>(i / 3);

        float cx = startX + col * (cardW + 11.0f);
        float cy = startY + row * (cardH + 11.0f);

        cards_[i].btnDetails.SetBounds(cx + cardW - 176.0f, cy + cardH - 34.0f, 80.0f, 26.0f);
        cards_[i].btnInstall.SetBounds(cx + cardW - 90.0f, cy + cardH - 34.0f, 80.0f, 26.0f);
    }

    // Installed rows buttons
    int rows = (cards_.empty() ? 0 : static_cast<int>((cards_.size() + 2) / 3));
    float instStartY = cards_.empty() ? (startY + 70.0f) : (startY + rows * (cardH + 11.0f) + 24.0f);
    btnOpenInstalledDir_.SetBounds(startX + contentW - 120.0f, instStartY + 4.0f, 120.0f, 28.0f);

    float listY = instStartY + 44.0f;
    for (size_t i = 0; i < installedFiles_.size(); ++i) {
        float rowY = listY + i * 54.0f;
        installedFiles_[i].toggle.SetBounds(startX + 14.0f, rowY + 13.0f, 38.0f, 22.0f);
        installedFiles_[i].btnDelete.SetBounds(startX + contentW - 86.0f, rowY + 11.0f, 74.0f, 26.0f);
    }

    float footerY = listY + (installedFiles_.empty() ? 50.0f : installedFiles_.size() * 54.0f) + 10.0f;
    float totalH = footerY + 30.0f - scrollStartY;
    scrollView_.SetContentHeight(totalH);
}

void WorkshopView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);

    float startX = 36.0f;
    float contentW = width - 72.0f;

    btnImportFile_.SetBounds(startX + contentW - 165.0f, 22.0f, 165.0f, 32.0f);

    // Toolbar (.workshop-toolbar)
    float tbX = startX;
    float tbY = 62.0f;
    float tbW = contentW;

    // Row 1: Kind segment
    float kindW = 100.0f;
    btnTypeMod_.SetBounds(tbX + 12.0f, tbY + 10.0f, kindW, 28.0f);
    btnTypeRP_.SetBounds(tbX + 12.0f + kindW + 6.0f, tbY + 10.0f, kindW + 10.0f, 28.0f);
    btnTypeShader_.SetBounds(tbX + 12.0f + (kindW + 6.0f) * 2 + 10.0f, tbY + 10.0f, kindW, 28.0f);

    // Row 2: Search + Sort
    float sortW = 140.0f;
    float searchBtnW = 80.0f;
    float searchInputW = tbW - 24.0f - searchBtnW - 8.0f - sortW - 8.0f;
    inputSearch_.SetBounds(tbX + 12.0f, tbY + 44.0f, searchInputW, 30.0f);
    btnSearch_.SetBounds(tbX + 12.0f + searchInputW + 8.0f, tbY + 44.0f, searchBtnW, 30.0f);
    comboSort_.SetBounds(tbX + 12.0f + searchInputW + 8.0f + searchBtnW + 8.0f, tbY + 44.0f, sortW, 30.0f);

    // Row 3: MC Version + Loader
    float filterW = (tbW - 32.0f) * 0.5f;
    comboMc_.SetBounds(tbX + 12.0f, tbY + 80.0f, filterW, 30.0f);
    comboLoader_.SetBounds(tbX + 12.0f + filterW + 8.0f, tbY + 80.0f, filterW, 30.0f);

    // Row 4: Category pills
    float pillW = 86.0f;
    btnCatAll_.SetBounds(tbX + 12.0f, tbY + 116.0f, 54.0f, 24.0f);
    btnCatOpt_.SetBounds(tbX + 12.0f + 54.0f + 6.0f, tbY + 116.0f, pillW + 16.0f, 24.0f);
    btnCatGfx_.SetBounds(tbX + 12.0f + 54.0f + 6.0f + pillW + 22.0f, tbY + 116.0f, pillW, 24.0f);
    btnCatUtil_.SetBounds(tbX + 12.0f + 54.0f + 6.0f + pillW * 2 + 28.0f, tbY + 116.0f, pillW, 24.0f);

    // Scroll area below toolbar
    float scrollStartY = tbY + 152.0f + 12.0f;
    scrollView_.SetBounds(startX, scrollStartY, contentW, height - scrollStartY - 16.0f);

    UpdateCardBounds();

    // Mod details modal sizing
    float mW = (std::min)(width - 60.0f, 500.0f);
    float mH = (std::min)(height - 80.0f, 360.0f);
    float mX = (width - mW) * 0.5f;
    float mY = (height - mH) * 0.5f;
    btnModModalClose_.SetBounds(mX + mW - 40.0f, mY + 14.0f, 28.0f, 28.0f);
    btnModModalWeb_.SetBounds(mX + mW - 240.0f, mY + mH - 46.0f, 130.0f, 32.0f);
    btnModModalInstall_.SetBounds(mX + mW - 102.0f, mY + mH - 46.0f, 92.0f, 32.0f);
}

void WorkshopView::Update(float dt) {
    animTime_ += dt;
    if (needRefreshInstalled_) {
        needRefreshInstalled_ = false;
        RefreshInstalled();
    }

    if (hasPendingItems_) {
        std::lock_guard<std::mutex> lock(cardsMutex_);
        cards_.clear();
        for (const auto& item : pendingItems_) {
            CardUI c;
            c.item = item;
            c.btnInstall.SetOnClick([this, item]() { InstallItem(item); });
            c.btnDetails.SetOnClick([this, item]() { OpenModModal(item); });
            cards_.push_back(c);
        }
        pendingItems_.clear();
        hasPendingItems_ = false;
        UpdateCardBounds();
    }

    if (isModModalOpen_) {
        btnModModalClose_.Update(dt);
        btnModModalWeb_.Update(dt);
        btnModModalInstall_.Update(dt);
        return;
    }

    btnImportFile_.Update(dt);
    btnTypeMod_.Update(dt);
    btnTypeRP_.Update(dt);
    btnTypeShader_.Update(dt);
    inputSearch_.Update(dt);
    btnSearch_.Update(dt);
    comboSort_.Update(dt);
    comboMc_.Update(dt);
    if (activeKind_ == "mod") comboLoader_.Update(dt);

    btnCatAll_.Update(dt);
    btnCatOpt_.Update(dt);
    btnCatGfx_.Update(dt);
    btnCatUtil_.Update(dt);

    btnOpenInstalledDir_.Update(dt);

    scrollView_.Update(dt);

    for (auto& c : cards_) {
        c.hoverAnim.Update(dt);
        c.btnInstall.Update(dt);
        c.btnDetails.Update(dt);
    }

    for (auto& f : installedFiles_) {
        f.toggle.Update(dt);
        f.btnDelete.Update(dt);
    }
}

bool WorkshopView::OnMouseMove(float x, float y) {
    if (isModModalOpen_) {
        bool h = btnModModalClose_.OnMouseMove(x, y);
        h |= btnModModalWeb_.OnMouseMove(x, y);
        h |= btnModModalInstall_.OnMouseMove(x, y);
        return h;
    }

    if (comboSort_.IsExpanded()) return comboSort_.OnMouseMove(x, y);
    if (comboMc_.IsExpanded()) return comboMc_.OnMouseMove(x, y);
    if (activeKind_ == "mod" && comboLoader_.IsExpanded()) return comboLoader_.OnMouseMove(x, y);

    bool handled = btnImportFile_.OnMouseMove(x, y);
    handled |= btnTypeMod_.OnMouseMove(x, y);
    handled |= btnTypeRP_.OnMouseMove(x, y);
    handled |= btnTypeShader_.OnMouseMove(x, y);
    handled |= inputSearch_.OnMouseMove(x, y);
    handled |= btnSearch_.OnMouseMove(x, y);
    handled |= comboSort_.OnMouseMove(x, y);
    handled |= comboMc_.OnMouseMove(x, y);
    if (activeKind_ == "mod") handled |= comboLoader_.OnMouseMove(x, y);
    handled |= btnCatAll_.OnMouseMove(x, y);
    handled |= btnCatOpt_.OnMouseMove(x, y);
    handled |= btnCatGfx_.OnMouseMove(x, y);
    handled |= btnCatUtil_.OnMouseMove(x, y);
    handled |= btnOpenInstalledDir_.OnMouseMove(x, y);

    D2D1_RECT_F sBounds = scrollView_.GetBounds();
    if (x >= sBounds.left && x <= sBounds.right && y >= sBounds.top && y <= sBounds.bottom) {
        float scroll = scrollView_.GetScrollOffset();
        float sy = y + scroll;

        float startX = 36.0f;
        float contentW = (bounds_.right > 72.0f) ? (bounds_.right - 72.0f) : 700.0f;
        float scrollStartY = 226.0f;
        float startY = scrollStartY + 4.0f;
        float cardW = (contentW - 22.0f) / 3.0f;
        float cardH = 145.0f;

        for (size_t i = 0; i < cards_.size(); ++i) {
            auto& c = cards_[i];
            int col = static_cast<int>(i % 3);
            int row = static_cast<int>(i / 3);
            float cx = startX + col * (cardW + 11.0f);
            float cy = startY + row * (cardH + 11.0f);

            bool hovered = (x >= cx && x <= cx + cardW && sy >= cy && sy <= cy + cardH);
            c.hoverAnim.SetTarget(hovered ? 1.0f : 0.0f);

            handled |= c.btnDetails.OnMouseMove(x, sy);
            handled |= c.btnInstall.OnMouseMove(x, sy);
        }

        for (auto& f : installedFiles_) {
            handled |= f.toggle.OnMouseMove(x, sy);
            handled |= f.btnDelete.OnMouseMove(x, sy);
        }
    }

    handled |= scrollView_.OnMouseMove(x, y);
    return handled;
}

bool WorkshopView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (isModModalOpen_) {
        if (btnModModalClose_.OnMouseButton(x, y, btn, down)) return true;
        if (btnModModalWeb_.OnMouseButton(x, y, btn, down)) return true;
        if (btnModModalInstall_.OnMouseButton(x, y, btn, down)) return true;

        float mW = (std::min)(bounds_.right - 60.0f, 500.0f);
        float mH = (std::min)(bounds_.bottom - 80.0f, 360.0f);
        float mX = (bounds_.right - mW) * 0.5f;
        float mY = (bounds_.bottom - mH) * 0.5f;
        if (down && (x < mX || x > mX + mW || y < mY || y > mY + mH)) {
            CloseModModal();
            return true;
        }
        return true;
    }

    // Comboboxes expanded take priority
    if (comboSort_.IsExpanded() && comboSort_.OnMouseButton(x, y, btn, down)) return true;
    if (comboMc_.IsExpanded() && comboMc_.OnMouseButton(x, y, btn, down)) return true;
    if (activeKind_ == "mod" && comboLoader_.IsExpanded() && comboLoader_.OnMouseButton(x, y, btn, down)) return true;

    if (btnImportFile_.OnMouseButton(x, y, btn, down)) return true;
    if (btnTypeMod_.OnMouseButton(x, y, btn, down)) return true;
    if (btnTypeRP_.OnMouseButton(x, y, btn, down)) return true;
    if (btnTypeShader_.OnMouseButton(x, y, btn, down)) return true;

    if (inputSearch_.OnMouseButton(x, y, btn, down)) return true;
    if (btnSearch_.OnMouseButton(x, y, btn, down)) return true;
    if (comboSort_.OnMouseButton(x, y, btn, down)) return true;
    if (comboMc_.OnMouseButton(x, y, btn, down)) return true;
    if (activeKind_ == "mod" && comboLoader_.OnMouseButton(x, y, btn, down)) return true;

    if (btnCatAll_.OnMouseButton(x, y, btn, down)) return true;
    if (btnCatOpt_.OnMouseButton(x, y, btn, down)) return true;
    if (btnCatGfx_.OnMouseButton(x, y, btn, down)) return true;
    if (btnCatUtil_.OnMouseButton(x, y, btn, down)) return true;

    D2D1_RECT_F sBounds = scrollView_.GetBounds();
    if (x >= sBounds.left && x <= sBounds.right && y >= sBounds.top && y <= sBounds.bottom) {
        float scroll = scrollView_.GetScrollOffset();
        float sy = y + scroll;

        if (btnOpenInstalledDir_.OnMouseButton(x, sy, btn, down)) return true;

        for (auto& c : cards_) {
            if (c.btnDetails.OnMouseButton(x, sy, btn, down)) return true;
            if (c.btnInstall.OnMouseButton(x, sy, btn, down)) return true;
        }

        for (auto& f : installedFiles_) {
            if (f.toggle.OnMouseButton(x, sy, btn, down)) return true;
            if (f.btnDelete.OnMouseButton(x, sy, btn, down)) return true;
        }
    }

    return scrollView_.OnMouseButton(x, y, btn, down);
}

bool WorkshopView::OnMouseWheel(float x, float y, float delta) {
    if (isModModalOpen_) return true;
    if (comboSort_.IsExpanded() && comboSort_.OnMouseWheel(x, y, delta)) return true;
    if (comboMc_.IsExpanded() && comboMc_.OnMouseWheel(x, y, delta)) return true;
    if (activeKind_ == "mod" && comboLoader_.IsExpanded() && comboLoader_.OnMouseWheel(x, y, delta)) return true;
    return scrollView_.OnMouseWheel(x, y, delta);
}

bool WorkshopView::OnChar(wchar_t ch) {
    if (isModModalOpen_) return false;
    return inputSearch_.OnChar(ch);
}

bool WorkshopView::OnKey(int vk, bool down) {
    if (isModModalOpen_) {
        if (down && vk == VK_ESCAPE) {
            CloseModModal();
            return true;
        }
        return false;
    }

    if (inputSearch_.IsFocused()) {
        if (vk == VK_RETURN && down) {
            DoSearch();
            return true;
        }
        return inputSearch_.OnKey(vk, down);
    }
    return false;
}

void WorkshopView::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    float startX = 36.0f;
    float contentW = bounds_.right - 72.0f;

    // View Head
    r.DrawString(L"Мастерская", D2D1::RectF(startX, 28.0f, startX + 300.0f, 52.0f), theme.text, 20.0f, FontStyle::Bold);
    r.DrawString(L"Моды, ресурспаки и шейдеры из Modrinth", D2D1::RectF(startX + 140.0f, 32.0f, startX + contentW - 180.0f, 52.0f), theme.muted, 11.0f);

    btnImportFile_.Render(r);

    // Toolbar Container (.workshop-toolbar)
    float tbY = 62.0f;
    float tbH = 152.0f;
    D2D1_RECT_F tbRect = D2D1::RectF(startX, tbY, startX + contentW, tbY + tbH);
    r.FillRoundedRect(tbRect, 8.0f, Color::FromRgba(32, 35, 32, 0.68f));
    r.DrawRoundedRect(tbRect, 8.0f, theme.line, 1.0f);

    btnTypeMod_.Render(r);
    btnTypeRP_.Render(r);
    btnTypeShader_.Render(r);

    inputSearch_.Render(r);
    btnSearch_.Render(r);
    comboSort_.Render(r);

    comboMc_.Render(r);
    if (activeKind_ == "mod") {
        comboLoader_.Render(r);
    }

    btnCatAll_.Render(r);
    btnCatOpt_.Render(r);
    btnCatGfx_.Render(r);
    btnCatUtil_.Render(r);

    // Scrollable Catalog Area
    scrollView_.BeginRender(r);

    float scrollStartY = 226.0f;
    float startY = scrollStartY + 4.0f;
    float cardW = (contentW - 22.0f) / 3.0f;
    float cardH = 145.0f;
    fs::path cDir = GetIconsCacheDir();

    if (cards_.empty() && !isLoading_) {
        D2D1_RECT_F eRect = D2D1::RectF(startX, startY, startX + contentW, startY + 60.0f);
        r.FillRoundedRect(eRect, 8.0f, Color::FromRgba(32, 35, 32, 0.5f));
        r.DrawString(L"Ничего не нашлось. Попробуй изменить запрос или версию.", eRect, theme.muted, 11.5f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    }

    for (size_t i = 0; i < cards_.size(); ++i) {
        auto& c = cards_[i];
        float h = c.hoverAnim.Get();
        int col = static_cast<int>(i % 3);
        int row = static_cast<int>(i / 3);

        float cx = startX + col * (cardW + 11.0f);
        float cy = startY + row * (cardH + 11.0f);
        D2D1_RECT_F itemRect = D2D1::RectF(cx, cy, cx + cardW, cy + cardH);

        r.FillRoundedRect(itemRect, 8.0f, Color::FromRgba(32, 35, 32, 0.85f + h * 0.10f));
        r.DrawRoundedRect(itemRect, 8.0f, anim::LerpColor(theme.line, theme.accent2, h * 0.6f), h > 0.1f ? 1.5f : 1.0f);

        // Icon box (42x42 rounded 8px) with real Modrinth image if cached
        D2D1_RECT_F iconRect = D2D1::RectF(cx + 12.0f, cy + 12.0f, cx + 54.0f, cy + 54.0f);
        fs::path iconPath = cDir / (Utf8ToWide(c.item.id) + L".png");
        bool drewIcon = false;
        if (fs::exists(iconPath)) {
            ID2D1Bitmap* bmp = r.LoadBitmapFromFile(iconPath.wstring());
            if (bmp) {
                r.PushClip(iconRect);
                r.DrawBitmap(bmp, iconRect);
                r.PopClip();
                r.DrawRoundedRect(iconRect, 8.0f, Color::FromRgba(240, 183, 130, 0.35f), 1.0f);
                drewIcon = true;
            }
        }

        if (!drewIcon) {
            D2D1_COLOR_F a1 = (activeKind_ == "mod") ? Color::FromRgba(207, 189, 154, 0.7f) : ((activeKind_ == "resourcepack") ? Color::FromRgba(56, 189, 248, 0.7f) : Color::FromRgba(16, 185, 129, 0.7f));
            D2D1_COLOR_F a2 = Color::FromRgba(32, 35, 32, 0.85f);
            r.FillGradientRoundedRect(iconRect, 8.0f, a1, a2, false);
            r.DrawRoundedRect(iconRect, 8.0f, Color::FromRgba(240, 183, 130, 0.3f), 1.0f);

            std::wstring initial = c.item.title.empty() ? L"M" : Utf8ToWide(c.item.title.substr(0, 1));
            r.DrawString(initial, iconRect, Color::FromHex(0xffffff), 18.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
        }

        // Title + Downloads pill
        r.DrawString(Utf8ToWide(c.item.title), D2D1::RectF(cx + 62.0f, cy + 12.0f, cx + cardW - 75.0f, cy + 32.0f), theme.text, 12.0f, FontStyle::Bold);

        std::wstring dlStr;
        if (c.item.downloads >= 1000000) {
            wchar_t buf[32];
            swprintf_s(buf, L"%.1fM ⬇", c.item.downloads / 1000000.0);
            dlStr = buf;
        } else if (c.item.downloads >= 1000) {
            wchar_t buf[32];
            swprintf_s(buf, L"%.0fK ⬇", c.item.downloads / 1000.0);
            dlStr = buf;
        } else {
            dlStr = std::to_wstring(c.item.downloads) + L" ⬇";
        }
        D2D1_RECT_F dlPill = D2D1::RectF(cx + cardW - 70.0f, cy + 12.0f, cx + cardW - 12.0f, cy + 28.0f);
        r.FillRoundedRect(dlPill, 4.0f, Color::FromRgba(178, 199, 142, 0.12f));
        r.DrawRoundedRect(dlPill, 4.0f, Color::FromRgba(178, 199, 142, 0.35f), 1.0f);
        r.DrawString(dlStr, dlPill, theme.mint, 9.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

        // Description snippet
        std::wstring desc = Utf8ToWide(c.item.description);
        if (desc.length() > 68) desc = desc.substr(0, 65) + L"...";
        r.DrawString(desc, D2D1::RectF(cx + 62.0f, cy + 32.0f, cx + cardW - 12.0f, cy + 68.0f), theme.muted, 9.5f, FontStyle::Regular);

        // Meta line: Author
        std::wstring metaAuthor = L"Автор: " + Utf8ToWide(c.item.author.empty() ? "—" : c.item.author);
        r.DrawString(metaAuthor, D2D1::RectF(cx + 12.0f, cy + 74.0f, cx + cardW - 12.0f, cy + 92.0f), Color::FromHex(0xb4bdaa), 9.5f);

        c.btnDetails.Render(r);
        c.btnInstall.Render(r);
    }

    // Installed content panel (.panel.installed-panel)
    int numRows = (cards_.empty() ? 0 : static_cast<int>((cards_.size() + 2) / 3));
    float instStartY = cards_.empty() ? (startY + 70.0f) : (startY + numRows * (cardH + 11.0f) + 24.0f);

    r.DrawString(L"Установлено в сборку", D2D1::RectF(startX, instStartY + 4.0f, startX + 300.0f, instStartY + 28.0f), theme.text, 15.0f, FontStyle::Bold);
    btnOpenInstalledDir_.Render(r);

    float listY = instStartY + 44.0f;
    if (installedFiles_.empty()) {
        D2D1_RECT_F emptyBox = D2D1::RectF(startX, listY, startX + contentW, listY + 50.0f);
        r.FillRoundedRect(emptyBox, 8.0f, Color::FromRgba(32, 35, 32, 0.5f));
        r.DrawRoundedRect(emptyBox, 8.0f, theme.line, 1.0f);
        r.DrawString(L"В этой сборке пока нет установленных файлов.", emptyBox, theme.muted, 11.0f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
    } else {
        for (size_t i = 0; i < installedFiles_.size(); ++i) {
            auto& f = installedFiles_[i];
            float rowY = listY + i * 54.0f;
            D2D1_RECT_F fRect = D2D1::RectF(startX, rowY, startX + contentW, rowY + 48.0f);

            r.FillRoundedRect(fRect, 8.0f, Color::FromRgba(32, 35, 32, 0.40f));
            r.DrawRoundedRect(fRect, 8.0f, theme.line, 1.0f);

            // Toggle switch for mods
            if (f.kind == "mod") {
                f.toggle.Render(r);
            }

            float textLeft = startX + (f.kind == "mod" ? 64.0f : 16.0f);

            // Display name (bold)
            r.DrawString(f.displayName, D2D1::RectF(textLeft, rowY + 6.0f, startX + contentW - 120.0f, rowY + 24.0f), theme.text, 12.0f, FontStyle::Bold);

            // Kind pill + Disabled pill + Size
            float pillLeft = textLeft;
            D2D1_RECT_F kindPill = D2D1::RectF(pillLeft, rowY + 26.0f, pillLeft + 52.0f, rowY + 42.0f);
            r.FillRoundedRect(kindPill, 4.0f, Color::FromRgba(207, 189, 154, 0.12f));
            r.DrawRoundedRect(kindPill, 4.0f, Color::FromRgba(207, 189, 154, 0.25f), 1.0f);
            std::wstring kindTitle = (f.kind == "mod") ? L"Мод" : ((f.kind == "resourcepack") ? L"Ресурспак" : L"Шейдер");
            r.DrawString(kindTitle, kindPill, Color::FromHex(0xb4bdaa), 8.5f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
            pillLeft += 58.0f;

            if (!f.isEnabled) {
                D2D1_RECT_F disPill = D2D1::RectF(pillLeft, rowY + 26.0f, pillLeft + 120.0f, rowY + 42.0f);
                r.FillRoundedRect(disPill, 4.0f, Color::FromRgba(245, 158, 11, 0.15f));
                r.DrawRoundedRect(disPill, 4.0f, Color::FromRgba(245, 158, 11, 0.40f), 1.0f);
                r.DrawString(L"Отключён (.disabled)", disPill, theme.amber, 8.5f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
                pillLeft += 126.0f;
            }

            r.DrawString(f.sizeStr, D2D1::RectF(pillLeft, rowY + 26.0f, pillLeft + 80.0f, rowY + 42.0f), theme.muted, 9.5f, FontStyle::Regular, DWRITE_TEXT_ALIGNMENT_LEADING, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

            f.btnDelete.Render(r);
        }

        float footerY = listY + installedFiles_.size() * 54.0f + 6.0f;
        std::wstring countStr = std::to_wstring(installedFiles_.size()) + L" файлов";
        r.DrawString(countStr, D2D1::RectF(startX, footerY, startX + 200.0f, footerY + 20.0f), theme.muted, 10.0f);
    }

    scrollView_.EndRender(r);

    // Dropdowns render on top of scrollview
    comboSort_.RenderDropdown(r);
    comboMc_.RenderDropdown(r);
    if (activeKind_ == "mod") {
        comboLoader_.RenderDropdown(r);
    }

    if (isLoading_) {
        float lx = startX + contentW * 0.5f;
        float ly = 320.0f;
        r.DrawString(L"Загрузка каталога Modrinth...", D2D1::RectF(lx - 120.0f, ly, lx + 120.0f, ly + 24.0f), theme.mint, 12.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER);
    }

    // Modal Details (#mod-modal)
    if (isModModalOpen_) {
        r.FillRoundedRect(D2D1::RectF(0, 0, bounds_.right, bounds_.bottom), 0.0f, Color::FromRgba(32, 35, 32, 0.78f));

        float mW = (std::min)(bounds_.right - 60.0f, 500.0f);
        float mH = (std::min)(bounds_.bottom - 80.0f, 360.0f);
        float mX = (bounds_.right - mW) * 0.5f;
        float mY = (bounds_.bottom - mH) * 0.5f;

        D2D1_RECT_F mRect = D2D1::RectF(mX, mY, mX + mW, mY + mH);
        r.FillRoundedRect(mRect, 8.0f, Color::FromHex(0x252b24));
        r.DrawRoundedRect(mRect, 8.0f, Color::FromRgba(240, 183, 130, 0.40f), 1.0f);

        // Head
        r.DrawString(L"MODRINTH", D2D1::RectF(mX + 20.0f, mY + 16.0f, mX + 300.0f, mY + 30.0f), theme.accent2, 9.0f, FontStyle::Bold);
        r.DrawString(Utf8ToWide(activeModalMod_.title), D2D1::RectF(mX + 20.0f, mY + 30.0f, mX + mW - 50.0f, mY + 54.0f), theme.text, 16.0f, FontStyle::Bold);
        std::wstring authorStr = L"Автор: " + Utf8ToWide(activeModalMod_.author.empty() ? "—" : activeModalMod_.author);
        r.DrawString(authorStr, D2D1::RectF(mX + 20.0f, mY + 54.0f, mX + mW - 50.0f, mY + 70.0f), theme.muted, 11.0f);

        btnModModalClose_.Render(r);

        // Icon (58x58) in modal
        D2D1_RECT_F mIconRect = D2D1::RectF(mX + 20.0f, mY + 76.0f, mX + 78.0f, mY + 134.0f);
        fs::path mIconPath = cDir / (Utf8ToWide(activeModalMod_.id) + L".png");
        bool drewMIcon = false;
        if (fs::exists(mIconPath)) {
            ID2D1Bitmap* bmp = r.LoadBitmapFromFile(mIconPath.wstring());
            if (bmp) {
                r.PushClip(mIconRect);
                r.DrawBitmap(bmp, mIconRect);
                r.PopClip();
                r.DrawRoundedRect(mIconRect, 8.0f, Color::FromRgba(240, 183, 130, 0.35f), 1.0f);
                drewMIcon = true;
            }
        }
        if (!drewMIcon) {
            r.FillGradientRoundedRect(mIconRect, 8.0f, theme.accent1, theme.accent2, false);
            std::wstring initial = activeModalMod_.title.empty() ? L"M" : Utf8ToWide(activeModalMod_.title.substr(0, 1));
            r.DrawString(initial, mIconRect, Color::FromHex(0xffffff), 22.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);
        }

        // Body: Downloads pill
        std::wstring dlStr = std::to_wstring(activeModalMod_.downloads) + L" загрузок";
        D2D1_RECT_F dlPill = D2D1::RectF(mX + 88.0f, mY + 76.0f, mX + 220.0f, mY + 98.0f);
        r.FillRoundedRect(dlPill, 4.0f, Color::FromRgba(178, 199, 142, 0.15f));
        r.DrawRoundedRect(dlPill, 4.0f, Color::FromRgba(178, 199, 142, 0.40f), 1.0f);
        r.DrawString(dlStr, dlPill, theme.mint, 10.0f, FontStyle::Bold, DWRITE_TEXT_ALIGNMENT_CENTER, DWRITE_PARAGRAPH_ALIGNMENT_CENTER);

        // Description
        std::wstring desc = Utf8ToWide(activeModalMod_.description);
        r.DrawString(desc, D2D1::RectF(mX + 20.0f, mY + 144.0f, mX + mW - 20.0f, mY + mH - 60.0f), theme.text, 11.5f, FontStyle::Regular);

        btnModModalWeb_.Render(r);
        btnModModalInstall_.Render(r);
    }
}

} // namespace aero

