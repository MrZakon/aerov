#pragma once
#include "../ui/UIElement.h"
#include "../ui/TextInput.h"
#include "../ui/Button.h"
#include "../ui/ScrollView.h"
#include "../store/AccountManager.h"
#include "../auth/MicrosoftAuth.h"
#include <vector>
#include <future>

namespace aero {

class AccountsView : public UIElement {
public:
    AccountsView();

    void Resize(float width, float height);
    void RefreshData();
    void CancelSignIn();
    void PollRedeem();
    void SetOnRedeemed(std::function<void()> callback) { onRedeemed_ = std::move(callback); }

    void Update(float dt) override;
    void Render(D2DRenderer& r) override;

    bool OnMouseMove(float x, float y) override;
    bool OnMouseButton(float x, float y, MouseBtn btn, bool down) override;
    bool OnMouseWheel(float x, float y, float delta) override;
    bool OnChar(wchar_t ch) override;
    bool OnKey(int vk, bool down) override;

private:
    void UpdateRowBounds();
    void AddOfflineAccount();
    void RedeemKey();

    TextInput inputNick_{ L"Steve_01" };
    Button btnAddOffline_{ L"Добавить", ButtonStyle::Primary };

    Button btnMsLogin_{ L"Войти через Microsoft ↗", ButtonStyle::Ghost };
    Button btnCopyMsCode_{ L"Скопировать код", ButtonStyle::Ghost };
    Button btnCancelMs_{ L"Отмена", ButtonStyle::Danger };
    MicrosoftDeviceFlow microsoft_;

    TextInput inputRedeem_{ L"AERO-XXXX-XXXX" };
    Button btnRedeem_{ L"Активировать", ButtonStyle::Primary };
    Button btnCopyHwid_{ L"Копировать", ButtonStyle::Ghost };
    std::string redeemToken_;
    std::future<std::string> redeemRequest_;
    std::function<void()> onRedeemed_;

    struct AccountRow {
        std::string id;
        std::wstring username;
        std::wstring typeLabel;
        AccountType type = AccountType::Offline;
        bool isActive = false;
        AnimatedFloat hoverAnim{ 0.0f, 15.0f };
        Button btnSelect{ L"Выбрать", ButtonStyle::Primary };
        Button btnDelete{ L"✕", ButtonStyle::Danger };
    };

    std::vector<AccountRow> rows_;
    ScrollView scrollView_;
    bool needRefresh_ = false;
};

} // namespace aero
