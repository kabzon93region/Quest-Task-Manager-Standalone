# Changelog

Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/).

## [Unreleased]

## [2.5.1] — 2026-08-23

### Исправлено
- Краш при запуске без ADB pairing: `PendingIntent` для RemoteInput-уведомления должен быть `FLAG_MUTABLE`, не `FLAG_IMMUTABLE`

## [2.5.0] — 2026-08-23

### Добавлено
- **Авто-обнаружение debug-порта** — кнопка «Авто» в настройках использует NsdManager (mDNS) и fallback на `/proc/net/tcp` через shell
- **Release-подпись** — автоматическое использование `keystore.properties` если файл существует, иначе debug-подпись
- **Unit-тесты** — тесты для `normalizePackageName` и `isLikelyPackageName`

### Изменено
- `.cursor/rules/DEVELOPMENT_RULES.mdc` — адаптирован под Kotlin/Android (заменены Python-правила на специфику Quest-приложения)
- Документация: версии обновлены до актуальных в README, STANDALONE, VERSIONING, GITHUB_PUBLISH

### Удалено
- `.cursor/rules/qest-apk-RULES.md` — правила перенесены в `DEVELOPMENT_RULES.mdc`

## [2.4.0] — 2026-07-11

### Добавлено
- **ADB-уведомление с RemoteInput** — настройка pairing и подключения прямо из шторки уведомлений (как в Shizuku), без переключения с экрана Wireless debugging.
- **Адаптивная разметка** — фильтры и кнопки закрытия сворачиваются в 2 строки в портретном режиме (FlexboxLayout). Табы прокручиваются горизонтально на узких экранах.

## [2.3.0] — 2026-06-08

Порт наработок **QTaskMgr v1.4.8** (основной мод на Shizuku) в standalone-линию.

### Добавлено
- `PackageListProbe`, `RunningSnapshotHolder` — списки через PM, один снимок процессов на refresh.
- Быстрый batched shell: `ps` + awk `/proc` + `dumpsys activity activities`.
- `UidRunningProbe` с uid-кэшем и VR-играми под оверлеем Quest.
- Kill с верификацией (`isPackageRunning`), закрытие по UID.
- `FileLogger.probe()` — без deadlock при shell-пробах.

### Изменено
- `RunningAppsProbe` — fast/slow режимы, `isJavaClassSegment` в `normalizePackageName`.
- `MemoryProbe` — `buildFastRamMap`, без полного `dumpsys meminfo` при refresh.
- `AppRepository` / фрагменты — shared snapshot, без `du` при refresh, RAM-only на «Запущенные».
- `ShellManager` — mutex на shell, расширенный `killTarget`.

### Исправлено
- Сломанные ссылки на удалённый `ShizukuShell` в `RunningAppsProbe`.
- Медленный refresh (множественные shell + meminfo по всем пакетам).

## [2.2.0] — 2026-06-08

### Добавлено
- **ShellWatchdog**: проверка живости ADB (`id`), авто-reconnect перед kill, refresh, терминалом и фоновой очисткой.
- Статусы shell: потеря соединения, необходимость нового debug-порта.

### Изменено
- Полное удаление Shizuku (см. 2.1.1).

## [2.1.1] — 2026-06-08

### Удалено
- Зависимости и код **Shizuku** — только Wireless ADB.

## [2.1.0] — 2026-06-08

### Добавлено
- **Kadb**: pair/connect к `127.0.0.1`, shell-команды через `AdbConnectionManager`.
- **AdbKeyStore**: RSA-ключи в `filesDir/adb/`.
- Async pair/connect в настройках; авто-connect при старте.
- Shizuku оставался запасным каналом до 2.1.1.

## [2.0.0] — 2026-06-08

### Добавлено
- **Standalone**-сборка: `com.quest3.taskmanager.standalone`, имя **QTaskMgr S** (рядом с основным QTaskMgr).
- Абстракция **ShellBackend** / **ShellManager**; UI **Wireless ADB** (pairing + connect, протокол в v2.1).
- Вкладка **Терминал** (shell-команды при готовом канале).
- Shizuku — временный **запасной** бэкенд до готовности ADB.

### Изменено
- Отдельный репозиторий [Quest-Task-Manager-Standalone](https://github.com/kabzon93region/Quest-Task-Manager-Standalone).

## [1.4.1] — 2026-06-11

### Добавлено
- Кнопка **DonationAlerts** в настройках; раздел «Поддержать проект» в README.

## [1.4.0] — 2026-06-11

### Добавлено
- Поиск по названию и пакету (7 символов) на вкладках «Запущенные» и «Приложения».
- В настройках: версия приложения и ссылка на GitHub.

### Исправлено
- Лаунчер: отображается **QTaskMgr**, не имя пакета; иконки приложения.
- Уведомление: корректный текст **«Закрыто: N»**.
- Kill «По правилам» / «Закрыть все» — весь список, не зависит от фильтра отображения.

## [1.3.2] — 2026-06-11

### Исправлено
- Очистка по уведомлению: расширенный список кандидатов, `killByRules` + `killTarget`, без фильтра kill_protected.

## [1.3.1] — 2026-06-11

### Исправлено
- Индикатор загрузки не гаснет при смене фильтра / параллельной загрузке.
- Ручное закрытие защищённых системных apps (кроме самого QTaskMgr).

## [1.3.0] — 2026-06-08

### Добавлено
- Фильтр **«Демоны»** на вкладках «Запущенные» и «Приложения» (нативные процессы, не в «Все»).
- Закрытие демонов: `force-stop` или `kill -9` по PID.

## [1.2.14] — 2026-06-11

### Добавлено
- RAM свободно/всего на вкладке «Запущенные» (между фильтрами и «Обновить»).

## [1.2.12] — 2026-06-11

### Изменено
- Параллельная загрузка списков при первом запуске; кэш при повторном.
- Kill обновляет только «Запущенные».

## [1.2.11] — 2026-06-11

### Изменено
- Старт: preload и refresh обеих вкладок списков.
- Авто-refresh только после kill; при смене вкладки — нет.

## [1.2.10] — 2026-06-11

### Исправлено
- Кнопка «Настройки Android»: `DeepLinkHomepageActivity` (Homepage не exported).

## [1.2.9] — 2026-06-11

### Исправлено
- Kill: успех при `am force-stop` (ложный fail из‑за `com.pkg.service.*` в ps).
- Settings: только `SettingsHomepageActivity`, убраны launcher/ManageApplications.

## [1.2.8] — 2026-06-11

### Исправлено
- Kill: ожидание завершения процесса после `am force-stop` (не «закрыто 0 / не удалось 1» при успехе).
- Настройки Android: главный экран через intent из foreground app (без «моргания» shell).

### Изменено
- Версия **1.2.7** по схеме X.Y.Z (удалена ошибочная нумерация 1.10.*).

## [1.2.1] — 2026-06-11

### Добавлено
- README: раздел «Android Settings на Quest» со ссылками (QGO, XR Native, Hidden Settings).

### Исправлено
- Диалог при отсутствии `com.android.settings` — из кнопки настроек и при тапе по приложению в списках.

## [1.2.0] — 2026-06-11

### Исправлено
- Kill: краш Shizuku `process hasn't exited` при `exitValue()` после `am force-stop`.
- Кнопка «Настройки Android» → `SettingsHomepageActivity` (главная), не `SystemDashboard`.

## [1.1.9] — 2026-06-11

### Исправлено
- Chip «Пользовательские» подсвечен по умолчанию (совпадает с фильтром списка).
- Уведомление: default on + `syncNotificationService` при старте/resume app.

## [1.1.8] — 2026-06-11

### Исправлено
- Settings shell: `com.android.settings/.Class` (пропущенный `/` ломал все запуски в v1.1.7).
- Клик по плашке на вкладке «Приложения» — зона иконка+текст, switches изолированы.
- Running: больше установленных процессов в списке (фильтр только native + not installed).

## [1.1.7] — 2026-06-10

### Исправлено
- Settings: Shizuku shell с кавычками вокруг component (`Settings$…` больше не ломается в sh); shell раньше intent.
- Running: meminfo/displayPackages отсекают нативные процессы (`media.*`, `hidl.*`, `@` в имени).
- Тап по плашке на вкладке «Приложения» (название, иконка, память).

## [1.1.6] — 2026-06-10

### Исправлено
- Running: только установленные пакеты; фильтр native process (`media.*`, `android.hidl.*`).
- Settings: прямой запуск `com.android.settings` (+ Shizuku fallback), без Meta/VrShell relay.
- Тап по плашке на вкладке «Приложения» → Android app details.

## [1.1.5] — 2026-06-09

### Исправлено
- Фильтр «Пользовательские»: системные процессы из dumpsys больше не попадают как user (UID, префиксы, unknown entries).
- «Настройки Android»: запуск через системный `ACTION_SETTINGS` (VrShell relay на Quest), без неэкспортируемого `Settings`.

## [1.1.4] — 2026-06-09

### Добавлено
- Вкладка **Лог** — просмотр файла лога в реальном времени (фоновое обновление, без сброса прокрутки и выделения).

## [1.1.3] — 2026-06-09

### Исправлено
- Кнопка «Настройки Android» открывает главный экран Settings, а не режим разработчика.
- Список запущенных: `dumpsys activity processes`, summary meminfo, расширенный парсинг `ps`.
- Тап по плашке в «Запущенные» → страница приложения в Android Settings.
- Закрытие: `am force-stop` + `am kill` + `kill -9` по PID; отчёт о защищённых и неудачных.

## [1.1.2] — 2026-06-09

### Исправлено
- Зависание и тёмный экран при запуске: рекурсия в логере (`ShizukuShell` → `FileLogger` → shell → снова лог).
- Лог на Quest: запись в `/sdcard/Download/` через Shizuku без зацикливания.

### Изменено
- При каждом запуске из лаунчера лог очищается (старые записи не накапливаются).
- Shell-команды пишутся только в logcat, не в файл лога.
- Дублирование лога во внутреннее хранилище приложения (надёжная запись).

## [1.1.1] — 2026-06-09

### Исправлено
- Release APK подписан — установка через SideQuest/adb (v1.1.0 → `INSTALL_PARSE_FAILED_NO_CERTIFICATES`).

## [1.1.0] — 2026-06-09

### Добавлено
- Вкладки: Запущенные, Приложения, Настройки.
- Диск и RAM отдельно в карточках приложений.
- Фильтр running: ps ∪ meminfo (без recents-призраков).
- Локальное обновление toggles без full refresh.
- Protected apps: kill-protected и system-filter, видимость self + NMB.
- `CleanupForegroundService` — фоновая очистка по уведомлению.
- `orderedForKill` — self последним при kill-all.
- Кнопка **Настройки Android** (`com.android.settings`).
