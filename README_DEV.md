# null vpn — Документация разработчика

## Структура проекта

```
carneliavpn/
├── clean-src/                    ← Очищенный исходный код (только Kotlin)
│   └── com/carnelia/vpn/
│       ├── MainActivity.kt       ← Главный экран (Jetpack Compose)
│       ├── SettingsActivity.kt   ← Настройки
│       ├── LogsActivity.kt       ← Просмотр логов
│       ├── StatisticsActivity.kt ← Статистика трафика
│       ├── GeoSpoofActivity.kt   ← GPS-подмена
│       ├── core/
│       │   ├── XrayCoreManager.kt        ← Управление ядром Xray
│       │   ├── VpnManager.kt             ← Жизненный цикл VPN
│       │   ├── NoiseModeManager.kt       ← Шумовой трафик
│       │   ├── TrafficStatsManager.kt    ← Сбор статистики
│       │   └── protocols/
│       │       ├── ShadowsocksProxyProtocol.kt  ← Shadowsocks через tun2socks
│       │       └── VpnProtocols.kt              ← Xray VLESS/VMess/Trojan/WireGuard
│       ├── data/
│       │   ├── ServerRepository.kt      ← Хранилище серверов
│       │   ├── Subscription.kt          ← Модель подписки
│       │   └── SubscriptionManager.kt   ← Загрузка/обновление серверов
│       ├── service/
│       │   ├── CarheliaVpnService.kt    ← Основной VPN Android-сервис
│       │   ├── BootReceiver.kt          ← Автозапуск при загрузке
│       │   ├── NetworkMonitor.kt        ← Мониторинг сети
│       │   └── VpnTileService.kt        ← Плитка в шторке
│       ├── utils/
│       │   ├── ConfigUtils.kt    ← Импорт конфигов (VLESS/VMess/SS/WG/Trojan)
│       │   ├── PrefsManager.kt   ← Все настройки (SharedPreferences)
│       │   ├── OpenVpnHelper.kt  ← Работа с OpenVPN-профилями
│       │   └── SecurityUtils.kt  ← Безопасность
│       └── ui/
│           ├── TrafficMapScreen.kt  ← Визуальная карта трафика
│           ├── ManualEntryDialog.kt ← Ручной ввод конфига
│           └── theme/               ← Темы оформления
├── carnelia-vpn-fork/android/    ← Полный Android-проект (сборка)
├── build_and_deploy.ps1          ← Скрипт: сборка + ADB установка
├── setup_dev.ps1                 ← Скрипт: установка зависимостей
└── make_clean_src.py             ← Скрипт: регенерация clean-src
```

---

## Быстрый старт

### 1. Установка окружения
```powershell
.\setup_dev.ps1
```
Требования:
- JDK 17+ (Android Studio включает JBR)
- Android SDK (API 34)
- ADB (в составе platform-tools)

### 2. Сборка APK
```powershell
.\build_and_deploy.ps1 -BuildOnly
```
APK будет в `D:\carneliavpn\null-vpn.apk`

### 3. Сборка + установка на телефон
```powershell
.\build_and_deploy.ps1
```
Телефон должен быть подключён по USB с включённой отладкой ADB.

### 4. Только установить уже собранный APK
```powershell
.\build_and_deploy.ps1 -DeployOnly
```

### 5. Указать конкретное устройство
```powershell
adb devices                             # посмотреть serial
.\build_and_deploy.ps1 -Device XXXXXXXX
```

---

## Сборка вручную через Gradle

```powershell
cd D:\carneliavpn\carnelia-vpn-fork\android

# Release APK (vanilla — без кошелька)
.\gradlew.bat assembleVanillaRelease --no-daemon

# Release APK (wallet — с криптокошельком)
.\gradlew.bat assembleWalletRelease --no-daemon

# Debug APK (не требует keystore)
.\gradlew.bat assembleVanillaDebug
```

Вывод: `app\build\outputs\apk\vanilla\release\`

---

## Установка через ADB вручную

```powershell
# Путь к ADB
$ADB = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe"

# Посмотреть подключённые устройства
& $ADB devices

# Установить APK
& $ADB install -r D:\carneliavpn\null-vpn.apk

# Запустить после установки
& $ADB shell am start -n "com.carnelia.vpn/.MainActivity"

# Унаследованный способ через Intent (если main не срабатывает)
& $ADB shell monkey -p com.carnelia.vpn -c android.intent.category.LAUNCHER 1

# Просмотр логов в реальном времени (LogcatFilter по пакету)
& $ADB logcat --pid=$(& $ADB shell pidof com.carnelia.vpn) -v time

# Uninstall
& $ADB uninstall com.carnelia.vpn
```

---

## Подпись APK

Новый keystore лежит в `android/app/nullvpn.jks`.

Параметры:
| Поле | Значение |
|------|----------|
| Файл | `nullvpn.jks` |
| Alias | `nullvpn` |
| Store password | `nullvpn2024` |
| Key password | `nullvpn2024` |

Для ручной подписи:
```powershell
# Путь к keytool (Android Studio JBR)
$KEYTOOL = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
$JARSIGNER = "C:\Program Files\Android\Android Studio\jbr\bin\jarsigner.exe"

# Проверить keystore
& $KEYTOOL -list -keystore D:\carneliavpn\carnelia-vpn-fork\android\app\nullvpn.jks -storepass nullvpn2024

# Пересподписать APK (если нужно)
& $JARSIGNER -verbose -sigalg SHA256withRSA -digestalg SHA-256 `
  -keystore D:\carneliavpn\carnelia-vpn-fork\android\app\nullvpn.jks `
  -storepass nullvpn2024 -keypass nullvpn2024 `
  D:\carneliavpn\null-vpn.apk nullvpn
```

---

## Как редактировать проект

### В Android Studio
1. Открыть: `File → Open → D:\carneliavpn\carnelia-vpn-fork\android`
2. Дождаться Gradle sync
3. Основные файлы в `app/src/main/kotlin/com/carnelia/vpn/`

### Где что менять
| Что хочу изменить | Файл |
|---|---|
| Название приложения | `res/values/strings.xml` → `app_name` |
| Package ID | `build.gradle.kts` → `applicationId` |
| Версия | `build.gradle.kts` → `versionName` / `versionCode` |
| Главный экран | `MainActivity.kt` |
| Добавить сервер (парсер) | `utils/ConfigUtils.kt` → функции `parseVless`, `parseShadowsocks` и т.д. |
| Настройки | `utils/PrefsManager.kt` + `SettingsActivity.kt` |
| VPN-подключение | `service/CarheliaVpnService.kt` + `core/VpnManager.kt` |
| Xray конфигурация | `core/XrayCoreManager.kt` → `buildConfig()` |
| Shadowsocks (tun2socks) | `core/protocols/ShadowsocksProxyProtocol.kt` |
| Карта трафика | `ui/TrafficMapScreen.kt` |
| Темы | `ui/theme/Theme.kt` |

---

## Поддерживаемые протоколы

| Протокол | Класс | Комментарий |
|---|---|---|
| VLESS + REALITY | `XrayCoreManager` | Xray-core binary |
| VLESS + TLS | `XrayCoreManager` | Xray-core binary |
| VMess | `XrayCoreManager` | Xray-core binary |
| Trojan | `XrayCoreManager` | Xray-core binary |
| Shadowsocks | `ShadowsocksProxyProtocol` | tun2socks Go AAR |
| WireGuard | `XrayCoreManager` | Через Xray wireguard outbound |
| AmneziaWG | `XrayCoreManager` | Парсится из INI, работает как WG без amneziawg-go |
| OpenVPN | `OpenVpnHelper` | ics-openvpn (vpnLib) |

---

## Структура конфига сервера (VpnServerConfig)

```kotlin
data class VpnServerConfig(
    val id: String,         // UUID для идентификации
    val name: String,       // Отображаемое имя
    val protocol: VpnProtocol, // VLESS, VMESS, SS, WG, TROJAN, OPENVPN...
    val host: String,       // Адрес сервера
    val port: Int,          // Порт
    val config: Map<String, String>,  // Параметры протокола
    val username: String? = null,
    val password: String? = null
)
```

Импорт конфига из ссылки:
```kotlin
val config = ConfigUtils.parseAccessKey("vless://uuid@host:port?...")
```

---

## Ключевые настройки (PrefsManager)

| Функция | Ключ | Описание |
|---|---|---|
| `isKillSwitchEnabled` | `kill_switch_enabled` | Блокировать трафик при отключении VPN |
| `isFragmentationEnabled` | `frag_enabled` | DPI-обход через фрагментацию |
| `isNoiseModeEnabled` | `noise_mode_enabled` | Шумовые запросы для маскировки |
| `isDoubleTunnelEnabled` | `double_tunnel_enabled` | Двойной туннель (цепочка прокси) |
| `isSplitTunnelingEnabled` | `split_tunneling_enabled` | Раздельное туннелирование |
| `isStealthModeEnabled` | `stealth_mode_v2` | Режим невидимки |
| `isAutoConnectEnabled` | `auto_connect_enabled` | Автоподключение |

---

## Решение типичных проблем

**Gradle sync падает с "SDK not found"**
```powershell
# Проверить local.properties
Get-Content D:\carneliavpn\carnelia-vpn-fork\android\local.properties
# Должна быть строка: sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

**ADB: device not found**
```powershell
# На телефоне: Настройки → О телефоне → 7 раз тапнуть "Номер сборки"
# Затем: Настройки → Для разработчиков → Отладка по USB = вкл
# Разрешить доступ на телефоне при подключении USB
adb kill-server ; adb start-server ; adb devices
```

**Ошибка подписи: "INSTALL_PARSE_FAILED_NO_CERTIFICATES"**
```powershell
# APK не подписан — убедитесь что используется release-вариант сборки
# Debug-сборка подписывается автоматически debug-ключом
.\gradlew.bat assembleVanillaDebug
```

**conflicting classes из Go (tun2socks)**
```
Уже обработано в build.gradle.kts через pickFirsts для go/**
```
