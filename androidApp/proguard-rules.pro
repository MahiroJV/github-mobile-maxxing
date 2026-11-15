# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class dev.octoexpress.**$$serializer { *; }
-keepclassmembers class dev.octoexpress.** {
    *** Companion;
}
-keepclasseswithmembers class dev.octoexpress.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp / Coroutines
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-keepclassmembers class * implements android.content.BroadcastReceiver {
    public <init>(...);
}
-keep class dev.octoexpress.app.notifications.NotificationSyncWorker { *; }
-keep class dev.octoexpress.app.notifications.NotificationActionReceiver { *; }
