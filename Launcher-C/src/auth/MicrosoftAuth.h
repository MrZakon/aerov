#pragma once
#include <string>
#include <chrono>
#include <future>
#include <optional>
#include <cstdint>

namespace aero {

struct DeviceCodeResponse {
    std::string deviceCode;
    std::string userCode;
    std::string verificationUrl;
    int interval = 5;
    int expiresIn = 900;
};

struct MicrosoftProfile {
    std::string nickname;
    std::string uuid;
    std::string accessToken;
    std::string refreshToken;
    std::int64_t expiresAt = 0;
};

class MicrosoftAuth {
public:
    static bool StartDeviceCode(DeviceCodeResponse& outResp, std::string* outError = nullptr);
    static bool PollDeviceToken(const std::string& deviceCode, std::string& outAccessToken, std::string& outRefreshToken,
        bool& outPending, std::string* outError = nullptr, bool* slowDown = nullptr);
    static bool CompleteLogin(const std::string& msaToken, const std::string& refreshToken, MicrosoftProfile& outProfile, std::string* outError = nullptr);
    static bool RefreshProfile(const std::string& refreshToken, MicrosoftProfile& outProfile, std::string* outError = nullptr);
};

class MicrosoftDeviceFlow {
public:
    struct Result {
        MicrosoftProfile profile;
        std::string error;
    };

    bool Start();
    void Cancel();
    std::optional<Result> Update();
    bool IsActive() const { return active_; }
    const std::string& Code() const { return device_.userCode; }
    const std::string& Status() const { return status_; }
    bool OpenBrowser() const;

private:
    using Clock = std::chrono::steady_clock;
    struct StartResult { DeviceCodeResponse device; std::string error; };
    struct TokenResult { std::string token, refresh, error; bool pending = false, slowDown = false; };
    std::future<StartResult> start_;
    std::future<TokenResult> poll_;
    std::future<Result> finish_;
    DeviceCodeResponse device_;
    bool active_ = false;
    std::string status_;
    Clock::time_point expiry_{};
    Clock::time_point nextPoll_{};
};

} // namespace aero
