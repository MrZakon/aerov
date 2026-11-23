#include "MicrosoftAuth.h"
#include "../net/HttpClient.h"
#include "../core/Logger.h"
#include "../core/StringUtils.h"
#include <algorithm>
#include <shellapi.h>
#include <nlohmann/json.hpp>

namespace aero {

static const std::string kClientId = "00000000402B5328";
static const std::string kScope = "service::user.auth.xboxlive.com::MBI_SSL";
static const std::string kTokenUrl = "https://login.live.com/oauth20_token.srf";

static std::string EncodeFormValue(const std::string& value) {
    constexpr char hex[] = "0123456789ABCDEF";
    std::string encoded;
    for (const unsigned char byte : value) {
        if ((byte >= 'a' && byte <= 'z') || (byte >= 'A' && byte <= 'Z') ||
            (byte >= '0' && byte <= '9') || byte == '-' || byte == '_' || byte == '.' || byte == '~') {
            encoded += static_cast<char>(byte);
        } else {
            encoded += '%';
            encoded += hex[byte >> 4];
            encoded += hex[byte & 15];
        }
    }
    return encoded;
}

bool MicrosoftAuth::StartDeviceCode(DeviceCodeResponse& outResp, std::string* outError) {
    Logger::Info("[MicrosoftAuth] Запрос кода авторизации (Device Code)...");
    std::string form = "client_id=" + kClientId +
                       "&scope=" + kScope +
                       "&response_type=device_code";

    auto resp = HttpClient::PostForm("https://login.live.com/oauth20_connect.srf", form);
    if (!resp.isSuccess || !resp.json.is_object() || !resp.json.contains("device_code")) {
        std::string err = resp.error.empty() ? "Не удалось получить код устройства Microsoft" : resp.error;
        Logger::Error("[MicrosoftAuth] Ошибка получения кода: " + err);
        if (outError) *outError = err;
        return false;
    }

    outResp.deviceCode = resp.json.value("device_code", "");
    outResp.userCode = resp.json.value("user_code", "");
    outResp.verificationUrl = resp.json.value("verification_uri", "https://www.microsoft.com/link");
    outResp.interval = resp.json.value("interval", 5);
    outResp.expiresIn = resp.json.value("expires_in", 900);

    const auto& url = outResp.verificationUrl;
    if (outResp.deviceCode.empty() || outResp.userCode.empty() ||
        !(url == "https://www.microsoft.com/link" || url == "https://microsoft.com/link" || url == "https://microsoft.com/devicelogin" ||
          url == "https://www.microsoft.com/devicelogin" || url == "https://login.live.com/oauth20_remoteconnect.srf")) {
        if (outError) *outError = "Microsoft вернул некорректный адрес входа";
        return false;
    }
    return true;
}

bool MicrosoftAuth::PollDeviceToken(
    const std::string& deviceCode,
    std::string& outAccessToken,
    std::string& outRefreshToken,
    bool& outPending,
    std::string* outError,
    bool* slowDown
) {
    outPending = false;
    if (slowDown) *slowDown = false;
    std::string form = "client_id=" + kClientId +
                       "&grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                       "&device_code=" + EncodeFormValue(deviceCode);

    auto resp = HttpClient::PostForm(kTokenUrl, form);
    if (resp.isSuccess && resp.json.is_object() && resp.json.contains("access_token")) {
        outAccessToken = resp.json.value("access_token", "");
        outRefreshToken = resp.json.value("refresh_token", "");
        Logger::Info("[MicrosoftAuth] Токен авторизации получен, продолжаю вход...");
        return true;
    }

    std::string err = resp.json.is_object() ? resp.json.value("error", "") : "";
    if (err == "authorization_pending" || err == "slow_down" || resp.statusCode == 0 ||
        resp.statusCode == 429 || resp.statusCode >= 500) {
        outPending = true;
        if (slowDown) *slowDown = err != "authorization_pending";
        return false;
    }
    if (err == "authorization_declined") {
        Logger::Warn("[MicrosoftAuth] Вход отклонён пользователем в браузере");
        if (outError) *outError = "Вход отменён в браузере";
        return false;
    }
    if (err == "expired_token" || err == "code_expired") {
        Logger::Warn("[MicrosoftAuth] Время действия кода Microsoft истекло");
        if (outError) *outError = "Код устарел — начни вход заново";
        return false;
    }

    std::string desc = resp.error.empty() ? "Ошибка авторизации Microsoft" : resp.error;
    Logger::Error("[MicrosoftAuth] Ошибка опроса токена: " + desc);
    if (outError) *outError = desc;
    return false;
}

bool MicrosoftAuth::CompleteLogin(
    const std::string& msaToken,
    const std::string& refreshToken,
    MicrosoftProfile& outProfile,
    std::string* outError
) {
    // 1. Xbox Live authentication
    Logger::Info("[MicrosoftAuth] Аутентификация в Xbox Live...");
    nlohmann::json xblReq;
    xblReq["Properties"]["AuthMethod"] = "RPS";
    xblReq["Properties"]["SiteName"] = "user.auth.xboxlive.com";
    xblReq["Properties"]["RpsTicket"] = "t=" + msaToken;
    xblReq["RelyingParty"] = "http://auth.xboxlive.com";
    xblReq["TokenType"] = "JWT";

    auto xblResp = HttpClient::Post("https://user.auth.xboxlive.com/user/authenticate", xblReq.dump());
    if (!xblResp.isSuccess || !xblResp.json.is_object() || !xblResp.json.contains("Token")) {
        Logger::Error("[MicrosoftAuth] Xbox Live отклонил авторизацию Microsoft (" + xblResp.error + ")");
        if (outError) *outError = "Xbox Live отклонил авторизацию Microsoft";
        return false;
    }

    std::string xblToken = xblResp.json.value("Token", "");
    std::string uhs;
    if (xblResp.json.contains("DisplayClaims") &&
        xblResp.json["DisplayClaims"].contains("xui") &&
        xblResp.json["DisplayClaims"]["xui"].is_array() &&
        !xblResp.json["DisplayClaims"]["xui"].empty()) {
        uhs = xblResp.json["DisplayClaims"]["xui"][0].value("uhs", "");
    }

    // 2. XSTS authorization
    Logger::Info("[MicrosoftAuth] Получение XSTS токена безопасности...");
    nlohmann::json xstsReq;
    xstsReq["Properties"]["SandboxId"] = "RETAIL";
    xstsReq["Properties"]["UserTokens"] = nlohmann::json::array({ xblToken });
    xstsReq["RelyingParty"] = "rp://api.minecraftservices.com/";
    xstsReq["TokenType"] = "JWT";

    auto xstsResp = HttpClient::Post("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq.dump());
    if (!xstsResp.isSuccess || !xstsResp.json.is_object() || !xstsResp.json.contains("Token")) {
        Logger::Error("[MicrosoftAuth] Не удалось получить XSTS-токен (" + xstsResp.error + ")");
        if (outError) *outError = "Не удалось получить XSTS-токен Xbox Live";
        return false;
    }
    std::string xstsToken = xstsResp.json.value("Token", "");

    // 3. Minecraft login with Xbox
    Logger::Info("[MicrosoftAuth] Вход в Minecraft Services...");
    nlohmann::json mcReq;
    mcReq["identityToken"] = "XBL3.0 x=" + uhs + ";" + xstsToken;

    auto mcResp = HttpClient::Post("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq.dump());
    if (!mcResp.isSuccess || !mcResp.json.is_object() || !mcResp.json.contains("access_token")) {
        Logger::Error("[MicrosoftAuth] Службы Minecraft отклонили токен (" + mcResp.error + ")");
        if (outError) *outError = "Службы Minecraft отклонили токен авторизации";
        return false;
    }
    std::string mcToken = mcResp.json.value("access_token", "");

    // 4. Minecraft profile
    Logger::Info("[MicrosoftAuth] Загрузка профиля игрока Minecraft...");
    auto profResp = HttpClient::Get("https://api.minecraftservices.com/minecraft/profile", mcToken);
    if (!profResp.isSuccess || !profResp.json.is_object() || !profResp.json.contains("id")) {
        if (profResp.statusCode == 404) {
            Logger::Error("[MicrosoftAuth] На этом аккаунте нет профиля Minecraft Java Edition (404)");
            if (outError) *outError = "На этом аккаунте нет профиля Minecraft Java Edition";
        } else {
            Logger::Error("[MicrosoftAuth] Не удалось загрузить профиль (" + profResp.error + ")");
            if (outError) *outError = "Не удалось загрузить профиль игрока Minecraft";
        }
        return false;
    }

    std::string rawId = profResp.json.value("id", "");
    std::string name = profResp.json.value("name", "");
    if (rawId.size() != 32 || rawId.find_first_not_of("0123456789abcdefABCDEF") != std::string::npos || name.empty()) {
        if (outError) *outError = "Профиль Minecraft неполный";
        return false;
    }

    // Format UUID with hyphens
    std::string formattedUuid = rawId;
    if (rawId.length() == 32) {
        formattedUuid = rawId.substr(0, 8) + "-" +
                        rawId.substr(8, 4) + "-" +
                        rawId.substr(12, 4) + "-" +
                        rawId.substr(16, 4) + "-" +
                        rawId.substr(20, 12);
    }

    Logger::Info("[MicrosoftAuth] Успешно! Лицензия: " + name + " (" + formattedUuid + ")");
    outProfile.nickname = name;
    outProfile.uuid = formattedUuid;
    outProfile.accessToken = mcToken;
    outProfile.refreshToken = refreshToken;
    if (mcResp.json.contains("expires_in") && mcResp.json["expires_in"].is_number_integer()) {
        const auto lifetime = mcResp.json["expires_in"].get<std::int64_t>();
        if (lifetime > 0 && lifetime < 7 * 24 * 60 * 60) {
            outProfile.expiresAt = std::chrono::duration_cast<std::chrono::seconds>(
                std::chrono::system_clock::now().time_since_epoch()).count() + lifetime;
        }
    }
    return true;
}

bool MicrosoftAuth::RefreshProfile(const std::string& refreshToken, MicrosoftProfile& outProfile, std::string* outError) {
    if (refreshToken.empty()) {
        if (outError) *outError = "Войдите в Microsoft заново во вкладке «Профили»";
        return false;
    }
    const auto response = HttpClient::PostForm(kTokenUrl, "client_id=" + kClientId +
        "&grant_type=refresh_token&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf"
        "&refresh_token=" + EncodeFormValue(refreshToken));
    if (!response.isSuccess || !response.json.is_object() || !response.json.contains("access_token")) {
        if (outError) *outError = response.statusCode == 0 || response.statusCode >= 500
            ? "Не удалось связаться с Microsoft. Повторите запуск." : "Сессия Microsoft истекла. Войдите заново во вкладке «Профили».";
        return false;
    }
    auto replacement = response.json.value("refresh_token", std::string{});
    if (replacement.empty()) replacement = refreshToken;
    return CompleteLogin(response.json.value("access_token", std::string{}), replacement, outProfile, outError);
}

bool MicrosoftDeviceFlow::Start() {
    if (active_ || start_.valid() || poll_.valid() || finish_.valid()) return false;
    active_ = true;
    status_ = "Запрашиваем код Microsoft";
    start_ = std::async(std::launch::async, [] {
        StartResult result;
        if (!MicrosoftAuth::StartDeviceCode(result.device, &result.error) && result.error.empty()) {
            result.error = "Не удалось получить код Microsoft";
        }
        return result;
    });
    return true;
}

void MicrosoftDeviceFlow::Cancel() {
    active_ = false;
    device_ = {};
    status_.clear();
}

bool MicrosoftDeviceFlow::OpenBrowser() const {
    return !device_.verificationUrl.empty() &&
        reinterpret_cast<INT_PTR>(ShellExecuteW(nullptr, L"open", Utf8ToWide(device_.verificationUrl).c_str(),
            nullptr, nullptr, SW_SHOWNORMAL)) > 32;
}

std::optional<MicrosoftDeviceFlow::Result> MicrosoftDeviceFlow::Update() {
    using namespace std::chrono_literals;
    const auto ready = [](const auto& future) {
        return future.valid() && future.wait_for(0ms) == std::future_status::ready;
    };
    const auto end = [this](std::string error) {
        Cancel();
        return Result{{}, std::move(error)};
    };
    try {
        if (ready(start_)) {
            auto response = start_.get();
            if (active_) {
                if (!response.error.empty()) return end(response.error);
                device_ = std::move(response.device);
                device_.interval = std::clamp(device_.interval, 1, 60);
                expiry_ = Clock::now() + std::chrono::seconds(std::clamp(device_.expiresIn, 1, 1800));
                nextPoll_ = Clock::now() + std::chrono::seconds(device_.interval);
                status_ = OpenBrowser() ? "Введите код на сайте Microsoft" : "Откройте microsoft.com/devicelogin и введите код";
            }
        }
        if (ready(poll_)) {
            auto response = poll_.get();
            if (active_) {
                if (!response.token.empty()) {
                    status_ = "Загружаем профиль Minecraft";
                    finish_ = std::async(std::launch::async, [response = std::move(response)] {
                        Result result;
                        if (!MicrosoftAuth::CompleteLogin(response.token, response.refresh, result.profile, &result.error)
                            && result.error.empty()) result.error = "Не удалось получить профиль Minecraft";
                        return result;
                    });
                } else if (response.pending) {
                    if (response.slowDown) device_.interval = (std::min)(60, device_.interval + 5);
                    nextPoll_ = Clock::now() + std::chrono::seconds(device_.interval);
                } else {
                    return end(response.error.empty() ? "Microsoft не подтвердил вход" : response.error);
                }
            }
        }
        if (ready(finish_)) {
            auto result = finish_.get();
            if (active_) {
                Cancel();
                return result;
            }
        }
        if (active_ && !device_.deviceCode.empty() && !finish_.valid()) {
            if (Clock::now() >= expiry_) return end("Код Microsoft истёк. Начните вход снова.");
            if (!poll_.valid() && Clock::now() >= nextPoll_) {
                poll_ = std::async(std::launch::async, [id = device_.deviceCode] {
                    TokenResult result;
                    MicrosoftAuth::PollDeviceToken(id, result.token, result.refresh, result.pending, &result.error, &result.slowDown);
                    return result;
                });
            }
        }
    } catch (const std::exception&) {
        if (active_) return end("Не удалось завершить вход Microsoft");
    }
    return std::nullopt;
}

} // namespace aero
