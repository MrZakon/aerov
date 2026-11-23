#pragma once
#include <string>
#include <vector>
#include <cstdint>
#include <unordered_map>

namespace aero {

enum class AccountType {
    Offline,
    Microsoft
};

struct Account {
    std::string id;
    AccountType type = AccountType::Offline;
    std::string username;
    std::string uuid;
    std::string accessToken;
    std::string skinUrl;

    // Microsoft specific
    std::string refreshToken;
    int64_t expiresAt = 0;
};

class AccountManager {
public:
    static AccountManager& Instance();

    void Load();
    bool Save();

    const std::vector<Account>& GetAccounts() const { return accounts_; }
    Account* GetActiveAccount();
    void SetActiveAccount(const std::string& accountId);

    Account* AddOfflineAccount(const std::string& nickname);
    bool AddMicrosoftAccount(const std::string& username, const std::string& uuid, const std::string& token,
        const std::string& refreshToken, std::int64_t expiresAt);
    bool RemoveAccount(const std::string& accountId);
    void UpdateAccount(const Account& acc);
    void ClearAeroAccount();

    static std::string GenerateOfflineUuid(const std::string& nickname);

private:
    AccountManager();
    std::wstring accountsFilePath_;
    std::vector<Account> accounts_;
    std::unordered_map<std::string, std::string> unreadableTokens_;
};

} // namespace aero
