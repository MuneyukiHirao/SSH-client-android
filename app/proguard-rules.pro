# SSH Terminal ProGuard Rules

# Optimization settings
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# sshlib
-keep class org.connectbot.** { *; }
-dontwarn org.connectbot.**

# JSch (used by sshlib)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Terminal View
-keep class com.termux.** { *; }
-dontwarn com.termux.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-keep class androidx.compose.** { *; }

# Koin
-keepnames class * extends org.koin.core.module.Module

# DataStore
-keep class androidx.datastore.** { *; }

# Keep Terminal Emulator and View classes for performance
-keep class com.example.sshterminal.ui.components.TerminalEmulator { *; }
-keep class com.example.sshterminal.ui.components.TerminalEmulator$TerminalCell { *; }
-keep class com.example.sshterminal.ui.components.TerminalEmulator$DirtyRegion { *; }
-keep class com.example.sshterminal.ui.components.TerminalAndroidView { *; }

# Keep domain models
-keep class com.example.sshterminal.domain.model.** { *; }
