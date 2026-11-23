cmake_minimum_required(VERSION 3.20)
project(AeroLauncher LANGUAGES CXX C RC)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(AERO_OUTPUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/bin" CACHE PATH "Directory for built launcher binaries")
add_compile_definitions(UNICODE _UNICODE NOMINMAX WIN32_LEAN_AND_MEAN)

if(MSVC)
    add_compile_options(/W4 /permissive- /utf-8 /EHsc /MP)
endif()

include_directories(
    src
    include
    resources
)

# Core sources
set(CORE_SOURCES
    src/core/Timer.h
    src/core/Animation.h
    src/core/Config.h
    src/core/HWID.h
    src/core/HWID.cpp
    src/core/Logger.h
    src/core/Logger.cpp
    src/core/Window.h
    src/core/Window.cpp
)

# Render sources
set(RENDER_SOURCES
    src/render/Theme.h
    src/render/D2DRenderer.h
    src/render/D2DRenderer.cpp
    src/render/Starfield.h
    src/render/Starfield.cpp
)

# UI controls
set(UI_SOURCES
    src/ui/UIElement.h
    src/ui/Button.h
    src/ui/Button.cpp
    src/ui/TextInput.h
    src/ui/TextInput.cpp
    src/ui/ComboBox.h
    src/ui/ComboBox.cpp
    src/ui/Checkbox.h
    src/ui/Checkbox.cpp
    src/ui/ProgressBar.h
    src/ui/ProgressBar.cpp
    src/ui/ScrollView.h
    src/ui/ScrollView.cpp
    src/ui/Toast.h
    src/ui/Toast.cpp
    src/ui/Slider.h
    src/ui/Slider.cpp
)

# Network & APIs
set(NET_SOURCES
    src/net/HttpClient.h
    src/net/HttpClient.cpp
    src/net/Downloader.h
    src/net/Downloader.cpp
    src/net/ServerPing.h
    src/net/ServerPing.cpp
    src/net/DiscordRpc.h
    src/net/DiscordRpc.cpp
)

# Data stores
set(STORE_SOURCES
    src/store/SettingsManager.h
    src/store/SettingsManager.cpp
    src/store/AccountManager.h
    src/store/AccountManager.cpp
    src/store/InstancesManager.h
    src/store/InstancesManager.cpp
)

# Auth & Workshop
set(SERVICES_SOURCES
    src/auth/AeroAuth.h
    src/auth/AeroAuth.cpp
    src/auth/MicrosoftAuth.h
    src/auth/MicrosoftAuth.cpp
    src/workshop/ModrinthClient.h
    src/workshop/ModrinthClient.cpp
)

# Minecraft game engine
set(GAME_SOURCES
    src/game/MinecraftMeta.h
    src/game/MinecraftMeta.cpp
    src/game/MinecraftInstaller.h
    src/game/MinecraftInstaller.cpp
    src/game/GameLauncher.h
    src/game/GameLauncher.cpp
)

# Views
set(VIEW_SOURCES
    src/views/TitleBarView.h
    src/views/TitleBarView.cpp
    src/views/SplashView.h
    src/views/SplashView.cpp
    src/views/InitialSetupView.h
    src/views/InitialSetupView.cpp
    src/views/AuthView.h
    src/views/AuthView.cpp
    src/views/PlayView.h
    src/views/PlayView.cpp
    src/views/AccountsView.h
    src/views/AccountsView.cpp
    src/views/VersionsView.h
    src/views/VersionsView.cpp
    src/views/WorkshopView.h
    src/views/WorkshopView.cpp
    src/views/GalleryView.h
    src/views/GalleryView.cpp
    src/views/ConsoleView.h
    src/views/ConsoleView.cpp
    src/views/SettingsView.h
    src/views/SettingsView.cpp
    src/views/ShellView.h
    src/views/ShellView.cpp
)

# Resources
set(RESOURCE_SOURCES
    resources/resource.h
    resources/launcher.rc
)

add_executable(AeroLauncher WIN32
    src/main.cpp
    src/net/LauncherUpdate.cpp
    src/views/LauncherUpdateDialog.cpp
    ${CORE_SOURCES}
    ${RENDER_SOURCES}
    ${UI_SOURCES}
    ${NET_SOURCES}
    ${STORE_SOURCES}
    ${SERVICES_SOURCES}
    ${GAME_SOURCES}
    ${VIEW_SOURCES}
    ${RESOURCE_SOURCES}
)

target_link_libraries(AeroLauncher PRIVATE
    d2d1
    dwrite
    windowscodecs
    dwmapi
    shell32
    ole32
    uuid
    user32
    gdi32
    shlwapi
    winhttp
    bcrypt
    crypt32
    wbemuuid
    ws2_32
)

if(MINGW)
    target_compile_definitions(AeroLauncher PRIVATE AERO_EMBED_MANIFEST)
    target_link_options(AeroLauncher PRIVATE -municode -static)
    if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        # Some MinGW distributions append a second process manifest from the GCC driver.
        execute_process(COMMAND "${CMAKE_CXX_COMPILER}" -dumpspecs OUTPUT_VARIABLE aero_gcc_specs)
        string(REGEX MATCH "\\*endfile:\n[^\n]+" aero_default_endfile "${aero_gcc_specs}")
        string(REPLACE "%{!shared:%:if-exists(default-manifest.o%s)}" ""
            aero_endfile "${aero_default_endfile}")
        if(NOT aero_endfile STREQUAL aero_default_endfile)
            set(aero_manifest_specs "${CMAKE_CURRENT_BINARY_DIR}/aero-manifest.specs")
            file(CONFIGURE OUTPUT "${aero_manifest_specs}" CONTENT "${aero_endfile}\n\n" @ONLY)
            target_link_options(AeroLauncher PRIVATE "-specs=${aero_manifest_specs}")
            set_property(TARGET AeroLauncher APPEND PROPERTY LINK_DEPENDS "${aero_manifest_specs}")
        endif()
    endif()
elseif(MSVC)
    target_link_options(AeroLauncher PRIVATE
        "/MANIFEST:EMBED"
        "/MANIFESTINPUT:${CMAKE_CURRENT_SOURCE_DIR}/resources/app.manifest"
    )
endif()

set_target_properties(AeroLauncher PROPERTIES
    OUTPUT_NAME "Aero Launcher"
    RUNTIME_OUTPUT_DIRECTORY "${AERO_OUTPUT_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${AERO_OUTPUT_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_DEBUG "${AERO_OUTPUT_DIR}"
)

add_executable(TestSuite
    tests/test_suite.cpp
    ${CORE_SOURCES}
    ${RENDER_SOURCES}
    ${UI_SOURCES}
    ${NET_SOURCES}
    ${STORE_SOURCES}
    ${SERVICES_SOURCES}
    ${GAME_SOURCES}
)

target_compile_definitions(TestSuite PRIVATE AERO_TESTING)
if(MINGW)
    target_link_options(TestSuite PRIVATE -static)
endif()

target_link_libraries(TestSuite PRIVATE
    d2d1
    dwrite
    windowscodecs
    dwmapi
    shell32
    ole32
    uuid
    user32
    gdi32
    shlwapi
    winhttp
    bcrypt
    crypt32
    wbemuuid
    ws2_32
)

set_target_properties(TestSuite PROPERTIES
    OUTPUT_NAME "test_suite"
    RUNTIME_OUTPUT_DIRECTORY "${AERO_OUTPUT_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_RELEASE "${AERO_OUTPUT_DIR}"
    RUNTIME_OUTPUT_DIRECTORY_DEBUG "${AERO_OUTPUT_DIR}"
)
