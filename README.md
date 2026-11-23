# Aero Launcher и AeroVisuals

- `Launcher-C/` — исходники Aero Launcher для Windows, ресурсы, тесты и скрипты сборки.
- `visuals/` — проекты Fabric для Minecraft 1.21.11, 26.1.2, 26.2, 26.3 и Free 1.21.11.

Для сборки лаунчера используйте CMake и компилятор C++20 под Windows. Для Minecraft 1.21.11 нужен JDK 21, для версий 26.x — JDK 25. В каждом проекте мода есть Gradle Wrapper. Первый запуск Gradle скачивает зависимости.

Задача `assemble` включает обфускацию. Передайте путь к отдельному обфускатору через `-PaeroObfuscator=<путь-к-jar>`. Для локальной сборки без обфускации используйте `gradlew.bat assemble -x protectRelease` из папки выбранной версии.
