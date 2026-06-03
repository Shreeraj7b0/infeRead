# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\niger\AppData\Local\Android\Sdk\tools\proguard\proguard-android.txt
# You can edit the include path and value by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Room compiler generated classes and constructors
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class **_Impl {
    <init>();
}
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.room.RoomDatabase

# Apache Commons Compress
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**

# Junrar
-dontwarn com.github.junrar.**

# PDFBox Android
-dontwarn com.tom_roush.pdfbox.**

# SLF4J logging
-dontwarn org.slf4j.**

