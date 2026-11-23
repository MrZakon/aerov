#include "ConsoleView.h"
#include "../game/GameLauncher.h"
#include "../core/Logger.h"
#include "../core/Config.h"
#include "../ui/Toast.h"
#include "../core/StringUtils.h"
#include <chrono>
#include <iomanip>
#include <sstream>

namespace aero {

ConsoleView::ConsoleView() {
    sink_->view = this;
    Logger::AddCallback([weak = std::weak_ptr<LogSink>(sink_)](const std::string& line, bool isErr, bool) {
        if (const auto sink = weak.lock()) {
            std::lock_guard lock(sink->mutex);
            if (sink->view) sink->view->AppendLog(line, isErr);
        }
    });

    btnClear_.SetOnClick([this]() { Clear(); });
    btnCopy_.SetOnClick([this]() { CopyToClipboard(); });
    btnAutoScroll_.SetOnClick([this]() {
        autoScroll_ = !autoScroll_;
        btnAutoScroll_.SetText(autoScroll_ ? L"Автоскролл: ВКЛ" : L"Автоскролл: ВЫКЛ");
        btnAutoScroll_.SetStyle(autoScroll_ ? ButtonStyle::Primary : ButtonStyle::Default);
        if (autoScroll_) {
            scrollView_.ScrollToBottom(false);
        }
    });
    btnKill_.SetOnClick([]() {
        GameLauncher::Instance().TerminateGame();
        Toast::Instance().Show(L"Процесс игры принудительно остановлен", ToastKind::Info);
    });

    AppendLog("Aero Launcher " + WideToUtf8(Config::AppVersion), false);
}

ConsoleView::~ConsoleView() {
    std::lock_guard lock(sink_->mutex);
    sink_->view = nullptr;
}

void ConsoleView::Clear() {
    std::lock_guard<std::mutex> lock(logsMutex_);
    logs_.clear();
    scrollView_.SetContentHeight(0);
    scrollView_.ResetScroll();
}

void ConsoleView::AppendLog(const std::string& line, bool isError) {
    auto now = std::chrono::system_clock::now();
    auto in_time_t = std::chrono::system_clock::to_time_t(now);
    std::stringstream ss;
    struct tm buf;
    localtime_s(&buf, &in_time_t);
    ss << std::put_time(&buf, "%H:%M:%S");
    std::string timeStr = ss.str();

    std::stringstream stream(line);
    std::string subline;

    while (std::getline(stream, subline)) {
        if (!subline.empty() && subline.back() == '\r') {
            subline.pop_back();
        }
        if (subline.empty()) continue;

        LogEntry entry;
        entry.time = timeStr;
        entry.text = subline;
        entry.isError = isError || (subline.find("ERROR") != std::string::npos) || (subline.find("Exception") != std::string::npos);
        entry.isWarn = (subline.find("WARN") != std::string::npos);

        {
            std::lock_guard<std::mutex> lock(logsMutex_);
            logs_.push_back(entry);
            if (logs_.size() > 3000) {
                logs_.erase(logs_.begin());
            }
        }
    }
}

void ConsoleView::CopyToClipboard() {
    std::wstring fullText;
    {
        std::lock_guard<std::mutex> lock(logsMutex_);
        for (const auto& l : logs_) {
            fullText += Utf8ToWide("[" + l.time + "] " + l.text + "\r\n");
        }
    }

    if (OpenClipboard(nullptr)) {
        EmptyClipboard();
        HGLOBAL hMem = GlobalAlloc(GMEM_MOVEABLE, (fullText.length() + 1) * sizeof(wchar_t));
        if (hMem) {
            auto* pData = static_cast<wchar_t*>(GlobalLock(hMem));
            memcpy(pData, fullText.data(), (fullText.length() + 1) * sizeof(wchar_t));
            GlobalUnlock(hMem);
            SetClipboardData(CF_UNICODETEXT, hMem);
        }
        CloseClipboard();
        Toast::Instance().Show(L"Логи скопированы в буфер обмена", ToastKind::Success);
    }
}

void ConsoleView::Resize(float width, float height) {
    SetBounds(0, 0, width, height);

    float startX = 36.0f;
    float startY = 24.0f;
    float contentW = width - 72.0f;

    const auto filterW = (std::min)(252.0f, contentW - 460.0f);
    inputFilter_.SetBounds(startX, startY + 40.0f, filterW, 36.0f);
    btnClear_.SetBounds(startX + filterW + 8, startY + 40.0f, 76, 36.0f);
    btnCopy_.SetBounds(startX + filterW + 92, startY + 40.0f, 96, 36.0f);
    btnAutoScroll_.SetBounds(startX + filterW + 196, startY + 40.0f, 112, 36.0f);
    btnKill_.SetBounds(startX + contentW - 136.0f, startY + 40.0f, 136.0f, 36.0f);

    float logY = startY + 90.0f;
    float logH = height - logY - 20.0f;
    scrollView_.SetBounds(startX, logY, contentW, logH);
}

void ConsoleView::Update(float dt) {
    animTime_ += dt;
    inputFilter_.Update(dt);
    btnClear_.Update(dt);
    btnCopy_.Update(dt);
    btnAutoScroll_.Update(dt);
    btnKill_.Update(dt);

    float contentW = bounds_.right - bounds_.left - 72.0f;
    float availTextW = (std::max)(100.0f, contentW - 90.0f);
    int charsPerLine = (std::max)(15, static_cast<int>(availTextW / 6.6f));

    std::string curFilter = WideToUtf8(inputFilter_.GetText());
    float totalH = 10.0f;
    {
        std::lock_guard<std::mutex> lock(logsMutex_);
        for (const auto& log : logs_) {
            if (!curFilter.empty() && log.text.find(curFilter) == std::string::npos) {
                continue;
            }
            int lineCount = (std::max)(1, (static_cast<int>(log.text.length()) + charsPerLine - 1) / charsPerLine);
            totalH += lineCount * 18.0f + 2.0f;
        }
    }
    scrollView_.SetContentHeight(totalH + 16.0f);

    if (autoScroll_) {
        scrollView_.ScrollToBottom(false);
    }
    scrollView_.Update(dt);
}

bool ConsoleView::OnMouseMove(float x, float y) {
    bool handled = false;
    handled |= inputFilter_.OnMouseMove(x, y);
    handled |= btnClear_.OnMouseMove(x, y);
    handled |= btnCopy_.OnMouseMove(x, y);
    handled |= btnAutoScroll_.OnMouseMove(x, y);
    handled |= btnKill_.OnMouseMove(x, y);
    handled |= scrollView_.OnMouseMove(x, y);

    if (scrollView_.IsDraggingScrollbar()) {
        if (autoScroll_ && !scrollView_.IsAtBottom(30.0f)) {
            autoScroll_ = false;
            btnAutoScroll_.SetText(L"Автоскролл: ВЫКЛ");
            btnAutoScroll_.SetStyle(ButtonStyle::Default);
        } else if (!autoScroll_ && scrollView_.IsAtBottom(30.0f)) {
            autoScroll_ = true;
            btnAutoScroll_.SetText(L"Автоскролл: ВКЛ");
            btnAutoScroll_.SetStyle(ButtonStyle::Primary);
        }
    }
    return handled;
}

bool ConsoleView::OnMouseButton(float x, float y, MouseBtn btn, bool down) {
    if (scrollView_.OnMouseButton(x, y, btn, down)) {
        if (scrollView_.IsDraggingScrollbar()) {
            if (autoScroll_ && !scrollView_.IsAtBottom(30.0f)) {
                autoScroll_ = false;
                btnAutoScroll_.SetText(L"Автоскролл: ВЫКЛ");
                btnAutoScroll_.SetStyle(ButtonStyle::Default);
            } else if (!autoScroll_ && scrollView_.IsAtBottom(30.0f)) {
                autoScroll_ = true;
                btnAutoScroll_.SetText(L"Автоскролл: ВКЛ");
                btnAutoScroll_.SetStyle(ButtonStyle::Primary);
            }
        }
        return true;
    }
    if (inputFilter_.OnMouseButton(x, y, btn, down)) return true;
    if (btnClear_.OnMouseButton(x, y, btn, down)) return true;
    if (btnCopy_.OnMouseButton(x, y, btn, down)) return true;
    if (btnAutoScroll_.OnMouseButton(x, y, btn, down)) return true;
    if (btnKill_.OnMouseButton(x, y, btn, down)) return true;
    return false;
}

bool ConsoleView::OnMouseWheel(float x, float y, float delta) {
    bool handled = scrollView_.OnMouseWheel(x, y, delta);
    if (handled) {
        if (delta > 0.0f) {
            // User scrolled upwards: pause auto-scrolling
            if (autoScroll_) {
                autoScroll_ = false;
                btnAutoScroll_.SetText(L"Автоскролл: ВЫКЛ");
                btnAutoScroll_.SetStyle(ButtonStyle::Default);
            }
        } else {
            // User scrolled downwards: resume if bottom reached
            if (!autoScroll_ && scrollView_.IsAtBottom(30.0f)) {
                autoScroll_ = true;
                btnAutoScroll_.SetText(L"Автоскролл: ВКЛ");
                btnAutoScroll_.SetStyle(ButtonStyle::Primary);
            }
        }
    }
    return handled;
}

bool ConsoleView::OnChar(wchar_t ch) {
    return inputFilter_.OnChar(ch);
}

bool ConsoleView::OnKey(int vk, bool down) {
    return inputFilter_.OnKey(vk, down);
}

void ConsoleView::Render(D2DRenderer& r) {
    auto& theme = Theme::Instance();

    float startX = 36.0f;
    float startY = 24.0f;
    float contentW = bounds_.right - 72.0f;

    r.DrawString(L"Журнал Minecraft", D2D1::RectF(startX, startY, startX + 240.0f, startY + 30.0f), theme.text, 18.0f, FontStyle::Bold);

    // Live indicator
    float pulse = (std::sin(animTime_ * 3.0f) + 1.0f) * 0.5f;
    float dotX = startX + 252.0f;
    float dotY = startY + 12.0f;
    D2D1_RECT_F liveDot = D2D1::RectF(dotX - 3.5f, dotY - 3.5f, dotX + 3.5f, dotY + 3.5f);
    r.FillRoundedRect(liveDot, 2.0f, theme.mint);

    r.DrawString(L"LIVE", D2D1::RectF(dotX + 8.0f, dotY - 7.0f, dotX + 60.0f, dotY + 9.0f), theme.mint, 9.0f, FontStyle::Bold);

    inputFilter_.Render(r);
    btnClear_.Render(r);
    btnCopy_.Render(r);
    btnAutoScroll_.Render(r);
    btnKill_.Render(r);

    float logY = startY + 90.0f;
    float logH = bounds_.bottom - logY - 20.0f;
    D2D1_RECT_F bgRect = D2D1::RectF(startX, logY, startX + contentW, logY + logH);

    r.FillRoundedRect(bgRect, 8.0f, Color::FromHex(0x252b24));
    r.DrawRoundedRect(bgRect, 8.0f, Color::FromRgba(240, 183, 130, 0.15f + pulse * 0.10f), 1.0f);
    r.DrawLine(D2D1::Point2F(startX + 4.0f, logY + 1.0f), D2D1::Point2F(startX + contentW - 4.0f, logY + 1.0f), Color::FromRgba(240, 183, 130, 0.25f), 1.0f);

    scrollView_.BeginRender(r);

    std::string filterStr = WideToUtf8(inputFilter_.GetText());

    std::vector<LogEntry> displayLogs;
    {
        std::lock_guard<std::mutex> lock(logsMutex_);
        displayLogs = logs_;
    }

    float availTextW = (std::max)(100.0f, contentW - 90.0f);
    int charsPerLine = (std::max)(15, static_cast<int>(availTextW / 6.6f));
    float scrollOffset = scrollView_.GetScrollOffset();

    float lineY = logY + 8.0f;
    for (const auto& log : displayLogs) {
        if (!filterStr.empty() && log.text.find(filterStr) == std::string::npos) {
            continue;
        }

        int lineCount = (std::max)(1, (static_cast<int>(log.text.length()) + charsPerLine - 1) / charsPerLine);
        float entryH = lineCount * 18.0f + 2.0f;

        // Viewport culling for buttery-smooth rendering
        float curScreenY = lineY - scrollOffset;
        if (curScreenY + entryH >= logY - 30.0f && curScreenY <= logY + logH + 30.0f) {
            D2D1_COLOR_F lineCol = theme.text;
            if (log.isError) lineCol = theme.red;
            else if (log.isWarn) lineCol = theme.amber;

            // Draw time
            r.DrawString(Utf8ToWide(log.time), D2D1::RectF(startX + 10.0f, lineY, startX + 70.0f, lineY + 18.0f), theme.muted, 11.0f, FontStyle::Mono);

            // Draw text with its full dynamic height so wrapped lines are clean
            r.DrawString(Utf8ToWide(log.text), D2D1::RectF(startX + 80.0f, lineY, startX + contentW - 10.0f, lineY + entryH), lineCol, 11.0f, FontStyle::Mono);
        }

        lineY += entryH;
    }

    scrollView_.EndRender(r);
}

} // namespace aero

