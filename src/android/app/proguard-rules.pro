# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Giữ nguyên AndroidX Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Giữ nguyên SDK của Jitsi
-keep class org.jitsi.meet.** { *; }
-keep class org.jitsi.meet.sdk.** { *; }

# Nếu bạn vẫn có ExoPlayer2 (chỉ để stub), giữ nguyên
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Giữ stub ReactVideoPackage
-keep class com.brentvatne.react.** { *; }
# noinspection ShrinkerUnresolvedReference
-keep class com.brentvatne.react.ReactVideoPackage { *; }

