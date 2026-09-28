# ==============================================================================
# ProGuard / R8 Configuration for Analog Anchor
# Optimized for Google Play Console Release & Android App Bundle (AAB)
# ==============================================================================

# Preserve source file and line number information for readable Google Play crash stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# 1. Core Application & Lifecycle Components
# ------------------------------------------------------------------------------
-keep class com.analoganchor.app.AnalogAnchorApplication { *; }

# Background Services & System Listeners
-keep class com.analoganchor.app.service.** { *; }
-keep class com.analoganchor.app.worker.** { *; }

# ------------------------------------------------------------------------------
# 2. WorkManager
# Prevents NoSuchMethodException when WorkManager reflectively instantiates workers
# ------------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ------------------------------------------------------------------------------
# 3. AndroidX Security Crypto & Google Tink
# Used by PendingAutofillPinStore for AES-256 encrypted PIN storage
# ------------------------------------------------------------------------------
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class androidx.security.crypto.** { *; }

# ------------------------------------------------------------------------------
# 4. Mapbox Maps SDK v11 & Common JNI / Reflection Keep Rules
# ------------------------------------------------------------------------------
-keep class com.mapbox.** { *; }
-keep interface com.mapbox.** { *; }
-dontwarn com.mapbox.**

# Mapbox GeoJSON & GSON
-keep class com.mapbox.geojson.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Preserve Parcelable classes used by Mapbox
-keepnames class com.mapbox.maps.** implements android.os.Parcelable
-keepnames class com.mapbox.geojson.** implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Preserve Native (JNI) methods called by libmapbox-maps.so and libmapbox-common.so
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve Mapbox MapView and styleable resources access
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Suppress warnings for optional Google Play Services location APIs referenced by Mapbox
-dontwarn com.google.android.gms.common.GoogleApiAvailability
-dontwarn com.google.android.gms.location.**
-dontwarn com.google.android.gms.tasks.**
-dontwarn com.google.errorprone.annotations.**

# ------------------------------------------------------------------------------
# 5. Room Database
# Preserves entities and TypeConverters across AppDatabase & ZenithDatabase
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 6. Moshi & OkHttp Serialization / Networking
# ------------------------------------------------------------------------------
-dontwarn com.squareup.moshi.**
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# ------------------------------------------------------------------------------
# 7. Coil Image Loader
# ------------------------------------------------------------------------------
-dontwarn coil.**
