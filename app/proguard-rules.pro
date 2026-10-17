# youtubedl-android Core
-keep class com.yausername.youtubedl.** { *; }
-keep class io.github.junkfood02.youtubedl.** { *; }

# Py4J
-keep class py4j.** { *; }
-dontwarn py4j.**

# FFmpeg
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.arthenica.** { *; }
-keep class org.ffmpeg.** { *; }
-dontwarn com.arthenica.**
-dontwarn org.ffmpeg.**

# Keep app data models
-keep class com.linktoaudio.app.data.** { *; }
