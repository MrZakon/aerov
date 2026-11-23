#pragma once
#include "../ui/UIElement.h"
#include "../ui/TextInput.h"
#include "../ui/Button.h"
#include "../ui/ScrollView.h"
#include <vector>
#include <string>
#include <mutex>

namespace aero {

struct LogEntry {
    std::string time;
    std::string text;
    bool isError = false;
    bool isWarn = false;
};

class ConsoleView : public UIElement {
public:
    ConsoleView();
    ~ConsoleView() override;

    void Resize(float width, float height);
    void AppendLog(const std::string& line, bool isError);
    void Clear();

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

private:
    struct LogSink {
        std::mutex mutex;
        ConsoleView* view = nullptr;
    };
    void CopyToClipboard();
    std::shared_ptr<LogSink> sink_ = std::make_shared<LogSink>();

    TextInput inputFilter_{ L"Фильтр по тексту..." };
    Button btnClear_{ L"Очистить", ButtonStyle::Default };
    Button btnCopy_{ L"Скопировать", ButtonStyle::Default };
    Button btnAutoScroll_{ L"Автоскролл: ВКЛ", ButtonStyle::Primary };
    Button btnKill_{ L"Остановить игру", ButtonStyle::Danger };

    std::vector<LogEntry> logs_;
    mutable std::mutex logsMutex_;
    ScrollView scrollView_;
    bool autoScroll_ = true;
    float animTime_ = 0.0f;
    std::string lastFilter_;
};

} // namespace aero
