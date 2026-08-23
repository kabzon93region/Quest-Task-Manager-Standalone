# QTaskMgr Standalone v2.5.0

## Авто-обнаружение debug-порта

Кнопка **«Авто»** в настройках — автоматически находит ADB debug-порт после перезагрузки Quest:

- **NsdManager (mDNS)** — обнаружение `_adb._tcp` сервисов (работает не на всех Quest-устройствах)
- **Fallback: `/proc/net/tcp`** — если shell уже подключён, ищет слушающий порт в диапазоне 30000–49999

## Release-подпись

- Автоматическое использование `keystore.properties` если файл существует
- Иначе — debug-подпись (для sideload через SideQuest/adb)
- Шаблон: `src/quest-app/keystore.properties.example`

## Unit-тесты

- Тесты для `normalizePackageName` — корректная очистка суффиксов Activity/Service/Application
- Тесты для `isLikelyPackageName` — распознавание пакетов vs нативных процессов

## Установка

APK: `dist/QTaskMgr-Standalone-v2.5.0-release.apk`

Совместим с обычным **QTaskMgr** (`com.quest3.taskmanager`) — можно ставить оба.
