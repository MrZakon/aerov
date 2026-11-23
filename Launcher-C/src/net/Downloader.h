#pragma once
#include <string>
#include <functional>
#include <atomic>
#include <cstdint>

namespace aero {

struct DownloadOptions {
    std::string url;
    std::wstring destPath;
    std::string token;
    std::string expectedSha1;
    std::string expectedSha256;
    std::function<void(size_t downloaded, size_t total)> onProgress;
    std::string* outFileName = nullptr;
    std::uint64_t expectedSize = 0;
    std::function<bool()> isCancelled;
    bool publicArtifact = false;
};

class Downloader {
public:
    static bool DownloadFile(const DownloadOptions& options, std::string* outError = nullptr);
    static void CancelAll();
    static void ResetCancel() { s_cancelRequested.store(false); }
    static bool IsCancelled() { return s_cancelRequested.load(); }

    static std::string ComputeSha1(const std::wstring& filePath);
    static std::string ComputeSha256(const std::wstring& filePath);
    static bool ExtractZip(const std::wstring& zipPath, const std::wstring& destDir);

private:
    static inline std::atomic<bool> s_cancelRequested{ false };
};

} // namespace aero
