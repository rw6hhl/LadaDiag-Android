# proguard-rules.pro — правила ProGuard для LadaDiag-Android.
# Версия: V1.0 (15.10.2026)
# Назначение: сохранение имён классов, используемых через рефлексию
#             (Android framework, FileProvider).

# Сохранить класс MainActivity (точка входа).
-keep class com.ladadiag.android.MainActivity { *; }

# Сохранить классы приложения (на случай рефлексии).
-keep class com.ladadiag.android.** { *; }

# Сохранить FileProvider (используется через AndroidManifest).
-keep class androidx.core.content.FileProvider { *; }

# Стандартные правила Android.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable