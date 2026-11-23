#pragma once
#include <string>
#include <cstdint>
#include <mutex>
#include <nlohmann/json.hpp>

namespace aero {

struct UserSubscription {
    bool active = false;
    std::string plan = "free";
    std::string status = "none";
    std::string startsAt;
    std::string endsAt;
    int daysLeft = 0;
    bool lifetime = false;
    bool canDownloadMod = false;
};

struct UserSession {
    std::string token;
    std::string userId;
    std::string username;
    std::string email;
    std::string role;
    std::string plan;
    std::string planLabel;
    UserSubscription subscription;
    bool hasSubscription = false;
    bool isOffline = false;
    bool verified = false;
};

enum class SessionRefresh { Ready, Unavailable, SignedOut, Superseded };

struct AuthReply {
    UserSession session;
    std::string error;
    bool useBrowser = false;
};

enum class WebLoginState { Waiting, Confirmed, Retry, Ended };

struct WebLoginReply {
    WebLoginState state = WebLoginState::Ended;
    AuthReply auth;
};

class AeroAuth {
public:
    static AeroAuth& Instance();

    bool IsAuthorized() const;
    UserSession GetSession() const;
    std::string GetOnlineToken() const;

    static AuthReply RequestLogin(const std::string& login, const std::string& password);
    static WebLoginReply PollWebLogin(const std::string& sessionId);
    void AcceptSession(UserSession session);

    bool Login(const std::string& login, const std::string& password, std::string* outError = nullptr);
    bool Register(const std::string& username, const std::string& email, const std::string& password, std::string* outError = nullptr);
    bool LoginWithToken(const std::string& token, std::string* outError = nullptr);
    void LoginOffline(const std::string& username);
    static std::string Redeem(const std::string& code, const std::string& token);
    bool Refresh(std::string* outError = nullptr);
    SessionRefresh RefreshSession();
    void Logout();
    static void Revoke(const std::string& token);

    // Web Session OAuth
    static bool StartWebSession(std::string& outSessionId, std::string& outAuthUrl, std::string* outError = nullptr);
    bool CheckWebSession(const std::string& sessionId, bool& outCompleted, std::string* outError = nullptr);
    bool CompleteWebSession(const nlohmann::json& response, std::string* outError = nullptr);

    void LoadSession();

private:
    AeroAuth();
    SessionRefresh RefreshImpl(std::string* outError);
    void SaveSessionLocked() const;
    void RemoveSessionFilesLocked() const;

    std::wstring sessionFilePath_;
    mutable std::mutex mutex_;
    std::uint64_t revision_ = 0;
    UserSession session_;
};

} // namespace aero
