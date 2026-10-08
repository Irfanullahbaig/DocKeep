# Play Console DEX scores require R8 shrinking, optimization, and obfuscation.
# Keep only reflective or generated entry points. Do not keep the whole app package.

-repackageclasses

# kotlinx.serialization backup models are decoded from JSON at runtime.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*
-keepclassmembers class com.dockeep.app.data.backup.BackupPayload {
    *** Companion;
}
-keepclassmembers class com.dockeep.app.data.backup.CategoryBackup {
    *** Companion;
}
-keepclassmembers class com.dockeep.app.data.backup.DocumentBackup {
    *** Companion;
}
-keep class com.dockeep.app.data.backup.**$$serializer { *; }
-keepclasseswithmembers class com.dockeep.app.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities and database (R8 9 no longer keeps constructors from a class keep).
-keep class com.dockeep.app.data.local.CategoryEntity {
    <init>(...);
}
-keep class com.dockeep.app.data.local.DocumentEntity {
    <init>(...);
}
-keep class com.dockeep.app.data.local.AppDatabase {
    <init>();
    public *;
}

# WorkManager constructs this worker by class name.
-keep class com.dockeep.app.notifications.ExpiryCheckWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Manifest receiver constructor under strict R8 keep rules.
-keep class com.dockeep.app.notifications.ExpiryNotificationReceiver {
    <init>();
}

-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_text_common.**
