# Keep Android entry points referenced by name in AndroidManifest.xml
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application
-keep public class * extends android.service.quicksettings.TileService
-keep public class * extends android.appwidget.AppWidgetProvider

-keepclassmembers class * {
    native <methods>;
}

# Gson Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers enum * { *; }

# Keep VPN data models (serialized/deserialized with Gson)
-keep class com.carnelia.vpn.core.VpnServerConfig { *; }
-keep class com.carnelia.vpn.core.VpnProtocol { *; }
-keep class com.carnelia.vpn.core.ConnectionState { *; }
-keep class com.carnelia.vpn.core.VpnErrorCode { *; }
-keep class com.carnelia.vpn.data.ServerRepository { *; }

# Keep tun2socks/shadowsocks Go-JNI bridge classes (called from native Go code)
-keep class tun2socks.** { *; }
-keep class shadowsocks.** { *; }
-keep class go.** { *; }

# Keep OpenVPN library (ics-openvpn)
-keep class de.blinkt.openvpn.** { *; }
-keep interface de.blinkt.openvpn.** { *; }

# Keep Compose internals that R8 might strip
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# TON Wallet & TON Connect
-keep class com.carnelia.vpn.wallet.** { *; }
-keep class com.iwebpp.crypto.** { *; }
-dontwarn com.iwebpp.crypto.**

