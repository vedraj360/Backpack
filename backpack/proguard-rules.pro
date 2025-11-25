############################################
# BACKPACK LIBRARY
############################################
-keep class com.vdx.backpack.** { *; }
-keepclassmembers class com.vdx.backpack.** { *; }

# Public APIs
-keep public class com.vdx.backpack.core.BackpackManager { *; }
-keep public class com.vdx.backpack.core.BackupConfig { *; }
-keep public class com.vdx.backpack.core.BackupResult { *; }
-keep public class com.vdx.backpack.core.BackupMetadata { *; }
-keep public interface com.vdx.backpack.storage.CloudStorageProvider { *; }


############################################
# ROOM DATABASE
############################################
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** getDatabase(...);
}


############################################
# GOOGLE DRIVE & GOOGLE API CLIENT
############################################

# Core Google API client libraries
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }

-dontwarn com.google.api.**
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.drive.**

# FIX: required for GoogleJsonError & parsing
-keep class com.google.api.client.googleapis.json.** { *; }
-keep class com.google.api.client.googleapis.services.json.** { *; }
-keep class com.google.api.client.json.** { *; }
-keep class com.google.api.client.util.** { *; }

# Keep all fields with @Key annotation
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# Required for HTTP + parsing
-keep class com.google.api.client.http.** { *; }


# Required so JSON parser can instantiate models
-keep class com.google.api.services.drive.model.** { *; }

# Prevent stripping annotation + signature metadata
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod, InnerClasses


############################################
# GOOGLE OAUTH / PLAY SERVICES
############################################
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

-dontwarn com.google.android.gms.**


############################################
# ANDROID KEYSTORE / CRYPTO
############################################
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

-dontwarn javax.crypto.**
-dontwarn java.security.**


############################################
# KOTLIN
############################################
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }

-dontwarn kotlin.**

-keepclassmembers class **$WhenMappings { <fields>; }

-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

-dontwarn kotlinx.coroutines.**


############################################
# HILT / DAGGER
############################################
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }

-keepclassmembers,allowobfuscation class * {
    @javax.inject.* *;
    @dagger.* *;
}

-keep @dagger.Module class *
-keep @dagger.hilt.** class *


############################################
# GSON
############################################
-keepattributes Signature
-keepattributes *Annotation*

-keep class com.google.gson.** { *; }

-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-dontwarn sun.misc.**


############################################
# TIMBER
############################################
-keep class timber.log.** { *; }
-dontwarn timber.log.**


############################################
# ANDROID GENERAL
############################################
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# View getters/setters
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}

# Activities & Fragments
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment


############################################
# WORKMANAGER
############################################
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

# Required Worker constructors
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Backpack-specific workers
-keep class com.vdx.backpack.worker.AutoBackupWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keep class com.vdx.backpack.worker.** extends androidx.work.Worker {
    public <init>(...);
}


############################################
# PARCELABLE
############################################
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

############################################
# SERIALIZABLE
############################################
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}


############################################
# LOG REMOVAL (RELEASE ONLY)
############################################
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}


############################################
# OPTIMIZATION
############################################
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose


-keep class com.google.** { *;}
-keep interface com.google.** { *;}
-dontwarn com.google.**

-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.collect.MinMaxPriorityQueue
-keepattributes *Annotation*,Signature
-keep class * extends com.google.api.client.json.GenericJson {
*;
}
-keep class com.google.api.services.drive.** {
*;
}