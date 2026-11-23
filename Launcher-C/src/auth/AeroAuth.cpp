#include "AeroAuth.h"
#include "../net/HttpClient.h"
#include "../store/SettingsManager.h"
#include "../core/Config.h"
#include "../core/Logger.h"
#include "../core/HWID.h"
#include <filesystem>
#include <fstream>
#include <utility>
#include <windows.h>
#include <shlobj.h>

namespace aero {

namespace fs = std::filesystem;
using Json = nlohmann::json;

namespace {

bool IsNativeToken(const std::string& value) {
    return value.size() == 64 && value.find_first_not_of("0123456789abcdef") == std::string::npos;
}

const Json& Payload(const Json& reply) {
    if (reply.is_object() && reply.contains("data") && reply["data"].is_object()) return reply["data"];
    return reply;
}

std::string StringField(const Json& object, const char* key) {
    const auto it = object.find(key);
    return it != object.end() && it->is_string() ? it->get<std::string>() : std::string{};
}

bool Flag(const Json& object, const char* key) {
    const auto it = object.find(key);
    return it != object.end() && ((it->is_boolean() && it->get<bool>()) || (it->is_number_integer() && *it != 0));
}

AuthReply ReadProfile(const std::string& token, const Json& profile) {
    AuthReply result;
    auto& s = result.session;
    s.userId = StringField(profile, "id");
    if (s.userId.empty() && profile.contains("id") && profile["id"].is_number_integer()) {
        s.userId = profile["id"].dump();
    }
    s.username = StringField(profile, "username");
    if (s.username.empty()) s.username = StringField(profile, "name");
    if (s.username.empty()) s.username = StringField(profile, "nickname");
    if (!IsNativeToken(token) || s.userId.empty() || s.username.empty()) {
        return { {}, "Сайт вернул неполный профиль. Повторите вход." };
    }

    s.token = token;
    s.email = StringField(profile, "email");
    s.role = StringField(profile, "role");
    const bool privileged = s.role == "owner" || s.role == "admin";
    s.hasSubscription = Flag(profile, "hasSubscription") || privileged;
    s.verified = true;

    const Json sub = profile.contains("subscription") && profile["subscription"].is_object()
        ? profile["subscription"] : Json::object();
    s.subscription.active = s.hasSubscription;
    s.subscription.canDownloadMod = true;
    s.subscription.startsAt = StringField(sub, "startsAt");
    s.subscription.endsAt = StringField(sub, "endsAt");
    s.subscription.status = StringField(sub, "status");
    if (s.subscription.status.empty()) s.subscription.status = s.hasSubscription ? "active" : "none";
    s.plan = privileged ? "lifetime" : s.hasSubscription ? StringField(sub, "plan") : "free";
    if (s.plan.empty()) s.plan = StringField(profile, "plan");
    if (s.plan.empty() || (s.plan == "free" && s.hasSubscription)) s.plan = "premium";
    s.subscription.plan = s.plan;
    s.subscription.lifetime = s.hasSubscription && s.plan == "lifetime";
    s.planLabel = s.subscription.lifetime ? "Lifetime" : s.hasSubscription ? "Premium" : "Free";
    return result;
}

AuthReply PasswordResponse(const HttpResponse& response) {
    if (!response.isSuccess) {
        return { {}, response.error.empty() ? "Не удалось войти" : response.error, response.statusCode == 403 };
    }
    const auto& data = Payload(response.json);
    if (Flag(response.json, "requiresConfirmation") || Flag(data, "requiresConfirmation")) {
        return { {}, "Подтвердите вход на сайте AeroProject", true };
    }
    auto token = StringField(response.json, "token");
    if (token.empty()) token = StringField(data, "token");
    const auto& user = data.contains("user") && data["user"].is_object() ? data["user"] : data;
    return ReadProfile(token, user);
}

bool ReportError(std::string* outError, std::string error) {
    if (outError) *outError = std::move(error);
    return false;
}

// The installed mod still reads this JSON from several historical paths.
std::vector<fs::path> SessionPaths(const fs::path& primary) {
    std::vector<fs::path> paths{primary};
#ifndef AERO_TESTING
    wchar_t appdata[MAX_PATH];
    if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_APPDATA, nullptr, 0, appdata))) {
        paths.push_back(fs::path(appdata) / L"Aero Launcher" / L"aero-session.json");
        paths.push_back(fs::path(appdata) / L".aero" / L"aero-session.json");
    }
#endif
    return paths;
}

} // namespace

AeroAuth::AeroAuth()
    : sessionFilePath_((fs::path(SettingsManager::GetAppDataAeroDir()) / L"aero-session.json").wstring()) {
    LoadSession();
}

AeroAuth& AeroAuth::Instance() {
    static AeroAuth instance;
    return instance;
}

bool AeroAuth::IsAuthorized() const {
    std::lock_guard lock(mutex_);
    return !session_.token.empty();
}

UserSession AeroAuth::GetSession() const {
    std::lock_guard lock(mutex_);
    return session_;
}

std::string AeroAuth::GetOnlineToken() const {
    std::lock_guard lock(mutex_);
    return session_.verified ? session_.token : std::string{};
}

void AeroAuth::AcceptSession(UserSession session) {
    std::lock_guard lock(mutex_);
    ++revision_;
    session_ = std::move(session);
    SaveSessionLocked();
}

AuthReply AeroAuth::RequestLogin(const std::string& login, const std::string& password) {
    const Json body{{"login", login}, {"password", password}};
    return PasswordResponse(HttpClient::ApiRequest(Config::Auth::Login, "POST", body.dump()));
}

bool AeroAuth::Login(const std::string& login, const std::string& password, std::string* outError) {
    auto result = RequestLogin(login, password);
    if (!result.error.empty()) return ReportError(outError, result.error);
    AcceptSession(std::move(result.session));
    return true;
}

bool AeroAuth::Register(const std::string& username, const std::string& email, const std::string& password, std::string* outError) {
    const Json body{{"username", username}, {"name", username}, {"email", email}, {"password", password}};
    const auto response = HttpClient::ApiRequest(Config::Auth::Register, "POST", body.dump());
    if (!response.isSuccess) return ReportError(outError, response.error);
    if (Flag(response.json, "requiresConfirmation") || Flag(Payload(response.json), "requiresConfirmation")) {
        return ReportError(outError, "Подтвердите почту на сайте AeroProject");
    }
    return Login(email, password, outError);
}

bool AeroAuth::LoginWithToken(const std::string& token, std::string* outError) {
    if (!IsNativeToken(token)) return ReportError(outError, "Недействительный токен Aero ID");
    const auto response = HttpClient::ApiRequest(Config::Auth::Me, "GET", "", token);
    if (!response.isSuccess) return ReportError(outError, response.error);
    auto result = ReadProfile(token, Payload(response.json));
    if (!result.error.empty()) return ReportError(outError, result.error);
    AcceptSession(std::move(result.session));
    return true;
}

void AeroAuth::LoginOffline(const std::string& username) {
    UserSession session;
    session.username = username.empty() ? "Player" : username;
    session.token = "offline";
    session.role = "user";
    session.plan = "free";
    session.planLabel = "Локальный режим";
    session.isOffline = true;
    AcceptSession(std::move(session));
}

std::string AeroAuth::Redeem(const std::string& code, const std::string& token) {
    if (!IsNativeToken(token)) return "Сначала войдите в Aero ID";
    const Json body{{"code", code}};
    const auto response = HttpClient::ApiRequest(Config::Auth::Redeem, "POST", body.dump(), token);
    if (response.isSuccess) return {};
    return response.error.empty() ? "Не удалось активировать ключ" : response.error;
}

SessionRefresh AeroAuth::RefreshImpl(std::string* outError) {
    std::string token;
    std::uint64_t revision;
    {
        std::lock_guard lock(mutex_);
        if (session_.token.empty()) return SessionRefresh::SignedOut;
        if (session_.isOffline) return SessionRefresh::Ready;
        token = session_.token;
        revision = revision_;
    }

    const auto response = HttpClient::ApiRequest(Config::Auth::Me, "GET", "", token);
    auto result = response.isSuccess
        ? ReadProfile(token, Payload(response.json))
        : AuthReply{ {}, response.error.empty() ? "Не удалось проверить Aero ID" : response.error };

    std::lock_guard lock(mutex_);
    if (revision != revision_) return SessionRefresh::Superseded;
    if (response.statusCode == 401 || response.statusCode == 403) {
        ++revision_;
        session_ = {};
        RemoveSessionFilesLocked();
        ReportError(outError, "Сессия Aero ID истекла. Войдите снова.");
        return SessionRefresh::SignedOut;
    }
    if (!result.error.empty()) {
        session_.verified = false;
        session_.subscription.canDownloadMod = false;
        ReportError(outError, result.error);
        return SessionRefresh::Unavailable;
    }
    if (!session_.userId.empty() && session_.userId != result.session.userId) {
        ++revision_;
        session_ = {};
        RemoveSessionFilesLocked();
        ReportError(outError, "Аккаунт Aero ID изменился. Войдите снова.");
        return SessionRefresh::SignedOut;
    }
    session_ = std::move(result.session);
    SaveSessionLocked();
    return SessionRefresh::Ready;
}

bool AeroAuth::Refresh(std::string* outError) {
    return RefreshImpl(outError) == SessionRefresh::Ready;
}

SessionRefresh AeroAuth::RefreshSession() {
    return RefreshImpl(nullptr);
}

void AeroAuth::RemoveSessionFilesLocked() const {
    for (const auto& path : SessionPaths(sessionFilePath_)) {
        std::error_code ec;
        fs::remove(path, ec);
        if (ec) Logger::Warn("[Auth] Не удалось удалить сохранённую сессию Aero ID");
    }
}

void AeroAuth::Logout() {
    std::lock_guard lock(mutex_);
    ++revision_;
    session_ = {};
    RemoveSessionFilesLocked();
}

void AeroAuth::Revoke(const std::string& token) {
    if (IsNativeToken(token)) HttpClient::ApiRequest(Config::Auth::Logout, "POST", "{}", token);
}

bool AeroAuth::StartWebSession(std::string& outSessionId, std::string& outAuthUrl, std::string* outError) {
    outSessionId.clear();
    outAuthUrl.clear();
    if (outError) outError->clear();
    const auto response = HttpClient::ApiRequest(Config::Auth::WebSessionStart, "POST", "{}");
    if (!response.isSuccess) return ReportError(outError, response.error);
    const auto& data = Payload(response.json);
    auto id = StringField(data, "sessionId");
    auto url = StringField(data, "authUrl");
    if (url.empty()) url = StringField(data, "url");
    if (url.starts_with("/access/?")) url = Config::DefaultBaseUrl + url;
    if (!IsNativeToken(id) || url != Config::DefaultBaseUrl + "/access/?action=launcher&sessionId=" + id) {
        return ReportError(outError, "Сайт вернул некорректный адрес входа");
    }
    outSessionId = std::move(id);
    outAuthUrl = std::move(url);
    return true;
}

bool AeroAuth::CompleteWebSession(const Json& response, std::string* outError) {
    const auto& data = Payload(response);
    const auto status = StringField(data, "status");
    if ((status != "confirmed" && status != "completed") || !data.contains("user")) {
        return ReportError(outError, "Сайт не подтвердил вход в Aero ID");
    }
    auto result = ReadProfile(StringField(data, "token"), data["user"]);
    if (!result.error.empty()) return ReportError(outError, result.error);
    AcceptSession(std::move(result.session));
    return true;
}

WebLoginReply AeroAuth::PollWebLogin(const std::string& sessionId) {
    if (!IsNativeToken(sessionId)) return {WebLoginState::Ended, {{}, "Некорректная сессия входа"}};
    const auto response = HttpClient::ApiRequest(Config::Auth::WebSessionStatus + "?sessionId=" + sessionId);
    if (!response.isSuccess) {
        if (response.statusCode == 0 || response.statusCode >= 500 || response.statusCode == 429) {
            return {WebLoginState::Retry, {{}, response.error}};
        }
        const auto error = response.statusCode == 404 ? "Срок ссылки истёк. Начните вход снова." : response.error;
        return {WebLoginState::Ended, {{}, error}};
    }
    const auto& data = Payload(response.json);
    const auto status = StringField(data, "status");
    if (status == "pending") return {WebLoginState::Waiting, {}};
    if ((status == "confirmed" || status == "completed") && data.contains("user")) {
        auto auth = ReadProfile(StringField(data, "token"), data["user"]);
        return {auth.error.empty() ? WebLoginState::Confirmed : WebLoginState::Ended, std::move(auth)};
    }
    return {WebLoginState::Ended, {{}, status == "cancelled" ? "Вход отменён в браузере" : "Сайт не подтвердил вход"}};
}

bool AeroAuth::CheckWebSession(const std::string& sessionId, bool& outCompleted, std::string* outError) {
    outCompleted = false;
    auto reply = PollWebLogin(sessionId);
    if (reply.state == WebLoginState::Waiting) return true;
    if (reply.state != WebLoginState::Confirmed) return ReportError(outError, reply.auth.error);
    AcceptSession(std::move(reply.auth.session));
    outCompleted = true;
    return true;
}

void AeroAuth::LoadSession() {
    std::lock_guard lock(mutex_);
    ++revision_;
    session_ = {};
    for (const auto& candidate : SessionPaths(sessionFilePath_)) {
        std::ifstream file(candidate);
        if (!file) continue;
        try {
            const Json saved = Json::parse(file);
            auto token = StringField(saved, "token");
            auto username = StringField(saved, "username");
            if ((token != "offline" && !IsNativeToken(token)) || username.empty()) continue;

            session_.token = std::move(token);
            session_.username = std::move(username);
            session_.userId = StringField(saved, "userId");
            session_.isOffline = session_.token == "offline";
            session_.plan = StringField(saved, "plan");
            session_.planLabel = session_.isOffline ? "Локальный режим" : StringField(saved, "planLabel");
            return;
        } catch (const Json::exception&) {
            Logger::Warn("[Auth] Сохранённая сессия Aero ID не читается");
        }
    }
}

void AeroAuth::SaveSessionLocked() const {
    const Json data{
        {"token", session_.token}, {"userId", session_.userId}, {"username", session_.username},
        {"email", session_.email}, {"role", session_.role}, {"plan", session_.plan},
        {"planLabel", session_.planLabel}, {"hasSubscription", session_.hasSubscription},
        {"isOffline", session_.isOffline}, {"hwid", HWID::Get()}, {"apiBaseUrl", Config::DefaultBaseUrl}
    };

    for (const auto& target : SessionPaths(sessionFilePath_)) {
        std::error_code ec;
        fs::create_directories(target.parent_path(), ec);
        const auto pending = target.wstring() + L"." + std::to_wstring(GetCurrentProcessId()) + L".tmp";
        std::ofstream stream(fs::path(pending), std::ios::binary | std::ios::trunc);
        stream << data.dump(2);
        stream.close();
        if (!stream || !MoveFileExW(pending.c_str(), target.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
            fs::remove(pending, ec);
            Logger::Warn("[Auth] Не удалось сохранить сессию Aero ID");
        }
    }
}

} // namespace aero
