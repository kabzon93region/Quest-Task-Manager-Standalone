# QTaskMgr Standalone v2.2.0

## Надёжность ADB

- **ShellWatchdog** — проверка живости сессии (`id`), авто-reconnect при потере связи.
- Переподключение перед refresh, kill, терминалом и фоновой очисткой.
- Статус shell: «соединение потеряно» / «нужно подключение».

## Без Shizuku

- Полностью удалены зависимости Shizuku (с **v2.1.1**).
- Только **Wireless ADB** — отдельный APK Shizuku не нужен.

## mDNS / PTY

- Авто-обнаружение debug-порта (mDNS) — в планах; пока ввод порта вручную после reboot.
- Интерактивный PTY-терминал — отложен; line-based shell достаточен для `id`, `ps`, `am`.

## Установка

APK: `dist/QTaskMgr-Standalone-v2.2.0-release.apk`

Совместим с обычным **QTaskMgr** (`com.quest3.taskmanager`) — можно ставить оба.
