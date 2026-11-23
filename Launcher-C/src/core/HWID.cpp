#include "HWID.h"
#include "StringUtils.h"
#include <windows.h>
#include <wbemidl.h>
#include <vector>
#include <iomanip>
#include <sstream>
#include <algorithm>
#include <cctype>

#pragma comment(lib, "wbemuuid.lib")

namespace aero {

static std::string s_cachedHwid;

void HWID::Reset() {
    s_cachedHwid.clear();
}

std::string HWID::QueryWmiUuid() {
    IWbemLocator* pLoc = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_WbemLocator, 0, CLSCTX_INPROC_SERVER, IID_IWbemLocator, reinterpret_cast<LPVOID*>(&pLoc));
    if (FAILED(hr) || !pLoc) return "";

    IWbemServices* pSvc = nullptr;
    BSTR bstrNamespace = SysAllocString(L"ROOT\\CIMV2");
    hr = pLoc->ConnectServer(bstrNamespace, nullptr, nullptr, 0, 0, 0, 0, &pSvc);
    SysFreeString(bstrNamespace);
    if (FAILED(hr) || !pSvc) {
        pLoc->Release();
        return "";
    }

    CoSetProxyBlanket(pSvc, RPC_C_AUTHN_WINNT, RPC_C_AUTHZ_NONE, nullptr, RPC_C_AUTHN_LEVEL_CALL, RPC_C_IMP_LEVEL_IMPERSONATE, nullptr, EOAC_NONE);

    IEnumWbemClassObject* pEnumerator = nullptr;
    BSTR bstrQuery = SysAllocString(L"WQL");
    BSTR bstrSelect = SysAllocString(L"SELECT UUID FROM Win32_ComputerSystemProduct");
    hr = pSvc->ExecQuery(bstrQuery, bstrSelect, WBEM_FLAG_FORWARD_ONLY | WBEM_FLAG_RETURN_IMMEDIATELY, nullptr, &pEnumerator);
    SysFreeString(bstrQuery);
    SysFreeString(bstrSelect);

    std::string result;
    if (SUCCEEDED(hr) && pEnumerator) {
        IWbemClassObject* pclsObj = nullptr;
        ULONG uReturn = 0;
        if (SUCCEEDED(pEnumerator->Next(WBEM_INFINITE, 1, &pclsObj, &uReturn)) && uReturn > 0 && pclsObj) {
            VARIANT vtProp;
            VariantInit(&vtProp);
            if (SUCCEEDED(pclsObj->Get(L"UUID", 0, &vtProp, 0, 0)) && vtProp.vt == VT_BSTR && vtProp.bstrVal) {
                result = WideToUtf8(vtProp.bstrVal);
            }
            VariantClear(&vtProp);
            pclsObj->Release();
        }
        pEnumerator->Release();
    }
    pSvc->Release();
    pLoc->Release();

    for (char& c : result) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
    if (result.length() > 8 && result != "UNKNOWN_HWID" && result.find("ERROR") == std::string::npos) {
        return result;
    }
    return "";
}

std::string HWID::QueryMachineGuid() {
    HKEY hKey = nullptr;
    if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Cryptography", 0, KEY_READ | KEY_WOW64_64KEY, &hKey) == ERROR_SUCCESS) {
        wchar_t buffer[256] = { 0 };
        DWORD bufferSize = sizeof(buffer);
        DWORD type = REG_SZ;
        if (RegQueryValueExW(hKey, L"MachineGuid", nullptr, &type, reinterpret_cast<LPBYTE>(buffer), &bufferSize) == ERROR_SUCCESS) {
            RegCloseKey(hKey);
            std::string guid = WideToUtf8(buffer);
            for (char& c : guid) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
            return guid;
        }
        RegCloseKey(hKey);
    }
    return "";
}

// SMBIOS UUID format
struct RawSMBIOSData {
    BYTE  Used20CallingMethod;
    BYTE  SMBIOSMajorVersion;
    BYTE  SMBIOSMinorVersion;
    BYTE  DmiRevision;
    DWORD Length;
    BYTE  SMBIOSTableData[1];
};

struct SMBIOSHeader {
    BYTE Type;
    BYTE Length;
    WORD Handle;
};

std::string HWID::QuerySmbiosUuid() {
    const DWORD sig = 'RSMB';
    DWORD size = GetSystemFirmwareTable(sig, 0, nullptr, 0);
    if (size == 0) return "";

    std::vector<BYTE> buffer(size);
    if (GetSystemFirmwareTable(sig, 0, buffer.data(), size) == 0) return "";

    auto* smbios = reinterpret_cast<RawSMBIOSData*>(buffer.data());
    bool isLittleEndian = (smbios->SMBIOSMajorVersion > 2) ||
                          (smbios->SMBIOSMajorVersion == 2 && smbios->SMBIOSMinorVersion >= 6);

    BYTE* ptr = smbios->SMBIOSTableData;
    BYTE* end = ptr + smbios->Length;

    while (ptr + sizeof(SMBIOSHeader) <= end) {
        auto* header = reinterpret_cast<SMBIOSHeader*>(ptr);
        if (header->Type == 1 && header->Length >= 0x19) { // Type 1 = System Information
            BYTE* uuid = ptr + 0x08;
            bool allZero = true;
            bool allFF = true;
            for (int i = 0; i < 16; ++i) {
                if (uuid[i] != 0) allZero = false;
                if (uuid[i] != 0xFF) allFF = false;
            }
            if (!allZero && !allFF) {
                std::ostringstream ss;
                ss << std::uppercase << std::hex << std::setfill('0');

                if (isLittleEndian) {
                    // SMBIOS 2.6+ specification: first three fields are little-endian
                    // time_low (bytes 3, 2, 1, 0)
                    ss << std::setw(2) << static_cast<int>(uuid[3])
                       << std::setw(2) << static_cast<int>(uuid[2])
                       << std::setw(2) << static_cast<int>(uuid[1])
                       << std::setw(2) << static_cast<int>(uuid[0]) << "-";
                    // time_mid (bytes 5, 4)
                    ss << std::setw(2) << static_cast<int>(uuid[5])
                       << std::setw(2) << static_cast<int>(uuid[4]) << "-";
                    // time_hi_and_version (bytes 7, 6)
                    ss << std::setw(2) << static_cast<int>(uuid[7])
                       << std::setw(2) << static_cast<int>(uuid[6]) << "-";
                    // clock_seq (bytes 8, 9)
                    ss << std::setw(2) << static_cast<int>(uuid[8])
                       << std::setw(2) << static_cast<int>(uuid[9]) << "-";
                    // node (bytes 10..15)
                    for (int i = 10; i < 16; ++i) {
                        ss << std::setw(2) << static_cast<int>(uuid[i]);
                    }
                } else {
                    for (int i = 0; i < 4; ++i) ss << std::setw(2) << static_cast<int>(uuid[i]);
                    ss << "-";
                    for (int i = 4; i < 6; ++i) ss << std::setw(2) << static_cast<int>(uuid[i]);
                    ss << "-";
                    for (int i = 6; i < 8; ++i) ss << std::setw(2) << static_cast<int>(uuid[i]);
                    ss << "-";
                    for (int i = 8; i < 10; ++i) ss << std::setw(2) << static_cast<int>(uuid[i]);
                    ss << "-";
                    for (int i = 10; i < 16; ++i) ss << std::setw(2) << static_cast<int>(uuid[i]);
                }
                return ss.str();
            }
        }
        ptr += header->Length;
        while (ptr + 1 < end && (*ptr != 0 || *(ptr + 1) != 0)) {
            ptr++;
        }
        ptr += 2;
    }
    return "";
}

std::string HWID::QueryCmdUuid() {
    FILE* pipe = _popen("powershell -NoProfile -NonInteractive -Command \"(Get-CimInstance Win32_ComputerSystemProduct).UUID\"", "r");
    if (!pipe) {
        pipe = _popen("wmic csproduct get uuid", "r");
    }
    if (!pipe) return "";

    char buffer[256] = { 0 };
    std::string result;
    while (fgets(buffer, sizeof(buffer), pipe)) {
        std::string line = buffer;
        while (!line.empty() && (line.back() == '\r' || line.back() == '\n' || line.back() == ' ')) {
            line.pop_back();
        }
        size_t start = line.find_first_not_of(" \t");
        if (start != std::string::npos) line = line.substr(start);
        if (line.length() > 8 && line != "UUID" && line != "UNKNOWN_HWID" && line.find("error") == std::string::npos) {
            result = line;
            break;
        }
    }
    _pclose(pipe);

    for (char& c : result) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
    return result;
}

std::string HWID::Get() {
    if (!s_cachedHwid.empty()) {
        return s_cachedHwid;
    }

    // 1. Direct native Win32 WMI COM query (exact match to Node.js Win32_ComputerSystemProduct.UUID)
    std::string wmiUuid = QueryWmiUuid();
    if (!wmiUuid.empty()) {
        s_cachedHwid = wmiUuid;
        return s_cachedHwid;
    }

    // 2. Direct SMBIOS firmware table parsing
    std::string uuid = QuerySmbiosUuid();
    if (!uuid.empty()) {
        s_cachedHwid = uuid;
        return s_cachedHwid;
    }

    // 3. MachineGuid from registry
    std::string guid = QueryMachineGuid();
    if (!guid.empty()) {
        s_cachedHwid = guid;
        return s_cachedHwid;
    }

    // 4. Fallback process query
    std::string cmdUuid = QueryCmdUuid();
    if (!cmdUuid.empty()) {
        s_cachedHwid = cmdUuid;
        return s_cachedHwid;
    }

    s_cachedHwid = "UNKNOWN_HWID";
    return s_cachedHwid;
}

} // namespace aero
