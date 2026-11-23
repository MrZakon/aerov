#include "Window.h"
#include <dwmapi.h>
#include <windowsx.h>
#include <shellapi.h>
#include <fstream>
#include <algorithm>

#ifdef IsMaximized
#undef IsMaximized
#endif

#pragma comment(lib, "dwmapi.lib")

#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif

namespace aero {

static const wchar_t* CLASS_NAME = L"AeroLauncherWindowClass";

Window::Window() = default;

Window::~Window() {
    Close();
}

bool Window::Create(const std::wstring& title, int width, int height) {
    const auto user32 = GetModuleHandleW(L"user32.dll");
    const auto queryDpi = reinterpret_cast<UINT(WINAPI*)()>(GetProcAddress(user32, "GetDpiForSystem"));
    if (queryDpi) dpi_ = queryDpi();

    RECT workArea{0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN)};
    SystemParametersInfoW(SPI_GETWORKAREA, 0, &workArea, 0);
    width_ = (std::min)(MulDiv(width, dpi_, 96), static_cast<int>(workArea.right - workArea.left));
    height_ = (std::min)(MulDiv(height, dpi_, 96), static_cast<int>(workArea.bottom - workArea.top));

    WNDCLASSEXW wc = { sizeof(WNDCLASSEXW) };
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = Window::WndProc;
    wc.hInstance = GetModuleHandleW(nullptr);
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.lpszClassName = CLASS_NAME;
    wc.hIcon = LoadIcon(wc.hInstance, MAKEINTRESOURCE(101));

    RegisterClassExW(&wc);

    // Calculate center screen position
    const int posX = workArea.left + (workArea.right - workArea.left - width_) / 2;
    const int posY = workArea.top + (workArea.bottom - workArea.top - height_) / 2;

    DWORD style = WS_POPUP | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_CAPTION;

    hwnd_ = CreateWindowExW(
        WS_EX_APPWINDOW,
        CLASS_NAME,
        title.c_str(),
        style,
        posX, posY, width_, height_,
        nullptr, nullptr, wc.hInstance, this
    );

    if (!hwnd_) return false;

    // Enable DWM shadow and keep custom client area
    MARGINS margins = { 1, 1, 1, 1 };
    DwmExtendFrameIntoClientArea(hwnd_, &margins);

    // Windows 11 Rounded corners
    DWORD cornerPref = 2; // DWMWCP_ROUND
    DwmSetWindowAttribute(hwnd_, DWMWA_WINDOW_CORNER_PREFERENCE, &cornerPref, sizeof(cornerPref));

    // Enable drag and drop
    DragAcceptFiles(hwnd_, TRUE);

    isOpen_ = true;
    return true;
}

void Window::Show() {
    if (hwnd_) {
        ShowWindow(hwnd_, SW_SHOW);
        UpdateWindow(hwnd_);
        SetWindowPos(hwnd_, nullptr, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED);
    }
}

void Window::Close() {
    if (hwnd_) {
        DestroyWindow(hwnd_);
        hwnd_ = nullptr;
        isOpen_ = false;
    }
}

void Window::Minimize() {
    if (hwnd_) {
        ShowWindow(hwnd_, SW_MINIMIZE);
    }
}

void Window::Maximize() {
    if (hwnd_) {
        if (IsMaximized()) {
            ShowWindow(hwnd_, SW_RESTORE);
        } else {
            ShowWindow(hwnd_, SW_MAXIMIZE);
        }
    }
}

bool Window::IsMaximized() const {
    if (!hwnd_) return false;
    WINDOWPLACEMENT wp = { sizeof(WINDOWPLACEMENT) };
    GetWindowPlacement(hwnd_, &wp);
    return wp.showCmd == SW_SHOWMAXIMIZED;
}

void Window::PollEvents() {
    MSG msg;
    while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) {
        if (msg.message == WM_QUIT) {
            isOpen_ = false;
            break;
        }
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
}

LRESULT CALLBACK Window::WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    Window* self = nullptr;
    if (msg == WM_NCCREATE) {
        auto* cs = reinterpret_cast<CREATESTRUCTW*>(lp);
        self = reinterpret_cast<Window*>(cs->lpCreateParams);
        SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
    } else {
        self = reinterpret_cast<Window*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
    }

    if (self) {
        return self->HandleMessage(hwnd, msg, wp, lp);
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

LRESULT Window::HandleMessage(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_GETMINMAXINFO: {
        auto* limits = reinterpret_cast<MINMAXINFO*>(lp);
        MONITORINFO monitor{sizeof(MONITORINFO)};
        const auto screen = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
        const POINT preferred{MulDiv(1000, dpi_, 96), MulDiv(664, dpi_, 96)};
        limits->ptMinTrackSize = preferred;
        if (GetMonitorInfoW(screen, &monitor)) {
            limits->ptMinTrackSize.x = (std::min)(preferred.x, monitor.rcWork.right - monitor.rcWork.left);
            limits->ptMinTrackSize.y = (std::min)(preferred.y, monitor.rcWork.bottom - monitor.rcWork.top);
        }
        return 0;
    }
    case WM_NCCALCSIZE: {
        if (wp == TRUE) {
            // Remove native titlebar and borders
            return 0;
        }
        break;
    }

    case WM_NCHITTEST: {
        POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) };
        ScreenToClient(hwnd, &pt);

        const int border = 6;
        RECT rc;
        GetClientRect(hwnd, &rc);

        if (!IsMaximized()) {
            if (pt.y < border && pt.x < border) return HTTOPLEFT;
            if (pt.y < border && pt.x >= rc.right - border) return HTTOPRIGHT;
            if (pt.y >= rc.bottom - border && pt.x < border) return HTBOTTOMLEFT;
            if (pt.y >= rc.bottom - border && pt.x >= rc.right - border) return HTBOTTOMRIGHT;
            if (pt.y < border) return HTTOP;
            if (pt.y >= rc.bottom - border) return HTBOTTOM;
            if (pt.x < border) return HTLEFT;
            if (pt.x >= rc.right - border) return HTRIGHT;
        }

        // Draggable custom titlebar area (excluding rightmost window control buttons)
        // Buttons occupy the rightmost ~120px in the title bar
        if (pt.y < MulDiv(titleBarHeight_, dpi_, 96)) {
            if (pt.x < rc.right - MulDiv(125, dpi_, 96)) {
                return HTCAPTION;
            }
        }
        return HTCLIENT;
    }

    case WM_DPICHANGED: {
        dpi_ = HIWORD(wp);
        const auto* rect = reinterpret_cast<const RECT*>(lp);
        SetWindowPos(hwnd, nullptr, rect->left, rect->top, rect->right - rect->left, rect->bottom - rect->top,
            SWP_NOZORDER | SWP_NOACTIVATE);
        if (onResize_) onResize_(width_, height_);
        return 0;
    }
    case WM_SIZE: {
        if (wp == SIZE_MINIMIZED) return 0;
        width_ = LOWORD(lp);
        height_ = HIWORD(lp);
        if (onResize_) {
            onResize_(width_, height_);
        }
        return 0;
    }

    case WM_MOUSEMOVE: {
        float x = static_cast<float>(GET_X_LPARAM(lp));
        float y = static_cast<float>(GET_Y_LPARAM(lp));
        if (onMouseMove_) {
            onMouseMove_(x / GetScale(), y / GetScale());
        }
        return 0;
    }

    case WM_LBUTTONDOWN:
    case WM_RBUTTONDOWN:
    case WM_MBUTTONDOWN: {
        float x = static_cast<float>(GET_X_LPARAM(lp));
        float y = static_cast<float>(GET_Y_LPARAM(lp));
        SetCapture(hwnd);
        MouseBtn btn = (msg == WM_LBUTTONDOWN) ? MouseBtn::Left : ((msg == WM_RBUTTONDOWN) ? MouseBtn::Right : MouseBtn::Middle);
        if (onMouseButton_) {
            onMouseButton_(x / GetScale(), y / GetScale(), btn, true);
        }
        return 0;
    }

    case WM_LBUTTONUP:
    case WM_RBUTTONUP:
    case WM_MBUTTONUP: {
        float x = static_cast<float>(GET_X_LPARAM(lp));
        float y = static_cast<float>(GET_Y_LPARAM(lp));
        ReleaseCapture();
        MouseBtn btn = (msg == WM_LBUTTONUP) ? MouseBtn::Left : ((msg == WM_RBUTTONUP) ? MouseBtn::Right : MouseBtn::Middle);
        if (onMouseButton_) {
            onMouseButton_(x / GetScale(), y / GetScale(), btn, false);
        }
        return 0;
    }

    case WM_MOUSEWHEEL: {
        POINT pt = { GET_X_LPARAM(lp), GET_Y_LPARAM(lp) };
        ScreenToClient(hwnd, &pt);
        float delta = static_cast<float>(GET_WHEEL_DELTA_WPARAM(wp)) / WHEEL_DELTA;
        if (onMouseWheel_) {
            onMouseWheel_(pt.x / GetScale(), pt.y / GetScale(), delta);
        }
        return 0;
    }

    case WM_CHAR: {
        wchar_t ch = static_cast<wchar_t>(wp);
        if (onChar_) {
            onChar_(ch);
        }
        return 0;
    }

    case WM_KEYDOWN: {
        if (onKey_) {
            onKey_(static_cast<int>(wp), true);
        }
        return 0;
    }

    case WM_KEYUP: {
        if (onKey_) {
            onKey_(static_cast<int>(wp), false);
        }
        return 0;
    }

    case WM_DROPFILES: {
        HDROP hDrop = reinterpret_cast<HDROP>(wp);
        UINT fileCount = DragQueryFileW(hDrop, 0xFFFFFFFF, nullptr, 0);
        std::vector<std::wstring> files;
        files.reserve(fileCount);
        for (UINT i = 0; i < fileCount; ++i) {
            wchar_t path[MAX_PATH];
            if (DragQueryFileW(hDrop, i, path, MAX_PATH)) {
                files.push_back(path);
            }
        }
        DragFinish(hDrop);
        if (onDropFiles_) {
            onDropFiles_(files);
        }
        return 0;
    }

    case WM_CLOSE: {
        {
            std::ofstream f("app.log", std::ios::app);
            f << "WM_CLOSE received\n";
        }
        isOpen_ = false;
        PostQuitMessage(0);
        return 0;
    }

    case WM_DESTROY: {
        {
            std::ofstream f("app.log", std::ios::app);
            f << "WM_DESTROY received\n";
        }
        isOpen_ = false;
        PostQuitMessage(0);
        return 0;
    }
    }

    return DefWindowProcW(hwnd, msg, wp, lp);
}

} // namespace aero
