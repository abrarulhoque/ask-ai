# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep some line number information for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve the Serializable interface
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Kotlinx serialization rules
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Kotlinx Serialization for all model classes in our app
-keep,includedescriptorclasses class com.example.askai.data.**$$serializer { *; }
-keepclassmembers class com.example.askai.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.askai.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# OpenAI Client Rules
-keep class com.aallam.openai.** { *; }
-keep class io.ktor.** { *; }

# Datastore Rules
-keep class androidx.datastore.** { *; }

# Don't warn on unused dependencies, as they might be used at runtime
-dontwarn org.slf4j.**
-dontwarn kotlin.reflect.jvm.internal.**

# Fix for missing classes in R8
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn io.ktor.util.debug.IntellijIdeaDebugDetector