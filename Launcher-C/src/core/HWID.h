#pragma once
#include <string>

namespace aero {

class HWID {
public:
    static std::string Get();
    static void Reset();

private:
    static std::string QueryWmiUuid();
    static std::string QuerySmbiosUuid();
    static std::string QueryCmdUuid();
    static std::string QueryMachineGuid();
};

} // namespace aero
