#pragma once
#include <windows.h>
#include <string>
#include <functional>
#include <vector>

namespace aero {

enum class MouseBtn {
    Left,
    Right,
    Middle
};

struct MouseEvent {
    float x;
    float y;
    MouseBtn button;
    bool isDown;
};

class Window {
public:
    Window();
    ~Window();

    bool Create(const std::wstring& title, int width = 1280, int height = 800);
    void Show();
    void Close();
    void Minimize();
    void Maximize();
    bool IsMaximized() const;

    HWND GetHwnd() const { return hwnd_; }
    int GetWidth() const { return width_; }
    int GetHeight() const { return height_; }
    float GetScale() const { return dpi_ / 96.0f; }
    bool IsOpen() const { return isOpen_; }

    void PollEvents();

    // Event callbacks
    using ResizeCallback = std::function<void(int, int)>;
    using MouseMoveCallback = std::function<void(float, float)>;
    using MouseButtonCallback = std::function<void(float, float, MouseBtn, bool)>;
    using MouseWheelCallback = std::function<void(float, float, float)>;
    using CharCallback = std::function<void(wchar_t)>;
    using KeyCallback = std::function<void(int, bool)>;
    using DropFilesCallback = std::function<void(const std::vector<std::wstring>&)>;

    void SetOnResize(ResizeCallback cb) { onResize_ = cb; }
    void SetOnMouseMove(MouseMoveCallback cb) { onMouseMove_ = cb; }
    void SetOnMouseButton(MouseButtonCallback cb) { onMouseButton_ = cb; }
    void SetOnMouseWheel(MouseWheelCallback cb) { onMouseWheel_ = cb; }
    void SetOnChar(CharCallback cb) { onChar_ = cb; }
    void SetOnKey(KeyCallback cb) { onKey_ = cb; }
    void SetOnDropFiles(DropFilesCallback cb) { onDropFiles_ = cb; }

    void SetCustomTitleBarHeight(int height) { titleBarHeight_ = height; }

private:
    static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp);
    LRESULT HandleMessage(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp);

    HWND hwnd_ = nullptr;
    int width_ = 1280;
    int height_ = 800;
    UINT dpi_ = 96;
    int titleBarHeight_ = 60;
    bool isOpen_ = false;

    ResizeCallback onResize_;
    MouseMoveCallback onMouseMove_;
    MouseButtonCallback onMouseButton_;
    MouseWheelCallback onMouseWheel_;
    CharCallback onChar_;
    KeyCallback onKey_;
    DropFilesCallback onDropFiles_;
};

} // namespace aero
