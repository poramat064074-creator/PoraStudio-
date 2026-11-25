# PoraStudio Production ProGuard / R8 Rules

# Keep debugging metadata (class/line numbers) cleanly mapped
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,JavascriptInterface
-renamesourcefileattribute SourceFile

# Preserve @Keep annotated classes and members (CloudModels, WebView JS Bridges)
-keep class androidx.annotation.Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}

# Preserve WebView @JavascriptInterface callbacks
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Firestore & Moshi serialization data classes
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }

# Keep Room entities and DAOs
-keep class com.example.data.local.** { *; }

# Preserve Kotlin reflection / coroutines internals used by Firebase & Room
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
