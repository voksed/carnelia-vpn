# 🔴 Carnelia VPN - Ультра-легкий VPN клиент

> **Форк Amnezia VPN** с переписанным UI на Kotlin/Jetpack Compose. Черный-Красный-Белый мрачный дизайн, поддержка Outline (Shadowsocks), OpenVPN, WireGuard.

## ⚡ Особенности

- **Ультра-легкий UI** - Kotlin/Jetpack Compose вместо Qt (~90% легче)
- **Поддержка протоколов** - Outline, OpenVPN, WireGuard, IKEv2
- **Мрачный дизайн** - Черно-красно-белая цветовая схема
- **Material Design 3** - Современный интерфейс
- **Android 8+** - Поддержка API 26+
- **Минимальный размер** - Целевой < 30MB (vs Amnezia 80MB+)

## 📦 Скачать & Собрать

### Вариант 1: GitHub Actions (Рекомендуется)

1. Форкните репозиторий или создайте свой с этим кодом
2. Запушьте на GitHub
3. GitHub Actions автоматически соберет APK
4. Скачайте APK из вкладки **Actions → Artifacts**

### Вариант 2: Локальная сборка

```bash
# Требуется: JDK 17+, Android SDK

cd android
./gradlew.bat assembleDebug        # Windows
./gradlew assembleDebug             # macOS/Linux

# APK будет в: android/app/build/outputs/apk/debug/
```

### Вариант 3: Android Studio

1. Откройте папку `android/` в Android Studio
2. Нажмите **Build → Build Bundles/APKs → Build APK(s)**
3. APK готов в `android/app/build/outputs/apk/debug/`

## 📱 Установка на устройство

```bash
adb install -r android/app/build/outputs/apk/debug/carnelia-vpn-debug.apk
```

## 🎨 Цветовая схема

```
🖤 Фон:        #0A0A0A (Пропасть черноты)
❤️ Основной:   #E53935 (Ярко-красный)
🔴 Вторичный:  #CC0000 (Темно-красный)
⚪ Текст:      #FFFFFF (Чистый белый)
🔆 Акцент:    #FF6B6B (Светло-красный)
```

## 🔧 Структура проекта

```
carnelia-vpn/
├── android/                        # Основной код
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── kotlin/            # Kotlin исходники
│   │   │   │   └── com/carnelia/vpn/
│   │   │   │       ├── MainActivity.kt
│   │   │   │       ├── SettingsActivity.kt
│   │   │   │       ├── core/      # VPN логика
│   │   │   │       ├── service/   # Foreground Service
│   │   │   │       ├── data/      # Хранилище
│   │   │   │       └── utils/
│   │   │   └── res/               # Ресурсы
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   ├── gradlew & gradlew.bat
│   └── BUILD.md                   # Инструкции сборки
├── .github/workflows/
│   ├── build.yml                  # CI/CD для GitHub Actions
│   └── android-build.yml          # Альтернативная конфиг
├── README.md                      # Этот файл
├── VPN-INTEGRATION.md             # Архитектура VPN
└── DEVELOPMENT.md                 # Гайд разработки
```

## 🚀 Быстрый старт

### 1. Запуск приложения

После установки APK на устройство, откройте приложение Carnelia VPN.

### 2. Добавление сервера

1. Нажмите **Параметры** (шестеренка)
2. Нажмите **+** для добавления нового сервера
3. Вставьте Outline ключ доступа: `ss://method:password@host:port/?outline=1`
4. Нажмите **Добавить**

### 3. Подключение

1. Выберите сервер
2. Нажмите огромную красную кнопку **ПОДКЛЮЧИТЬ**
3. Статус должен измениться на **● АКТИВНО** зеленым

## 🔐 VPN Протоколы

### Outline (Shadowsocks) ⭐ Рекомендуется

```
Формат: ss://[МЕТОД]:[ПАРОЛЬ]@[ХОСТ]:[ПОРТ]/?outline=1
Пример: ss://chacha20-ietf-poly1305:mypassword@vpn.example.com:1234/?outline=1
```

- ✅ Самый легкий (~2MB)
- ✅ Самый быстрый (низкая латенция)
- ✅ Работает в ограниченных сетях
- ✅ Поддерживает обфускацию

### OpenVPN

```
Формат: .ovpn конфигурационный файл
```

- ✅ Широко совместим
- ✅ Проверенный протокол
- ❌ Немного медленнее
- ❌ Больший размер

### WireGuard

```
Формат: Конфиг с ключами
```

- ✅ Самый современный
- ✅ Наименьший код
- ✅ Высокая скорость
- ❌ Меньше поддержки серверами

## 📊 Статистика

- **Размер приложения**: ~15-25 MB (в зависимости от ABI)
- **Минимум RAM**: 100 MB свободной
- **API уровень**: 26+ (Android 8.0+)
- **Время сборки**: ~2-3 минуты (на среднем ПК)

## 🛠️ Разработка

Для добавления новых функций или исправления багов:

1. Форкните репозиторий
2. Создайте ветку: `git checkout -b feature/your-feature`
3. Изменяйте код
4. Коммитьте: `git commit -am "Add feature"`
5. Пушьте: `git push origin feature/your-feature`
6. Создайте Pull Request

## 📚 Документация

- **Архитектура VPN**: [VPN-INTEGRATION.md](VPN-INTEGRATION.md)
- **Гайд разработчика**: [DEVELOPMENT.md](DEVELOPMENT.md)
- **Инструкции сборки**: [android/BUILD.md](android/BUILD.md)

## ⚠️ Требования & Лицензия

- **Лицензия**: GPLv3 compatible (наследуем от Amnezia VPN)
- **Java**: 17+
- **Android SDK**: API 26+

## 🙏 Благодарности

Этот проект основан на отличной работе команды **Amnezia VPN**.
Мы переписали только UI слой для мобильного использования.

**Original**: https://github.com/amnezia-vpn/amnezia-client

## 📞 Контакты & Поддержка

- 🐛 **Баги**: GitHub Issues
- 💡 **Предложения**: GitHub Discussions  
- 📧 **Email**: dev@carnelia.vpn

---

**Made with ❤️ and a lot of ☕**

Carnelia VPN © 2026
