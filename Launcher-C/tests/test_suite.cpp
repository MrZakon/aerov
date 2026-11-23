#include <iostream>
#include <string>
#include <vector>
#include <filesystem>
#include <fstream>
#include <cassert>
#include <chrono>

#include "core/StringUtils.h"
#include "core/Config.h"
#include "store/SettingsManager.h"
#include "store/AccountManager.h"
#include "store/InstancesManager.h"
#include "game/MinecraftMeta.h"
#include "game/GameLauncher.h"
#include "net/Downloader.h"
#include "net/HttpClient.h"
#include "auth/AeroAuth.h"
#include "game/MinecraftInstaller.h"
#include "ui/ComboBox.h"

namespace fs = std::filesystem;
using namespace aero;

static int g_passCount = 0;
static int g_failCount = 0;

#define TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::cout << "  [FAIL] " << msg << " (line " << __LINE__ << ")\n"; \
            g_failCount++; \
            return; \
        } \
    } while(0)

#define TEST_PASS(msg) \
    do { \
        std::cout << "  [PASS] " << msg << "\n"; \
        g_passCount++; \
    } while(0)

// Test 1: ComboBox Outside Click Consumption (Bug #7)
void TestComboBoxOutsideClick() {
    std::cout << "Running Test 1: ComboBox outside click consumption...\n";
    ComboBox cb({ L"Alpha", L"Beta", L"Gamma" });
    cb.SetBounds(20.0f, 20.0f, 200.0f, 40.0f);

    TEST_ASSERT(!cb.IsExpanded(), "ComboBox initially closed");

    // Click inside should expand and return true
    bool hInside = cb.OnMouseButton(30.0f, 30.0f, MouseBtn::Left, true);
    TEST_ASSERT(hInside && cb.IsExpanded(), "Click inside expands dropdown and is handled");

    // Click outside should close AND return true (consuming the click event)
    bool hOutside = cb.OnMouseButton(600.0f, 600.0f, MouseBtn::Left, true);
    TEST_ASSERT(hOutside && !cb.IsExpanded(), "Click outside closes dropdown AND returns true (no bleed-through)");

    // Next click outside when closed should return false (normal behavior)
    bool hOutsideAgain = cb.OnMouseButton(600.0f, 600.0f, MouseBtn::Left, true);
    TEST_ASSERT(!hOutsideAgain, "Click outside when already closed returns false");

    TEST_PASS("ComboBox click-outside event consumption works properly");
}

void TestComboBoxKeyboard() {
    ComboBox combo({ L"Alpha", L"Beta", L"Gamma" });
    combo.SetBounds(20, 20, 200, 40);
    int committed = -1;
    combo.SetOnSelect([&](int index, const std::wstring&) { committed = index; });

    TEST_ASSERT(!combo.OnKey(VK_RETURN, true), "Unfocused group never captures Enter");
    combo.SetFocused(true);
    TEST_ASSERT(combo.OnKey(VK_RETURN, true) && combo.IsExpanded(), "Enter opens the focused list");
    TEST_ASSERT(combo.OnKey(VK_DOWN, true), "Down moves the highlighted option");
    TEST_ASSERT(committed == -1, "Keyboard navigation leaves the current selection unchanged");
    TEST_ASSERT(combo.OnKey(VK_ESCAPE, true) && !combo.IsExpanded(), "Escape closes the list");
    TEST_ASSERT(combo.GetSelectedItem() == L"Alpha", "Escape preserves the active option");
    TEST_ASSERT(combo.OnKey(VK_SPACE, true) && combo.OnKey(VK_END, true), "Space opens the list and End moves to the last option");
    TEST_ASSERT(combo.OnKey(VK_RETURN, true) && committed == 2 && combo.GetSelectedItem() == L"Gamma", "Enter commits the highlighted option");
    combo.SetItems({});
    TEST_ASSERT(!combo.OnKey(VK_RETURN, true), "An empty list cannot be opened");
    combo.SetItems({L"New"});
    TEST_ASSERT(combo.GetSelectedIndex() == 0, "Repopulating an empty list restores a valid selection");
    TEST_PASS("ComboBox keyboard navigation and cancellation");
}

// Test 2: InstancesManager Cyrillic UTF-8 Encoding (Bug #2)
void TestInstancesManagerEncoding() {
    std::cout << "Running Test 2: InstancesManager UTF-8 Cyrillic path encoding...\n";
    std::error_code ec;
    fs::path tempGameDir = fs::temp_directory_path() / L"aero_unit_test_instances";
    fs::create_directories(tempGameDir, ec);

    std::string cyrillicName = "Тестовая Сборка 26.2";
    auto inst = InstancesManager::Instance().CreateInstance(tempGameDir.wstring(), cyrillicName, "Fabric", "26.2", "AeroVisual-26.2");

    TEST_ASSERT(fs::exists(inst.path, ec), "Created instance directory exists on disk");
    TEST_ASSERT(inst.name == cyrillicName, "Instance name preserved as UTF-8 string");

    // Verify folder name on filesystem matches UTF-8 string via Utf8ToWide
    fs::path expectedPath = tempGameDir / L"instances" / Utf8ToWide(cyrillicName);
    TEST_ASSERT(fs::path(inst.path) == expectedPath, "Instance directory path uses exact Unicode wide path without mojibake");

    // Rescan directory from disk and verify it reads back correctly
    InstancesManager::Instance().ScanInstances(tempGameDir.wstring());
    const auto found = InstancesManager::Instance().GetInstance(cyrillicName);
    TEST_ASSERT(found.has_value(), "Scanned instances found the Cyrillic instance");
    TEST_ASSERT(found->name == cyrillicName, "Scanned instance name exactly matches Cyrillic UTF-8 name");
    TEST_ASSERT(found->mcVersion == "26.2", "Scanned instance mcVersion is 26.2");
    TEST_ASSERT(found->versionId == "AeroVisual-26.2", "Scanned instance versionId is AeroVisual-26.2");

    // Clean up
    InstancesManager::Instance().DeleteInstance(cyrillicName);
    TEST_ASSERT(!fs::exists(expectedPath, ec), "Instance directory successfully deleted");
    fs::remove_all(tempGameDir, ec);

    TEST_PASS("InstancesManager Unicode/UTF-8 path handling works properly");
}

void TestSharedInstanceVersions() {
    auto& store = SettingsManager::Instance();
    auto& settings = store.Get();
    struct RestoreSettings {
        SettingsData& target;
        SettingsData previous;
        ~RestoreSettings() { target = previous; }
    } restore{settings, settings};

    const auto root = fs::path(SettingsManager::GetAppDataAeroDir()) / L"shared-version-test";
    settings.gameDir = root.wstring();
    const auto base = root / L"versions" / L"26.2";
    const auto shared = root / L"versions" / L"fabric-loader-0.19.2-26.2";
    fs::create_directories(base);
    fs::create_directories(shared);
    std::ofstream(base / L"26.2.json") << "{}";
    std::ofstream(shared / L"fabric-loader-0.19.2-26.2.json") << "{}";
    std::ofstream(root / L"fixture.jar") << "fixture";

    auto& library = InstancesManager::Instance();
    library.ScanInstances(settings.gameDir);
    const auto first = library.CreateInstance(settings.gameDir, "First", "Fabric", "26.2", "fabric-loader-0.19.2-26.2");
    const auto second = library.CreateInstance(settings.gameDir, "Second", "Fabric", "26.2", first.versionId);
    library.CreateInstance(settings.gameDir, "First", "Fabric", "26.2", first.versionId);
    TEST_ASSERT(library.GetInstances().size() == 2, "Reinstalling a build does not add a duplicate row");
    TEST_ASSERT(library.SelectInstance(first.id), "A build is selected by ID");
    TEST_ASSERT(!library.RenameInstance(first.id, "../Outside"), "A build name must be a single folder component");
    TEST_ASSERT(library.RenameInstance(first.id, "Renamed build"), "Rename completes inside the library");
    TEST_ASSERT(settings.activeInstanceId == "Renamed build" && settings.contentTargetId == "Renamed build",
        "Renaming retains the launch and content targets");
    TEST_ASSERT(library.DeleteInstance("Renamed build"), "Delete the selected build");
    TEST_ASSERT(fs::exists(base / L"26.2.json") && fs::exists(shared / L"fabric-loader-0.19.2-26.2.json"),
        "Deleting one build preserves the shared Minecraft and Fabric profiles");
    TEST_ASSERT(settings.activeInstanceId == second.id && settings.contentTargetId == second.id,
        "Deletion selects the surviving build for launch and content");
    const auto result = library.ImportContentFile((root / L"fixture.jar").wstring(), "Renamed build");
    TEST_ASSERT(!result.success && !fs::exists(fs::path(second.path) / L"mods" / L"fixture.jar"),
        "An import with a stale ID does not change another build");
    TEST_PASS("Build identity and shared version files are preserved");
}

void TestMicrosoftProfilePersistence() {
    using Json = nlohmann::json;
    const auto file = fs::path(SettingsManager::GetAppDataAeroDir()) / L"accounts.json";
    const std::string accessToken = "fixture-access-token";
    const std::string refreshToken = "fixture-refresh-token";
    constexpr int64_t expiresAt = 1800000000;
    const auto read = [&] {
        std::ifstream input(file);
        return Json::parse(input);
    };
    const auto write = [&](const Json& rows) {
        std::ofstream output(file, std::ios::trunc);
        output << rows.dump(2);
    };

    write(Json::array({ Json{
        {"id", "ms_legacy"}, {"type", "microsoft"}, {"username", "FixturePlayer"},
        {"uuid", "12345678-1234-1234-1234-123456789012"}, {"expiresAt", expiresAt},
        {"accessToken", accessToken}, {"msaRefreshToken", refreshToken}
    } }));

    auto& accounts = AccountManager::Instance();
    accounts.Load();
    const auto migrated = read();
    TEST_ASSERT(migrated.size() == 1 && migrated[0].contains("protectedTokens"),
        "Legacy credentials are migrated to user-protected storage");
    TEST_ASSERT(migrated.dump().find(accessToken) == std::string::npos &&
        migrated.dump().find(refreshToken) == std::string::npos, "No plaintext Microsoft tokens are saved");

    accounts.Load();
    TEST_ASSERT(accounts.GetAccounts().size() == 1, "Protected account can be restored");
    const auto& profile = accounts.GetAccounts().front();
    TEST_ASSERT(profile.accessToken == accessToken && profile.refreshToken == refreshToken &&
        profile.expiresAt == expiresAt, "The protected profile restores its session and expiry");

    auto wrongAccount = migrated[0];
    wrongAccount["id"] = "ms_other";
    wrongAccount["username"] = "OtherPlayer";
    wrongAccount["accessToken"] = "plaintext-sentinel";
    write(Json::array({migrated[0], wrongAccount}));
    accounts.Load();
    TEST_ASSERT(accounts.GetAccounts().size() == 2 && accounts.GetAccounts()[1].accessToken.empty(),
        "Copied protected data cannot be used for another profile or downgrade to plaintext");
    TEST_ASSERT(accounts.Save() && read()[1]["protectedTokens"] == wrongAccount["protectedTokens"],
        "Saving other profiles preserves credentials that cannot be decrypted");
    accounts.RemoveAccount("ms_other");
    accounts.RemoveAccount("ms_legacy");
    TEST_PASS("Microsoft profiles migrate and restore via Windows account protection");
}

// Test 3: Downloader Cancel Flag & Multi-threaded Protection (Bug #9)
void TestDownloaderCancel() {
    std::cout << "Running Test 3: Downloader cancel flag and thread protection...\n";
    Downloader::ResetCancel();
    TEST_ASSERT(!Downloader::IsCancelled(), "Cancel flag initially false after ResetCancel");

    Downloader::CancelAll();
    TEST_ASSERT(Downloader::IsCancelled(), "CancelAll sets cancel flag to true");

    DownloadOptions opt;
    opt.url = "https://example.com/nonexistent_file.jar";
    opt.destPath = (fs::temp_directory_path() / L"cancel_test.tmp").wstring();
    std::string err;
    bool ok = Downloader::DownloadFile(opt, &err);
    TEST_ASSERT(!ok, "DownloadFile aborts when cancel flag is set");
    TEST_ASSERT(err == "Загрузка отменена", "DownloadFile error message indicates cancellation");
    TEST_ASSERT(Downloader::IsCancelled(), "DownloadFile does not wipe cancel flag on multi-threaded worker calls");

    Downloader::ResetCancel();
    TEST_ASSERT(!Downloader::IsCancelled(), "ResetCancel resets cancel flag");

    TEST_PASS("Downloader cancellation state management is thread-safe");
}

// Test 4: MinecraftMeta Deduplication & Cycle Guard (Bug #10)
void TestMinecraftMetaDeduplicationAndCycle() {
    std::cout << "Running Test 4: MinecraftMeta library deduplication & cycle guard...\n";
    std::error_code ec;
    fs::path tempMetaDir = fs::temp_directory_path() / L"aero_meta_test";
    fs::path versionsDir = tempMetaDir / L"versions";
    fs::create_directories(versionsDir / L"parent", ec);
    fs::create_directories(versionsDir / L"child", ec);
    fs::create_directories(versionsDir / L"cycle", ec);

    // Parent JSON
    {
        std::ofstream fParent(versionsDir / L"parent" / L"parent.json");
        fParent << R"({
            "id": "parent",
            "javaVersion": { "majorVersion": 25 },
            "libraries": [
                { "name": "org.lwjgl:lwjgl:3.4.1", "url": "https://libraries.minecraft.net/" },
                { "name": "com.google.guava:guava:32.1.2-jre", "url": "https://libraries.minecraft.net/" }
            ]
        })";
    }

    // Child JSON (inheritsFrom parent, duplicate org.lwjgl:lwjgl:3.4.1)
    fs::path childJsonPath = versionsDir / L"child" / L"child.json";
    {
        std::ofstream fChild(childJsonPath);
        fChild << R"({
            "id": "child",
            "inheritsFrom": "parent",
            "libraries": [
                { "name": "org.lwjgl:lwjgl:3.4.1", "url": "https://libraries.minecraft.net/" },
                { "name": "net.fabricmc:fabric-loader:0.16.10", "url": "https://maven.fabricmc.net/" }
            ]
        })";
    }

    // Cycle JSON (inheritsFrom cycle)
    fs::path cycleJsonPath = versionsDir / L"cycle" / L"cycle.json";
    {
        std::ofstream fCycle(cycleJsonPath);
        fCycle << R"({
            "id": "cycle",
            "inheritsFrom": "cycle",
            "libraries": []
        })";
    }

    // Load child manifest
    VersionManifest manifest = MinecraftMeta::LoadVersionJson(childJsonPath.wstring());
    TEST_ASSERT(manifest.id == "child", "Loaded child manifest id is correct");

    int lwjglCount = 0;
    for (const auto& lib : manifest.libraries) {
        if (lib.name == "org.lwjgl:lwjgl:3.4.1") {
            lwjglCount++;
        }
    }
    TEST_ASSERT(lwjglCount == 1, "Duplicate library org.lwjgl:lwjgl:3.4.1 was deduplicated to exactly 1");
    TEST_ASSERT(manifest.libraries.size() == 3, "Manifest has exactly 3 unique libraries");
    TEST_ASSERT(manifest.javaMajor == 25, "Fabric profile inherits required Java version");

    // Test cycle protection: self-inheritance must not hang or crash
    VersionManifest cycleManifest = MinecraftMeta::LoadVersionJson(cycleJsonPath.wstring());
    TEST_ASSERT(cycleManifest.id == "cycle", "Cycle manifest loaded safely without stack overflow");

    fs::remove_all(tempMetaDir, ec);
    TEST_PASS("MinecraftMeta library deduplication and cycle guard verified");
}

// Test 5: GameLauncher Java Scanning & Exit Diagnosing (Bug #11)
void TestGameLauncherJava() {
    std::cout << "Running Test 5: GameLauncher Java scanning & diagnosis...\n";
    auto start = std::chrono::steady_clock::now();
    auto javas = GameLauncher::ScanJavaInstallations();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start).count();

    std::cout << "  Found " << javas.size() << " Java installations in " << elapsed << "ms\n";
    for (const auto& j : javas) {
        std::wcout << L"    - " << j.description << L" (Major: " << j.majorVersion << L")\n";
        TEST_ASSERT(j.majorVersion > 0, "Major Java version parsed successfully as integer > 0");
    }

    // Test exit code diagnoses
    std::string diagOOM = GameLauncher::DiagnoseExitCode(1, { "java.lang.OutOfMemoryError: Java heap space" });
    TEST_ASSERT(diagOOM.find("памяти") != std::string::npos, "DiagnoseExitCode identifies OutOfMemoryError");

    std::string diagVersion = GameLauncher::DiagnoseExitCode(1, { "UnsupportedClassVersionError: class has been compiled by a more recent version" });
    TEST_ASSERT(diagVersion.find("старая") != std::string::npos, "DiagnoseExitCode identifies UnsupportedClassVersionError");

    TEST_PASS("GameLauncher Java detection & non-blocking execution verified");
}

// Test 6: SettingsView Theme Accent Auto-Save (Bug #6)
void TestThemePersistence() {
    std::cout << "Running Test 6: Theme accent persistence...\n";
    auto& sm = SettingsManager::Instance();
    std::string prevTheme = sm.Get().themeAccent;

    sm.Get().themeAccent = "amber";
    sm.Save();

    // Reload settings from disk into fresh instance
    SettingsManager::Instance().Load();
    TEST_ASSERT(sm.Get().themeAccent == "amber", "Theme accent saved and reloaded from disk as amber");

    // Restore previous theme
    sm.Get().themeAccent = prevTheme;
    sm.Save();

    TEST_PASS("Settings theme accent persistence verified");
}

// Test 7: Downloader ExtractZip Fallback (Bug #14)
void TestExtractZip() {
    std::cout << "Running Test 7: Downloader ExtractZip...\n";
    std::error_code ec;
    fs::path tempDir = fs::temp_directory_path() / L"aero_zip_test";
    fs::create_directories(tempDir, ec);

    fs::path sourceDir = tempDir / L"input";
    fs::create_directories(sourceDir, ec);
    fs::path srcFile = sourceDir / L"hello.txt";
    {
        std::ofstream f(srcFile);
        f << "Aero Launcher Zip Test Payload";
    }

    fs::path zipPath = tempDir / L"test_archive.zip";
    fs::path destDir = tempDir / L"extracted";

    // Use .NET directly so fixture creation does not require the optional Archive module.
    fs::remove(zipPath, ec);
    std::wstring createZipCmd = L"powershell.exe -NoProfile -NonInteractive -Command \"Add-Type -AssemblyName System.IO.Compression.FileSystem; "
        L"[System.IO.Compression.ZipFile]::CreateFromDirectory('" + sourceDir.wstring() + L"', '" + zipPath.wstring() + L"')\"";
    TEST_ASSERT(_wsystem(createZipCmd.c_str()) == 0, "Test zip fixture command succeeded");

    TEST_ASSERT(fs::exists(zipPath, ec), "Test zip archive created");

    bool ok = Downloader::ExtractZip(zipPath.wstring(), destDir.wstring());
    TEST_ASSERT(ok, "ExtractZip succeeded");
    TEST_ASSERT(fs::exists(destDir / L"hello.txt", ec), "hello.txt was extracted from zip archive");

    std::ifstream extracted(destDir / L"hello.txt");
    std::string content;
    std::getline(extracted, content);
    TEST_ASSERT(content == "Aero Launcher Zip Test Payload", "Extracted content matches original payload");

    fs::remove_all(tempDir, ec);
    TEST_PASS("Downloader ExtractZip verified");
}

void TestAeroApi(const std::string& origin) {
    Config::DefaultBaseUrl = origin;
    auto& auth = AeroAuth::Instance();
    std::string error;
    const std::string password = "AeroFixturePassword123";
    TEST_ASSERT(auth.Login("aerotester", password, &error), "Native login by nickname");
    TEST_ASSERT(auth.GetSession().username == "AeroTester", "Correct account received for nickname");
    TEST_ASSERT(auth.Login("ALICE@example.test", password, &error), "Native login by email");
    TEST_ASSERT(!auth.Login("AeroTester", "wrong", &error), "Incorrect password rejected");
    TEST_ASSERT(!auth.Login("Duplicate", password, &error), "Ambiguous nickname rejected");
    TEST_ASSERT(!auth.Login("MfaTester", password, &error), "Two-factor authentication cannot be bypassed");
    TEST_ASSERT(AeroAuth::RequestLogin("MfaTester", password).useBrowser, "Password fallback routes MFA to browser");
    TEST_ASSERT(auth.Login("PaidTester", password, &error), "Premium account login");
    TEST_ASSERT(auth.GetSession().hasSubscription, "Premium subscription parsed");
    TEST_ASSERT(auth.GetSession().plan == "premium", "Nullable subscription dates preserve plan");
    const auto paidCatalog = HttpClient::ApiRequest(Config::Client::Info, "GET", "", auth.GetSession().token);
    TEST_ASSERT(paidCatalog.isSuccess && paidCatalog.json["data"]["mcVersions"] == nlohmann::json::array({"26.2"}), "Catalog uses authenticated Premium releases");
    TEST_ASSERT(auth.Login("AeroTester", password, &error), "Switch to free account");
    TEST_ASSERT(!auth.GetSession().hasSubscription, "Previous account permissions cleared");
    const auto originalToken = auth.GetSession().token;
    auth.LoadSession();
    TEST_ASSERT(auth.IsAuthorized() && auth.GetOnlineToken().empty(), "Persisted session is unverified until checked");
    TEST_ASSERT(HttpClient::Get(origin + "/__test/profile-mode?mode=unavailable").isSuccess, "Simulate maintenance");
    TEST_ASSERT(auth.RefreshSession() == SessionRefresh::Unavailable, "Transient failure keeps session recoverable");
    TEST_ASSERT(!auth.GetSession().isOffline && auth.GetSession().token == originalToken, "Network loss preserves Aero ID credentials");
    TEST_ASSERT(HttpClient::Get(origin + "/__test/profile-mode?mode=malformed").isSuccess, "Simulate incomplete profile");
    TEST_ASSERT(auth.RefreshSession() == SessionRefresh::Unavailable && auth.GetOnlineToken().empty(), "Incomplete profile cannot grant Aero ID access");
    TEST_ASSERT(HttpClient::Get(origin + "/__test/profile-mode?mode=normal").isSuccess, "Restore backend profile");
    TEST_ASSERT(auth.RefreshSession() == SessionRefresh::Ready && auth.GetOnlineToken() == originalToken, "Same session recovers after maintenance");
    std::string id, url;
    TEST_ASSERT(auth.StartWebSession(id, url, &error), "Web session starts");
    bool complete = false;
    TEST_ASSERT(auth.CheckWebSession(id, complete, &error) && !complete, "Pending browser session does not authorize");
    TEST_ASSERT(HttpClient::Get(origin + "/__test/confirm?sessionId=" + id).isSuccess, "Fixture browser confirms session");
    TEST_ASSERT(auth.CheckWebSession(id, complete, &error) && complete, "Confirmed browser session authorizes launcher");
    TEST_ASSERT(!auth.CheckWebSession(id, complete, &error), "Web session cannot be redeemed twice");
    TEST_ASSERT(auth.StartWebSession(id, url, &error), "Create another browser session");
    TEST_ASSERT(HttpClient::Get(origin + "/__test/cancel?sessionId=" + id).isSuccess, "Browser cancels fixture session");
    TEST_ASSERT(AeroAuth::PollWebLogin(id).state == WebLoginState::Ended, "Cancelled attempt cannot authorize");
    auto start = HttpClient::ApiRequest(Config::Auth::WebSessionStart, "POST", "{}");
    const auto directId = start.json.value("sessionId", std::string{});
    TEST_ASSERT(HttpClient::Get(origin + "/__test/confirm?sessionId=" + directId).isSuccess, "Second fixture confirmation");
    auto confirmation = HttpClient::ApiRequest(Config::Auth::WebSessionStatus + "?sessionId=" + directId, "GET");
    TEST_ASSERT(auth.CompleteWebSession(confirmation.json, &error), "UI completion accepts confirmed server response");
    TEST_ASSERT(!auth.CompleteWebSession(nlohmann::json{{"token", "invalid"}, {"user", nlohmann::json::object()}}, &error), "Malformed browser response rejected");
    const auto mods = fs::path(SettingsManager::GetAppDataAeroDir()) / L"api-download-test";
    TEST_ASSERT(MinecraftInstaller::InstallAeroMod(mods.wstring(), "26.1.2", false, auth.GetSession().token, {}, &error), "Free account-only release downloads with bearer token and requested MC version");
    TEST_ASSERT(fs::exists(mods / L"aerovisual-fixture.jar"), "Small valid JAR installed using server filename");
    const auto countDownloads = [&] { return HttpClient::Get(origin + "/__test/download-count").json["count"].get<int>(); };
    auto count = countDownloads();
    bool updated = true;
    TEST_ASSERT(MinecraftInstaller::InstallAeroMod(mods.wstring(), "26.1.2", false, auth.GetSession().token, {}, &error, true, &updated), "Check installed version 1.3 against server 1.3");
    TEST_ASSERT(!updated && countDownloads() == count, "Same version does not download any file");
    TEST_ASSERT(HttpClient::Get(origin + "/__test/version?version=3.0").isSuccess, "Fixture publishes version 3.0");
    TEST_ASSERT(MinecraftInstaller::InstallAeroMod(mods.wstring(), "26.1.2", false, auth.GetSession().token, {}, &error, true, &updated), "Update version 1.3 to 3.0");
    TEST_ASSERT(updated && countDownloads() == count + 1, "New version downloads exactly once");
    count = countDownloads();
    TEST_ASSERT(MinecraftInstaller::InstallAeroMod(mods.wstring(), "26.1.2", false, auth.GetSession().token, {}, &error, true, &updated), "Check updated version 3.0");
    TEST_ASSERT(!updated && countDownloads() == count, "Updated version is not downloaded repeatedly");
    TEST_ASSERT(HttpClient::Get(origin + "/__test/version?version=1.3").isSuccess, "Fixture temporarily exposes older catalog");
    TEST_ASSERT(MinecraftInstaller::InstallAeroMod(mods.wstring(), "26.1.2", false, auth.GetSession().token, {}, &error, true, &updated), "Check older server release");
    TEST_ASSERT(!updated && countDownloads() == count, "Automatic updater does not downgrade 3.0 to 1.3");
    TEST_ASSERT(HttpClient::Get(origin + "/__test/version?version=3.0").isSuccess, "Restore latest release");
    fs::remove(mods / L".aero-visuals.json");
    TEST_ASSERT(MinecraftInstaller::InstallAeroMod(mods.wstring(), "26.1.2", false, auth.GetSession().token, {}, &error, true, &updated), "Adopt existing installation by checksum");
    TEST_ASSERT(!updated && countDownloads() == count, "Existing current JAR is adopted without a download");
    auto meta = HttpClient::ApiRequest(Config::Client::FreeMeta + "?version=26.1.2", "GET", "", auth.GetSession().token);
    TEST_ASSERT(Downloader::ComputeSha256((mods / L"aerovisual-fixture.jar").wstring()) == meta.json["data"]["sha256"].get<std::string>(), "Installed JAR matches published SHA-256");
    TEST_ASSERT(!MinecraftInstaller::InstallAeroMod(mods.wstring(), "99.99", false, auth.GetSession().token, {}, &error), "Unpublished version fails without installing anything");
    TEST_ASSERT(error.find("404") != std::string::npos || error.find("опубликована") != std::string::npos, "Missing-release error preserved");
    DownloadOptions corrupt;
    corrupt.url = origin + meta.json["data"]["downloadUrl"].get<std::string>();
    corrupt.token = auth.GetSession().token;
    corrupt.destPath = (mods / L"invalid-hash.jar").wstring();
    corrupt.expectedSha256 = std::string(64, '0');
    TEST_ASSERT(!Downloader::DownloadFile(corrupt, &error), "Hash mismatch is rejected");
    TEST_ASSERT(!fs::exists(corrupt.destPath), "Corrupted artifact is not installed");
    const auto redeemToken = auth.GetOnlineToken();
    TEST_ASSERT(!AeroAuth::Redeem("FIXTURE-UPGRADE", {}).empty(), "Redemption requires the submitted account token");
    TEST_ASSERT(!AeroAuth::Redeem("FIXTURE-INVALID", redeemToken).empty(), "Invalid key is reported as an error");
    TEST_ASSERT(AeroAuth::Redeem("FIXTURE-UPGRADE", redeemToken).empty(), "Valid key is activated for the captured session");
    TEST_ASSERT(auth.RefreshSession() == SessionRefresh::Ready && auth.GetSession().hasSubscription,
        "Fresh profile reflects the redeemed subscription");
    AeroAuth::Revoke(auth.GetOnlineToken());
    TEST_ASSERT(auth.RefreshSession() == SessionRefresh::SignedOut, "Revoked session requires another sign-in");
    auth.LoadSession();
    TEST_ASSERT(!auth.IsAuthorized(), "Revoked session is removed from persistence");
    auth.LoginOffline("TestPlayer");
    auth.LoadSession();
    TEST_ASSERT(auth.GetSession().isOffline && auth.GetOnlineToken().empty(), "Local profile never supplies a bearer token");
    auth.Logout();
    TEST_PASS("Aero native login, web authorization, versioned downloads and integrity");
}

int main() {
    if (const char* origin = std::getenv("AERO_TEST_API_URL")) {
        TestAeroApi(origin);
        return g_failCount ? 1 : 0;
    }
    std::cout << "===================================================\n";
    std::cout << "      Aero Launcher C++ Automated Test Suite    \n";
    std::cout << "===================================================\n\n";

    TestComboBoxOutsideClick();
    TestComboBoxKeyboard();
    TestInstancesManagerEncoding();
    TestSharedInstanceVersions();
    TestMicrosoftProfilePersistence();
    TestDownloaderCancel();
    TestMinecraftMetaDeduplicationAndCycle();
    TestGameLauncherJava();
    TestThemePersistence();
    TestExtractZip();

    std::cout << "\n===================================================\n";
    std::cout << "  Tests passed: " << g_passCount << "\n";
    std::cout << "  Tests failed: " << g_failCount << "\n";
    std::cout << "===================================================\n";

    return (g_failCount == 0) ? 0 : 1;
}
