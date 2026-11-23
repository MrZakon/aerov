#pragma once
#include <string>
#include <windows.h>

namespace aero {

inline std::wstring Utf8ToWide(const std::string& str) {
    if (str.empty()) return L"";
    int len = MultiByteToWideChar(CP_UTF8, 0, str.data(), static_cast<int>(str.length()), nullptr, 0);
    if (len <= 0) return L"";
    std::wstring res(len, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, str.data(), static_cast<int>(str.length()), res.data(), len);
    return res;
}

inline std::string WideToUtf8(const std::wstring& wstr) {
    if (wstr.empty()) return "";
    int len = WideCharToMultiByte(CP_UTF8, 0, wstr.data(), static_cast<int>(wstr.length()), nullptr, 0, nullptr, nullptr);
    if (len <= 0) return "";
    std::string res(len, '\0');
    WideCharToMultiByte(CP_UTF8, 0, wstr.data(), static_cast<int>(wstr.length()), res.data(), len, nullptr, nullptr);
    return res;
}

} // namespace aero
