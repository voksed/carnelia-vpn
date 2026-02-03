-keep class com.carnelia.vpn.** { *; }
-keep interface com.carnelia.vpn.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Gson Rules
# Gson uses generic type information stored in a class file when working with fields. ProGuard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
# Keep TypeToken and its subclasses (including anonymous ones) to preserve generic types
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Prevent R8 from stripping Enum members used by Gson
-keepclassmembers enum * { *; }

# Explicitly keep the Server Configuration models and their fields
-keep class com.carnelia.vpn.core.VpnServerConfig { *; }
-keep class com.carnelia.vpn.core.VpnProtocol { *; }
-keep class com.carnelia.vpn.core.ConnectionState { *; }
-keep class com.carnelia.vpn.core.VpnErrorCode { *; }
# Keep the repository class to prevent side-effects with context
-keep class com.carnelia.vpn.data.ServerRepository { *; }

-keep class * extends androidx.compose.** { *; }
