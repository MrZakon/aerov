#pragma once
#include <windows.h>
#include <filesystem>
#include <fstream>
#include <functional>
#include <map>
#include <vector>
#include <cwctype>
#include <stdexcept>
#include "MinecraftInstaller.h"
#include "../core/StringUtils.h"
#include "AeroCoreScript.h"

namespace aero::aerocore {
inline constexpr const char* VersionId = "aerocore-26.3";
inline std::filesystem::path Root(const std::wstring& gameDir) {
    return std::filesystem::path(gameDir) / L"engines" / L"AeroCore" / L"v0.2.91";
}
inline bool Ready(const std::wstring& gameDir) {
    const auto root = Root(gameDir);
    return std::filesystem::is_regular_file(root / L"aerocore-ready.txt")
        && std::filesystem::is_regular_file(root / L"horizon-client.exe")
        && std::filesystem::is_regular_file(root / L".local/horizon-client/26.3/horizon-resources.json");
}
inline std::wstring Quote(const std::wstring& value) {
    // Windows CommandLineToArgvW quoting, including paths ending in a backslash.
    std::wstring out = L"\"";
    size_t slashes = 0;
    for (wchar_t c : value) {
        if (c == L'\\') { ++slashes; continue; }
        out.append(c == L'"' ? slashes * 2 + 1 : slashes, L'\\');
        slashes = 0;
        out += c;
    }
    out.append(slashes * 2, L'\\');
    return out + L"\"";
}
inline std::vector<wchar_t> Environment(const std::wstring& home) {
    struct IgnoreCase {
        bool operator()(const std::wstring& a, const std::wstring& b) const { return _wcsicmp(a.c_str(), b.c_str()) < 0; }
    };
    std::map<std::wstring, std::wstring, IgnoreCase> values;
    LPWCH block = GetEnvironmentStringsW();
    if (block) {
        for (const wchar_t* p = block; *p; p += wcslen(p) + 1) {
            std::wstring line(p);
            auto eq = line.find(L'=', line[0] == L'=' ? 1 : 0);
            if (eq == std::wstring::npos) continue;
            std::wstring key = line.substr(0, eq), upper = key;
            for (auto& c : upper) c = towupper(c);
            if (upper != L"HORIZON_HOME") values[key] = line.substr(eq + 1);
        }
        FreeEnvironmentStringsW(block);
    }
    values[L"HORIZON_HOME"] = home;
    std::vector<wchar_t> result;
    for (const auto& [key, value] : values) {
        auto line = key + L"=" + value;
        result.insert(result.end(), line.begin(), line.end());
        result.push_back(0);
    }
    result.push_back(0);
    return result;
}
inline bool Install(const std::wstring& gameDir, MinecraftInstaller::ProgressCallback progress,
                    std::string* error) {
    namespace fs = std::filesystem;
    try {
        const auto root = fs::absolute(Root(gameDir));
        fs::create_directories(root);
        // AeroCore's installer deliberately bypasses the legacy Java-version aliases.
        auto script = root / L"install-aerocore.ps1";
        { std::ofstream out(script, std::ios::binary); out << InstallScript; if (!out) throw std::runtime_error("Cannot write AeroCore installer"); }
        wchar_t systemDir[MAX_PATH]{};
        GetSystemDirectoryW(systemDir, MAX_PATH);
        auto powershell = fs::path(systemDir) / L"WindowsPowerShell" / L"v1.0" / L"powershell.exe";
        auto command = Quote(powershell.wstring()) + L" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "
            + Quote(script.wstring()) + L" -Root " + Quote(root.wstring())
            + L" -MinecraftDir " + Quote(fs::absolute(gameDir).wstring());
        SECURITY_ATTRIBUTES sa{sizeof(sa), nullptr, TRUE};
        HANDLE read = nullptr, write = nullptr;
        if (!CreatePipe(&read, &write, &sa, 0)) throw std::runtime_error("Cannot create AeroCore install log");
        SetHandleInformation(read, HANDLE_FLAG_INHERIT, 0);
        STARTUPINFOW si{}; si.cb = sizeof(si); si.dwFlags = STARTF_USESTDHANDLES;
        si.hStdOutput = write; si.hStdError = write;
        PROCESS_INFORMATION pi{};
        bool started = CreateProcessW(powershell.c_str(), command.data(), nullptr, nullptr, TRUE,
                                      CREATE_NO_WINDOW, nullptr, root.c_str(), &si, &pi) != FALSE;
        CloseHandle(write);
        if (!started) { CloseHandle(read); throw std::runtime_error("Cannot start AeroCore installer"); }
        std::ofstream log(root / L"install.log", std::ios::binary);
        char buffer[4096]; DWORD count = 0;
        if (progress) progress(0.5f, L"AeroCore: загрузка движка, Java и подготовка ресурсов…");
        while (ReadFile(read, buffer, sizeof(buffer), &count, nullptr) && count) log.write(buffer, count);
        CloseHandle(read);
        WaitForSingleObject(pi.hProcess, INFINITE);
        DWORD code = 1; GetExitCodeProcess(pi.hProcess, &code);
        CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
        if (code != 0) throw std::runtime_error("AeroCore installation failed; see " + WideToUtf8((root / L"install.log").wstring()));
        if (progress) progress(1.0f, L"AeroCore 26.3 готов");
        return true;
    } catch (const std::exception& e) { if (error) *error = e.what(); return false; }
}
} // namespace aero::aerocore
