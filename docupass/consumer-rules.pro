# MediaPipe Tasks Vision uses JNI + reflection internally.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# OkHttp.
-dontwarn okhttp3.**
-dontwarn okio.**
