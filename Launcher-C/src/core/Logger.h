#pragma once
#include <string>
#include <vector>
#include <functional>
#include <mutex>

namespace aero {

using LogCallback = std::function<void(const std::string& line, bool isError, bool isWarn)>;

class Logger {
public:
    static void Log(const std::string& line, bool isError = false, bool isWarn = false);
    static void Info(const std::string& line);
    static void Warn(const std::string& line);
    static void Error(const std::string& line);

    static void AddCallback(LogCallback cb);
    static void ClearCallbacks();

private:
    static std::vector<LogCallback> s_callbacks;
    static std::mutex s_mutex;
};

} // namespace aero
