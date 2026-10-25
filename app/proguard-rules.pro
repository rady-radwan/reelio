# VLC talks to its Java side through JNI
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**
# FFmpeg audio decoders are found by reflection
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.DefaultRenderersFactory { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn kotlin.annotations.jvm.**
