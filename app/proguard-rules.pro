# ProGuard rules for PC Switch Android app
# https://developer.android.com/studio/build/shrink-code

# Keep activity classes (referenced from AndroidManifest)
-keep public class * extends androidx.activity.ComponentActivity

# Keep Compose ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep Compose-generated classes
-keepnames class androidx.compose.runtime.internal合成的**

# Keep DataStore classes
-keep class androidx.datastore.core.** { *; }

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Retrofit classes if used
-keep class retrofit2.** { *; }

# Keep JSON serialization annotations
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Keep serialization classes (kotlinx.serialization)
-keep class kotlinx.serialization.** { *; }
-keepattributes RuntimeVisibleAnnotations

# Keep app classes with serialization annotations
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep view classes referenced from XML
-keep public class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Don't warn about unused classes in dependencies
-dontwarn androidx.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.crypto.tink.**

# Don't inline static methods (AndroidX uses reflection)
-keep class android.view.View {
    @java.lang.annotation.Retention *;
    @java.lang.annotation.Target *;
}
