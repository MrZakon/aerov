#include "AeroCore.h"
#include <fstream>
#include "GameLauncher.h"
#include "MinecraftMeta.h"
#include "../store/SettingsManager.h"
#include "../core/Config.h"
#include "../core/StringUtils.h"
#include "../net/Downloader.h"
#include "../net/HttpClient.h"
#include <objbase.h>
#include <filesystem>
#include <sstream>
#include <regex>
#include <chrono>
#include <iomanip>
#include <mutex>
#include <bcrypt.h>

#pragma comment(lib, "bcrypt.lib")

namespace aero {

namespace fs = std::filesystem;

std::string GameLauncher::GetAeroGuardToken() {
    int64_t window5Min = static_cast<int64_t>(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count() / (1000 * 60 * 5));
    std::string payload = "AERO_SEC_AUTH_x8F2D_KERNEL_GUARD:" + std::to_string(window5Min);

    BCRYPT_ALG_HANDLE hAlg = nullptr;
    if (BCryptOpenAlgorithmProvider(&hAlg, BCRYPT_SHA256_ALGORITHM, nullptr, 0) != 0) return "";

    DWORD hashObjSize = 0, dataLen = 0;
    BCRYPT_OBJECT_LENGTH;
    BCryptGetProperty(hAlg, BCRYPT_OBJECT_LENGTH, reinterpret_cast<PBYTE>(&hashObjSize), sizeof(DWORD), &dataLen, 0);
    DWORD hashLen = 0;
    BCryptGetProperty(hAlg, BCRYPT_HASH_LENGTH, reinterpret_cast<PBYTE>(&hashLen), sizeof(DWORD), &dataLen, 0);

    std::vector<BYTE> hashObj(hashObjSize);
    std::vector<BYTE> hashResult(hashLen);
    BCRYPT_HASH_HANDLE hHash = nullptr;
    if (BCryptCreateHash(hAlg, &hHash, hashObj.data(), hashObjSize, nullptr, 0, 0) == 0) {
        BCryptHashData(hHash, reinterpret_cast<PBYTE>(const_cast<char*>(payload.data())), static_cast<ULONG>(payload.length()), 0);
        BCryptFinishHash(hHash, hashResult.data(), hashLen, 0);
        BCryptDestroyHash(hHash);
    }
    BCryptCloseAlgorithmProvider(hAlg, 0);

    std::ostringstream ss;
    ss << std::hex << std::setfill('0');
    for (BYTE b : hashResult) ss << std::setw(2) << static_cast<int>(b);
    return ss.str();
}

std::string GameLauncher::ResolveVersionFolder(const std::wstring& gameDirW, const std::string& versionId) {
    fs::path versionsDir = fs::path(gameDirW) / L"versions";
    if (!fs::exists(versionsDir)) return versionId;

    // 1. Exact directory match: versions/<versionId>/<versionId>.json
    fs::path directJson = versionsDir / Utf8ToWide(versionId) / Utf8ToWide(versionId + ".json");
    if (fs::exists(directJson)) return versionId;

    // 2. Extract potential Minecraft version number (e.g. 1.21.4, 1.20.4, 1.16.5)
    std::string mcVer;
    std::regex mcRegex(R"((\d+\.\d+(?:\.\d+)?))");
    std::smatch match;
    if (std::regex_search(versionId, match, mcRegex)) {
        mcVer = match[1].str();
    }

    bool wantsAero = (versionId.find("Aero") != std::string::npos ||
                        versionId.find("aero") != std::string::npos);
    bool wantsFabric = (versionId.find("Fabric") != std::string::npos ||
                        versionId.find("fabric") != std::string::npos ||
                        wantsAero);

    std::string candidateAero;
    std::string candidateFabric;
    std::string candidateVanilla;

    try {
        for (const auto& entry : fs::directory_iterator(versionsDir)) {
            if (!entry.is_directory()) continue;
            std::string folderName = entry.path().filename().string();
            fs::path jsonFile = entry.path() / Utf8ToWide(folderName + ".json");
            if (!fs::exists(jsonFile)) continue;

            if (wantsAero && (folderName.rfind("AeroVisual-", 0) == 0 || folderName.rfind("Aero-", 0) == 0)) {
                if (!mcVer.empty() && folderName.find(mcVer) != std::string::npos) {
                    return folderName;
                }
                if (candidateAero.empty()) candidateAero = folderName;
            } else if (folderName.find("fabric-loader") != std::string::npos) {
                if (!mcVer.empty() && folderName.find(mcVer) != std::string::npos) {
                    candidateFabric = folderName;
                } else if (candidateFabric.empty()) {
                    candidateFabric = folderName;
                }
            } else if (!mcVer.empty() && folderName == mcVer) {
                candidateVanilla = folderName;
            }
        }
    } catch (...) {}

    if (wantsAero && !candidateAero.empty()) return candidateAero;
    if (wantsFabric && !candidateFabric.empty()) return candidateFabric;
    if (!candidateFabric.empty()) return candidateFabric;
    if (!candidateVanilla.empty()) return candidateVanilla;

    return versionId;
}

GameLauncher& GameLauncher::Instance() {
    static GameLauncher s_instance;
    return s_instance;
}

GameLauncher::~GameLauncher() {
    TerminateGame();
}

static int CheckJavaVersion(const std::wstring& javaExe) {
    SECURITY_ATTRIBUTES sa = { sizeof(sa), nullptr, TRUE };
    HANDLE hRead, hWrite;
    if (!CreatePipe(&hRead, &hWrite, &sa, 0)) return 0;
    SetHandleInformation(hRead, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOW si = { sizeof(si) };
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = hWrite;
    si.hStdError = hWrite;

    PROCESS_INFORMATION pi = { 0 };
    std::wstring cmd = L"\"" + javaExe + L"\" -version";

    int major = 0;
    if (CreateProcessW(nullptr, cmd.data(), nullptr, nullptr, TRUE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        CloseHandle(hWrite);

        char buf[2048] = { 0 };
        std::string output;
        const auto deadline = GetTickCount64() + 3000;
        DWORD wait = WAIT_TIMEOUT;
        while (GetTickCount64() < deadline) {
            DWORD available = 0;
            if (!PeekNamedPipe(hRead, nullptr, 0, nullptr, &available, nullptr)) break;
            if (available == 0) {
                if (wait == WAIT_OBJECT_0) break;
                wait = WaitForSingleObject(pi.hProcess, 20);
                continue;
            }
            DWORD bytes = 0;
            if (!ReadFile(hRead, buf, (std::min)(available, static_cast<DWORD>(sizeof(buf))), &bytes, nullptr)) break;
            if (output.size() + bytes > 16384) break;
            output.append(buf, bytes);
        }

        const auto now = GetTickCount64();
        const DWORD remaining = now < deadline ? static_cast<DWORD>(deadline - now) : 0;
        DWORD exitCode = 1;
        if (WaitForSingleObject(pi.hProcess, remaining) != WAIT_OBJECT_0) {
            TerminateProcess(pi.hProcess, 1);
            WaitForSingleObject(pi.hProcess, 1000);
        } else {
            GetExitCodeProcess(pi.hProcess, &exitCode);
        }

        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);

        // Parse version
        std::smatch match;
        std::regex verRegex(R"(version\s+\"(\d+)(?:\.(\d+))?)");
        if (exitCode == 0 && std::regex_search(output, match, verRegex)) {
            int first = std::stoi(match[1]);
            major = (first == 1 && match[2].matched) ? std::stoi(match[2]) : first;
        }
    } else {
        CloseHandle(hWrite);
    }
    CloseHandle(hRead);

    return major;
}

static std::vector<JavaInstallation> s_cachedJavas;
static std::mutex s_javaMutex;
static bool s_javasScanned = false;

namespace {

const char* OracleJavaZip(int major) {
    switch (major) {
    case 16: return "https://download.java.net/java/GA/jdk16.0.2/d4a915d82b4c4fbb9bde534da945d746/7/GPL/openjdk-16.0.2_windows-x64_bin.zip";
    case 17: return "https://download.oracle.com/java/17/archive/jdk-17.0.12_windows-x64_bin.zip";
    case 21: return "https://download.oracle.com/java/21/archive/jdk-21.0.12.1_windows-x64_bin.zip";
    case 23: return "https://download.oracle.com/java/23/archive/jdk-23.0.2_windows-x64_bin.zip";
    case 25: return "https://download.oracle.com/java/25/latest/jdk-25_windows-x64_bin.zip";
    case 27: return "https://download.oracle.com/java/27/latest/jdk-27_windows-x64_bin.zip";
    default: return nullptr;
    }
}

int RequiredJava(int manifestMajor, const std::string& gameVersion) {
    if (manifestMajor > 0) return manifestMajor;
    static const std::regex release(R"(^1\.(\d{1,2})(?:\.(\d{1,2}))?$)");
    std::smatch match;
    if (!std::regex_match(gameVersion, match, release)) {
        return gameVersion.starts_with("a") || gameVersion.starts_with("b") ? 8 : 21;
    }
    const int minor = std::stoi(match[1]);
    if (minor < 17) return 8;
    if (minor == 17) return 16;
    if (minor < 20 || (minor == 20 && (!match[2].matched || std::stoi(match[2]) < 5))) return 17;
    return 21;
}

bool ExtractJavaZip(const fs::path& zip, const fs::path& target) {
    wchar_t systemDir[MAX_PATH]{};
    const UINT length = GetSystemDirectoryW(systemDir, MAX_PATH);
    if (!length || length >= MAX_PATH) return false;
    const auto tar = fs::path(systemDir) / L"tar.exe";
    auto args = L"\"" + tar.wstring() + L"\" -xf \"" + zip.wstring() + L"\" -C \"" + target.wstring() + L"\"";
    STARTUPINFOW startup{sizeof(startup)};
    PROCESS_INFORMATION process{};
    if (!CreateProcessW(tar.c_str(), args.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW,
        nullptr, nullptr, &startup, &process)) return false;
    const DWORD wait = WaitForSingleObject(process.hProcess, 120000);
    if (wait != WAIT_OBJECT_0) {
        TerminateProcess(process.hProcess, 1);
        WaitForSingleObject(process.hProcess, 5000);
    }
    DWORD code = 1;
    const bool success = wait == WAIT_OBJECT_0 && GetExitCodeProcess(process.hProcess, &code) && code == 0;
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return success;
}

std::wstring InstallOracleJava(int major, const std::function<void(const std::wstring&, float)>& progress,
        std::string* error) {
    const auto fail = [&](std::string reason) -> std::wstring {
        if (error) *error = std::move(reason);
        return {};
    };
    const auto source = OracleJavaZip(major);
    const auto label = "Java " + std::to_string(major);
    if (!source) return fail("Для " + label +
        " нет настроенного публичного архива Oracle. Установите нужную версию с https://www.oracle.com/java/technologies/downloads/ и выберите её в настройках.");
    if (Downloader::IsCancelled()) return fail("Установка Java отменена");

    const std::string url(source);
    const auto title = Utf8ToWide(label);
    if (progress) progress(L"Проверка загрузки " + title + L" у Oracle", -1);
    const auto checksum = HttpClient::Get(url + ".sha256", "", 10000, false);
    static const std::regex digest(R"(^\s*([0-9a-fA-F]{64})\s*$)");
    std::smatch match;
    if (!checksum.isSuccess || checksum.body.size() > 128 || !std::regex_match(checksum.body, match, digest)) {
        return fail("Oracle не вернул контрольную сумму для " + label + ". Проверьте соединение и повторите.");
    }

    const auto appData = SettingsManager::GetAppDataAeroDir();
    GUID id{};
    wchar_t name[40]{};
    if (appData.empty() || FAILED(CoCreateGuid(&id)) || !StringFromGUID2(id, name, 40)) {
        return fail("Не удалось подготовить папку для установки Java.");
    }
    const fs::path root = fs::path(appData) / L"java";
    const fs::path temporary = root / L"staging" / name;
    const auto unpacked = temporary / L"unpacked";
    std::error_code ec;
    if (!fs::create_directories(unpacked, ec)) return fail("Не удалось создать временную папку Java.");
    struct StagingCleanup {
        fs::path path;
        ~StagingCleanup() { std::error_code ignored; fs::remove_all(path, ignored); }
    } cleanup{temporary};

    DownloadOptions options;
    const auto zip = temporary / L"oracle-java.zip";
    options.url = url;
    options.destPath = zip.wstring();
    options.expectedSha256 = match[1].str();
    options.publicArtifact = true;
    if (progress) progress(L"Скачивание " + title + L" с сайта Oracle", -1);
    options.onProgress = [&](size_t bytes, size_t total) {
        if (progress) progress(L"Скачивание " + title + L" с сайта Oracle",
            total > 0 ? static_cast<float>(bytes) / static_cast<float>(total) : -1.0f);
    };
    std::string downloadError;
    if (!Downloader::DownloadFile(options, &downloadError)) {
        return fail("Не удалось скачать или проверить " + label + " с сайта Oracle. " + downloadError);
    }
    if (Downloader::IsCancelled()) return fail("Установка Java отменена");
    if (progress) progress(L"Установка " + title, -1);
    if (!ExtractJavaZip(zip, unpacked)) return fail("Не удалось распаковать архив " + label + ".");
    if (Downloader::IsCancelled()) return fail("Установка Java отменена");

    for (const auto& entry : fs::directory_iterator(unpacked, ec)) {
        if (ec) break;
        const auto exe = entry.path() / L"bin" / L"java.exe";
        if (!fs::is_regular_file(exe, ec) || CheckJavaVersion(exe.wstring()) != major) continue;
        const auto finalPath = root / (L"oracle-" + std::to_wstring(major) + L"-" + name);
        fs::rename(entry.path(), finalPath, ec);
        if (ec) return fail("Не удалось сохранить установленную " + label + ".");
        std::lock_guard lock(s_javaMutex);
        s_javasScanned = false;
        return (finalPath / L"bin" / L"java.exe").wstring();
    }
    return fail("В архиве Oracle не найдена рабочая " + label + ".");
}

} // namespace

std::vector<JavaInstallation> GameLauncher::ScanJavaInstallations(bool forceRefresh) {
    std::lock_guard<std::mutex> lock(s_javaMutex);
    if (s_javasScanned && !forceRefresh) {
        return s_cachedJavas;
    }

    std::vector<JavaInstallation> list;
    std::vector<fs::path> candidates;

    auto addCandidate = [&](const fs::path& p) {
        std::error_code ec;
        if (fs::exists(p, ec) && fs::is_regular_file(p, ec)) {
            candidates.push_back(p);
        }
    };

    // Check JAVA_HOME & JDK_HOME
    wchar_t envBuf[MAX_PATH];
    if (GetEnvironmentVariableW(L"JAVA_HOME", envBuf, MAX_PATH) > 0) {
        addCandidate(fs::path(envBuf) / L"bin" / L"java.exe");
    }
    if (GetEnvironmentVariableW(L"JDK_HOME", envBuf, MAX_PATH) > 0) {
        addCandidate(fs::path(envBuf) / L"bin" / L"java.exe");
    }

    // Roots to scan for JDK/JRE installations
    std::vector<std::wstring> rootDirs = {
        L"C:\\Program Files\\Java",
        L"C:\\Program Files\\Eclipse Adoptium",
        L"C:\\Program Files\\Microsoft",
        L"C:\\Program Files\\BellSoft",
        L"C:\\Program Files\\Amazon Corretto",
        L"C:\\Program Files\\Zulu",
        L"C:\\Program Files\\Semeru",
        L"C:\\Program Files (x86)\\Java",
        L"C:\\Program Files (x86)\\Minecraft Launcher\\runtime"
    };
    const auto appDataAero = SettingsManager::GetAppDataAeroDir();
    if (!appDataAero.empty()) rootDirs.push_back((fs::path(appDataAero) / L"java").wstring());

    // Add user profile dirs (.jdks, AppData, etc.)
    if (GetEnvironmentVariableW(L"USERPROFILE", envBuf, MAX_PATH) > 0) {
        fs::path userProfile(envBuf);
        rootDirs.push_back((userProfile / L".jdks").wstring());
    }
    if (GetEnvironmentVariableW(L"LOCALAPPDATA", envBuf, MAX_PATH) > 0) {
        fs::path localApp(envBuf);
        rootDirs.push_back((localApp / L"Programs" / L"Java").wstring());
        rootDirs.push_back((localApp / L"Programs" / L"Eclipse Adoptium").wstring());
    }
    if (GetEnvironmentVariableW(L"APPDATA", envBuf, MAX_PATH) > 0) {
        fs::path appData(envBuf);
        rootDirs.push_back((appData / L".minecraft" / L"runtime").wstring());
    }

    for (const auto& r : rootDirs) {
        std::error_code ec;
        if (!fs::exists(r, ec) || !fs::is_directory(r, ec)) continue;

        try {
            for (const auto& entry : fs::directory_iterator(r, ec)) {
                if (ec) break;
                if (entry.is_directory(ec)) {
                    addCandidate(entry.path() / L"bin" / L"java.exe");
                    addCandidate(entry.path() / L"java.exe");

                    // 1 level deeper for runtimes like runtime/java-runtime-gamma/windows-x64/java-runtime-gamma/bin/java.exe
                    for (const auto& subEntry : fs::directory_iterator(entry.path(), ec)) {
                        if (ec) break;
                        if (subEntry.is_directory(ec)) {
                            addCandidate(subEntry.path() / L"bin" / L"java.exe");
                            addCandidate(subEntry.path() / L"java.exe");
                        }
                    }
                }
            }
        } catch (...) {}
    }

    // System PATH java
    candidates.push_back(L"java.exe");

    for (const auto& path : candidates) {
        int ver = CheckJavaVersion(path.wstring());
        if (ver > 0) {
            std::wstring normPath = path.wstring();
            bool alreadyInList = false;
            for (const auto& existing : list) {
                if (_wcsicmp(existing.path.c_str(), normPath.c_str()) == 0) {
                    alreadyInList = true;
                    break;
                }
            }
            if (!alreadyInList) {
                JavaInstallation item;
                item.path = normPath;
                item.majorVersion = ver;
                item.description = L"Java " + std::to_wstring(ver) + L" (" + normPath + L")";
                list.push_back(item);
            }
        }
    }

    // Sort list by major version descending
    std::sort(list.begin(), list.end(), [](const JavaInstallation& a, const JavaInstallation& b) {
        return a.majorVersion > b.majorVersion;
    });

    s_cachedJavas = list;
    s_javasScanned = true;
    return s_cachedJavas;
}

std::string GameLauncher::DiagnoseExitCode(int exitCode, const std::vector<std::string>& logLines) {
    std::string text;
    for (const auto& l : logLines) text += l + "\n";

    if (text.find("UnsupportedClassVersionError") != std::string::npos) {
        return "Java слишком старая. Выбери более новую версию Java в настройках.";
    }
    if (text.find("Could not find or load main class") != std::string::npos) {
        return "Java не нашла главный класс — версия установлена не полностью. Нажми «Проверить и починить».";
    }
    if (text.find("NoClassDefFoundError") != std::string::npos || text.find("ClassNotFoundException") != std::string::npos) {
        return "Не хватает библиотек версии. Нажми «Проверить и починить» во вкладке «Менеджер версий».";
    }
    if (text.find("no lwjgl") != std::string::npos || text.find("UnsatisfiedLinkError") != std::string::npos) {
        return "Не распакованы нативные библиотеки (LWJGL). Нажми «Проверить и починить».";
    }
    if (text.find("OutOfMemoryError") != std::string::npos) {
        return "Не хватило оперативной памяти — уменьши выделенную RAM в настройках.";
    }
    if (text.find("Incompatible mod set") != std::string::npos) {
        return "Конфликт модов — мод собран под другую версию игры или загрузчика. Убери лишние моды в Мастерской.";
    }
    if (exitCode == 1) {
        return "Игра завершилась с кодом 1 — смотри подробности в консоли.";
    }
    return "Игра завершилась с кодом " + std::to_string(exitCode);
}

void GameLauncher::TerminateGame() {
    HANDLE h = hCurrentProcess_.load();
    if (h) {
        TerminateProcess(h, 0);
    }
    if (monitorThread_.joinable()) {
        monitorThread_.join();
    }
    isRunning_.store(false);
}

bool GameLauncher::Launch(
    const std::string& versionId,
    const Account& account,
    const std::wstring& instanceDir,
    std::function<void(const std::string&, bool)> onLog,
    std::function<void(int, const std::string&)> onExit,
    std::string* outError,
    std::function<void(const std::wstring&, float)> onPreparation
) {
    if (isRunning_.load()) {
        if (outError) *outError = "Игра уже запущена";
        return false;
    }

    auto& settings = SettingsManager::Instance().Get();
    if (versionId == aerocore::VersionId) {
        if (!aerocore::Ready(settings.gameDir)) {
            if (outError) *outError = "AeroCore не установлен. Откройте Менеджер версий.";
            return false;
        }
        const auto root = fs::absolute(aerocore::Root(settings.gameDir));
        const auto exe = root / L"horizon-client.exe";
        const auto runDir = fs::absolute(instanceDir);
        std::error_code ec;
        fs::create_directories(runDir, ec);
        if (ec) { if (outError) *outError = "Cannot create AeroCore game directory"; return false; }
        auto command = aerocore::Quote(exe.wstring()) + L" --resources "
            + aerocore::Quote((root / L".local/horizon-client/26.3").wstring())
            + L" --name " + aerocore::Quote(Utf8ToWide(account.username))
            + L" --width " + std::to_wstring(settings.windowWidth)
            + L" --height " + std::to_wstring(settings.windowHeight);
        const auto sessionPath = runDir / L"aerocore-session.json";
        if (account.type == AccountType::Microsoft && !account.accessToken.empty()) {
            nlohmann::json session = {{"name", account.username}, {"uuid", account.uuid},
                {"access_token", account.accessToken}, {"user_type", "msa"}};
            std::ofstream out(sessionPath, std::ios::binary);
            out << session.dump();
            if (!out) { if (outError) *outError = "Cannot write AeroCore session"; return false; }
            command += L" --session-file " + aerocore::Quote(sessionPath.wstring());
        }
        auto environment = aerocore::Environment(runDir.wstring());
        SECURITY_ATTRIBUTES sa{sizeof(sa), nullptr, TRUE};
        HANDLE read = nullptr, write = nullptr;
        if (!CreatePipe(&read, &write, &sa, 0)) { if (outError) *outError = "Cannot create AeroCore log pipe"; fs::remove(sessionPath, ec); return false; }
        SetHandleInformation(read, HANDLE_FLAG_INHERIT, 0);
        STARTUPINFOW si{}; si.cb = sizeof(si); si.dwFlags = STARTF_USESTDHANDLES;
        si.hStdOutput = write; si.hStdError = write;
        PROCESS_INFORMATION pi{};
        bool started = CreateProcessW(exe.c_str(), command.data(), nullptr, nullptr, TRUE,
            CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT, environment.data(), runDir.c_str(), &si, &pi) != FALSE;
        CloseHandle(write);
        if (!started) {
            CloseHandle(read); fs::remove(sessionPath, ec);
            if (outError) *outError = "Cannot launch AeroCore: " + std::to_string(GetLastError());
            return false;
        }
        CloseHandle(pi.hThread);
        hCurrentProcess_ = pi.hProcess;
        isRunning_.store(true);
        if (monitorThread_.joinable()) monitorThread_.join();
        auto finished = [onExit, sessionPath](int code, const std::string& diag) {
            std::error_code error; fs::remove(sessionPath, error);
            if (onExit) onExit(code, code == 0 ? diag : "AeroCore завершился с кодом " + std::to_string(code) + ". Проверьте журнал игры.");
        };
        monitorThread_ = std::thread(&GameLauncher::MonitorProcess, this, pi.hProcess, read, onLog, finished);
        if (onLog) onLog("[AeroCore] Native engine 26.3 started", false);
        return true;
    }
    fs::path gameDir = settings.gameDir;
    std::string resolvedId = ResolveVersionFolder(gameDir.wstring(), versionId);
    fs::path verDir = gameDir / L"versions" / Utf8ToWide(resolvedId);
    fs::path verJsonPath = verDir / Utf8ToWide(resolvedId + ".json");

    if (!fs::exists(verJsonPath)) {
        if (outError) *outError = "Версия не установлена на диске: " + versionId + " (папка: " + resolvedId + ")";
        return false;
    }

    VersionManifest manifest = MinecraftMeta::LoadVersionJson(verJsonPath.wstring());
    std::string baseVersionId = manifest.inheritsFrom.empty() ? manifest.id : manifest.inheritsFrom;

    // Build Classpath
    std::wstring classpath;
    fs::path libRootDir = gameDir / L"libraries";
    for (const auto& lib : manifest.libraries) {
        if (!lib.isNative) {
            fs::path p = libRootDir / Utf8ToWide(lib.path);
            if (fs::exists(p)) {
                if (!classpath.empty()) classpath += L";";
                classpath += p.wstring();
            }
        }
    }

    fs::path clientJarPath = gameDir / L"versions" / Utf8ToWide(baseVersionId) / Utf8ToWide(baseVersionId + ".jar");
    if (fs::exists(clientJarPath)) {
        if (!classpath.empty()) classpath += L";";
        classpath += clientJarPath.wstring();
    }

    // Java binary
    const int requiredJava = RequiredJava(manifest.javaMajor, baseVersionId);
    std::wstring javaExe = settings.javaPath;
    if (javaExe.empty() || CheckJavaVersion(javaExe) != requiredJava) {
        javaExe.clear();
        for (const auto& java : ScanJavaInstallations(true)) {
            if (java.majorVersion == requiredJava) {
                javaExe = java.path;
                break;
            }
        }
        if (javaExe.empty()) javaExe = InstallOracleJava(requiredJava, onPreparation, outError);
        if (javaExe.empty()) return false;
    }
    if (Downloader::IsCancelled()) return false;
    if (onPreparation) onPreparation(L"Запуск Minecraft", -1);

    // Natives path
    fs::path nativesDir = verDir / Utf8ToWide(resolvedId + "-natives");

    // Aero security guard token and session file
    std::string guardToken = GetAeroGuardToken();
    std::wstring sessionPath = (fs::path(SettingsManager::GetAppDataAeroDir()) / L"aero-session.json").wstring();

    // Command line construction
    std::wstringstream cmd;
    cmd << L"\"" << javaExe << L"\" ";
    cmd << L"-Xms" << settings.memoryMin << L"G ";
    cmd << L"-Xmx" << settings.memoryMax << L"G ";
    cmd << L"-Dlog4j2.formatMsgNoLookups=true ";
    cmd << L"-Dminecraft.launcher.brand=AeroLauncher ";
    cmd << L"-Dminecraft.launcher.version=" << Config::AppVersion << L" ";
    cmd << L"-Dfile.encoding=UTF-8 ";
    cmd << L"-Daero.guard.token=" << Utf8ToWide(guardToken) << L" ";
    cmd << L"-Daero.sessionFile=\"" << sessionPath << L"\" ";
    cmd << L"-Djava.library.path=\"" << nativesDir.wstring() << L"\" ";
    cmd << Utf8ToWide(settings.jvmArgs) << L" ";
    cmd << L"-cp \"" << classpath << L"\" ";
    cmd << Utf8ToWide(manifest.mainClass) << L" ";

    // Game arguments
    fs::path runDir = instanceDir.empty() ? gameDir : fs::path(instanceDir);
    fs::path assetsDir = gameDir / L"assets";
    std::string userType = (account.type == AccountType::Microsoft) ? "msa" : "legacy";

    cmd << L"--username " << Utf8ToWide(account.username) << L" ";
    cmd << L"--version " << Utf8ToWide(versionId) << L" ";
    cmd << L"--gameDir \"" << runDir.wstring() << L"\" ";
    cmd << L"--assetsDir \"" << assetsDir.wstring() << L"\" ";
    cmd << L"--assetIndex " << Utf8ToWide(manifest.assetIndexId.empty() ? baseVersionId : manifest.assetIndexId) << L" ";
    cmd << L"--uuid " << Utf8ToWide(account.uuid) << L" ";
    cmd << L"--accessToken " << Utf8ToWide(account.accessToken.empty() ? "0" : account.accessToken) << L" ";
    cmd << L"--userType " << Utf8ToWide(userType) << L" ";
    cmd << L"--versionType release ";
    cmd << L"--width " << settings.windowWidth << L" ";
    cmd << L"--height " << settings.windowHeight << L" ";
    if (settings.fullscreen) {
        cmd << L"--fullscreen ";
    }

    // Set Environment Variables for the launched process
    SetEnvironmentVariableW(L"AERO_SESSION_FILE", sessionPath.c_str());
    SetEnvironmentVariableW(L"AERO_API_BASE_URL", Utf8ToWide(Config::DefaultBaseUrl).c_str());

    // Setup stdout/stderr pipe
    SECURITY_ATTRIBUTES sa = { sizeof(sa), nullptr, TRUE };
    HANDLE hRead, hWrite;
    if (!CreatePipe(&hRead, &hWrite, &sa, 0)) {
        if (outError) *outError = "Failed to create log pipe";
        return false;
    }
    SetHandleInformation(hRead, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOW si = { sizeof(si) };
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = hWrite;
    si.hStdError = hWrite;

    PROCESS_INFORMATION pi = { 0 };
    std::wstring cmdLine = cmd.str();

    if (!CreateProcessW(
        nullptr,
        cmdLine.data(),
        nullptr,
        nullptr,
        TRUE,
        0,
        nullptr,
        runDir.c_str(),
        &si,
        &pi
    )) {
        CloseHandle(hWrite);
        CloseHandle(hRead);
        if (outError) *outError = "Не удалось запустить процесс Java (код ошибки " + std::to_string(GetLastError()) + ")";
        return false;
    }

    CloseHandle(hWrite);
    CloseHandle(pi.hThread);

    hCurrentProcess_ = pi.hProcess;
    isRunning_.store(true);

    if (onLog) {
        onLog("[Launcher] Запуск игры: " + versionId, false);
    }

    // Spawn monitoring thread
    if (monitorThread_.joinable()) {
        monitorThread_.join();
    }
    monitorThread_ = std::thread(&GameLauncher::MonitorProcess, this, pi.hProcess, hRead, onLog, onExit);

    return true;
}

void GameLauncher::MonitorProcess(
    HANDLE hProcess,
    HANDLE hPipeRead,
    std::function<void(const std::string&, bool)> onLog,
    std::function<void(int, const std::string&)> onExit
) {
    char buffer[4096];
    DWORD readBytes = 0;
    std::string lineAccumulator;
    std::vector<std::string> lastLines;

    while (ReadFile(hPipeRead, buffer, sizeof(buffer) - 1, &readBytes, nullptr) && readBytes > 0) {
        buffer[readBytes] = '\0';
        lineAccumulator.append(buffer, readBytes);

        size_t newlinePos = 0;
        while ((newlinePos = lineAccumulator.find('\n')) != std::string::npos) {
            std::string line = lineAccumulator.substr(0, newlinePos);
            lineAccumulator.erase(0, newlinePos + 1);

            if (!line.empty() && line.back() == '\r') line.pop_back();

            bool isErr = (line.find("ERROR") != std::string::npos || line.find("Exception") != std::string::npos);
            if (onLog) onLog(line, isErr);

            lastLines.push_back(line);
            if (lastLines.size() > 50) lastLines.erase(lastLines.begin());
        }
    }

    CloseHandle(hPipeRead);

    WaitForSingleObject(hProcess, INFINITE);

    DWORD exitCode = 0;
    GetExitCodeProcess(hProcess, &exitCode);
    CloseHandle(hProcess);

    hCurrentProcess_ = nullptr;
    isRunning_.store(false);

    std::string diag = DiagnoseExitCode(static_cast<int>(exitCode), lastLines);
    if (onExit) {
        onExit(static_cast<int>(exitCode), diag);
    }
}

} // namespace aero
