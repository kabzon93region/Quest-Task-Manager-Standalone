# QTaskMgr Standalone v2.5.5

## Что нового

### Исправлено
- **Авто-порт** — убран mDNS из auto-discovery (возвращал 5555). Теперь «Авто» использует только `/proc/net/tcp` и shell
- **Kill protected** — убран широкий префикс `com.oculus.`, добавлены конкретные системные пакеты. Теперь `com.oculus.hzosgallery` и другие non-system приложения можно закрывать
- **IPv6 loopback** — подключение через `::1` (IPv6) вместо только `127.0.0.1`. На Quest adbd слушает на `::` (IPv6 all interfaces)
- **Статус-бар** — вместо «Wireless debugging выключен» показывает «подключение не удалось — проверьте debug-порт»

### Улучшения
- Детальное логирование попыток подключения
- Улучшенный UX после pairing: чёткое сообщение о необходимости ввести debug-порт

## Установка

Скачайте APK и установите через SideQuest или `adb install`:

```
adb install -r QTaskMgr-Standalone-v2.5.5-release.apk
```

## Требования

- Meta Quest 2/3/Pro
- Developer Mode включён
- Wireless debugging включён
