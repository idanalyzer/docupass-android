# kotlinx.serialization — keep generated serializers for the public models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.idanalyzer.docupass.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.idanalyzer.docupass.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# MediaPipe Tasks Vision uses JNI + reflection internally.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# OkHttp.
-dontwarn okhttp3.**
-dontwarn okio.**
