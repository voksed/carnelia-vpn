-keep class com.carnelia.vpn.** { *; }
-keep interface com.carnelia.vpn.** { *; }
-keepclassmembers class * {
    native <methods>;
}
-keep class * extends androidx.compose.** { *; }
