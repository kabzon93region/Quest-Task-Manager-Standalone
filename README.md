# Quest Task Manager Standalone (QTaskMgr S)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/release-v2.5.5-blue)](https://github.com/kabzon93region/Quest-Task-Manager-Standalone/releases)
[![Android](https://img.shields.io/badge/Android-10%2B%20(API%2029)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/10)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Meta Quest](https://img.shields.io/badge/Meta%20Quest-2%20%2F%203%20%2F%20Pro-0082FC?logo=meta&logoColor=white)](https://www.meta.com/quest/)
[![Wireless ADB](https://img.shields.io/badge/Shell-Wireless%20ADB-546E7A)](docs/STANDALONE.md)

**Диспетчер задач для Meta Quest без Shizuku** — встроенный shell через **Wireless ADB**, вкладка **Терминал**, тот же функционал kill/фон/уведомление, что у [QTaskMgr](https://github.com/kabzon93region/Quest-Task-Manager).

| | |
|---|---|
| **Разработчик** | [kabzon93region](https://github.com/kabzon93region) |
| **Package** | `com.quest3.taskmanager.standalone` |
| **Версия** | 2.5.5 |
| **GitHub** | [Quest-Task-Manager-Standalone](https://github.com/kabzon93region/Quest-Task-Manager-Standalone) |
| **Основная линия** | [Quest-Task-Manager](https://github.com/kabzon93region/Quest-Task-Manager) (Shizuku) |

## Быстрый старт

1. Developer Mode + **Wireless debugging** на Quest.
2. Установите APK Standalone (можно **рядом** с обычным QTaskMgr).
3. **Настройки** → Shell: pairing-код и порты → **Сопряжение** → **Подключить**.
4. **Терминал** → `id` (должен быть `uid=2000(shell)`).
5. После перезагрузки Quest — обновите debug-порт и нажмите **Подключить**.

## Сборка

```powershell
cd B:\quest3\PC\quest-task-manager-standalone
.\scripts\build-apk.ps1 -Release
```

APK: `dist\QTaskMgr-Standalone-v2.5.5-release.apk`

## Документация

- [docs/STANDALONE.md](docs/STANDALONE.md) — ADB, архитектура, roadmap
- [docs/RELEASE_NOTES_v2.4.0.md](docs/RELEASE_NOTES_v2.4.0.md)

## Лицензия

MIT — см. [LICENSE](LICENSE).

## Поддержать проект

Разовый донат картой РФ, СБП, ЮMoney, VK Pay:

**[DonationAlerts → kabzon93region](https://www.donationalerts.com/r/kabzon93region)**

Та же ссылка — во вкладке **Настройки** в приложении.
