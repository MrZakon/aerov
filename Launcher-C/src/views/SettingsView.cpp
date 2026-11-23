#include "SettingsView.h"
#include "../store/SettingsManager.h"
#include "../net/DiscordRpc.h"
#include "../ui/Toast.h"
#include "../core/StringUtils.h"
#include <shellapi.h>
#include <shobjidl.h>
#include <algorithm>
#include <cmath>

namespace aero {

static std::wstring PickFolderDialog(HWND hwndOwner, const std::wstring& defaultFolder) {
    std::wstring result;
    IFileOpenDialog* pFileOpen = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_ALL, IID_IFileOpenDialog, reinterpret_cast<void**>(&pFileOpen));
    if (SUCCEEDED(hr)) {
        DWORD dwOptions;
        if (SUCCEEDED(pFileOpen->GetOptions(&dwOptions))) {
            pFileOpen->SetOptions(dwOptions | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
        }
        pFileOpen->SetTitle(L"Выберите папку с данными игры");
        if (!defaultFolder.empty()) {
            IShellItem* pFolder = nullptr;
            if (SUCCEEDED(SHCreateItemFromParsingName(defaultFolder.c_str(), NULL, IID_PPV_ARGS(&pFolder)))) {
                pFileOpen->SetFolder(pFolder);
                pFolder->Release();
            }
        }
        if (SUCCEEDED(pFileOpen->Show(hwndOwner))) {
            IShellItem* pItem = nullptr;
            if (SUCCEEDED(pFileOpen->GetResult(&pItem))) {
                PWSTR pszFilePath = nullptr;
                if (SUCCEEDED(pItem->GetDisplayName(SIGDN_FILESYSPATH, &pszFilePath))) {
                    result = pszFilePath;
                    CoTaskMemFree(pszFilePath);
                }
                pItem->Release();
            }
        }
        pFileOpen->Release();
    }
    return result;
}

static std::wstring PickJavaDialog(HWND hwndOwner, const std::wstring& defaultFolder) {
    std::wstring result;
    IFileOpenDialog* pFileOpen = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, NULL, CLSCTX_ALL, IID_IFileOpenDialog, reinterpret_cast<void**>(&pFileOpen));
    if (SUCCEEDED(hr)) {
        COMDLG_FILTERSPEC rgSpec[] = {
            { L"Исполняемый файл Java (*.exe)", L"*.exe" },
            { L"Все файлы (*.*)", L"*.*" }
        };
        pFileOpen->SetFileTypes(ARRAYSIZE(rgSpec), rgSpec);
        pFileOpen->SetTitle(L"Выберите файл java.exe");
        if (!defaultFolder.empty()) {
            IShellItem* pFolder = nullptr;
            if (SUCCEEDED(SHCreateItemFromParsingName(defaultFolder.c_str(), NULL, IID_PPV_ARGS(&pFolder)))) {
                pFileOpen->SetFolder(pFolder);
                pFolder->Release();
            }
        }
        if (SUCCEEDED(pFileOpen->Show(hwndOwner))) {
            IShellItem* pItem = nullptr;
            if (SUCCEEDED(pFileOpen->GetResult(&pItem))) {
                PWSTR pszFilePath = nullptr;
                if (SUCCEEDED(pItem->GetDisplayName(SIGDN_FILESYSPATH, &pszFilePath))) {
                    result = pszFilePath;
                    CoTaskMemFree(pszFilePath);
                }
                pItem->Release();
            }
        }
        pFileOpen->Release();
    }
    return result;
}

SettingsView::SettingsView() {
    // Detect total physical PC RAM
    MEMORYSTATUSEX mem;
    mem.dwLength = sizeof(mem);
    if (GlobalMemoryStatusEx(&mem)) {
        totalRamGb_ = static_cast<int>(std::round(static_cast<double>(mem.ullTotalPhys) / (1024.0 * 1024.0 * 1024.0)));
        if (totalRamGb_ < 2) totalRamGb_ = 2;
    }

    sliderRam_.SetRange(1.0f, static_cast<float>((std::min)(totalRamGb_, 32)), 1.0f);
    sliderRam_.SetOnChanged([this](float val) {
        curRamGb_ = static_cast<int>(std::round(val));
    });

    btnTabGeneral_.SetOnClick([this]() {
        activeTab_ = 0;
        tabPillY_.SetTarget(74.0f);
        tabContentOffset_.SnapTo(12.0f);
        tabContentOffset_.SetTarget(0.0f);
    });
    btnTabJvm_.SetOnClick([this]() {
        activeTab_ = 1;
        tabPillY_.SetTarget(74.0f + 45.0f);
        tabContentOffset_.SnapTo(12.0f);
        tabContentOffset_.SetTarget(0.0f);
    });
    btnTabTheme_.SetOnClick([this]() {
        activeTab_ = 2;
        tabPillY_.SetTarget(74.0f + 90.0f);
        tabContentOffset_.SnapTo(12.0f);
        tabContentOffset_.SetTarget(0.0f);
    });

    tabPillY_.SnapTo(74.0f);

    btnThemeEmerald_.SetOnClick([]() {
        Theme::Instance().Apply(ThemeKind::Emerald);
        SettingsManager::Instance().Get().themeAccent = "emerald";
        SettingsManager::Instance().Save();
    });
    btnThemeViolet_.SetOnClick([]() {
        Theme::Instance().Apply(ThemeKind::Violet);
        SettingsManager::Instance().Get().themeAccent = "violet";
        SettingsManager::Instance().Save();
    });
    btnThemeCyan_.SetOnClick([]() {
        Theme::Instance().Apply(ThemeKind::Cyan);
        SettingsManager::Instance().Get().themeAccent = "cyan";
        SettingsManager::Instance().Save();
    });
    btnThemeAmber_.SetOnClick([]() {
        Theme::Instance().Apply(ThemeKind::Amber);
        SettingsManager::Instance().Get().themeAccent = "amber";
        SettingsManager::Instance().Save();
    });

    btnSave_.SetOnClick([this]() { SaveSettings(); });

    btnBrowseDir_.SetOnClick([this]() {
        std::wstring cur = inputGameDir_.GetText();
        std::wstring picked = PickFolderDialog(nullptr, cur);
        if (!picked.empty()) {
            inputGameDir_.SetText(picked);
        }
    });

    btnOpenDir_.SetOnClick([this]() {
        auto& settings = SettingsManager::Instance().Get();
        ShellExecuteW(nullptr, L"open", settings.gameDir.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
    });

    btnBrowseJava_.SetOnClick([this]() {
        std::wstring cur = inputJavaPath_.GetText();
        std::wstring picked = PickJavaDialog(nullptr, cur);
        if (!picked.empty()) {
            inputJavaPath_.SetText(picked);
            SettingsManager::Instance().Get().javaPath = picked;
            SettingsManager::Instance().Save();
            RefreshData();
            Toast::Instance().Show(L"Java обновлена: " + picked, ToastKind::Success);
        }
    });

    comboJava_.SetOnSelect([this](int idx, const std::wstring& /*text*/) {
        if (idx == 0) {
            inputJavaPath_.SetText(L"");
            SettingsManager::Instance().Get().javaPath.clear();
            SettingsManager::Instance().Save();
        } else if (idx > 0 && idx <= static_cast<int>(detectedJavas_.size())) {
            const auto& java = detectedJavas_[idx - 1];
            std::wstring pickedPath = java.path;
            inputJavaPath_.SetText(pickedPath);
            SettingsManager::Instance().Get().javaPath = pickedPath;
            SettingsManager::Instance().Save();
            Toast::Instance().Show(L"Выбрана: Java " + std::to_wstring(java.majorVersion), ToastKind::Success);
        }
    });

    btnReRunSetup_.SetOnClick([this]() {
        if (onReRunSetup_) onReRunSetup_();
    });

    // JVM quick presets
    btnJvmPerf_.SetOnClick([this]() {
        inputJvmArgs_.SetText(L"-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions");
        Toast::Instance().Show(L"Применён пресет производительности JVM", ToastKind::Info);
    });
    btnJvmStable_.SetOnClick([this]() {
        inputJvmArgs_.SetText(L"-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UnlockExperimentalVMOptions");
        Toast::Instance().Show(L"Применён пресет стабильности JVM", ToastKind::Info);
    });
    btnJvmReset_.SetOnClick([this]() {
        inputJvmArgs_.SetText(L"-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -Dfile.encoding=UTF-8");
        Toast::Instance().Show(L"Сброшены стандартные аргументы JVM", ToastKind::Info);
    });

    RefreshData();
}

void SettingsView::SetRamValue(int gb) {
    curRamGb_ = (std::clamp)(gb, 1, totalRamGb_);
    sliderRam_.SetValue(static_cast<float>(curRamGb_));
}

void SettingsView::RefreshData() {
    const auto& s = SettingsManager::Instance().Get();
    inputGameDir_.SetText(s.gameDir);
    inputJavaPath_.SetText(s.javaPath);

    // Auto-detect Java runtimes and populate comboJava_
    detectedJavas_ = GameLauncher::ScanJavaInstallations();
    std::vector<std::wstring> comboItems{L"Авто — подобрать Java для сборки"};
    int selectedIdx = 0;

    for (size_t i = 0; i < detectedJavas_.size(); ++i) {
        comboItems.push_back(detectedJavas_[i].description);
        if (_wcsicmp(detectedJavas_[i].path.c_str(), s.javaPath.c_str()) == 0) {
            selectedIdx = static_cast<int>(i + 1);
        }
    }

    if (selectedIdx == 0 && !s.javaPath.empty()) {
        comboItems.push_back(L"Пользовательский (" + s.javaPath + L")");
        selectedIdx = static_cast<int>(comboItems.size() - 1);
    }

    comboJava_.SetItems(comboItems);
    if (selectedIdx >= 0) {
        comboJava_.SetSelectedIndex(selectedIdx);
    }

    curRamGb_ = s.memoryMax > 0 ? s.memoryMax : 4;
    sliderRam_.SetValue(static_cast<float>(curRamGb_));

    inputJvmArgs_.SetText(Utf8ToWide(s.jvmArgs));

    checkStarfield_.SetChecked(s.starfield);
    checkDiscord_.SetChecked(s.discordRpc);
    checkGlow_.SetChecked(s.bgGlow);
    checkSounds_.SetChecked(s.soundEffects);
}

void SettingsView::SaveSettings() {
    auto& s = SettingsManager::Instance().Get();
    s.gameDir = inputGameDir_.GetText();
    s.javaPath = inputJavaPath_.GetText();
    s.memoryMax = curRamGb_;

    s.jvmArgs = WideToUtf8(inputJvmArgs_.GetText());
    s.starfield = checkStarfield_.IsChecked();
    s.discordRpc = checkDiscord_.IsChecked();
    s.bgGlow = checkGlow_.IsChecked();
    s.soundEffects = checkSounds_.IsChecked();

    DiscordRpc::Instance().SetEnabled(s.discordRpc);
    SettingsManager::Instance().Save();

    Toast::Instance().Show(L"Настройки успешно сохранены", ToastKind::Success);
}

void SettingsView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);

    float startX = 36.0f;
    float startY = 24.0f;

    // Tabs on left
    btnTabGeneral_.SetBounds(startX, startY + 50.0f, 180.0f, 40.0f);
    btnTabJvm_.SetBounds(startX, startY + 95.0f, 180.0f, 40.0f);
    btnTabTheme_.SetBounds(startX, startY + 140.0f, 180.0f, 40.0f);

    float contentX = startX + 210.0f;
    float panelW = (std::min)(width - contentX - 48.0f, 680.0f);
    float panelH = 370.0f;
    float innerW = panelW - 30.0f;
    float gap = 8.0f;

    // General tab controls
    float btnDirW = 85.0f;
    float inputDirW = innerW - btnDirW * 2.0f - gap * 2.0f;
    inputGameDir_.SetBounds(contentX, startY + 52.0f, inputDirW, 40.0f);
    btnBrowseDir_.SetBounds(contentX + inputDirW + gap, startY + 52.0f, btnDirW, 40.0f);
    btnOpenDir_.SetBounds(contentX + inputDirW + gap + btnDirW + gap, startY + 52.0f, btnDirW, 40.0f);
    checkDiscord_.SetBounds(contentX, startY + 134.0f, 320.0f, 30.0f);

    // JVM & Memory tab controls (spacious, no overlap)
    float btnJavaW = 85.0f;
    float comboJavaW = innerW - btnJavaW - gap;
    comboJava_.SetBounds(contentX, startY + 48.0f, comboJavaW, 38.0f);
    btnBrowseJava_.SetBounds(contentX + comboJavaW + gap, startY + 48.0f, btnJavaW, 38.0f);

    sliderRam_.SetBounds(contentX, startY + 144.0f, innerW, 24.0f);

    inputJvmArgs_.SetBounds(contentX, startY + 204.0f, innerW, 36.0f);
    float jvmBtnW = (innerW - gap * 2.0f) / 3.0f;
    btnJvmPerf_.SetBounds(contentX, startY + 248.0f, jvmBtnW, 28.0f);
    btnJvmStable_.SetBounds(contentX + (jvmBtnW + gap) * 1, startY + 248.0f, jvmBtnW, 28.0f);
    btnJvmReset_.SetBounds(contentX + (jvmBtnW + gap) * 2, startY + 248.0f, jvmBtnW, 28.0f);

    // Theme tab controls
    float themeBtnW = (innerW - gap * 3.0f) / 4.0f;
    btnThemeEmerald_.SetBounds(contentX, startY + 52.0f, themeBtnW, 42.0f);
    btnThemeViolet_.SetBounds(contentX + (themeBtnW + gap) * 1, startY + 52.0f, themeBtnW, 42.0f);
    btnThemeCyan_.SetBounds(contentX + (themeBtnW + gap) * 2, startY + 52.0f, themeBtnW, 42.0f);
    btnThemeAmber_.SetBounds(contentX + (themeBtnW + gap) * 3, startY + 52.0f, themeBtnW, 42.0f);

    checkStarfield_.SetBounds(contentX, startY + 115.0f, 320.0f, 30.0f);
    checkGlow_.SetBounds(contentX, startY + 152.0f, 320.0f, 30.0f);
    checkSounds_.SetBounds(contentX, startY + 189.0f, 320.0f, 30.0f);

    // Save and Re-run setup buttons compactly under card
    float btnY = startY + 40.0f + panelH + 16.0f;
    btnSave_.SetBounds(contentX, btnY, 200.0f, 42.0f);
    btnReRunSetup_.SetBounds(contentX + 215.0f, btnY, 270.0f, 42.0f);
}

void SettingsView::Update(float dt) {
    tabPillY_.Update(dt);
    tabContentOffset_.Update(dt);
    animTime_ += dt;

    btnTabGeneral_.Update(dt);
    btnTabJvm_.Update(dt);
    btnTabTheme_.Update(dt);
    btnSave_.Update(dt);
    btnReRunSetup_.Update(dt);

    if (activeTab_ == 0) {
        inputGameDir_.Update(dt);
        btnBrowseDir_.Update(dt);
        btnOpenDir_.Update(dt);
        checkDiscord_.Update(dt);
    } else if (activeTab_ == 1) {
        dispRamVal_.SetTarget(static_cast<float>(curRamGb_));
        dispRamVal_.Update(dt);

        comboJava_.Update(dt);
        btnBrowseJava_.Update(dt);
        sliderRam_.Update(dt);

        inputJvmArgs_.Update(dt);
        btnJvmPerf_.Update(dt);
        btnJvmStable_.Update(dt);
        btnJvmReset_.Update(dt);
    } else if (activeTab_ == 2) {
        btnThemeEmerald_.Update(dt);
        btnThemeViolet_.Update(dt);
        btnThemeCyan_.Update(dt);
        btnThemeAmber_.Update(dt);
        checkStarfield_.Update(dt);
        checkGlow_.Update(dt);
        checkSounds_.Update(dt);
    }
}

bool SettingsView::OnMouseMove(float x, float y) {
    if (activeTab_ == 1 && comboJava_.IsExpanded()) {
        return comboJava_.OnMouseMove(x, y);
    }

    bool handled = false;
    handled |= btnTabGeneral_.OnMouseMove(x, y);
    handled |= btnTabJvm_.OnMouseMove(x, y);
    handled |= btnTabTheme_.OnMouseMove(x, y);
    handled |= btnSave_.OnMouseMove(x, y);
    handled |= btnReRunSetup_.OnMouseMove(x, y);

    if (activeTab_ == 0) {
        handled |= inputGameDir_.OnMouseMove(x, y);
        handled |= btnBrowseDir_.OnMouseMove(x, y);
        handled |= btnOpenDir_.OnMouseMove(x, y);
        handled |= checkDiscord_.OnMouseMove(x, y);
    } else if (activeTab_ == 1) {
        handled |= comboJava_.OnMouseMove(x, y);
        handled |= btnBrowseJava_.OnMouseMove(x, y);
        handled |= sliderRam_.OnMouseMove(x, y);
        handled |= inputJvmArgs_.OnMouseMove(x, y);
        handled |= btnJvmPerf_.OnMouseMove(x, y);
        handled |= btnJvmStable_.OnMouseMove(x, y);
        handled |= btnJvmReset_.OnMouseMove(x, y);
    } else if (activeTab_ == 2) {
        handled |= btnThemeEmerald_.OnMouseMove(x, y);
        handled |= btnThemeViolet_.OnMouseMove(x, y);
        handled |= btnThemeCyan_.OnMouseMove(x, y);
        handled |= btnThemeAmber_.OnMouseMove(x, y);
        handled |= checkStarfield_.OnMouseMove(x, y);
        handled |= checkGlow_.OnMouseMove(x, y);
        handled |= checkSounds_.OnMouseMove(x, y);
    }
    return handled;
}

bool SettingsView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (activeTab_ == 1 && comboJava_.IsExpanded() && comboJava_.OnMouseButton(x, y, btn, down)) {
        return true;
    }

    if (btnTabGeneral_.OnMouseButton(x, y, btn, down)) return true;
    if (btnTabJvm_.OnMouseButton(x, y, btn, down)) return true;
    if (btnTabTheme_.OnMouseButton(x, y, btn, down)) return true;
    if (btnSave_.OnMouseButton(x, y, btn, down)) return true;
    if (btnReRunSetup_.OnMouseButton(x, y, btn, down)) return true;

    if (activeTab_ == 0) {
        if (inputGameDir_.OnMouseButton(x, y, btn, down)) return true;
        if (btnBrowseDir_.OnMouseButton(x, y, btn, down)) return true;
        if (btnOpenDir_.OnMouseButton(x, y, btn, down)) return true;
        if (checkDiscord_.OnMouseButton(x, y, btn, down)) return true;
    } else if (activeTab_ == 1) {
        if (comboJava_.OnMouseButton(x, y, btn, down)) return true;
        if (btnBrowseJava_.OnMouseButton(x, y, btn, down)) return true;
        if (sliderRam_.OnMouseButton(x, y, btn, down)) return true;
        if (inputJvmArgs_.OnMouseButton(x, y, btn, down)) return true;
        if (btnJvmPerf_.OnMouseButton(x, y, btn, down)) return true;
        if (btnJvmStable_.OnMouseButton(x, y, btn, down)) return true;
        if (btnJvmReset_.OnMouseButton(x, y, btn, down)) return true;
    } else if (activeTab_ == 2) {
        if (btnThemeEmerald_.OnMouseButton(x, y, btn, down)) return true;
        if (btnThemeViolet_.OnMouseButton(x, y, btn, down)) return true;
        if (btnThemeCyan_.OnMouseButton(x, y, btn, down)) return true;
        if (btnThemeAmber_.OnMouseButton(x, y, btn, down)) return true;
        if (checkStarfield_.OnMouseButton(x, y, btn, down)) return true;
        if (checkGlow_.OnMouseButton(x, y, btn, down)) return true;
        if (checkSounds_.OnMouseButton(x, y, btn, down)) return true;
    }
    return false;
}

bool SettingsView::OnMouseWheel(float x, float y, float delta) {
    if (activeTab_ == 1 && comboJava_.IsExpanded()) {
        return comboJava_.OnMouseWheel(x, y, delta);
    }
    return false;
}

bool SettingsView::OnChar(wchar_t ch) {
    if (activeTab_ == 0) {
        if (inputGameDir_.IsFocused()) return inputGameDir_.OnChar(ch);
    } else if (activeTab_ == 1) {
        if (inputJvmArgs_.IsFocused()) return inputJvmArgs_.OnChar(ch);
    }
    return false;
}

bool SettingsView::OnKey(int vk, bool down) {
    if (activeTab_ == 0) {
        if (inputGameDir_.IsFocused()) return inputGameDir_.OnKey(vk, down);
    } else if (activeTab_ == 1) {
        if (inputJvmArgs_.IsFocused()) return inputJvmArgs_.OnKey(vk, down);
    }
    return false;
}

void SettingsView::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    float startX = 36.0f;
    float startY = 24.0f;
    float contentW = bounds_.right - 72.0f;

    r.DrawString(L"Настройки", D2D1::RectF(startX, startY, startX + contentW, startY + 30.0f), theme.text, 18.0f, FontStyle::Bold);

    // Animated sliding spring pill behind active tab
    float py = tabPillY_.Get();
    D2D1_RECT_F tabPill = D2D1::RectF(startX, py, startX + 180.0f, py + 40.0f);
    r.FillGradientRoundedRect(tabPill, 6.0f, Color::FromRgba(240, 183, 130, 0.22f), Color::FromRgba(240, 183, 130, 0.06f), false);
    r.DrawRoundedRect(tabPill, 6.0f, Color::FromRgba(240, 183, 130, 0.35f), 1.0f);
    r.FillRoundedRect(D2D1::RectF(startX, py + 8.0f, startX + 3.0f, py + 32.0f), 1.5f, theme.accent2);

    // Sidebar tabs
    btnTabGeneral_.Render(r);
    btnTabJvm_.Render(r);
    btnTabTheme_.Render(r);

    float contentX = startX + 210.0f;
    float panelW = (std::min)(contentW - 210.0f, 680.0f);
    float panelH = 370.0f;
    float innerW = panelW - 30.0f;

    D2D1_RECT_F panelRect = D2D1::RectF(contentX - 15.0f, startY + 40.0f, contentX - 15.0f + panelW, startY + 40.0f + panelH);
    r.FillRoundedRect(panelRect, 8.0f, Color::FromRgba(32, 35, 32, 0.70f));
    r.DrawRoundedRect(panelRect, 8.0f, theme.line, 1.0f);

    // Animated panel contents transition offset
    float tabOffsetY = tabContentOffset_.Get();
    D2D1_MATRIX_3X2_F curT;
    r.GetRenderTarget()->GetTransform(&curT);
    D2D1_MATRIX_3X2_F panelT = D2D1::Matrix3x2F::Translation(0.0f, tabOffsetY) * curT;
    r.GetRenderTarget()->SetTransform(panelT);

    if (activeTab_ == 0) {
        r.DrawString(L"Папка игры", D2D1::RectF(contentX, startY + 28.0f, contentX + innerW, startY + 46.0f), theme.muted, 10.0f, FontStyle::Bold);
        inputGameDir_.Render(r);
        btnBrowseDir_.Render(r);
        btnOpenDir_.Render(r);
        r.DrawString(L"Место хранения версий, сборок и общих файлов Minecraft.", D2D1::RectF(contentX, startY + 84.0f, contentX + innerW, startY + 100.0f), theme.muted, 9.5f);

        r.DrawString(L"Интеграции", D2D1::RectF(contentX, startY + 108.0f, contentX + innerW, startY + 126.0f), theme.muted, 10.0f, FontStyle::Bold);
        checkDiscord_.Render(r);
    } else if (activeTab_ == 1) {
        // --- 1. Java Executable ---
        r.DrawString(L"Java", D2D1::RectF(contentX, startY + 28.0f, contentX + panelW - 20.0f, startY + 46.0f), theme.muted, 10.0f, FontStyle::Bold);
        comboJava_.Render(r);
        btnBrowseJava_.Render(r);

        // --- 2. RAM Memory Slider ---
        r.DrawString(L"Оперативная память", D2D1::RectF(contentX, startY + 96.0f, contentX + 230.0f, startY + 114.0f), theme.muted, 10.0f, FontStyle::Bold);

        // Total PC RAM badge pill (like Node.js launcher)
        std::wstring pcRamStr = L"В ПК: " + std::to_wstring(totalRamGb_) + L" ГБ";
        D2D1_RECT_F ramPill = D2D1::RectF(contentX + 225.0f, startY + 94.0f, contentX + 325.0f, startY + 114.0f);
        r.FillRoundedRect(ramPill, 4.0f, Color::FromRgba(16, 185, 129, 0.15f));
        r.DrawRoundedRect(ramPill, 4.0f, Color::FromRgba(16, 185, 129, 0.35f), 1.0f);
        r.DrawString(pcRamStr, ramPill, Color::FromHex(0x34d399), 10.0f, FontStyle::Bold);

        // Big value label + description hint
        std::wstring ramValStr = std::to_wstring(curRamGb_) + L" ГБ";
        r.DrawString(ramValStr, D2D1::RectF(contentX, startY + 116.0f, contentX + 80.0f, startY + 140.0f), theme.accent2, 18.0f, FontStyle::Bold);

        std::wstring ramHint;
        if (curRamGb_ <= 2) {
            ramHint = L"Минимально для легких сборок и ваниллы";
        } else if (curRamGb_ <= 8) {
            ramHint = L"Рекомендуется для Aero Visuals, шейдеров и модов";
        } else {
            ramHint = L"Для тяжелых сборок с максимальной дальностью прорисовки";
        }
        r.DrawString(ramHint, D2D1::RectF(contentX + 85.0f, startY + 120.0f, contentX + panelW - 10.0f, startY + 140.0f), theme.muted, 10.0f);

        sliderRam_.Render(r);

        // --- 3. JVM Arguments ---
        r.DrawString(L"Аргументы JVM", D2D1::RectF(contentX, startY + 184.0f, contentX + panelW - 20.0f, startY + 202.0f), theme.muted, 10.0f, FontStyle::Bold);
        inputJvmArgs_.Render(r);

        btnJvmPerf_.Render(r);
        btnJvmStable_.Render(r);
        btnJvmReset_.Render(r);

        // Render comboJava_ dropdown after other elements so it sits on top
        comboJava_.RenderDropdown(r);
    } else if (activeTab_ == 2) {
        r.DrawString(L"Цветовая тема", D2D1::RectF(contentX, startY + 28.0f, contentX + panelW - 20.0f, startY + 48.0f), theme.muted, 10.0f, FontStyle::Bold);

        btnThemeEmerald_.Render(r);
        btnThemeViolet_.Render(r);
        btnThemeCyan_.Render(r);
        btnThemeAmber_.Render(r);

        // Draw color preview indicator dot inside each theme button
        auto drawThemeDot = [&](Button& btn, D2D1_COLOR_F c1, const std::string& themeKey) {
            D2D1_RECT_F b = btn.GetBounds();
            float dotX = b.left + 16.0f;
            float dotY = (b.top + b.bottom) * 0.5f;
            D2D1_RECT_F dotRect = D2D1::RectF(dotX - 4.0f, dotY - 4.0f, dotX + 4.0f, dotY + 4.0f);
            r.FillRoundedRect(dotRect, 2.0f, c1);

            if (SettingsManager::Instance().Get().themeAccent == themeKey) {
                float pulse = (std::sin(animTime_ * 3.5f) + 1.0f) * 0.5f;
                D2D1_RECT_F selHalo = D2D1::RectF(b.left - 2.0f, b.top - 2.0f, b.right + 2.0f, b.bottom + 2.0f);
                r.DrawRoundedRect(selHalo, 6.0f, D2D1::ColorF(c1.r, c1.g, c1.b, 0.40f + pulse * 0.35f), 1.5f);
            }
        };

        drawThemeDot(btnThemeEmerald_, Color::FromHex(0x66e4c4), "emerald");
        drawThemeDot(btnThemeViolet_, Color::FromHex(0xb4bdaa), "violet");
        drawThemeDot(btnThemeCyan_, Color::FromHex(0xb4bdaa), "cyan");
        drawThemeDot(btnThemeAmber_, Color::FromHex(0xfbbf24), "amber");

        r.DrawString(L"Эффекты", D2D1::RectF(contentX, startY + 95.0f, contentX + panelW - 20.0f, startY + 115.0f), theme.muted, 10.0f, FontStyle::Bold);
        checkStarfield_.Render(r);
        checkGlow_.Render(r);
        checkSounds_.Render(r);
    }

    r.GetRenderTarget()->SetTransform(curT);

    btnSave_.Render(r);
    btnReRunSetup_.Render(r);
}

} // namespace aero

