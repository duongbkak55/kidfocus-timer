# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ---- Kotlin ---------------------------------------------------------------------------------
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.coroutines.** { *; }

# ---- Hilt -----------------------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
}
-keep class dagger.hilt.** { *; }

# ---- Room -----------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# ---- DataStore ------------------------------------------------------------------------------
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite* {
    <fields>;
}

# ---- Compose --------------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }

# ---- App models (never obfuscate domain models) --------------------------------------------
-keep class com.kidfocus.timer.domain.model.** { *; }
-keep class com.kidfocus.timer.data.database.** { *; }

# ---- Serialization --------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
