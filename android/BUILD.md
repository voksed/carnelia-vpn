# Carnelia VPN - Quick Build Guide

## Сборка локально (Windows/macOS/Linux)

### Требования:
- JDK 17+ (Eclipse Temurin или любой другой)
- Android SDK (API 26+)

### Шаги:

```bash
# 1. Перейти в папку android
cd android

# 2. На Windows:
./gradlew.bat assembleDebug

# На macOS/Linux:
./gradlew assembleDebug

# 3. APK будет в:
# android/app/build/outputs/apk/debug/carnelia-vpn-debug.apk
```

## Установка на устройство

```bash
adb install -r android/app/build/outputs/apk/debug/carnelia-vpn-debug.apk
```

## Сборка через GitHub Actions (Рекомендуется)

1. Запушить в GitHub:
```bash
git remote add origin https://github.com/YOUR_USERNAME/carnelia-vpn.git
git push -u origin main
```

2. GitHub Actions автоматически запустит сборку
3. APK будет доступен в разделе **Actions** → **Artifacts**

## Структура проекта

```
android/
├── app/                    # Основное приложение
│   ├── src/main/
│   │   ├── kotlin/        # Kotlin исходники
│   │   ├── res/           # Ресурсы (strings, colors)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts       # Root конфиг
├── settings.gradle.kts
├── gradlew & gradlew.bat  # Gradle wrapper
└── gradle/wrapper/        # Gradle properties
```

## Какие версии поддерживаются

- **compileSdk**: 34 (Android 14)
- **targetSdk**: 34
- **minSdk**: 26 (Android 8.0)
- **Kotlin**: 1.9.20
- **Compose**: 1.6.0

## Troubleshooting

### Error: JAVA_HOME is not set
```bash
# На Windows (в PowerShell):
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.7.7-hotspot"

# На macOS/Linux:
export JAVA_HOME=/path/to/jdk17
```

### Error: Android SDK not found
```bash
# Установить через Android Studio или:
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

### Build зависает
- Это может быть проблема с загрузкой зависимостей
- Решение: перезагрузить, очистить кэш
```bash
./gradlew clean
./gradlew assembleDebug
```

## Release сборка

```bash
./gradlew assembleRelease
```

Требуется подпись (keystore), см. [Android Signing Documentation](https://developer.android.com/studio/publish/app-signing)

## Дополнительно

- Документация VPN интеграции: [VPN-INTEGRATION.md](../VPN-INTEGRATION.md)
- Разработка: [DEVELOPMENT.md](../DEVELOPMENT.md)
