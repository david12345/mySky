# Kotlinx Serialization: manter os serializers gerados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Modelos DTO da OpenSky (desserializados por reflexão de nomes).
-keep class com.mysky.app.data.source.opensky.** { *; }
