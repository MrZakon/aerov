#pragma once
#include <string>

namespace aero {

struct Config {
    static inline std::wstring AppName = L"Aero Launcher";
    static inline std::wstring AppVersion = L"1.3.0";
    static inline std::wstring UserAgent = L"AeroLauncher/1.3.0 (+https://aeroproject.space)";

    static inline std::string DefaultBaseUrl = "https://aeroproject.space";
    // Endpoints
    struct Auth {
        static inline std::string Register = "/api/auth/register";
        static inline std::string Login = "/api/visuals/client/login";
        static inline std::string Logout = "/api/visuals/client/logout";
        static inline std::string Me = "/api/auth/me";
        static inline std::string Redeem = "/api/auth/redeem";
        static inline std::string Password = "/api/auth/password";
        static inline std::string WebSessionStart = "/api/auth/web-session/start";
        static inline std::string WebSessionStatus = "/api/auth/web-session/status";
        static inline std::string WebSessionCancel = "/api/auth/web-session/cancel";
    };

    struct Client {
        static inline std::string Info = "/api/client/info";
        static inline std::string ModMeta = "/api/client/download/mod";
        static inline std::string ModFile = "/api/client/download/mod/file";
        static inline std::string LauncherMeta = "/api/client/download/launcher";
        static inline std::string LauncherFile = "/api/client/download/launcher/file";
        static inline std::string FreeMeta = "/api/client/download/free";
        static inline std::string FreeFile = "/api/client/download/free/file";
    };

    struct System {
        static inline std::string Status = "/api/status";
        static inline std::string Health = "/api/health";
        static inline std::string Mod = "/api/mod";
        static inline std::string Changelog = "/api/changelog";
    };

    struct MinecraftMeta {
        static inline std::string VanillaManifest = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
        static inline std::string AssetsRoot = "https://resources.download.minecraft.net";
        static inline std::string FabricGame = "https://meta.fabricmc.net/v2/versions/game";
        static inline std::string FabricLoader = "https://meta.fabricmc.net/v2/versions/loader";
    };

    struct Modrinth {
        static inline std::string Api = "https://api.modrinth.com/v2";
        static inline std::string Site = "https://modrinth.com";
    };

    struct Discord {
        static inline std::string ClientId = "1548006102392635522";
    };
};

} // namespace aero
