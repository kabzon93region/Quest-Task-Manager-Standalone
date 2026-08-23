# QTaskMgr Standalone v2.1.0

## Wireless ADB

- **Pairing** и **подключение** к `127.0.0.1` через библиотеку [Kadb](https://github.com/flyfishxu/Kadb).
- RSA-ключи сохраняются в приватном хранилище приложения (`filesDir/adb/`).
- После pairing достаточно вводить **debug-порт** и нажимать «Подключить».
- Авто-connect при запуске, если pairing уже выполнен.

## UI

- Async pair/connect с блокировкой кнопок во время операции.
- Понятные сообщения об ошибках (таймаут кода, connection refused).
- Вкладка **Терминал** — команды через ADB shell (`id`, `ps`, `am`).

## Зависимости

- `compileSdk 36`, Kotlin 2.3, AGP 8.8.
- В этой версии Shizuku оставался запасным каналом (удалён в **v2.1.1**).

## Установка

APK: `dist/QTaskMgr-Standalone-v2.1.0-release.apk`

1. Developer Mode + **Wireless debugging** на Quest.
2. Pairing (код + порт) → Connect (debug port).
3. Терминал → `id` → `uid=2000(shell)`.
