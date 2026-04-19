# ============================================
# Flutter
# ============================================
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.embedding.engine.** { *; }
-keep class io.flutter.embedding.** { *; }
-keep class io.flutter.plugins.** { *; }

# ============================================
# Chaquopy / Python
# ============================================
-keep class com.chaquo.python.** { *; }

# See get_sam in class.pxi
-keep class kotlin.jvm.functions.** { *; }
-keep class kotlin.jvm.internal.FunctionBase { *; }
-keep class kotlin.reflect.KAnnotatedElement { *; }

-dontwarn org.jetbrains.annotations.NotNull

# ============================================
# Kotlin stdlib
# ============================================
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ============================================
# Dart model classes (reflection / serialization)
# ============================================
# DownloadItem, DownloadStatus, DownloadType, ExportStatus, FormatOptions
# These classes use toMap()/fromMap() serialization patterns
-keep class com.example.ytdown.** { *; }
-keepclassmembers class com.example.ytdown.** {
    *** toMap();
    <init>(java.util.Map);
}

# Keep enum values (used in serialization)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# General serialization safety
# ============================================
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# ============================================
# AndroidX / Support
# ============================================
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# ============================================
# FFmpeg / Media
# ============================================
-dontwarn com.arthenica.**
-keep class com.arthenica.** { *; }

# ============================================
# Desugar / JDK
# ============================================
-dontwarn java.lang.invoke.LambdaMetafactory

# ============================================
# Google Play Core (referenced by Flutter but not needed for non-Play-Store builds)
# ============================================
-dontwarn com.google.android.play.core.**
-keep class com.google.android.play.core.** { *; }
