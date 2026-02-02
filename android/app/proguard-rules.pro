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
#-keep class com.google.gson.stream.** { *; }

# Prevent R8 from stripping Enum members used by Gson
-keepclassmembers enum * { *; }

-keep class * extends androidx.compose.** { *; }
