#include "HttpClient.h"
#include "../core/Config.h"
#include "../core/HWID.h"
#include "../core/StringUtils.h"
#include "../core/Logger.h"
#include <windows.h>
#include <winhttp.h>
#include <sstream>

#pragma comment(lib, "winhttp.lib")

namespace aero {

std::string HttpClient::DescribeError(int status, const std::string& raw) {
    if (raw == "Unauthorized") return "Сессия истекла — войди снова";
    if (raw == "Invalid token") return "Токен недействителен — войди снова";
    if (raw == "Validation failed") return "Проверь поля формы";
    if (raw == "Too many attempts" || raw == "Too many requests") return "Слишком много попыток — подожди немного";
    if (raw == "User not found") return "Аккаунт не найден";
    if (raw == "Not found") return "Метод не найден на сервере";
    if (raw == "Download failed") return "Сервер не смог отдать файл";
    if (raw == "Admin only") return "Нужны права администратора";

    if (!raw.empty()) return raw;

    switch (status) {
    case 400: return "Неверные данные запроса";
    case 401: return "Нужна авторизация";
    case 403: return "Нет активной подписки";
    case 404: return "Файл или метод не найден на сервере";
    case 409: return "Ник или e-mail уже заняты";
    case 429: return "Слишком много запросов — подожди немного";
    case 500: return "Ошибка на сервере";
    case 502:
    case 503: return "Сервер на техработах";
    default:  return "Ошибка сети или сервера";
    }
}

static std::string FormatWinHttpError(DWORD err) {
    switch (err) {
    case ERROR_WINHTTP_CANNOT_CONNECT: return "Не удалось установить соединение (CANNOT_CONNECT)";
    case ERROR_WINHTTP_CONNECTION_ERROR: return "Ошибка соединения (CONNECTION_ERROR)";
    case ERROR_WINHTTP_NAME_NOT_RESOLVED: return "Имя сервера не найдено в DNS";
    case ERROR_WINHTTP_TIMEOUT: return "Время ожидания сервера истекло (TIMEOUT)";
    case ERROR_WINHTTP_SECURE_FAILURE: return "Ошибка защищённого SSL/TLS соединения";
    default: return "Код ошибки сети: " + std::to_string(err);
    }
}

static HttpResponse ExecuteWinHttpOnce(const std::string& urlStr, const std::string& method, const std::string& jsonBody, const std::string& token, int timeoutMs, const std::string& contentType, DWORD accessType, bool allowRedirects) {
    HttpResponse resp;
    std::wstring wUrl = Utf8ToWide(urlStr);

    URL_COMPONENTS urlComp = { sizeof(URL_COMPONENTS) };
    urlComp.dwHostNameLength = static_cast<DWORD>(-1);
    urlComp.dwUrlPathLength = static_cast<DWORD>(-1);
    urlComp.dwExtraInfoLength = static_cast<DWORD>(-1);

    if (!WinHttpCrackUrl(wUrl.c_str(), static_cast<DWORD>(wUrl.length()), 0, &urlComp)) {
        resp.error = "Некорректный адрес URL";
        return resp;
    }

    std::wstring hostName(urlComp.lpszHostName, urlComp.dwHostNameLength);
    std::wstring urlPath;
    if (urlComp.dwUrlPathLength > 0) {
        urlPath.assign(urlComp.lpszUrlPath, urlComp.dwUrlPathLength);
    }
    if (urlComp.dwExtraInfoLength > 0 && urlComp.lpszExtraInfo) {
        urlPath.append(urlComp.lpszExtraInfo, urlComp.dwExtraInfoLength);
    }
    if (urlPath.empty()) {
        urlPath = L"/";
    }
    INTERNET_PORT port = urlComp.nPort;
    bool isHttps = (urlComp.nScheme == INTERNET_SCHEME_HTTPS);

    HINTERNET hSession = WinHttpOpen(
        Config::UserAgent.c_str(),
        accessType,
        WINHTTP_NO_PROXY_NAME,
        WINHTTP_NO_PROXY_BYPASS,
        0
    );
    if (!hSession) {
        resp.error = "Ошибка инициализации WinHttp: " + FormatWinHttpError(GetLastError());
        return resp;
    }

    DWORD protocols = WINHTTP_FLAG_SECURE_PROTOCOL_TLS1_2 | WINHTTP_FLAG_SECURE_PROTOCOL_TLS1_3;
    if (!WinHttpSetOption(hSession, WINHTTP_OPTION_SECURE_PROTOCOLS, &protocols, sizeof(protocols))) {
        protocols = WINHTTP_FLAG_SECURE_PROTOCOL_TLS1_2;
        WinHttpSetOption(hSession, WINHTTP_OPTION_SECURE_PROTOCOLS, &protocols, sizeof(protocols));
    }

    WinHttpSetTimeouts(hSession, timeoutMs, timeoutMs, timeoutMs, timeoutMs);

    HINTERNET hConnect = WinHttpConnect(hSession, hostName.c_str(), port, 0);
    if (!hConnect) {
        DWORD dwErr = GetLastError();
        WinHttpCloseHandle(hSession);
        resp.error = "Не удалось подключиться к серверу: " + FormatWinHttpError(dwErr);
        return resp;
    }

    std::wstring wMethod = Utf8ToWide(method);
    DWORD dwFlags = isHttps ? WINHTTP_FLAG_SECURE : 0;

    HINTERNET hRequest = WinHttpOpenRequest(
        hConnect,
        wMethod.c_str(),
        urlPath.c_str(),
        nullptr,
        WINHTTP_NO_REFERER,
        WINHTTP_DEFAULT_ACCEPT_TYPES,
        dwFlags
    );

    if (!hRequest) {
        DWORD dwErr = GetLastError();
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        resp.error = "Ошибка создания запроса: " + FormatWinHttpError(dwErr);
        return resp;
    }

    DWORD redirectPolicy = allowRedirects
        ? WINHTTP_OPTION_REDIRECT_POLICY_DISALLOW_HTTPS_TO_HTTP
        : WINHTTP_OPTION_REDIRECT_POLICY_NEVER;
    WinHttpSetOption(hRequest, WINHTTP_OPTION_REDIRECT_POLICY, &redirectPolicy, sizeof(redirectPolicy));

    // Headers
    std::wstring headers = L"Accept: application/json\r\n";
    if (urlStr.starts_with(Config::DefaultBaseUrl + "/")) {
        headers += L"X-HWID: " + Utf8ToWide(HWID::Get()) + L"\r\n";
    }
    if (!token.empty()) {
        headers += L"Authorization: Bearer " + Utf8ToWide(token) + L"\r\n";
    }
    if (!jsonBody.empty()) {
        headers += L"Content-Type: " + Utf8ToWide(contentType) + L"\r\n";
    }

    BOOL sendOk = WinHttpSendRequest(
        hRequest,
        headers.c_str(),
        static_cast<DWORD>(headers.length()),
        jsonBody.empty() ? nullptr : const_cast<char*>(jsonBody.data()),
        static_cast<DWORD>(jsonBody.length()),
        static_cast<DWORD>(jsonBody.length()),
        0
    );

    if (!sendOk || !WinHttpReceiveResponse(hRequest, nullptr)) {
        DWORD dwErr = GetLastError();
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        resp.error = "Не удалось подключиться к серверу (" + FormatWinHttpError(dwErr) + ")";
        return resp;
    }

    DWORD statusCode = 0;
    DWORD statusSize = sizeof(statusCode);
    WinHttpQueryHeaders(
        hRequest,
        WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
        WINHTTP_HEADER_NAME_BY_INDEX,
        &statusCode,
        &statusSize,
        WINHTTP_NO_HEADER_INDEX
    );
    resp.statusCode = static_cast<int>(statusCode);

    // Read response body
    DWORD dwSize = 0;
    while (WinHttpQueryDataAvailable(hRequest, &dwSize) && dwSize > 0) {
        std::vector<char> buffer(dwSize);
        DWORD dwDownloaded = 0;
        if (WinHttpReadData(hRequest, buffer.data(), dwSize, &dwDownloaded)) {
            resp.body.append(buffer.data(), dwDownloaded);
        }
    }

    WinHttpCloseHandle(hRequest);
    WinHttpCloseHandle(hConnect);
    WinHttpCloseHandle(hSession);

    if (!resp.body.empty()) {
        try {
            resp.json = nlohmann::json::parse(resp.body);
            if (resp.json.is_object()) {
                if (resp.json.contains("error")) {
                    if (resp.json["error"].is_string()) {
                        resp.error = HttpClient::DescribeError(resp.statusCode, resp.json["error"].get<std::string>());
                    } else {
                        resp.error = HttpClient::DescribeError(resp.statusCode, resp.json["error"].dump());
                    }
                } else if (resp.json.contains("message") && resp.json["message"].is_string()) {
                    resp.error = HttpClient::DescribeError(resp.statusCode, resp.json["message"].get<std::string>());
                }
            }
        } catch (...) {
            // Non-JSON response
        }
    }

    if (resp.statusCode >= 200 && resp.statusCode < 300) {
        if (resp.json.is_object() && resp.json.contains("success")) {
            bool successVal = true;
            if (resp.json["success"].is_boolean()) {
                successVal = resp.json["success"].get<bool>();
            } else if (resp.json["success"].is_number()) {
                successVal = (resp.json["success"].get<int>() != 0);
            }
            if (!successVal) {
                resp.isSuccess = false;
                if (resp.error.empty()) resp.error = "Ошибка операции";
            } else {
                resp.isSuccess = true;
            }
        } else {
            resp.isSuccess = true;
        }
    } else {
        resp.isSuccess = false;
        if (resp.error.empty()) {
            resp.error = HttpClient::DescribeError(resp.statusCode, "");
        }
    }

    return resp;
}

static HttpResponse ExecuteWinHttp(const std::string& urlStr, const std::string& method, const std::string& jsonBody, const std::string& token, int timeoutMs, const std::string& contentType = "application/json", bool allowRedirects = false) {
    auto resp = ExecuteWinHttpOnce(urlStr, method, jsonBody, token, timeoutMs, contentType,
        WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY, allowRedirects);
    if (!resp.isSuccess) {
        // The query may contain a one-time browser sign-in secret.
        Logger::Warn("[HTTP] " + method + " " + urlStr.substr(0, urlStr.find('?')) + ": " +
            (resp.statusCode ? std::to_string(resp.statusCode) : "нет соединения"));
    }
    return resp;
}


HttpResponse HttpClient::Get(const std::string& url, const std::string& token, int timeoutMs, bool allowRedirects) {
    if (!url.empty() && url[0] == '/') {
        return ApiRequest(url, "GET", "", token);
    }
    return ExecuteWinHttp(url, "GET", "", token, timeoutMs, "application/json", allowRedirects && token.empty());
}

HttpResponse HttpClient::Post(const std::string& url, const std::string& jsonBody, const std::string& token, int timeoutMs) {
    if (!url.empty() && url[0] == '/') {
        return ApiRequest(url, "POST", jsonBody, token);
    }
    return ExecuteWinHttp(url, "POST", jsonBody, token, timeoutMs);
}

HttpResponse HttpClient::PostForm(const std::string& url, const std::string& formBody, int timeoutMs) {
    return ExecuteWinHttp(url, "POST", formBody, "", timeoutMs, "application/x-www-form-urlencoded");
}

HttpResponse HttpClient::ApiRequest(const std::string& pathname, const std::string& method, const std::string& jsonBody, const std::string& token) {
    if (!pathname.starts_with("/") || pathname.starts_with("//")) {
        HttpResponse fail;
        fail.error = "Некорректный адрес AeroProject";
        return fail;
    }
    return ExecuteWinHttp(Config::DefaultBaseUrl + pathname, method, jsonBody, token, 8000);
}

} // namespace aero
