#pragma once
#include <string>
#include <windows.h>
#include <atomic>
#include <thread>
#include <mutex>
#include <vector>

namespace aero {

struct DiscordActivity {
    std::string state;
    std::string details;
    std::string largeImage = "aero_logo";
    std::string largeText = "Aero Visuals 1.1.0 • aeroproject.space";
    std::string smallImage = "aero_logo";
    std::string smallText = "Визуалы Aero Visuals активны";
    int64_t startTimestamp = 0;
};

class DiscordRpc {
public:
    static DiscordRpc& Instance();

    void Initialize(const std::string& clientId = "1548006102392635522");
    void Shutdown();

    void SetEnabled(bool enabled);
    void SetActivity(const DiscordActivity& activity);
    void ClearActivity();

private:
    DiscordRpc();
    ~DiscordRpc();

    void WorkerLoop();
    bool TryConnectPipe();
    void ReadPackets();
    void SendPacket(int op, const std::string& json);

    std::string clientId_ = "1548006102392635522";
    HANDLE hPipe_ = INVALID_HANDLE_VALUE;
    std::atomic<bool> isRunning_{ false };
    std::atomic<bool> isEnabled_{ true };
    std::atomic<bool> isConnected_{ false };
    std::vector<char> receiveBuffer_;
    DiscordActivity currentActivity_;
    std::mutex mutex_;
    std::thread workerThread_;
};

} // namespace aero
