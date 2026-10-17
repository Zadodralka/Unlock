# VPN Client для iOS

Базовый клиент в духе Happ: добавляете ссылку подписки (или `vless://` / `vmess://` /
`trojan://` / `ss://` / `hy2://`), выбираете сервер, нажимаете кнопку — весь трафик
iPhone идёт через сервер. Ядро — [Xray-core](https://github.com/XTLS/Xray-core) через
[libXray](https://github.com/XTLS/libXray), поэтому поддерживается всё, что умеет Xray:
VLESS + Reality / Vision, XHTTP, gRPC, WS, Trojan, Shadowsocks, Hysteria2.

## Что умеет

- Подписки по ссылке: base64-список, обычный список ссылок, Xray JSON.
  Читаются заголовки `profile-title`, `subscription-userinfo` (трафик и срок),
  `support-url`, `announce`, `profile-update-interval`.
- Отправляет `x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model`, поэтому работает
  лимит устройств в Remnawave.
- Отдельные серверы ссылками, по одной на строку.
- Реальный пинг через каждый сервер (HTTP-запрос к `generate_204`).
- Подключение одной кнопкой, смена сервера на лету, автообновление подписок.
- Импорт по диплинку `vpnclient://add/<ссылка>` — можно дать кнопку в боте или на сайте.
- DNS идёт через туннель; локальные сети (192.168.x.x и т.п.) — напрямую.
- Журнал ядра Xray в настройках — пригодится для отладки.

## Как устроено

```
App (SwiftUI)                         PacketTunnel (Network Extension)
  ProfileStore — подписки, пинг         PacketTunnelProvider
  VPNController — VPN-профиль    ──►     поднимает utun, находит его fd,
  libXray: разбор ссылок, пинг           запускает Xray с TUN-inbound на этом fd
            \__ общий App Group: profiles.json, active-outbound.json, xray.log __/
```

## Что нужно

1. **Mac с Xcode 16+.**
2. **Платный аккаунт Apple Developer ($99/год).** С бесплатным Apple ID приложение
   с Network Extension не подписать — iOS не даст создать VPN-профиль.
3. **Homebrew**, в нём `brew install go xcodegen`.

## Запуск на iPhone

```bash
cd ios

# 1. Ядро Xray -> Frameworks/LibXray.xcframework (5–10 минут при первой сборке)
./scripts/build-libxray.sh
#    Или скачайте готовый артефакт LibXray.xcframework со страницы сборки
#    GitHub Actions (workflow «iOS») и положите в Frameworks/.

# 2. Свои Team ID и bundle id
cp Config/Local.xcconfig.example Config/Local.xcconfig
open -e Config/Local.xcconfig        # впишите DEVELOPMENT_TEAM и APP_BUNDLE_ID

# 3. Проект Xcode
xcodegen generate
open VPNClient.xcodeproj
```

В Xcode:

1. Подключите iPhone кабелем и выберите его вверху как устройство запуска.
2. Нажмите ▶︎ Run. С автоматической подписью Xcode сам зарегистрирует bundle id,
   App Group и возможность Network Extension. Если появится ошибка подписи, откройте
   таргеты **VPNClient** и **PacketTunnel** → *Signing & Capabilities*, выберите свою
   команду и нажмите *Try Again*.
3. На iPhone при первом запуске: *Настройки → Основные → VPN и управление устройством*
   → доверьтесь своему сертификату разработчика. Если iOS попросит, включите
   *Режим разработчика* (*Настройки → Конфиденциальность и безопасность*).
4. В приложении нажмите **+**, вставьте ссылку подписки, затем большую кнопку.
   При первом подключении iOS спросит разрешение добавить VPN-конфигурацию.

## Если не подключается

- Откройте ⚙︎ → «Журнал Xray»: там ошибки ядра (неверный ключ Reality, таймауты и т.п.).
- Ошибка старта туннеля показывается красным под кнопкой.
- Подписка отдаёт веб-страницу вместо ссылок — панель не узнала клиента по User-Agent.
  Сейчас он `VPNClient/<версия>`; поменять можно в `App/SubscriptionClient.swift`.
- Логи туннеля из системы: Mac → приложение «Консоль» → ваш iPhone → фильтр `PacketTunnel`.

## Ограничения этой версии

- Только IPv4 внутри туннеля (DNS отдаёт только A-записи, IPv6 не используется).
- Нет сканера QR, раздельного туннелирования, виджетов и правил маршрутизации — это
  следующие шаги.
- Пинг при включённом VPN идёт через туннель, так что цифры получаются завышенными.
- Иконка приложения не задана.
