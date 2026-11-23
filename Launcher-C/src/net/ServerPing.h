#pragma once
#include <string>

namespace aero {

struct ServerPingResult {
    bool isOnline = false;
    int pingMs = 0;
    std::string motd;
    int playersOnline = 0;
    int playersMax = 0;
    std::string version;
    std::string error;
};

class ServerPing {
public:
    static ServerPingResult Ping(const std::string& host, int port = 25565, int timeoutMs = 4000);
};

} // namespace aero
