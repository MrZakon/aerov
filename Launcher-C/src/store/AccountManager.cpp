#include "AccountManager.h"
#include "SettingsManager.h"
#include "../core/Logger.h"
#include <windows.h>
#include <bcrypt.h>
#include <wincrypt.h>
#include <algorithm>
#include <optional>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <filesystem>
#include <utility>
#include <nlohmann/json.hpp>

namespace aero {

namespace fs = std::filesystem;

namespace {
using Json = nlohmann::json;

struct ProtectedBlob {
    DATA_BLOB data{};
    ~ProtectedBlob() {
        if (data.pbData) {
            SecureZeroMemory(data.pbData, data.cbData);
            LocalFree(data.pbData);
        }
    }
};

DATA_BLOB AsBlob(const std::string& bytes) {
    return {static_cast<DWORD>(bytes.size()),
        reinterpret_cast<BYTE*>(const_cast<char*>(bytes.data()))};
}

std::optional<std::string> ProtectMicrosoftTokens(const Account& account) {
    auto plaintext = Json{{"access", account.accessToken}, {"refresh", account.refreshToken}}.dump();
    auto input = AsBlob(plaintext);
    auto accountId = AsBlob(account.id);
    ProtectedBlob protectedData;
    const auto ok = CryptProtectData(&input, L"Aero Launcher Microsoft profile", &accountId,
        nullptr, nullptr, CRYPTPROTECT_UI_FORBIDDEN, &protectedData.data);
    SecureZeroMemory(plaintext.data(), plaintext.size());
    if (!ok) return std::nullopt;

    constexpr DWORD format = CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF;
    DWORD size = 0;
    if (!CryptBinaryToStringA(protectedData.data.pbData, protectedData.data.cbData, format, nullptr, &size)) {
        return std::nullopt;
    }
    std::string encoded(size, '\0');
    if (!CryptBinaryToStringA(protectedData.data.pbData, protectedData.data.cbData, format, encoded.data(), &size)) {
        return std::nullopt;
    }
    if (const auto terminator = encoded.find('\0'); terminator != std::string::npos) {
        encoded.resize(terminator);
    }
    return encoded;
}

bool UnprotectMicrosoftTokens(const std::string& encoded, Account& account) {
    if (encoded.empty()) return false;
    constexpr DWORD format = CRYPT_STRING_BASE64;
    const auto encodedSize = static_cast<DWORD>(encoded.size());
    DWORD size = 0;
    if (!CryptStringToBinaryA(encoded.data(), encodedSize, format, nullptr, &size, nullptr, nullptr)) return false;
    std::vector<BYTE> bytes(size);
    if (!CryptStringToBinaryA(encoded.data(), encodedSize, format, bytes.data(), &size, nullptr, nullptr)) return false;

    DATA_BLOB input{size, bytes.data()};
    auto accountId = AsBlob(account.id);
    ProtectedBlob plaintext;
    if (!CryptUnprotectData(&input, nullptr, &accountId, nullptr, nullptr,
        CRYPTPROTECT_UI_FORBIDDEN, &plaintext.data)) return false;

    if (plaintext.data.cbData == 0) return false;
    const auto begin = plaintext.data.pbData;
    const auto end = begin + plaintext.data.cbData;
    auto decoded = Json::parse(begin, end, nullptr, false);
    if (!decoded.is_object() || !decoded.contains("access") || !decoded["access"].is_string() ||
        !decoded.contains("refresh") || !decoded["refresh"].is_string()) return false;
    account.accessToken = std::move(decoded["access"].get_ref<std::string&>());
    account.refreshToken = std::move(decoded["refresh"].get_ref<std::string&>());
    return true;
}
} // namespace

AccountManager::AccountManager() {
    std::wstring appData = SettingsManager::GetAppDataAeroDir();
    accountsFilePath_ = (fs::path(appData) / L"accounts.json").wstring();
}

AccountManager& AccountManager::Instance() {
    static AccountManager s_instance;
    return s_instance;
}

std::string AccountManager::GenerateOfflineUuid(const std::string& nickname) {
    std::string input = "OfflinePlayer:" + nickname;

    BCRYPT_ALG_HANDLE hAlg = nullptr;
    if (BCryptOpenAlgorithmProvider(&hAlg, BCRYPT_MD5_ALGORITHM, nullptr, 0) != 0) {
        return "00000000-0000-3000-8000-000000000000";
    }

    DWORD hashObjSize = 0, dataLen = 0;
    BCryptGetProperty(hAlg, BCRYPT_OBJECT_LENGTH, reinterpret_cast<PBYTE>(&hashObjSize), sizeof(DWORD), &dataLen, 0);
    DWORD hashLen = 16;

    std::vector<BYTE> hashObj(hashObjSize);
    std::vector<BYTE> hashResult(hashLen);

    BCRYPT_HASH_HANDLE hHash = nullptr;
    if (BCryptCreateHash(hAlg, &hHash, hashObj.data(), hashObjSize, nullptr, 0, 0) == 0) {
        BCryptHashData(hHash, reinterpret_cast<PBYTE>(const_cast<char*>(input.data())), static_cast<ULONG>(input.length()), 0);
        BCryptFinishHash(hHash, hashResult.data(), hashLen, 0);
        BCryptDestroyHash(hHash);
    }
    BCryptCloseAlgorithmProvider(hAlg, 0);

    // RFC 4122 version 3 UUID adjustments
    hashResult[6] = (hashResult[6] & 0x0F) | 0x30;
    hashResult[8] = (hashResult[8] & 0x3F) | 0x80;

    std::ostringstream ss;
    ss << std::hex << std::setfill('0');
    for (int i = 0; i < 4; ++i) ss << std::setw(2) << static_cast<int>(hashResult[i]);
    ss << "-";
    for (int i = 4; i < 6; ++i) ss << std::setw(2) << static_cast<int>(hashResult[i]);
    ss << "-";
    for (int i = 6; i < 8; ++i) ss << std::setw(2) << static_cast<int>(hashResult[i]);
    ss << "-";
    for (int i = 8; i < 10; ++i) ss << std::setw(2) << static_cast<int>(hashResult[i]);
    ss << "-";
    for (int i = 10; i < 16; ++i) ss << std::setw(2) << static_cast<int>(hashResult[i]);

    return ss.str();
}

void AccountManager::Load() {
    accounts_.clear();
    unreadableTokens_.clear();
    std::ifstream file{fs::path(accountsFilePath_)};
    if (!file) return;
    const auto saved = Json::parse(file, nullptr, false);
    file.close();

    const Json* list = nullptr;
    if (saved.is_array()) list = &saved;
    else if (saved.contains("items") && saved["items"].is_array()) list = &saved["items"];
    else return;

    bool migrate = false;
    bool canRewrite = true;
    for (const auto& item : *list) {
        if (!item.is_object()) {
            canRewrite = false;
            continue;
        }
        try {
            Account account;
            account.id = item.value("id", "");
            const auto type = item.value("type", "offline");
            const auto auth = item.value("auth", "");
            if (type == "aero" || account.id.starts_with("aero_")) {
                migrate = true;
                continue;
            }
            account.type = type == "microsoft" || auth == "microsoft" || type == "online"
                ? AccountType::Microsoft : AccountType::Offline;
            account.username = item.value("username", item.value("nickname", ""));
            account.uuid = item.value("uuid", "");
            account.skinUrl = item.value("skinUrl", "");
            account.expiresAt = item.value("expiresAt", 0LL);

            if (account.username.empty()) {
                canRewrite = false;
                continue;
            }
            if (account.id.empty()) {
                account.id = (account.type == AccountType::Microsoft ? "ms_" : "offline_") + account.username;
            }
            const auto duplicate = std::find_if(accounts_.begin(), accounts_.end(), [&](const Account& existing) {
                return existing.id == account.id || (existing.username == account.username && existing.type == account.type);
            });
            if (duplicate != accounts_.end()) continue;

            if (account.type == AccountType::Microsoft) {
                if (item.contains("protectedTokens")) {
                    const auto& protectedTokens = item["protectedTokens"];
                    if (protectedTokens.is_string()) {
                        auto encoded = protectedTokens.get<std::string>();
                        if (!UnprotectMicrosoftTokens(encoded, account)) {
                            unreadableTokens_.emplace(account.id, std::move(encoded));
                            account.expiresAt = 0;
                        }
                    } else {
                        canRewrite = false;
                    }
                } else {
                    account.accessToken = item.value("accessToken", "");
                    account.refreshToken = item.value("refreshToken", item.value("msaRefreshToken", ""));
                    migrate |= !account.accessToken.empty() || !account.refreshToken.empty();
                }
            } else {
                account.accessToken = "offline";
                if (account.uuid.empty()) account.uuid = GenerateOfflineUuid(account.username);
            }

            accounts_.push_back(std::move(account));
        } catch (const Json::exception&) {
            canRewrite = false;
        }
    }

    ClearAeroAccount();
    if (migrate && canRewrite) Save();
}

bool AccountManager::Save() {
    Json records = Json::array();
    for (const auto& account : accounts_) {
        Json item{
            {"id", account.id}, {"type", account.type == AccountType::Microsoft ? "microsoft" : "offline"},
            {"username", account.username}, {"uuid", account.uuid}, {"skinUrl", account.skinUrl},
            {"expiresAt", account.expiresAt}
        };
        if (account.type == AccountType::Microsoft) {
            if (!account.accessToken.empty() || !account.refreshToken.empty()) {
                auto encrypted = ProtectMicrosoftTokens(account);
                if (!encrypted) {
                    Logger::Warn("[Auth] Не удалось защитить данные профиля Microsoft");
                    return false;
                }
                item["protectedTokens"] = std::move(*encrypted);
            } else if (const auto saved = unreadableTokens_.find(account.id); saved != unreadableTokens_.end()) {
                item["protectedTokens"] = saved->second;
            }
        }
        records.push_back(std::move(item));
    }

    const auto tmpPath = accountsFilePath_ + L"." + std::to_wstring(GetCurrentProcessId()) + L".tmp";
    std::ofstream file{fs::path(tmpPath), std::ios::binary | std::ios::trunc};
    file << records.dump(2);
    file.close();
    if (!file || !MoveFileExW(tmpPath.c_str(), accountsFilePath_.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
        std::error_code ec;
        fs::remove(tmpPath, ec);
        Logger::Warn("[Auth] Не удалось сохранить игровые профили");
        return false;
    }
    return true;
}

Account* AccountManager::GetActiveAccount() {
    auto& activeId = SettingsManager::Instance().Get().activeAccountId;
    if (activeId.empty()) return nullptr;
    for (auto& acc : accounts_) {
        if (acc.id == activeId) return &acc;
    }
    return nullptr;
}

void AccountManager::SetActiveAccount(const std::string& accountId) {
    SettingsManager::Instance().Get().activeAccountId = accountId;
    SettingsManager::Instance().Save();
}

Account* AccountManager::AddOfflineAccount(const std::string& nickname) {
    for (auto& acc : accounts_) {
        if (acc.type == AccountType::Offline && acc.username == nickname) {
            SetActiveAccount(acc.id);
            return &acc;
        }
    }

    Account acc;
    acc.id = "offline_" + nickname;
    acc.type = AccountType::Offline;
    acc.username = nickname;
    acc.uuid = GenerateOfflineUuid(nickname);
    acc.accessToken = "offline";
    accounts_.push_back(acc);

    SetActiveAccount(acc.id);
    Save();
    return &accounts_.back();
}

bool AccountManager::AddMicrosoftAccount(const std::string& username, const std::string& uuid, const std::string& token,
    const std::string& refreshToken, std::int64_t expiresAt) {
    for (auto& acc : accounts_) {
        if (acc.type == AccountType::Microsoft && (acc.uuid == uuid || (acc.refreshToken.empty() && acc.username == username))) {
            acc.username = username;
            if (!uuid.empty()) acc.uuid = uuid;
            if (!token.empty()) acc.accessToken = token;
            if (!refreshToken.empty()) acc.refreshToken = refreshToken;
            acc.expiresAt = expiresAt;
            unreadableTokens_.erase(acc.id);
            SetActiveAccount(acc.id);
            return Save();
        }
    }

    Account acc;
    acc.id = "ms_" + uuid;
    acc.type = AccountType::Microsoft;
    acc.username = username;
    acc.uuid = uuid.empty() ? GenerateOfflineUuid(username) : uuid;
    acc.accessToken = token.empty() ? "microsoft_license" : token;
    acc.refreshToken = refreshToken;
    acc.expiresAt = expiresAt;
    accounts_.push_back(acc);

    SetActiveAccount(acc.id);
    return Save();
}

bool AccountManager::RemoveAccount(const std::string& accountId) {
    const auto found = std::find_if(accounts_.begin(), accounts_.end(), [&](const Account& account) {
        return account.id == accountId;
    });
    if (found == accounts_.end()) return false;

    const auto index = found - accounts_.begin();
    auto removed = std::move(*found);
    auto encrypted = unreadableTokens_.extract(removed.id);
    accounts_.erase(found);
    if (!Save()) {
        accounts_.insert(accounts_.begin() + index, std::move(removed));
        if (!encrypted.empty()) unreadableTokens_.insert(std::move(encrypted));
        return false;
    }
    if (SettingsManager::Instance().Get().activeAccountId == removed.id) {
        SetActiveAccount(accounts_.empty() ? "" : accounts_.front().id);
    }
    return true;
}

void AccountManager::UpdateAccount(const Account& updated) {
    for (auto& acc : accounts_) {
        if (acc.id == updated.id) {
            acc = updated;
            Save();
            return;
        }
    }
    accounts_.push_back(updated);
    Save();
}

void AccountManager::ClearAeroAccount() {
    auto& activeId = SettingsManager::Instance().Get().activeAccountId;
    const auto originalSize = accounts_.size();
    accounts_.erase(std::remove_if(accounts_.begin(), accounts_.end(), [](const Account& a) {
        return a.id.rfind("aero_", 0) == 0;
    }), accounts_.end());
    if (activeId.rfind("aero_", 0) == 0) {
        activeId = accounts_.empty() ? "" : accounts_[0].id;
        SettingsManager::Instance().Save();
    }
    if (originalSize != accounts_.size()) Save();
}

} // namespace aero
