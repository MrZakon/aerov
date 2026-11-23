#include "Logger.h"
#include <windows.h>

namespace aero {

std::vector<LogCallback> Logger::s_callbacks;
std::mutex Logger::s_mutex;

void Logger::Log(const std::string& line, bool isError, bool isWarn) {
    std::string prefix = isError ? "[ERROR] " : (isWarn ? "[WARN] " : "[INFO] ");
    std::string fullLine = prefix + line;
    OutputDebugStringA((fullLine + "\n").c_str());

    std::vector<LogCallback> cbs;
    {
        std::lock_guard<std::mutex> lock(s_mutex);
        cbs = s_callbacks;
    }
    for (auto& cb : cbs) {
        if (cb) {
            cb(line, isError, isWarn);
        }
    }
}

void Logger::Info(const std::string& line) {
    Log(line, false, false);
}

void Logger::Warn(const std::string& line) {
    Log(line, false, true);
}

void Logger::Error(const std::string& line) {
    Log(line, true, false);
}

void Logger::AddCallback(LogCallback cb) {
    std::lock_guard<std::mutex> lock(s_mutex);
    s_callbacks.push_back(cb);
}

void Logger::ClearCallbacks() {
    std::lock_guard<std::mutex> lock(s_mutex);
    s_callbacks.clear();
}

} // namespace aero
