#include "DiscordRpc.h"
#include <nlohmann/json.hpp>
#include <chrono>
#include "../core/Logger.h"
#include "../core/Config.h"
#include "../core/StringUtils.h"

namespace aero {

DiscordRpc::DiscordRpc() = default;

DiscordRpc::~DiscordRpc() {
    Shutdown();
}

DiscordRpc& DiscordRpc::Instance() {
    static DiscordRpc s_instance;
    return s_instance;
}

void DiscordRpc::Initialize(const std::string& clientId) {
    if (isRunning_.load()) return;
    clientId_ = clientId.empty() ? "1548006102392635522" : clientId;

    isRunning_.store(true);
    workerThread_ = std::thread(&DiscordRpc::WorkerLoop, this);
}

void DiscordRpc::Shutdown() {
    isRunning_.store(false);
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (hPipe_ != INVALID_HANDLE_VALUE) {
            CloseHandle(hPipe_);
            hPipe_ = INVALID_HANDLE_VALUE;
        }
        isConnected_.store(false);
    }
    if (workerThread_.joinable()) {
        workerThread_.join();
    }
}

void DiscordRpc::SetEnabled(bool enabled) {
    bool previous = isEnabled_.exchange(enabled);
    if (!enabled && isConnected_.load()) {
        ClearActivity();
    }
    if (enabled && !previous) {
        DiscordActivity activity;
        { std::lock_guard<std::mutex> lock(mutex_); activity = currentActivity_; }
        SetActivity(activity);
    }
}

bool DiscordRpc::TryConnectPipe() {
    for (int i = 0; i <= 9; ++i) {
        std::wstring pipeName = L"\\\\.\\pipe\\discord-ipc-" + std::to_wstring(i);
        HANDLE h = CreateFileW(
            pipeName.c_str(),
            GENERIC_READ | GENERIC_WRITE,
            0,
            nullptr,
            OPEN_EXISTING,
            0,
            nullptr
        );

        if (h != INVALID_HANDLE_VALUE) {
            {
                std::lock_guard<std::mutex> lock(mutex_);
                hPipe_ = h;
                receiveBuffer_.clear();
                isConnected_.store(true);
            }

            // Send handshake (Opcode 0)
            nlohmann::json handshake;
            handshake["v"] = 1;
            handshake["client_id"] = clientId_;
            SendPacket(0, handshake.dump());

            return true;
        }
    }
    return false;
}

void DiscordRpc::SendPacket(int op, const std::string& json) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (hPipe_ == INVALID_HANDLE_VALUE) return;

    int32_t len = static_cast<int32_t>(json.length());
    std::vector<char> buffer(8 + len);
    memcpy(buffer.data(), &op, 4);
    memcpy(buffer.data() + 4, &len, 4);
    memcpy(buffer.data() + 8, json.data(), len);

    DWORD written = 0;
    if (!WriteFile(hPipe_, buffer.data(), static_cast<DWORD>(buffer.size()), &written, nullptr) || written != buffer.size()) {
        CloseHandle(hPipe_);
        hPipe_ = INVALID_HANDLE_VALUE;
        isConnected_.store(false);
    }
}

void DiscordRpc::ReadPackets() {
    std::vector<std::pair<int32_t, std::string>> packets;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (hPipe_ == INVALID_HANDLE_VALUE) return;
        DWORD available = 0;
        auto disconnect = [this]() {
            CloseHandle(hPipe_); hPipe_ = INVALID_HANDLE_VALUE;
            isConnected_.store(false); receiveBuffer_.clear();
        };
        if (!PeekNamedPipe(hPipe_, nullptr, 0, nullptr, &available, nullptr)) { disconnect(); return; }
        if (available) {
            char bytes[8192]; DWORD received = 0;
            if (!ReadFile(hPipe_, bytes, (std::min)(available, DWORD(sizeof(bytes))), &received, nullptr)) { disconnect(); return; }
            receiveBuffer_.insert(receiveBuffer_.end(), bytes, bytes + received);
        }
        while (receiveBuffer_.size() >= 8) {
            int32_t op = 0; uint32_t length = 0;
            memcpy(&op, receiveBuffer_.data(), 4); memcpy(&length, receiveBuffer_.data() + 4, 4);
            if (length > 1024 * 1024) { disconnect(); return; }
            if (receiveBuffer_.size() < size_t(length) + 8) break;
            packets.emplace_back(op, std::string(receiveBuffer_.data() + 8, length));
            receiveBuffer_.erase(receiveBuffer_.begin(), receiveBuffer_.begin() + 8 + length);
            if (op == 2) { disconnect(); break; }
        }
    }
    for (const auto& [op, body] : packets) {
        if (op == 3) { SendPacket(4, body); continue; }
        if (op == 2) { Logger::Warn("Discord RPC: connection closed; will reconnect."); continue; }
        auto message = nlohmann::json::parse(body, nullptr, false);
        if (!message.is_object()) continue;
        // Successful command replies contain "evt": null, unlike dispatch events.
        const auto event = message.find("evt");
        if (event == message.end() || !event->is_string()) continue;
        if (*event == "READY") {
            Logger::Info("Discord RPC: connected to Aero application " + clientId_);
            DiscordActivity activity;
            { std::lock_guard<std::mutex> lock(mutex_); activity = currentActivity_; }
            if (isEnabled_.load()) SetActivity(activity);
        } else if (*event == "ERROR") {
            Logger::Warn("Discord RPC: " + message.value("data", nlohmann::json::object()).dump());
        }
    }
}

void DiscordRpc::SetActivity(const DiscordActivity& activity) {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        currentActivity_ = activity;
    }
    if (!isEnabled_.load() || !isConnected_.load()) return;

    nlohmann::json payload;
    payload["cmd"] = "SET_ACTIVITY";

    nlohmann::json act;
    act["state"] = activity.state.empty() ? "В лаунчере • Кастомные визуалы" : activity.state;
    act["details"] = activity.details.empty() ? "Aero Launcher " + WideToUtf8(Config::AppVersion) + " • aeroproject.space" : activity.details;

    if (activity.startTimestamp > 0) {
        act["timestamps"]["start"] = activity.startTimestamp;
    }

    act["assets"]["large_image"] = activity.largeImage;
    act["assets"]["large_text"] = activity.largeText;
    act["assets"]["small_image"] = activity.smallImage;
    act["assets"]["small_text"] = activity.smallText;

    nlohmann::json btn1, btn2;
    btn1["label"] = "🌐 Перейти на сайт";
    btn1["url"] = "https://aeroproject.space";
    btn2["label"] = "✨ Скачать визуалы";
    btn2["url"] = "https://aeroproject.space/visuals/";
    act["buttons"] = nlohmann::json::array({ btn1, btn2 });

    payload["args"]["pid"] = static_cast<int>(GetCurrentProcessId());
    payload["args"]["activity"] = act;
    payload["nonce"] = "aero-" + std::to_string(std::chrono::system_clock::now().time_since_epoch().count());

    SendPacket(1, payload.dump());
}

void DiscordRpc::ClearActivity() {
    if (!isConnected_.load()) return;

    nlohmann::json payload;
    payload["cmd"] = "SET_ACTIVITY";
    payload["args"]["pid"] = static_cast<int>(GetCurrentProcessId());
    payload["args"]["activity"] = nullptr;
    payload["nonce"] = "aero-clear";

    SendPacket(1, payload.dump());
}

void DiscordRpc::WorkerLoop() {
    auto nextConnect = std::chrono::steady_clock::now();
    while (isRunning_.load()) {
        try {
        if (isEnabled_.load()) {
            if (!isConnected_.load() && std::chrono::steady_clock::now() >= nextConnect) {
                TryConnectPipe();
                nextConnect = std::chrono::steady_clock::now() + std::chrono::seconds(5);
            }
        }
        if (isConnected_.load()) ReadPackets();
        } catch (const std::exception& error) {
            // An optional integration must never terminate the launcher.
            { std::lock_guard<std::mutex> lock(mutex_);
              if (hPipe_ != INVALID_HANDLE_VALUE) CloseHandle(hPipe_);
              hPipe_ = INVALID_HANDLE_VALUE; isConnected_.store(false); receiveBuffer_.clear(); }
            OutputDebugStringA((std::string("Discord RPC: ") + error.what() + "\n").c_str());
            nextConnect = std::chrono::steady_clock::now() + std::chrono::seconds(5);
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(250));
    }
}

} // namespace aero
