# QTaskMgr Standalone

Отдельная линия разработки: **без Shizuku**, shell через **Wireless ADB** на том же устройстве (localhost).

## Отличия от Quest-Task-Manager

| | Основной QTaskMgr | Standalone |
|--|-------------------|------------|
| Package | `com.quest3.taskmanager` | `com.quest3.taskmanager.standalone` |
| Лаунчер | QTaskMgr | QTaskMgr S |
| Shell | Shizuku APK | Wireless ADB (Kadb) |
| Терминал | Нет | Вкладка «Терминал» |
| Версия (актуальная) | **1.4.8** | **2.4.0** |

Оба приложения можно установить **одновременно**.

### Паритет функций running/kill (с v2.3.0 / основной 1.4.8)

| Возможность | Основной | Standalone |
|-------------|----------|------------|
| VR-игры под оверлеем Quest (uid `/proc`) | ✓ | ✓ |
| Быстрый refresh (~2–5 с, batched shell) | ✓ | ✓ |
| `RunningSnapshotHolder` (один снимок на refresh) | ✓ | ✓ |
| Kill с верификацией по `ps` + UID | ✓ | ✓ |
| `normalizePackageName` (IntoTheRadius и др.) | ✓ | ✓ |
| Без `du` при refresh, кэш диска | ✓ | ✓ |
| ShellWatchdog / auto-reconnect | — | ✓ |

## Roadmap

| Версия | Содержание |
|--------|------------|
| **2.0.0** | ShellBackend, UI ADB, терминал |
| **2.1.0** | Kadb pair/connect к `127.0.0.1` |
| **2.1.1** | Удаление Shizuku |
| **2.2.0** | ShellWatchdog, auto-reconnect |
| **2.3.0** | Паритет с QTaskMgr 1.4.8: VR-детекция, быстрый refresh, kill |
| **2.4.0** | ADB-уведомление с RemoteInput, адаптивная разметка |

## Настройка Wireless ADB

1. Wireless debugging **Вкл**.
2. **Pair device with pairing code** → код и pairing port → **Сопряжение** (один раз).
3. Debug port с экрана Wireless debugging → **Подключить**.
4. После перезагрузки Quest — новый debug port (mDNS в будущем) → снова **Подключить**.

## Архитектура shell

- `shell/adb/AdbKeyStore.kt` — RSA-ключи.
- `shell/adb/AdbConnectionManager.kt` — pair, connect, exec, Mutex.
- `shell/AdbShellBackend.kt` — реализация `ShellBackend`.
- `shell/ShellWatchdog.kt` — health check и auto-reconnect.
- `shell/ShellManager.kt` — единая точка для kill, appops, терминала.

## Сборка

```powershell
.\scripts\build-apk.ps1 -Release
```
