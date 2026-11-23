#pragma once

#include "../auth/AeroAuth.h"
#include "../ui/Button.h"
#include "../ui/TextInput.h"
#include <chrono>
#include <functional>
#include <future>
#include <vector>

namespace aero {

class AuthView : public UIElement {
public:
    AuthView();
    void Resize(float width, float height);
    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

    void SetOnSuccess(std::function<void()> callback) { onSuccess_ = std::move(callback); }
    void Reset(const std::wstring& notice = {});

private:
    enum class Page { Browser, Password, Local };
    struct WebStart {
        std::string id;
        std::string url;
        std::string error;
    };
    using Clock = std::chrono::steady_clock;

    void Show(Page page);
    void BeginBrowser();
    void OpenBrowser();
    void CancelBrowser();
    void SubmitPassword();
    void SubmitLocal();
    void PollRequests();
    void RefreshButtons();
    std::vector<UIElement*> Controls();
    void Complete(UserSession session);

    Page page_ = Page::Browser;
    bool browserActive_ = false;
    bool browserRetrying_ = false;
    bool needsBrowser_ = false;
    std::string browserId_;
    std::string browserUrl_;
    Clock::time_point expiresAt_{};
    Clock::time_point pollAt_{};
    std::wstring notice_;
    bool isError_ = false;
    D2D1_RECT_F form_{};
    D2D1_RECT_F artwork_{};
    bool split_ = false;

    TextInput login_{L"Никнейм или email"};
    TextInput password_{L"Пароль Aero ID"};
    TextInput nickname_{L"Никнейм Minecraft"};
    Button browser_{L"Продолжить в браузере", ButtonStyle::Primary};
    Button cancel_{L"Отменить вход", ButtonStyle::Ghost};
    Button submit_{L"Войти", ButtonStyle::Primary};
    Button back_{L"К способам входа", ButtonStyle::Ghost};
    Button passwordMode_{L"Войти по паролю", ButtonStyle::Default};
    Button localMode_{L"Играть локально", ButtonStyle::Ghost};
    Button help_{L"Создать аккаунт или восстановить пароль", ButtonStyle::Ghost};

    std::function<void()> onSuccess_;
    std::future<WebStart> webStart_;
    std::future<WebLoginReply> webPoll_;
    std::future<AuthReply> passwordRequest_;
};

} // namespace aero
