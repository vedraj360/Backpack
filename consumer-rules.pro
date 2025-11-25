-keep class com.vdx.backpack.core.** { *; }
-keep interface com.vdx.backpack.storage.CloudStorageProvider { *; }
-keep class com.vdx.backpack.core.BackupResult { *; }
-keep class com.vdx.backpack.core.BackupMetadata { *; }


# WorkManager Workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker

-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Backpack Workers
-keep class com.vdx.backpack.worker.** {
    public <init>(...);
}

# ===== Google API / Drive =====
-keep class com.google.** { *; }
-keep interface com.google.** { *; }
-dontwarn com.google.**

-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.collect.MinMaxPriorityQueue

-keepattributes *Annotation*,Signature

# Keep JSON models for reflection
-keep class * extends com.google.api.client.json.GenericJson { *; }

# Keep Drive API service classes
-keep class com.google.api.services.drive.** { *; }

# ===== Automatically generated suppressions =====
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid