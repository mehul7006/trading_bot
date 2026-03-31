# Add project specific ProGuard rules here.
# Keep data model classes for Gson deserialization
-keep class com.trading.bot.android.api.models.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
