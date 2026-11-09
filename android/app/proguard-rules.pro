# WatchWave ships an offline-first host; keep the reflection-driven surface stable.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class com.watchwave.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class kotlinx.serialization.**$$serializer { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
