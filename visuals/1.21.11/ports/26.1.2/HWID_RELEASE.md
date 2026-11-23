# HWID и защищённая сборка

Текущее поведение проверки при запуске и исправление GPU-ожидания: [STARTUP_AUTH_GPU.md](STARTUP_AUTH_GPU.md).

Обе версии используют POST /api/client/verify-hwid с сессией Aero Launcher и X-HWID. Сайт проверяет активную подписку и привязку users.hwid. Успешная проверка кэшируется только в памяти процесса Minecraft до перезапуска.

Исходники лаунчера Windows/Linux/macOS сохраняют hwid и apiBaseUrl в файл сессии; Windows-лаунчер собран в Launcher-C/bin/Aero Launcher.exe. Для старых файлов без hwid сохраняется проверка серверной привязки. Значения токенов и HWID не выводятся в лог.

Сборка: assemble -x test -x compileTestJava --offline --no-daemon; затем scripts/obfuscate-hwid-release.ps1 с обфускатором из E:/Razrabotka/AeroProject/obf. Защищённые JAR: build/protected, карты имён отдельно: build/protected-mappings, параметры и контрольные суммы: build/protected/build-receipt.json. Карты не распространять вместе с релизом.

Применён профиль совместимости Fabric: переименование классов, шифрование строк, удаление отладочной информации и перемешивание членов. Сохранены точки входа Fabric, mixin-классы, JNA/HWID и мосты Windows Media / Voicechat. Текущий OBF не переименовывает методы и поля. Главное меню и GLFW-поведение не менялись. В папки mods файлы не устанавливались. Тесты и приложения не запускались.
