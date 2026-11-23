#include "ServerPing.h"
#include <winsock2.h>
#include <ws2tcpip.h>
#include <vector>
#include <chrono>
#include <nlohmann/json.hpp>

#pragma comment(lib, "ws2_32.lib")

namespace aero {

static void EncodeVarInt(int value, std::vector<uint8_t>& out) {
    uint32_t v = static_cast<uint32_t>(value);
    while (true) {
        if ((v & ~0x7F) == 0) {
            out.push_back(static_cast<uint8_t>(v));
            break;
        } else {
            out.push_back(static_cast<uint8_t>((v & 0x7F) | 0x80));
            v >>= 7;
        }
    }
}

static bool ReadVarInt(const uint8_t* buffer, size_t size, size_t& offset, int& outValue) {
    int result = 0;
    int numRead = 0;
    while (true) {
        if (offset >= size) return false;
        uint8_t byte = buffer[offset++];
        result |= (byte & 0x7F) << (7 * numRead);
        numRead++;
        if (numRead > 5) return false;
        if ((byte & 0x80) != 0x80) break;
    }
    outValue = result;
    return true;
}

ServerPingResult ServerPing::Ping(const std::string& host, int port, int timeoutMs) {
    ServerPingResult res;

    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        res.error = "WSAStartup failed";
        return res;
    }

    struct addrinfo hints = { 0 }, *servinfo = nullptr;
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = IPPROTO_TCP;

    std::string portStr = std::to_string(port);
    if (getaddrinfo(host.c_str(), portStr.c_str(), &hints, &servinfo) != 0) {
        WSACleanup();
        res.error = "DNS resolution failed";
        return res;
    }

    SOCKET s = socket(servinfo->ai_family, servinfo->ai_socktype, servinfo->ai_protocol);
    if (s == INVALID_SOCKET) {
        freeaddrinfo(servinfo);
        WSACleanup();
        res.error = "Socket creation failed";
        return res;
    }

    DWORD tv = timeoutMs;
    setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, reinterpret_cast<const char*>(&tv), sizeof(tv));
    setsockopt(s, SOL_SOCKET, SO_SNDTIMEO, reinterpret_cast<const char*>(&tv), sizeof(tv));

    auto tStart = std::chrono::steady_clock::now();
    if (connect(s, servinfo->ai_addr, static_cast<int>(servinfo->ai_addrlen)) == SOCKET_ERROR) {
        closesocket(s);
        freeaddrinfo(servinfo);
        WSACleanup();
        res.error = "Не удалось подключиться к серверу";
        return res;
    }
    freeaddrinfo(servinfo);

    // Handshake packet (ID 0x00)
    std::vector<uint8_t> packetData;
    packetData.push_back(0x00); // Packet ID
    EncodeVarInt(765, packetData); // Protocol version
    EncodeVarInt(static_cast<int>(host.length()), packetData);
    packetData.insert(packetData.end(), host.begin(), host.end());
    packetData.push_back(static_cast<uint8_t>((port >> 8) & 0xFF));
    packetData.push_back(static_cast<uint8_t>(port & 0xFF));
    EncodeVarInt(1, packetData); // Next state: 1 (status)

    std::vector<uint8_t> handshake;
    EncodeVarInt(static_cast<int>(packetData.size()), handshake);
    handshake.insert(handshake.end(), packetData.begin(), packetData.end());

    // Status request packet: length 1, id 0x00
    handshake.push_back(0x01);
    handshake.push_back(0x00);

    send(s, reinterpret_cast<const char*>(handshake.data()), static_cast<int>(handshake.size()), 0);

    std::vector<uint8_t> incoming;
    char chunk[4096];
    while (true) {
        int bytes = recv(s, chunk, sizeof(chunk), 0);
        if (bytes <= 0) break;
        incoming.insert(incoming.end(), chunk, chunk + bytes);

        size_t offset = 0;
        int totalLen = 0, packetId = 0, strLen = 0;
        if (!ReadVarInt(incoming.data(), incoming.size(), offset, totalLen)) continue;
        if (!ReadVarInt(incoming.data(), incoming.size(), offset, packetId)) continue;
        if (!ReadVarInt(incoming.data(), incoming.size(), offset, strLen)) continue;

        if (incoming.size() - offset >= static_cast<size_t>(strLen)) {
            std::string jsonStr(reinterpret_cast<char*>(incoming.data() + offset), strLen);
            auto tEnd = std::chrono::steady_clock::now();
            res.pingMs = static_cast<int>(std::chrono::duration_cast<std::chrono::milliseconds>(tEnd - tStart).count());
            res.isOnline = true;

            try {
                auto j = nlohmann::json::parse(jsonStr);
                if (j.contains("description")) {
                    if (j["description"].is_string()) {
                        res.motd = j["description"].get<std::string>();
                    } else if (j["description"].is_object() && j["description"].contains("text")) {
                        res.motd = j["description"]["text"].get<std::string>();
                    }
                }
                if (j.contains("players") && j["players"].is_object()) {
                    res.playersOnline = j["players"].value("online", 0);
                    res.playersMax = j["players"].value("max", 0);
                }
                if (j.contains("version") && j["version"].is_object()) {
                    res.version = j["version"].value("name", "Minecraft");
                }
            } catch (...) {
                res.motd = "Minecraft Server";
            }
            break;
        }
    }

    closesocket(s);
    WSACleanup();

    if (!res.isOnline && res.error.empty()) {
        res.error = "Таймаут соединения";
    }

    return res;
}

} // namespace aero
