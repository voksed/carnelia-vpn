# Carnelia VPN Development Guide

## Структура проекта

```
android/
├── app/                    # Основное приложение
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/     # Kotlin исходники
│   │   │   │   └── com/carnelia/vpn/
│   │   │   │       ├── MainActivity.kt
│   │   │   │       ├── service/
│   │   │   │       └── ui/
│   │   │   ├── res/        # Ресурсы (строки, цвета)
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle.kts
├── build.gradle.kts        # Root build config
└── settings.gradle.kts     # Gradle settings
```

## Требования

- JDK 17+ (Eclipse Temurin)
- Android SDK 26+ (API 26)
- Gradle 8.6+

## Сборка локально

```bash
cd android

# Debug build
./gradlew assembleDebug

# Release build (требует подписания)
./gradlew assembleRelease

# Установка на устройство
adb install -r app/build/outputs/apk/debug/carnelia-vpn-debug.apk

# Запуск тестов
./gradlew test
```

## Разработка

### Добавление новой зависимости

1. Отредактируйте `android/app/build.gradle.kts`
2. Добавьте в `dependencies { }`
3. Синхронизируйте Gradle

Пример:
```kotlin
dependencies {
    implementation("com.example:library:1.0.0")
}
```

### Структура кода

- **MainActivity.kt** - Главный UI с Compose
- **service/CarheliaVpnService.kt** - VPN сервис (фоновый)
- **ui/theme/** - Material Design 3 тема

## CI/CD

Репозиторий настроен на GitHub Actions:

- При push в `main` или `dev` - автоматическая сборка Debug APK
- При создании tag - создается Release с APK артифактом

## Подробнее

- [Android Documentation](https://developer.android.com)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io)
