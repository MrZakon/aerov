#pragma once
#include <windows.h>
#include <algorithm>

namespace aero {

class Timer {
public:
    Timer() {
        QueryPerformanceFrequency(&frequency_);
        QueryPerformanceCounter(&lastTime_);
        startTime_ = lastTime_;
    }

    void Tick() {
        LARGE_INTEGER currentTime;
        QueryPerformanceCounter(&currentTime);

        LONGLONG delta = currentTime.QuadPart - lastTime_.QuadPart;
        lastTime_ = currentTime;

        deltaTime_ = static_cast<float>(static_cast<double>(delta) / frequency_.QuadPart);
        if (deltaTime_ > 0.1f) {
            deltaTime_ = 0.1f; // Cap delta to avoid jumps on window drag
        }

        totalTime_ = static_cast<float>(static_cast<double>(currentTime.QuadPart - startTime_.QuadPart) / frequency_.QuadPart);
    }

    float GetDeltaTime() const { return deltaTime_; }
    float GetTotalTime() const { return totalTime_; }

    void SleepRemaining(float targetFps = 60.0f) {
        if (targetFps <= 0.0f) return;
        double frameTargetSec = 1.0 / static_cast<double>(targetFps);

        LARGE_INTEGER now;
        QueryPerformanceCounter(&now);
        double elapsedSec = static_cast<double>(now.QuadPart - lastTime_.QuadPart) / static_cast<double>(frequency_.QuadPart);
        if (elapsedSec >= 0.0 && elapsedSec < frameTargetSec) {
            double remainingSec = frameTargetSec - elapsedSec;
            if (remainingSec > 0.001) {
                DWORD sleepMs = static_cast<DWORD>(remainingSec * 1000.0);
                Sleep((std::min)(sleepMs, DWORD{100}));
            }
        }
    }

private:
    LARGE_INTEGER frequency_{ 0 };
    LARGE_INTEGER lastTime_{ 0 };
    LARGE_INTEGER startTime_{ 0 };
    float deltaTime_ = 0.016f;
    float totalTime_ = 0.0f;
};

} // namespace aero
