# Unlock для Android

Клиент в духе Happ на ядре [Xray-core](https://github.com/XTLS/Xray-core) (через
[libXray](https://github.com/XTLS/libXray)): VLESS + Reality / Vision, XHTTP, gRPC, WS,
Trojan, Shadowsocks, Hysteria2. Android 9 и новее.

## Что умеет

- Подписки по ссылке: base64-список, обычный список ссылок, Xray JSON.
  Читаются заголовки `profile-title`, `subscription-userinfo` (трафик и срок),
  `support-url`, `announce`, `profile-update-interval`.
- Отправляет `x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model` — работает лимит
  устройств в Remnawave. User-Agent — `Unlock/<версия>`.
- Отдельные серверы ссылками, по одной на строку.
- Реальный пинг через каждый сервер.
- Подключение одной кнопкой, смена сервера на лету, автообновление подписок,
  уведомление с кнопкой «Отключить», поддержка Always-on VPN.
- Импорт по ссылке `unlock://add/<ссылка>` — можно дать кнопку в боте или на сайте.
- DNS идёт через туннель; локальные сети (192.168.x.x и т.п.) — напрямую.
- Журнал ядра Xray в настройках (⚙︎).

## Установить на телефон (без сборки)

APK собирает GitHub Actions при каждом изменении в `android/`.

1. Откройте вкладку **Actions** репозитория → workflow **Android** → последний
   зелёный запуск.
2. Внизу, в разделе **Artifacts**, скачайте **Unlock-apk** (это zip).
3. Распакуйте, внутри `app-debug.apk`. Перекиньте его на телефон любым способом:
   кабелем, через Telegram «Избранное», Google Drive.
4. Откройте файл на телефоне. Android попросит разрешить установку из этого
   источника — разрешите.
5. В приложении: **+** → вставьте ссылку подписки → большая кнопка. При первом
   подключении Android спросит разрешение на VPN.

Все сборки подписаны одним ключом, поэтому новая версия ставится поверх старой без удаления.

## Как устроено

```
MainActivity (Compose)                  UnlockVpnService (процесс :vpn)
  ProfileStore — подписки, пинг           VpnService.Builder -> TUN
  libXray: разбор ссылок, пинг     ──►    Xray с TUN-inbound на дескрипторе TUN
            \__ files/: profiles.json, active-outbound.json, vpn-state.json, xray.log __/
```

Собственный трафик приложения исключён из VPN, поэтому ядро ходит к серверу напрямую.

## Сборка у себя

Нужны Android Studio (или Android SDK + JDK 17), Go и NDK. Ядро собирается скриптом
`scripts/build-libxray.sh` (Linux/macOS; на Windows — через WSL) или берётся из CI.
После этого: `./gradlew assembleDebug`.

## Ограничения этой версии

- Только IPv4 внутри туннеля.
- Нет сканера QR, раздельного туннелирования по приложениям и плитки в шторке.
- Ключ подписи — тестовый и лежит в репозитории. Для публикации в Google Play нужен
  свой секретный ключ.
