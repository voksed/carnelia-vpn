# Carnelia VPN

Лёгкий форк Amnezia VPN на нативном Android (Kotlin + Jetpack Compose) без Qt.

## Особенности

- 📱 **Kotlin + Jetpack Compose** — современный нативный Android UI
- 🎨 **Material Design 3** — красивые и минималистичные стили
- ⚡ **Лёгкий** — ~40-50 МБ APK вместо 100+ МБ Qt версии
- 🔒 **Безопасный** — поддержка OpenVPN и WireGuard (TODO)
- 🌓 **Dark Mode** — встроенная поддержка тёмной темы

## Быстрый старт

### Требования

- Android SDK 23+
- Kotlin 1.9.x
- Gradle 8.x
- Java 11

### Сборка

```bash
git clone https://github.com/yourusername/carnelia-vpn.git
cd carnelia-vpn
./gradlew assembleRelease
```

APK будет в `app/build/outputs/apk/release/`.

### Установка на устройство

```bash
adb install app/build/outputs/apk/release/carnelia-vpn-release.apk
```

## Структура проекта

```
carnelia-vpn/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/carnelia/vpn/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── service/
│   │   │   │   │   └── VpnService.kt
│   │   │   │   └── ui/theme/
│   │   │   │       ├── Theme.kt
│   │   │   │       └── Type.kt
│   │   │   ├── res/
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── themes.xml
│   │   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Roadmap

- [x] Базовый UI на Compose
- [x] Material Design 3
- [ ] OpenVPN протокол
- [ ] WireGuard протокол
- [ ] VPN сервер управление
- [ ] Settings экран
- [ ] Dark mode (полностью)
- [ ] GitHub Actions CI/CD

## Лицензия

GPL-3.0 — Open Source

## Контрибьютинг

Пулл-реквесты приветствуются!
