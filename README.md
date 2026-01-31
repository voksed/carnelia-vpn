# Carnelia VPN

**Carnelia VPN** — это ультра-легкий, мощный и современный VPN-клиент, основанный на ядре Amnezia VPN, но с полной переработкой UI под Kotlin/Jetpack Compose.

## Отличия от Amnezia

- **UI**: Вместо Qt используем Jetpack Compose (Android-first) и SwiftUI (iOS) — на 90% более легкие
- **Размер**: Целевой размер приложения < 30 MB (vs Amnezia ~80MB)
- **Производительность**: Встроенная оптимизация для мобильных устройств
- **Современность**: Material Design 3, системные компоненты, ближе к платформам

## Поддерживаемые платформы

- ✅ Android 8+ (Kotlin/Compose) — ПРИОРИТЕТ
- ✅ iOS 12+ (SwiftUI) — вторая очередь
- 🟡 Desktop (Linux/Windows/macOS) — в планах (может быть Tauri/Rust вместо Qt)

## Поддерживаемые протоколы

- OpenVPN
- WireGuard
- IKEv2
- AmneziaWG

## Архитектура

```
carnelia-vpn/
├── android/              # Android (Kotlin/Compose)
│   ├── app/
│   │   ├── src/main/kotlin/com/carnelia/
│   │   ├── src/main/AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── ios/                  # iOS (SwiftUI) - позже
├── shared/               # Общее (логика VPN, протоколы)
│   ├── vpn-core/         # Адаптированное ядро из Amnezia
│   └── protocols/        # OpenVPN, WireGuard, IKEv2
├── docs/
└── .github/workflows/    # CI/CD
```

## Быстрый старт

### Android

```bash
cd android
./gradlew assembleDebug
```

Установка на устройство:

```bash
adb install -r app/build/outputs/apk/debug/*.apk
```

## Лицензия

Наследуем лицензию Amnezia VPN (GPLv3 compatible)

## Благодарности

Этот проект основан на отличной работе команды Amnezia VPN.
