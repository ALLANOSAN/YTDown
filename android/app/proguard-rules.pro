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
# Room Database & WorkManager
# ============================================
# Room: Necessário para que as queries e o banco de dados funcionem
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# WorkManager: Garante que os workers de download não sejam removidos
-keep class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.Worker

# ============================================
# FFmpeg / Media
# ============================================
-dontwarn com.arthenica.**
-keep class com.arthenica.** { *; }

# ============================================
# JNI / Native Bridge
# ============================================
# Mantém todos os métodos nativos (JNI). Vital para Chaquopy e bibliotecas .so
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================
# Domain Models & Serialization
# ============================================
# Mantém as classes de domínio e Value Classes. 
# Se o R8 renomear os campos, o JSON vindo do Python não será mapeado corretamente.
-keep class com.example.ytdown.core.domain.** { *; }
-keep class com.example.ytdown.core.business.** { *; }
-keepclassmembers class com.example.ytdown.core.domain.** { *; }

# ============================================
# Desugar / JDK
# ============================================
-dontwarn java.lang.invoke.LambdaMetafactory
