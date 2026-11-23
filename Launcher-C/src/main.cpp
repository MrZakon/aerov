#include <windows.h>
#include "core/Window.h"
#include "core/Timer.h"
#include "core/Config.h"
#include "render/D2DRenderer.h"
#include "render/Theme.h"
#include "render/Starfield.h"
#include "store/SettingsManager.h"
#include "store/AccountManager.h"
#include "auth/AeroAuth.h"
#include "net/DiscordRpc.h"
#include "net/LauncherUpdate.h"
#include "ui/Toast.h"
#include "views/TitleBarView.h"
#include "views/SplashView.h"
#include "views/InitialSetupView.h"
#include "views/LauncherUpdateDialog.h"
#include "views/AuthView.h"
#include "views/ShellView.h"
#include "core/HWID.h"
#include "core/Logger.h"
#include "core/StringUtils.h"
#include "game/GameLauncher.h"

#include <algorithm>
#include <fstream>
#include <future>
#include <utility>
#include <dbghelp.h>
#pragma comment(lib, "dbghelp.lib")

using namespace aero;

static LONG WINAPI CrashFilter(EXCEPTION_POINTERS* ep) {
    std::ofstream f("crash.log");
    f << "Crash: ExceptionCode = 0x" << std::hex << ep->ExceptionRecord->ExceptionCode << "\n";
    f << "Address = 0x" << std::hex << (uintptr_t)ep->ExceptionRecord->ExceptionAddress << "\n";
    f.close();
    return EXCEPTION_EXECUTE_HANDLER;
}

int WINAPI wWinMain(HINSTANCE /*hInstance*/, HINSTANCE /*hPrevInstance*/, LPWSTR /*lpCmdLine*/, int /*nCmdShow*/) {
    SetUnhandledExceptionFilter(CrashFilter);
    try {
        const std::filesystem::path appData = SettingsManager::GetAppDataAeroDir();
        std::ofstream(appData / L"startup.log", std::ios::trunc) << "=== Startup ===\n";
        auto logStep = [&](const std::string& msg) {
            std::ofstream f(appData / L"startup.log", std::ios::app);
            f << msg << "\n";
        };

        logStep("1. CoInitializeEx");
        CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);

        Logger::AddCallback([appData](const std::string& line, bool isErr, bool isWarn) {
            std::ofstream f(appData / L"launcher.log", std::ios::app);
            if (f.is_open()) {
                std::string prefix = isErr ? "[ERROR] " : (isWarn ? "[WARN] " : "[INFO] ");
                f << prefix << line << "\n";
            }
        });

        logStep("2. Load configs");
        SettingsManager::Instance().Load();
        AccountManager::Instance().Load();
        auto& auth = AeroAuth::Instance();

        logStep("3. Theme & Discord");
        const auto& s = SettingsManager::Instance().Get();
        if (s.themeAccent == "violet") Theme::Instance().Apply(ThemeKind::Violet);
        else if (s.themeAccent == "cyan") Theme::Instance().Apply(ThemeKind::Cyan);
        else if (s.themeAccent == "amber") Theme::Instance().Apply(ThemeKind::Amber);
        else Theme::Instance().Apply(ThemeKind::Emerald);

        {
            DiscordRpc::Instance().SetEnabled(s.discordRpc);
            DiscordRpc::Instance().Initialize(s.discordClientId);
            DiscordActivity act;
            act.state = "В лаунчере • Кастомные визуалы";
            act.details = "Aero Launcher " + WideToUtf8(Config::AppVersion);
            act.startTimestamp = std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now().time_since_epoch()).count();
            DiscordRpc::Instance().SetActivity(act);
        }

        logStep("4. Window Create");
        Window window;
        if (!window.Create(Config::AppName + L" " + Config::AppVersion, 1280, 800)) {
            logStep("ERROR: Window Create failed!");
            return 1;
        }

        logStep("5. D2DRenderer Initialize");
        D2DRenderer renderer;
        if (!renderer.Initialize(window.GetHwnd())) {
            logStep("ERROR: D2DRenderer Initialize failed!");
            return 1;
        }

        logStep("6. Views initialize");
        Starfield starfield;
        starfield.Resize(1280.0f, 800.0f);
        starfield.SetEnabled(s.starfield);

        TitleBarView titleBar(window);
        SplashView splashView;
        InitialSetupView initialSetupView;
        AuthView authView;
        ShellView shellView;

        logStep("7. Session check");

    using Clock = std::chrono::steady_clock;
    std::future<SessionRefresh> authRefresh;
    UserSession previousAuth;
    bool authRefreshQueued = false;
    auto retryDelay = std::chrono::seconds(2);
    auto retryAt = Clock::now();
    const auto refreshAuth = [&] {
        if (authRefresh.valid()) {
            authRefreshQueued = true;
            return;
        }
        previousAuth = auth.GetSession();
        if (previousAuth.token.empty() || previousAuth.isOffline) return;
        shellView.SetAuthChecking(true);
        authRefresh = std::async(std::launch::async, [&auth] { return auth.RefreshSession(); });
    };
    bool showAuth = !auth.IsAuthorized();
    bool showInitialSetup = !showAuth && !SettingsManager::Instance().Get().initialSetupDone;
    if (!showAuth) refreshAuth();

    LauncherUpdate launcherUpdate;
    LauncherUpdateDialog updateDialog([&] { launcherUpdate.Accept(appData); },
                                      [&] { launcherUpdate.Dismiss(); });

    auto resizeViews = [&](int w, int h) {
        const float scale = window.GetScale();
        const float fw = w / scale;
        const float fh = h / scale;
        renderer.SetDpi(scale * 96.0f);
        renderer.Resize(w, h);
        starfield.Resize(fw, fh);
        titleBar.Resize(fw);
        initialSetupView.Resize(fw, fh);
        authView.Resize(fw, fh);
        shellView.Resize(fw, fh);
        updateDialog.Resize(fw, fh);
    };

    resizeViews(window.GetWidth(), window.GetHeight());

    initialSetupView.SetOnFinish([&]() {
        showInitialSetup = false;
        showAuth = false;
        resizeViews(window.GetWidth(), window.GetHeight());
        shellView.RefreshData();
    });

    authView.SetOnSuccess([&]() {
        showAuth = false;
        showInitialSetup = false;
        auto& s = SettingsManager::Instance().Get();
        s.initialSetupDone = true;
        retryDelay = std::chrono::seconds(2);
        retryAt = Clock::now() + std::chrono::seconds(300);
        SettingsManager::Instance().Save();
        resizeViews(window.GetWidth(), window.GetHeight());
        shellView.RefreshData();
    });

    shellView.SetOnLogout([&]() {
        showAuth = true;
        showInitialSetup = false;
        authRefreshQueued = false;
        authView.Reset();
    });
    shellView.SetOnAccountChanged([&] { refreshAuth(); });

    shellView.SetOnReRunSetup([&]() {
        initialSetupView.SyncWithAuth();
        showInitialSetup = true;
    });

    // Event hooks
    window.SetOnResize([&](int w, int h) {
        resizeViews(w, h);
    });

    window.SetOnMouseMove([&](float x, float y) {
        if (updateDialog.IsVisible()) { updateDialog.OnMouseMove(x, y); return; }
        starfield.SetMousePos(x, y, true);
        if (!splashView.IsFinished()) return;
        if (titleBar.OnMouseMove(x, y)) return;
        if (showAuth) authView.OnMouseMove(x, y);
        else if (showInitialSetup) initialSetupView.OnMouseMove(x, y);
        else shellView.OnMouseMove(x, y);
    });

    window.SetOnMouseButton([&](float x, float y, MouseBtn btn, bool down) {
        if (updateDialog.IsVisible()) { updateDialog.OnMouseButton(x, y, btn, down); return; }
        if (!splashView.IsFinished()) {
            if (down) splashView.Skip();
            return;
        }
        if (titleBar.OnMouseButton(x, y, btn, down)) return;
        if (showAuth) authView.OnMouseButton(x, y, btn, down);
        else if (showInitialSetup) initialSetupView.OnMouseButton(x, y, btn, down);
        else shellView.OnMouseButton(x, y, btn, down);
    });

    window.SetOnMouseWheel([&](float x, float y, float delta) {
        if (updateDialog.IsVisible()) return;
        if (!splashView.IsFinished()) return;
        if (showAuth) authView.OnMouseWheel(x, y, delta);
        else if (showInitialSetup) return;
        else shellView.OnMouseWheel(x, y, delta);
    });

    window.SetOnChar([&](wchar_t ch) {
        if (updateDialog.IsVisible()) return;
        if (!splashView.IsFinished()) return;
        if (showAuth) authView.OnChar(ch);
        else if (showInitialSetup) initialSetupView.OnChar(ch);
        else shellView.OnChar(ch);
    });

    window.SetOnKey([&](int vk, bool down) {
        if (updateDialog.IsVisible()) { updateDialog.OnKey(vk, down); return; }
        if (!splashView.IsFinished()) {
            if (down) splashView.Skip();
            return;
        }
        if (showAuth) authView.OnKey(vk, down);
        else if (showInitialSetup) initialSetupView.OnKey(vk, down);
        else shellView.OnKey(vk, down);
    });

    window.SetOnDropFiles([&](const std::vector<std::wstring>& files) {
        if (updateDialog.IsVisible()) return;
        if (!splashView.IsFinished()) return;
        if (!showAuth && !showInitialSetup) {
            shellView.HandleDropFiles(files);
        }
    });

    logStep("8. Window Show");
    window.Show();

    logStep("9. Entering main loop");
    Timer timer;

    // 60 FPS Render & Event Loop
    while (window.IsOpen()) {
        window.PollEvents();
        timer.Tick();
        float dt = timer.GetDeltaTime();

        // Update components
        titleBar.Update(dt);
        starfield.SetEnabled(SettingsManager::Instance().Get().starfield);
        if (!splashView.IsFinished() || showInitialSetup) starfield.Update(dt);
        splashView.Update(dt);

        if (authRefresh.valid() && authRefresh.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
            shellView.SetAuthChecking(false);
            SessionRefresh result;
            try { result = authRefresh.get(); }
            catch (const std::exception&) { result = SessionRefresh::Unavailable; }
            if (result == SessionRefresh::SignedOut && !auth.IsAuthorized() && !showAuth) {
                showInitialSetup = false;
                showAuth = true;
                authView.Reset(L"Сессия Aero ID истекла. Войдите снова.");
            } else if (!showAuth) {
                const auto session = auth.GetSession();
                if (session.verified != previousAuth.verified || session.plan != previousAuth.plan ||
                    session.username != previousAuth.username) shellView.RefreshData();
            }
            if (result == SessionRefresh::Unavailable) {
                retryAt = Clock::now() + retryDelay;
                retryDelay = (std::min)(retryDelay * 2, std::chrono::seconds(30));
            } else if (result != SessionRefresh::Superseded) {
                retryDelay = std::chrono::seconds(2);
                retryAt = Clock::now() + std::chrono::minutes(5);
            }
            if (std::exchange(authRefreshQueued, false) && !showAuth) refreshAuth();
        }
        if (!showAuth && !authRefresh.valid() && Clock::now() >= retryAt) {
            const auto current = auth.GetSession();
            if (!current.token.empty() && !current.isOffline) refreshAuth();
        }

        if (showAuth) authView.Update(dt);
        else if (showInitialSetup) initialSetupView.Update(dt);
        else shellView.Update(dt);

        Toast::Instance().Update(dt);

        if (!window.IsOpen()) break;
        if (splashView.IsFinished() && GetForegroundWindow() == window.GetHwnd()) {
            if (launcherUpdate.Poll(window.GetHwnd(), shellView.IsGameActive())) break;
            const auto version = launcherUpdate.OfferedVersion();
            if (version.empty()) updateDialog.Hide();
            else if (!updateDialog.IsVisible()) updateDialog.Show(Config::AppVersion, version);
        }
        if (updateDialog.IsVisible()) updateDialog.Update(dt);
        if (IsIconic(window.GetHwnd())) {
            timer.SleepRemaining(12.0f);
            continue;
        }

        // Render pass
        renderer.BeginDraw();
        const float w = window.GetWidth() / window.GetScale();
        const float h = window.GetHeight() / window.GetScale();

        if (!splashView.IsFinished() || showInitialSetup) {
            renderer.DrawBackground(w, h, timer.GetTotalTime());
            starfield.Render(renderer.GetRenderTarget(), renderer.GetSolidBrush());
        } else {
            renderer.Clear(Theme::Instance().bg);
        }

        if (splashView.IsFinished()) {
            titleBar.Render(renderer);

            if (showAuth) {
                authView.Render(renderer);
            } else if (showInitialSetup) {
                initialSetupView.Render(renderer);
            } else {
                shellView.Render(renderer);
            }
        } else {
            float mainAlpha = splashView.GetMainUiAlpha();
            if (mainAlpha > 0.005f) {
                float mainScale = splashView.GetMainUiScale();
                float mainOffsetY = splashView.GetMainUiOffsetY();

                float cx = w * 0.5f;
                float cy = h * 0.5f;
                D2D1_MATRIX_3X2_F xform = D2D1::Matrix3x2F::Scale(mainScale, mainScale, D2D1::Point2F(cx, cy))
                                        * D2D1::Matrix3x2F::Translation(0.0f, mainOffsetY);
                renderer.GetRenderTarget()->SetTransform(xform);
                renderer.PushLayer(mainAlpha);

                titleBar.Render(renderer);

                if (showAuth) {
                    authView.Render(renderer);
                } else if (showInitialSetup) {
                    initialSetupView.Render(renderer);
                } else {
                    shellView.Render(renderer);
                }

                renderer.PopLayer();
                renderer.GetRenderTarget()->SetTransform(D2D1::Matrix3x2F::Identity());
            }

            splashView.Render(renderer, w, h);
        }

        Toast::Instance().Render(renderer, w, h);
        if (updateDialog.IsVisible()) updateDialog.Render(renderer);

        renderer.EndDraw();
        timer.SleepRemaining(GetForegroundWindow() == window.GetHwnd() ? 60.0f : 24.0f);
    }

        logStep("10. Loop exited, shutting down");
        DiscordRpc::Instance().Shutdown();
        CoUninitialize();

        return 0;
    } catch (const std::exception& e) {
        const std::filesystem::path appData = SettingsManager::GetAppDataAeroDir();
        std::ofstream f(appData / L"crash.log", std::ios::app);
        f << "std::exception: " << e.what() << "\n";
        return 1;
    } catch (...) {
        const std::filesystem::path appData = SettingsManager::GetAppDataAeroDir();
        std::ofstream f(appData / L"crash.log", std::ios::app);
        f << "unknown C++ exception\n";
        return 1;
    }
}
