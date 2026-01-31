# Carnelia VPN - VPN Integration Guide

## Architecture Overview

Carnelia VPN использует модульную архитектуру для поддержки нескольких VPN протоколов:

```
┌─────────────────────────────────────┐
│         UI Layer (Compose)          │
│   MainActivity • SettingsActivity   │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│        VPN Manager Layer            │
│   • VpnManager (главный контроллер)  │
│   • Управление состоянием            │
│   • Координация протоколов           │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│         VPN Protocol Layer                      │
├─────────────────────────────────────────────────┤
│  • OutlineVpnProtocol (Shadowsocks)            │
│  • OpenVpnProtocol (Traditional VPN)           │
│  • WireGuardProtocol (Modern crypto)           │
│  • ProtocolFactory (Создание экземпляров)      │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│      VPN Service (Foreground Service)           │
│   • CarheliaVpnService                          │
│   • Android VPN Interface Management            │
│   • Traffic tunneling & routing                 │
└─────────────────────────────────────────────────┘
```

## Supported Protocols

### 1. **Outline VPN** (Recommended - Lightweight)
- **Based on**: Shadowsocks
- **Encryption**: ChaCha20-IETF-Poly1305, AES-256-GCM
- **Weight**: ~2MB
- **Features**: Fast, low latency, works in restricted networks
- **Format**: `ss://[method]:[password]@[host]:[port]/?outline=1`

```kotlin
val outlineConfig = VpnServerConfig(
    id = "outline-1",
    name = "Outline Server US",
    protocol = VpnProtocol.OUTLINE,
    host = "vpn.example.com",
    port = 1234,
    config = mapOf(
        "method" to "chacha20-ietf-poly1305",
        "password" to "your-password"
    )
)
vpnManager.connect(outlineConfig)
```

### 2. **OpenVPN**
- **Based on**: OpenSSL, tap interface
- **Encryption**: AES-256-CBC, ChaCha20
- **Weight**: ~5MB
- **Features**: Cross-platform, widely compatible
- **Format**: `.ovpn` configuration file

### 3. **WireGuard**
- **Based on**: Modern kernel-space VPN
- **Encryption**: ChaCha20, Poly1305
- **Weight**: ~1MB
- **Features**: Fastest, smallest code base
- **Format**: Key-based configuration

## Core Components

### VpnManager
Главный класс управления VPN состоянием:

```kotlin
val vpnManager = VpnManager()

// Подключение
vpnManager.connect(serverConfig)

// Отключение
vpnManager.disconnect()

// Слушатели событий
vpnManager.onStateChanged { state ->
    when (state) {
        ConnectionState.CONNECTED -> {}
        ConnectionState.ERROR -> {}
        else -> {}
    }
}

vpnManager.onStatsChanged { stats ->
    println("Sent: ${stats.bytesSent}, Received: ${stats.bytesReceived}")
}
```

### CarheliaVpnService
Foreground Service для VPN:

```kotlin
// Запуск
val intent = Intent(this, CarheliaVpnService::class.java).apply {
    action = CarheliaVpnService.ACTION_CONNECT
    putExtra(CarheliaVpnService.EXTRA_CONFIG, config)
}
startService(intent)

// Остановка
val intent = Intent(this, CarheliaVpnService::class.java).apply {
    action = CarheliaVpnService.ACTION_DISCONNECT
}
startService(intent)
```

### Protocol Abstraction
Все протоколы реализуют `IVpnProtocol` интерфейс:

```kotlin
interface IVpnProtocol {
    suspend fun prepare(): VpnErrorCode
    suspend fun start(config: VpnServerConfig): VpnErrorCode
    suspend fun stop()
    fun getConnectionState(): ConnectionState
    fun getBytesTransferred(): Pair<Long, Long>
}
```

## Configuration Management

### Импорт Outline ключа
```kotlin
val accessKey = "ss://chacha20-ietf-poly1305:password@vpn.example.com:1234/?outline=1"
val config = OutlineConfigHelper.parseOutlineAccessKey(accessKey)
```

### Сохранение конфигов
```kotlin
val repository = VpnConfigRepository(dataStore)
repository.saveServer(config)
repository.setLastServer(config.id)
```

## Integration Points

### 1. Adding New Protocol
1. Создать класс, реализующий `IVpnProtocol`
2. Добавить в `ProtocolFactory.createProtocol()`
3. Добавить в `VpnProtocol` enum

```kotlin
class CustomVpnProtocol : IVpnProtocol {
    override suspend fun prepare() = VpnErrorCode.NO_ERROR
    override suspend fun start(config: VpnServerConfig) = VpnErrorCode.NO_ERROR
    // ...
}
```

### 2. UI Integration
MainActivity уже интегрирована с VpnManager:
- Показывает статус соединения
- Отображает статистику (отправлено/получено)
- Управляет подключением/отключением

### 3. Permission Handling
Все необходимые permissions в AndroidManifest.xml:
- BIND_VPN_SERVICE
- INTERNET
- CHANGE_NETWORK_STATE
- FOREGROUND_SERVICE

## Dependencies

```kotlin
// Outline (Shadowsocks)
// implementation("org.outline:outline-android:1.0.0") // Когда будет доступен

// WireGuard
implementation("com.wireguard.android:tunnel:1.0.23")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")
```

## Testing

### Local Testing
1. Сконнектить Android device (USB) или запустить эмулятор
2. Запустить: `./gradlew installDebug`
3. Открыть приложение
4. Нажать "ПОДКЛЮЧИТЬ"

### Protocol Testing
Каждый протокол тестируется отдельно:
```kotlin
val protocol = OutlineVpnProtocol()
protocol.prepare()
protocol.start(config)
protocol.onConnectionStateChanged { state -> println(state) }
```

## Future Improvements

- [ ] IKEv2 поддержка
- [ ] XRay протокол
- [ ] Split tunneling (include/exclude приложения)
- [ ] Automatic server selection
- [ ] Backup конфигурации
- [ ] VPN kill switch
- [ ] DNS over HTTPS/TLS
