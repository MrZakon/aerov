#pragma once
#include "UIElement.h"
#include <vector>
#include <string>
#include <functional>
#include "../core/Animation.h"

namespace aero {

class ComboBox : public UIElement {
public:
    ComboBox(const std::vector<std::wstring>& items = {});

    void SetItems(const std::vector<std::wstring>& items);
    void SetSelectedIndex(int index);
    int GetSelectedIndex() const { return selectedIndex_; }
    std::wstring GetSelectedItem() const;

    void SetOnSelect(std::function<void(int, const std::wstring&)> cb) { onSelect_ = cb; }

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;
    void RenderDropdown(D2DRenderer& r); // Render after all other elements so it sits on top

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;
    bool OnKey(int vk, bool down) override;

    bool IsExpanded() const { return isExpanded_; }
    void CloseDropdown();

private:
    void OpenDropdown();
    void SelectItem(int index);
    void RevealItem(int index);

    std::vector<std::wstring> items_;
    int selectedIndex_ = 0;
    bool isExpanded_ = false;
    int hoveredItemIndex_ = -1;
    std::function<void(int, const std::wstring&)> onSelect_;
    AnimatedFloat expandAnim_{ 0.0f, 22.0f };

    float scrollY_ = 0.0f;
    float targetScrollY_ = 0.0f;
    float maxVisibleHeight_ = 240.0f;
};

} // namespace aero
