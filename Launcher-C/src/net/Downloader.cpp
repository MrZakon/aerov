#include "Downloader.h"
#include "../core/Config.h"
#include "../core/HWID.h"
#include "../core/StringUtils.h"
#include "../core/Logger.h"
#include <windows.h>
#include <winhttp.h>
#include <bcrypt.h>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <iomanip>

#pragma comment(lib, "bcrypt.lib")
#pragma comment(lib, "winhttp.lib")

namespace aero {

namespace fs = std::filesystem;

void Downloader::CancelAll() {
    s_cancelRequested.store(true);
}

static std::string ComputeHash(const std::wstring& filePath, LPCWSTR algId) {
    BCRYPT_ALG_HANDLE hAlg = nullptr;
    if (BCryptOpenAlgorithmProvider(&hAlg, algId, nullptr, 0) != 0) return "";

    DWORD hashObjSize = 0, dataLen = 0;
    BCryptGetProperty(hAlg, BCRYPT_OBJECT_LENGTH, reinterpret_cast<PBYTE>(&hashObjSize), sizeof(DWORD), &dataLen, 0);
    DWORD hashLen = 0;
    BCryptGetProperty(hAlg, BCRYPT_HASH_LENGTH, reinterpret_cast<PBYTE>(&hashLen), sizeof(DWORD), &dataLen, 0);

    std::vector<BYTE> hashObj(hashObjSize);
    std::vector<BYTE> hashResult(hashLen);

    BCRYPT_HASH_HANDLE hHash = nullptr;
    if (BCryptCreateHash(hAlg, &hHash, hashObj.data(), hashObjSize, nullptr, 0, 0) != 0) {
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return "";
    }

    std::ifstream file(fs::path(filePath), std::ios::binary);
    if (!file.is_open()) {
        BCryptDestroyHash(hHash);
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return "";
    }

    std::vector<char> buffer(64 * 1024);
    while (file.read(buffer.data(), buffer.size()) || file.gcount() > 0) {
        BCryptHashData(hHash, reinterpret_cast<PBYTE>(buffer.data()), static_cast<ULONG>(file.gcount()), 0);
    }

    BCryptFinishHash(hHash, hashResult.data(), hashLen, 0);
    BCryptDestroyHash(hHash);
    BCryptCloseAlgorithmProvider(hAlg, 0);

    std::ostringstream ss;
    ss << std::hex << std::setfill('0');
    for (BYTE b : hashResult) {
        ss << std::setw(2) << static_cast<int>(b);
    }
    return ss.str();
}

std::string Downloader::ComputeSha1(const std::wstring& filePath) {
    return ComputeHash(filePath, BCRYPT_SHA1_ALGORITHM);
}

std::string Downloader::ComputeSha256(const std::wstring& filePath) {
    return ComputeHash(filePath, BCRYPT_SHA256_ALGORITHM);
}

static std::string ExtractContentDispositionFilename(const std::string& header) {
    if (header.empty()) return "";
    size_t utfPos = header.find("filename*=UTF-8''");
    if (utfPos != std::string::npos) {
        size_t start = utfPos + 17;
        size_t end = header.find(';', start);
        return (end == std::string::npos) ? header.substr(start) : header.substr(start, end - start);
    }
    size_t pos = header.find("filename=\"");
    if (pos != std::string::npos) {
        size_t start = pos + 10;
        size_t end = header.find('"', start);
        if (end != std::string::npos) return header.substr(start, end - start);
    }
    pos = header.find("filename=");
    if (pos != std::string::npos) {
        size_t start = pos + 9;
        size_t end = header.find(';', start);
        std::string fn = (end == std::string::npos) ? header.substr(start) : header.substr(start, end - start);
        while (!fn.empty() && (fn.front() == ' ' || fn.front() == '"')) fn.erase(fn.begin());
        while (!fn.empty() && (fn.back() == ' ' || fn.back() == '"')) fn.pop_back();
        return fn;
    }
    return "";
}

static bool DownloadCancelled(const DownloadOptions& options) {
    return options.isCancelled ? options.isCancelled() : Downloader::IsCancelled();
}

static bool DownloadFileOnce(const DownloadOptions& options, DWORD accessType, std::string* outError) {
    std::wstring partPath = options.destPath + L".part";

    std::wstring wUrl = Utf8ToWide(options.url);
    URL_COMPONENTS urlComp = { sizeof(URL_COMPONENTS) };
    urlComp.dwHostNameLength = static_cast<DWORD>(-1);
    urlComp.dwUrlPathLength = static_cast<DWORD>(-1);
    urlComp.dwExtraInfoLength = static_cast<DWORD>(-1);

    if (!WinHttpCrackUrl(wUrl.c_str(), static_cast<DWORD>(wUrl.length()), 0, &urlComp)) {
        if (outError) *outError = "Некорректный адрес URL";
        return false;
    }

    std::wstring hostName(urlComp.lpszHostName, urlComp.dwHostNameLength);
    std::wstring urlPath;
    if (urlComp.lpszUrlPath && urlComp.dwUrlPathLength > 0) {
        urlPath.assign(urlComp.lpszUrlPath, urlComp.dwUrlPathLength);
    }
    if (urlComp.lpszExtraInfo && urlComp.dwExtraInfoLength > 0) {
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
        if (outError) *outError = "WinHttpOpen failed";
        return false;
    }

    DWORD protocols = WINHTTP_FLAG_SECURE_PROTOCOL_TLS1_2 | WINHTTP_FLAG_SECURE_PROTOCOL_TLS1_3;
    if (!WinHttpSetOption(hSession, WINHTTP_OPTION_SECURE_PROTOCOLS, &protocols, sizeof(protocols))) {
        protocols = WINHTTP_FLAG_SECURE_PROTOCOL_TLS1_2;
        WinHttpSetOption(hSession, WINHTTP_OPTION_SECURE_PROTOCOLS, &protocols, sizeof(protocols));
    }
    WinHttpSetTimeouts(hSession, 15000, 15000, 15000, 30000);

    HINTERNET hConnect = WinHttpConnect(hSession, hostName.c_str(), port, 0);
    if (!hConnect) {
        WinHttpCloseHandle(hSession);
        if (outError) *outError = "WinHttpConnect failed";
        return false;
    }

    HINTERNET hRequest = WinHttpOpenRequest(
        hConnect,
        L"GET",
        urlPath.c_str(),
        nullptr,
        WINHTTP_NO_REFERER,
        WINHTTP_DEFAULT_ACCEPT_TYPES,
        isHttps ? WINHTTP_FLAG_SECURE : 0
    );

    if (!hRequest) {
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        if (outError) *outError = "WinHttpOpenRequest failed";
        return false;
    }

    DWORD optionVal = options.token.empty() && !options.publicArtifact
        ? WINHTTP_OPTION_REDIRECT_POLICY_DISALLOW_HTTPS_TO_HTTP
        : WINHTTP_OPTION_REDIRECT_POLICY_NEVER;
    WinHttpSetOption(hRequest, WINHTTP_OPTION_REDIRECT_POLICY, &optionVal, sizeof(optionVal));

    std::wstring headers = L"User-Agent: " + Config::UserAgent + L"\r\n";
    if (!options.token.empty()) {
        headers += L"Authorization: Bearer " + Utf8ToWide(options.token) + L"\r\n";
    }
    if (!options.publicArtifact && options.url.starts_with(Config::DefaultBaseUrl + "/")) {
        headers += L"X-HWID: " + Utf8ToWide(HWID::Get()) + L"\r\n";
    }

    BOOL sent = WinHttpSendRequest(hRequest, headers.c_str(), static_cast<DWORD>(headers.length()), nullptr, 0, 0, 0);
    if (!sent || !WinHttpReceiveResponse(hRequest, nullptr)) {
        DWORD errCode = GetLastError();
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        if (outError) *outError = "Не удалось отправить запрос (код " + std::to_string(errCode) + ")";
        return false;
    }

    DWORD statusCode = 0;
    DWORD statusSize = sizeof(statusCode);
    WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER, WINHTTP_HEADER_NAME_BY_INDEX, &statusCode, &statusSize, WINHTTP_NO_HEADER_INDEX);

    if (statusCode < 200 || statusCode >= 300) {
        std::string errBody;
        DWORD dwSize = 0;
        if (WinHttpQueryDataAvailable(hRequest, &dwSize) && dwSize > 0) {
            std::vector<char> errBuf(dwSize + 1, 0);
            DWORD dwRead = 0;
            if (WinHttpReadData(hRequest, errBuf.data(), dwSize, &dwRead)) {
                errBody.assign(errBuf.data(), dwRead);
            }
        }
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        if (outError) *outError = "HTTP " + std::to_string(statusCode) + (errBody.empty() ? "" : (": " + errBody));
        return false;
    }

    if (options.outFileName) {
        wchar_t cdBuf[512] = { 0 };
        DWORD cdSize = sizeof(cdBuf);
        if (WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_CUSTOM, L"Content-Disposition", cdBuf, &cdSize, WINHTTP_NO_HEADER_INDEX)) {
            *options.outFileName = ExtractContentDispositionFilename(WideToUtf8(cdBuf));
        }
    }

    // Total content length
    wchar_t lenBuf[64] = { 0 };
    DWORD lenBufSize = sizeof(lenBuf);
    size_t totalBytes = 0;
    if (WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_CONTENT_LENGTH, WINHTTP_HEADER_NAME_BY_INDEX, lenBuf, &lenBufSize, WINHTTP_NO_HEADER_INDEX)) {
        totalBytes = static_cast<size_t>(_wtoi64(lenBuf));
    }

    std::ofstream outFile(fs::path(partPath), std::ios::binary | std::ios::trunc);
    if (!outFile.is_open()) {
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        if (outError) *outError = "Не удалось создать целевой файл для записи";
        return false;
    }

    size_t downloadedBytes = 0;
    DWORD dwSize = 0;
    std::vector<char> buffer(64 * 1024);

    std::string transferError;
    for (;;) {
        if (DownloadCancelled(options)) {
            transferError = "Загрузка отменена";
            break;
        }
        if (!WinHttpQueryDataAvailable(hRequest, &dwSize)) {
            transferError = "Не удалось дочитать файл";
            break;
        }
        if (dwSize == 0) break;

        DWORD toRead = (std::min)(dwSize, static_cast<DWORD>(buffer.size()));
        DWORD dwDownloaded = 0;
        if (!WinHttpReadData(hRequest, buffer.data(), toRead, &dwDownloaded) || dwDownloaded == 0) {
            transferError = "Не удалось прочитать данные файла";
            break;
        }
        if (options.expectedSize && downloadedBytes + dwDownloaded > options.expectedSize) {
            transferError = "Размер файла превышает ожидаемый";
            break;
        }
        outFile.write(buffer.data(), dwDownloaded);
        if (!outFile) {
            transferError = "Не удалось записать файл";
            break;
        }
        downloadedBytes += dwDownloaded;
        if (options.onProgress) {
            options.onProgress(downloadedBytes, totalBytes);
        }
    }

    outFile.close();
    WinHttpCloseHandle(hRequest);
    WinHttpCloseHandle(hConnect);
    WinHttpCloseHandle(hSession);

    if (transferError.empty() && (!outFile || (options.expectedSize && downloadedBytes != options.expectedSize))) {
        transferError = "Файл записан не полностью";
    }
    if (!transferError.empty()) {
        std::error_code ec;
        fs::remove(partPath, ec);
        if (outError) *outError = transferError;
        return false;
    }

    // Hash checks
    if (!options.expectedSha1.empty()) {
        std::string calcSha1 = Downloader::ComputeSha1(partPath);
        if (_stricmp(calcSha1.c_str(), options.expectedSha1.c_str()) != 0) {
            std::error_code ec;
            fs::remove(partPath, ec);
            if (outError) *outError = "Несовпадение SHA1: ожидалось " + options.expectedSha1 + ", получено " + calcSha1;
            return false;
        }
    }

    if (!options.expectedSha256.empty()) {
        std::string calcSha256 = Downloader::ComputeSha256(partPath);
        if (_stricmp(calcSha256.c_str(), options.expectedSha256.c_str()) != 0) {
            std::error_code ec;
            fs::remove(partPath, ec);
            if (outError) *outError = "Несовпадение SHA256: ожидалось " + options.expectedSha256 + ", получено " + calcSha256;
            return false;
        }
    }

    // Atomic move
    if (!MoveFileExW(partPath.c_str(), options.destPath.c_str(), MOVEFILE_REPLACE_EXISTING)) {
        if (outError) *outError = "MoveFileEx failed to replace destination";
        return false;
    }

    return true;
}

bool Downloader::DownloadFile(const DownloadOptions& options, std::string* outError) {
    if (DownloadCancelled(options)) {
        if (outError) *outError = "Загрузка отменена";
        return false;
    }

    fs::path dest(options.destPath);
    if (dest.has_parent_path()) {
        std::error_code ec;
        fs::create_directories(dest.parent_path(), ec);
    }

    std::string err;
    // 1. Попытка через прямое соединение (NO_PROXY)
    if (DownloadFileOnce(options, WINHTTP_ACCESS_TYPE_NO_PROXY, &err)) {
        return true;
    }

    // 2. Если прямое не прошло и не отменено — пробуем системный прокси
    if (!DownloadCancelled(options)) {
        std::string proxyErr;
        if (DownloadFileOnce(options, WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, &proxyErr)) {
            return true;
        }
        if (!proxyErr.empty()) err = proxyErr;
    }

    if (outError) *outError = err;
    Logger::Error("[Download] Не удалось скачать " + options.url + ": " + err);
    return false;
}

bool Downloader::ExtractZip(const std::wstring& zipPath, const std::wstring& destDir) {
    std::error_code ec;
    fs::create_directories(destDir, ec);

    std::wstring cmd = L"tar.exe -xf \"" + zipPath + L"\" -C \"" + destDir + L"\"";
    STARTUPINFOW si = { sizeof(si) };
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi = { 0 };

    if (CreateProcessW(nullptr, cmd.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        WaitForSingleObject(pi.hProcess, 60000);
        DWORD exitCode = 0;
        GetExitCodeProcess(pi.hProcess, &exitCode);
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
        if (exitCode == 0) return true;
    }

    // Fallback 1: PowerShell Expand-Archive (Windows 10/11 or Windows 7 with WMF 5)
    std::wstring psCmd = L"powershell.exe -NoProfile -NonInteractive -Command \"Expand-Archive -Force -LiteralPath '" +
        zipPath + L"' -DestinationPath '" + destDir + L"'\"";
    PROCESS_INFORMATION psPi = { 0 };
    if (CreateProcessW(nullptr, psCmd.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &psPi)) {
        WaitForSingleObject(psPi.hProcess, 60000);
        DWORD psExit = 0;
        GetExitCodeProcess(psPi.hProcess, &psExit);
        CloseHandle(psPi.hProcess);
        CloseHandle(psPi.hThread);
        if (psExit == 0) return true;
    }

    // Fallback 2: COM Shell.Application via PowerShell (works on Windows 7 out of the box)
    std::wstring comPsCmd = L"powershell.exe -NoProfile -NonInteractive -Command \""
        L"$shell = New-Object -ComObject Shell.Application; "
        L"$zip = $shell.NameSpace('" + zipPath + L"'); "
        L"$dest = $shell.NameSpace('" + destDir + L"'); "
        L"if ($zip -and $dest) { $dest.CopyHere($zip.Items(), 16) }\"";
    PROCESS_INFORMATION comPi = { 0 };
    if (CreateProcessW(nullptr, comPsCmd.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &comPi)) {
        WaitForSingleObject(comPi.hProcess, 60000);
        DWORD comExit = 0;
        GetExitCodeProcess(comPi.hProcess, &comExit);
        CloseHandle(comPi.hProcess);
        CloseHandle(comPi.hThread);
        return comExit == 0;
    }

    return false;
}

} // namespace aero
