# JARVIS Voice Assistant - ProGuard Rules

# Keep Vosk classes
-keep class org.vosk.** { *; }
-keepclasseswithmembernames class org.vosk.** { *; }

# Keep TensorFlow Lite classes
-keep class org.tensorflow.** { *; }
-keepclasseswithmembernames class org.tensorflow.** { *; }

# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.jarvis.assistant.**$$serializer { *; }
-keepclassmembers class com.jarvis.assistant.** {
    *** Companion;
}
-keepclasseswithmembers class com.jarvis.assistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
