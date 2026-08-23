# Публикация на GitHub (private → public)

Пошаговая инструкция для репозитория **Quest Task Manager Standalone (QTaskMgr S)**.

**Разработчик:** [kabzon93region](https://github.com/kabzon93region)  
**Репозиторий:** [Quest-Task-Manager-Standalone](https://github.com/kabzon93region/Quest-Task-Manager-Standalone)

---

## Часть 1. Подготовка на ПК

### 1.1. Проверить `.gitignore`

Не должны попасть в Git:

- `**/build/`, `.gradle/`, `local.properties`
- `*.log`, `logs/`
- `dist/*.apk`
- `*.jks`, `keystore.properties`, `signing.properties`

### 1.2. Собрать release APK

```powershell
cd B:\quest3\PC\quest-task-manager-standalone
.\scripts\build-apk.ps1 -Release
```

Результат: `dist\QTaskMgr-Standalone-v2.4.0-release.apk`

Release подписывается debug-ключом (для SideQuest/adb). Без подписи Android выдаёт `INSTALL_PARSE_FAILED_NO_CERTIFICATES`.

---

## Часть 2. GitHub Release

### v2.4.0 (актуальный)

Тег `v2.4.0`, описание: [RELEASE_NOTES_v2.4.0.md](RELEASE_NOTES_v2.4.0.md), asset `QTaskMgr-Standalone-v2.4.0-release.apk`.

Прямая ссылка на APK (шаблон для README и плашек):

`https://github.com/kabzon93region/Quest-Task-Manager-Standalone/releases/download/vX.Y.Z/QTaskMgr-Standalone-vX.Y.Z-release.apk`

### Предыдущие релизы

| Версия | Примечание |
|--------|------------|
| v2.4.0 | ADB-уведомление с RemoteInput, адаптивная разметка |
| v2.3.0 | Паритет с QTaskMgr 1.4.8: VR-детекция, быстрый refresh |
| v2.2.0 | ShellWatchdog, auto-reconnect |
| v2.1.0 | Kadb pair/connect к `127.0.0.1` |
| v2.0.0 | ShellBackend, UI ADB, терминал |

---

## Часть 3. Последующие релизы (шпаргалка)

### Формат `RELEASE_NOTES_vX.Y.Z.md`

Только блок **«Что нового в vX.Y.Z»** — изменения этой версии.

Не дублировать установку, возможности, требования и лицензию: они описаны в [README.md](../README.md). Полные секции в release notes — только если в этой версии они **реально изменились**.

1. Обновить `versionCode` / `versionName` в `src/quest-app/app/build.gradle.kts` и `scripts/build-apk.ps1`.
2. Добавить запись в `CHANGELOG.md` и `docs/RELEASE_NOTES_vX.Y.Z.md` (по правилу выше).
3. `.\scripts\build-apk.ps1 -Release`
4. Commit + push в `main`.
5. GitHub → Releases → тег `vX.Y.Z` → описание из `RELEASE_NOTES` → asset `dist\QTaskMgr-Standalone-vX.Y.Z-release.apk`.

### GitHub CLI

```powershell
gh release create v2.5.0 dist\QTaskMgr-Standalone-v2.5.0-release.apk --title "QTaskMgr Standalone v2.5.0" --notes-file docs\RELEASE_NOTES_v2.5.0.md
```

---

## Часть 4. Переход в Public (когда готовы)

1. **Settings** → **General** → **Danger Zone** → **Change repository visibility** → **Public**
2. Убедиться, что в README и LICENSE указан MIT и ссылка на `docs/THIRD_PARTY.md`

---

## Подпись release APK (опционально, для production)

По умолчанию `assembleRelease` подписывается debug-ключом (удобно для sideload).

Для постоянного release-ключа:

```powershell
keytool -genkey -v -keystore release.jks -alias qtaskmgr -keyalg RSA -keysize 2048 -validity 10000
```

Создайте `src/quest-app/keystore.properties` (в `.gitignore`):

```properties
storeFile=../../release.jks
storePassword=***
keyAlias=qtaskmgr
keyPassword=***
```

И раскомментируйте блок `signingConfigs` в `app/build.gradle.kts` (см. комментарии в файле).

---

## Чеклист перед публикацией

- [ ] `LICENSE`, `NOTICE`, `docs/THIRD_PARTY.md` на месте
- [ ] README актуален (версия, установка)
- [ ] Release APK собран и протестирован на Quest
- [ ] В репозитории нет логов, `build/`, keystore
- [ ] Private repo создан, push выполнен
