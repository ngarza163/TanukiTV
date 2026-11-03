# AnimeTV Android TV ProGuard / R8 Rules

# Keep Jetpack Compose & TV Foundation
-keep class androidx.compose.** { *; }
-keep class androidx.tv.** { *; }

# Keep Data Models & Storage for Gson / JSON serialization
-keepclassmembers class com.darknet.animetv.data.model.** { <fields>; }
-keep class com.darknet.animetv.data.model.** { *; }
-keepclassmembers class com.darknet.animetv.update.** { <fields>; }
-keep class com.darknet.animetv.update.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Keep FileProvider
-keep class androidx.core.content.FileProvider { *; }
