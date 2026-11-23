#pragma once
#include "../ui/UIElement.h"
#include "../ui/Button.h"
#include "../ui/ScrollView.h"
#include "../store/InstancesManager.h"
#include <vector>

namespace aero {

class GalleryView : public UIElement {
public:
    GalleryView();

    void Resize(float width, float height);
    void RefreshData();

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;

private:
    struct ScreenshotCard {
        ScreenshotInfo info;
        AnimatedFloat hoverAnim{ 0.0f, 15.0f };
    };

    Button btnOpenFolder_{ L"Открыть папку скриншотов", ButtonStyle::Default };
    std::vector<ScreenshotCard> cards_;
    ScrollView scrollView_;
    float animTime_ = 0.0f;
};

} // namespace aero
