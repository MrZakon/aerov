#include "AuthView.h"
#include "../core/Config.h"
#include "../core/StringUtils.h"
#include "../store/AccountManager.h"
#include "../ui/Toast.h"
#include "../render/AeroDesign.h"
#include <shellapi.h>
#include <algorithm>

namespace aero {

namespace {
using namespace std::chrono_literals;

template <class Result>
bool Ready(const std::future<Result>& future) {
    return future.valid() && future.wait_for(0ms) == std::future_status::ready;
}

bool Open(const std::string& url) {
    return reinterpret_cast<INT_PTR>(ShellExecuteW(nullptr, L"open", Utf8ToWide(url).c_str(),
        nullptr, nullptr, SW_SHOWNORMAL)) > 32;
}

std::string Trim(const std::string& value) {
    const auto first = value.find_first_not_of(" \r\n\t");
    if (first == std::string::npos) return {};
    return value.substr(first, value.find_last_not_of(" \r\n\t") - first + 1);
}
} // namespace

AuthView::AuthView() {
    password_.SetPassword(true);
    login_.SetOnEnter([this] {
        login_.SetFocused(false);
        password_.SetFocused(true);
    });
    password_.SetOnEnter([this] { SubmitPassword(); });
    nickname_.SetOnEnter([this] { SubmitLocal(); });
    browser_.SetOnClick([this] { browserActive_ ? OpenBrowser() : BeginBrowser(); });
    cancel_.SetOnClick([this] { CancelBrowser(); });
    passwordMode_.SetOnClick([this] { Show(Page::Password); });
    localMode_.SetOnClick([this] { Show(Page::Local); });
    back_.SetOnClick([this] { Show(Page::Browser); });
    submit_.SetOnClick([this] { page_ == Page::Local ? SubmitLocal() : SubmitPassword(); });
    help_.SetOnClick([this] {
        if (!Open(Config::DefaultBaseUrl + "/access/")) {
            notice_ = L"Не удалось открыть сайт AeroProject";
            isError_ = true;
        }
    });
}

void AuthView::Resize(float width, float height) {
    SetBounds(0, 60, width, height);
    split_ = width >= 896;
    const float formW = split_ ? 402.0f : (std::min)(402.0f, width - 64);
    const float rightAreaW = split_ ? 500.0f : width;
    const float x = width - (rightAreaW + formW) / 2;
    const float y = (std::max)(84.0f, 60 + (height - 60 - 444.0f) * 0.5f);
    form_ = D2D1::RectF(x, y, x + formW, y + 444);
    artwork_ = D2D1::RectF(20, 80, width - rightAreaW, height - 20);

    browser_.SetBounds(x, y + (needsBrowser_ ? 326 : 132), formW, 48);
    cancel_.SetBounds(x, y + 192, formW, 36);
    passwordMode_.SetBounds(x, y + 256, formW, 44);
    localMode_.SetBounds(x, y + 312, formW, 36);
    help_.SetBounds(x, y + 388, formW, 40);
    login_.SetBounds(x, y + 140, formW, 44);
    password_.SetBounds(x, y + 212, formW, 44);
    nickname_.SetBounds(x, y + 140, formW, 44);
    submit_.SetBounds(x, y + (page_ == Page::Local ? 216 : 272), formW, 44);
    back_.SetBounds(x, y + (page_ == Page::Local ? 276 : 382), formW, 40);
}

void AuthView::Show(Page page) {
    for (auto* control : Controls()) control->SetFocused(false);
    page_ = page;
    needsBrowser_ = false;
    notice_.clear();
    isError_ = false;
    password_.SetText(L"");
    for (auto* field : {&login_, &password_, &nickname_}) field->SetFocused(false);
    if (page == Page::Password) login_.SetFocused(true);
    if (page == Page::Local) nickname_.SetFocused(true);
    Resize(bounds_.right, bounds_.bottom);
    RefreshButtons();
}

void AuthView::Reset(const std::wstring& notice) {
    CancelBrowser();
    login_.SetText(L"");
    nickname_.SetText(L"");
    Show(Page::Browser);
    notice_ = notice;
    isError_ = !notice.empty();
}

void AuthView::BeginBrowser() {
    if (webStart_.valid() || webPoll_.valid() || passwordRequest_.valid()) return;
    if (page_ != Page::Browser) Show(Page::Browser);
    browserActive_ = true;
    browserRetrying_ = false;
    isError_ = false;
    notice_.clear();
    expiresAt_ = Clock::now() + 10min;
    webStart_ = std::async(std::launch::async, [] {
        WebStart start;
        AeroAuth::StartWebSession(start.id, start.url, &start.error);
        return start;
    });
    RefreshButtons();
}

void AuthView::OpenBrowser() {
    if (!browserUrl_.empty() && !Open(browserUrl_)) {
        notice_ = L"Браузер не открылся. Попробуйте ещё раз.";
        isError_ = true;
    } else {
        notice_.clear();
        isError_ = false;
    }
}

void AuthView::CancelBrowser() {
    browserActive_ = false;
    browserRetrying_ = false;
    browserId_.clear();
    browserUrl_.clear();
    notice_.clear();
    isError_ = false;
    RefreshButtons();
}

void AuthView::SubmitPassword() {
    if (passwordRequest_.valid()) return;
    auto login = Trim(WideToUtf8(login_.GetText()));
    auto password = WideToUtf8(password_.GetText());
    if (login.empty() || password.empty()) {
        notice_ = L"Введите никнейм или email и пароль";
        isError_ = true;
        login_.SetFocused(login.empty());
        password_.SetFocused(!login.empty());
        return;
    }
    needsBrowser_ = false;
    notice_.clear();
    isError_ = false;
    password_.SetText(L"");
    passwordRequest_ = std::async(std::launch::async, [login = std::move(login), password = std::move(password)] {
        return AeroAuth::RequestLogin(login, password);
    });
    Resize(bounds_.right, bounds_.bottom);
    RefreshButtons();
}

void AuthView::SubmitLocal() {
    const auto nickname = Trim(WideToUtf8(nickname_.GetText()));
    if (nickname.size() < 3 || nickname.size() > 16 ||
        nickname.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_") != std::string::npos) {
        notice_ = L"Никнейм должен содержать 3–16 латинских букв, цифр или знаков _";
        isError_ = true;
        return;
    }
    AeroAuth::Instance().LoginOffline(nickname);
    AccountManager::Instance().AddOfflineAccount(nickname);
    if (onSuccess_) onSuccess_();
}

void AuthView::Complete(UserSession session) {
    CancelBrowser();
    AeroAuth::Instance().AcceptSession(std::move(session));
    Toast::Instance().Show(L"Вход в Aero ID выполнен", ToastKind::Success);
    if (onSuccess_) onSuccess_();
}

void AuthView::PollRequests() {
    if (browserActive_ && Clock::now() >= expiresAt_) {
        CancelBrowser();
        notice_ = L"Время ожидания истекло. Начните вход снова.";
        isError_ = true;
    }
    if (Ready(webStart_)) {
        try {
            auto result = webStart_.get();
            if (browserActive_) {
                if (result.id.empty()) {
                    CancelBrowser();
                    notice_ = result.error.empty() ? L"Не удалось связаться с Aero ID" : Utf8ToWide(result.error);
                    isError_ = true;
                } else {
                    browserId_ = std::move(result.id);
                    browserUrl_ = std::move(result.url);
                    pollAt_ = Clock::now() + 2s;
                    OpenBrowser();
                }
            }
        } catch (const std::exception&) {
            if (browserActive_) {
                CancelBrowser();
                notice_ = L"Не удалось подготовить вход через браузер";
                isError_ = true;
            }
        }
    }
    if (Ready(webPoll_)) {
        WebLoginReply result;
        try { result = webPoll_.get(); }
        catch (const std::exception&) { result.state = WebLoginState::Retry; }
        if (browserActive_) {
            switch (result.state) {
            case WebLoginState::Confirmed:
                Complete(std::move(result.auth.session));
                return;
            case WebLoginState::Waiting:
                browserRetrying_ = false;
                pollAt_ = Clock::now() + 2s;
                break;
            case WebLoginState::Retry:
                browserRetrying_ = true;
                pollAt_ = Clock::now() + 5s;
                break;
            case WebLoginState::Ended:
                CancelBrowser();
                notice_ = Utf8ToWide(result.auth.error.empty() ? "Вход завершился. Попробуйте снова." : result.auth.error);
                isError_ = true;
                break;
            }
        }
    }
    if (browserActive_ && !browserId_.empty() && !webPoll_.valid() && Clock::now() >= pollAt_) {
        webPoll_ = std::async(std::launch::async, [id = browserId_] { return AeroAuth::PollWebLogin(id); });
    }
    if (Ready(passwordRequest_)) {
        AuthReply result;
        try { result = passwordRequest_.get(); }
        catch (const std::exception&) { result.error = "Не удалось выполнить вход"; }
        if (result.error.empty()) {
            Complete(std::move(result.session));
            return;
        }
        needsBrowser_ = result.useBrowser;
        notice_ = Utf8ToWide(result.error);
        isError_ = !needsBrowser_;
        if (!needsBrowser_) password_.SetFocused(true);
        Resize(bounds_.right, bounds_.bottom);
    }
}

void AuthView::RefreshButtons() {
    browser_.SetText(browserActive_ ? (browserUrl_.empty() ? L"Подготовка ссылки…" : L"Открыть браузер снова") : L"Продолжить в браузере");
    browser_.SetEnabled(browserActive_ ? !browserUrl_.empty() : !webStart_.valid() && !webPoll_.valid());
    submit_.SetText(page_ == Page::Local ? L"Продолжить локально" : passwordRequest_.valid() ? L"Проверяем данные…" : L"Войти в Aero ID");
    submit_.SetEnabled(!passwordRequest_.valid());
    login_.SetEnabled(!passwordRequest_.valid());
    password_.SetEnabled(!passwordRequest_.valid());
    back_.SetEnabled(!passwordRequest_.valid());
}

std::vector<UIElement*> AuthView::Controls() {
    if (page_ == Page::Local) return {&nickname_, &submit_, &back_};
    if (page_ == Page::Password) {
        if (needsBrowser_) return {&login_, &password_, &submit_, &browser_, &back_};
        return {&login_, &password_, &submit_, &back_};
    }
    if (browserActive_) return {&browser_, &cancel_};
    return {&browser_, &passwordMode_, &localMode_, &help_};
}

void AuthView::Update(float dt) {
    PollRequests();
    RefreshButtons();
    for (auto* control : Controls()) control->Update(dt);
}

bool AuthView::OnMouseMove(float x, float y) {
    bool handled = false;
    for (auto* control : Controls()) handled |= control->OnMouseMove(x, y);
    return handled;
}

bool AuthView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    auto controls = Controls();
    if (btn == MouseBtn::Left && down) {
        for (auto* control : controls) control->SetFocused(control->IsEnabled() && control->Contains(x, y));
    }
    bool handled = false;
    for (auto* control : controls) handled |= control->OnMouseButton(x, y, btn, down);
    return handled;
}

bool AuthView::OnChar(wchar_t ch) {
    for (auto* control : Controls()) {
        if (control->IsEnabled() && control->IsFocused()) return control->OnChar(ch);
    }
    return false;
}

bool AuthView::OnKey(int vk, bool down) {
    if (!down) return false;
    if (vk == VK_ESCAPE) {
        if (browserActive_) CancelBrowser();
        else if (!passwordRequest_.valid() && page_ != Page::Browser) Show(Page::Browser);
        return true;
    }
    auto controls = Controls();
    if (vk == VK_TAB) {
        std::erase_if(controls, [](UIElement* control) { return !control->IsEnabled(); });
        if (controls.empty()) return true;
        const auto focused = std::find_if(controls.begin(), controls.end(), [](UIElement* control) { return control->IsFocused(); });
        const bool reverse = (GetKeyState(VK_SHIFT) & 0x8000) != 0;
        if (focused == controls.end()) (reverse ? controls.back() : controls.front())->SetFocused(true);
        else {
            (*focused)->SetFocused(false);
            const auto index = static_cast<size_t>(focused - controls.begin());
            controls[(index + (reverse ? controls.size() - 1 : 1)) % controls.size()]->SetFocused(true);
        }
        return true;
    }
    for (auto* control : controls) {
        if (!control->IsFocused() || !control->IsEnabled()) continue;
        if (control->OnKey(vk, down)) return true;
    }
    if (vk == VK_RETURN && page_ == Page::Browser && browser_.IsEnabled()) {
        browserActive_ ? OpenBrowser() : BeginBrowser();
        return true;
    }
    return false;
}

void AuthView::Render(D2DRenderer& r) {
    const auto& t = Theme::Instance();
    r.FillRect(bounds_, t.bg);
    if (split_) {
        aero::Landscape(r, artwork_);
        const float left = artwork_.left + 32;
        const float bottom = artwork_.bottom;
        r.DrawString(L"AERO", D2D1::RectF(left, artwork_.top + 32, artwork_.right - 24, artwork_.top + 88),
            Color::FromHex(0xfff5e5), 40, FontStyle::Bold);
        r.DrawString(L"Открой свой\nследующий мир.", D2D1::RectF(left, bottom - 168, artwork_.right - 24, bottom - 58),
            Color::FromHex(0xfff5e5), 34, FontStyle::Bold);
        r.DrawString(L"Твои сборки, моды и приключения — в одном месте.",
            D2D1::RectF(left, bottom - 36, artwork_.right - 24, bottom - 8), Color::FromHex(0xc4c9b8), 12);
    }
    aero::Panel(r, D2D1::RectF(form_.left - 24, form_.top - 12, form_.right + 24, form_.bottom + 28));
    const auto x = form_.left;
    const auto y = form_.top;
    const auto right = form_.right;
    const auto title = page_ == Page::Local ? L"Локальная игра" : L"Вход в Aero ID";
    const auto description = page_ == Page::Local ? L"Сохраните игровой профиль Minecraft на этом компьютере."
        : page_ == Page::Password ? L"Войдите с никнеймом или email вашего аккаунта."
        : L"Подключите аккаунт AeroProject и откройте свои сборки AeroVisuals.";

    r.DrawString(title, D2D1::RectF(x, y + 8, right, y + 58), t.text, 34, FontStyle::Bold);
    r.DrawString(description, D2D1::RectF(x, y + 64, right, y + 116), t.muted, 15);

    if (page_ == Page::Browser && !browserActive_) {
        r.DrawLine(D2D1::Point2F(x, y + 236), D2D1::Point2F(right, y + 236), t.line);
        r.DrawLine(D2D1::Point2F(x, y + 372), D2D1::Point2F(right, y + 372), t.line);
    }
    if (page_ == Page::Password) {
        r.DrawString(L"Аккаунт", D2D1::RectF(x, y + 118, right, y + 138), t.text, 12);
        r.DrawString(L"Пароль", D2D1::RectF(x, y + 190, right, y + 210), t.text, 12);
    }
    if (page_ == Page::Local) {
        r.DrawString(L"Имя игрока", D2D1::RectF(x, y + 118, right, y + 138), t.text, 12);
    }
    if (browserActive_ && !browserUrl_.empty()) {
        const auto status = browserRetrying_ ? L"Связь прервалась. Повторяем проверку…" : L"Подтвердите вход на сайте AeroProject";
        r.DrawString(status, D2D1::RectF(x, y + 248, right, y + 278), t.text, 14, FontStyle::Bold);
    }
    for (auto* control : Controls()) control->Render(r);
    if (!notice_.empty()) {
        const float noticeY = y + (page_ == Page::Browser ? (browserActive_ ? 286 : 186)
            : page_ == Page::Local ? 332 : needsBrowser_ ? 426 : 326);
        r.DrawString(notice_, D2D1::RectF(x, noticeY, right, noticeY + 48), isError_ ? t.red : t.muted, 13);
    }
}

} // namespace aero
