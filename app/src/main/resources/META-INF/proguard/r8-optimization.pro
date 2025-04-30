# Aggressive optimization configuration for R8

# Enable optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Remove logging for release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove BuildConfig fields that aren't needed
-assumenosideeffects class com.example.askai.BuildConfig {
    private static final boolean DEBUG = false;
}

# Aggressively remove unused code and resources
-repackageclasses ''
-flattenpackagehierarchy ''
-mergeinterfacesaggressively

# Remove Kotlin metadata to reduce size
-dontwarn kotlin.**

# Keep entry points to the app
-keep public class com.example.askai.MainActivity { *; }
-keep public class com.example.askai.ProcessTextActivity { *; }
-keep public class com.example.askai.OverlayService { *; }

# Remove kotlin.Metadata annotations to further reduce size
-assumenosideeffects class kotlin.Metadata {
    *;
}

# Remove Compose compiler metadata to reduce size
-assumenosideeffects class androidx.compose.runtime.internal.ComposableLambdaImpl {
    void <init>(...);
}

# Further optimization rules
-assumevalues class android.os.Build$VERSION {
    int SDK_INT return 29..35;
} 