# DSL224Manager ProGuard / R8 Rules

# Preserve JavaScript Interface for WebView No-Code Picker
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.personal.dsl224manager.WebUIPickerBridge { *; }

# Preserve Data Models and Enums
-keep class com.personal.dsl224manager.RouterProfile { *; }
-keep class com.personal.dsl224manager.NetworkDevice { *; }
-keep class com.personal.dsl224manager.FirmwareType { *; }
-keep class com.personal.dsl224manager.RouterPreset { *; }
-keep class com.personal.dsl224manager.AutoDetectResult { *; }
-keep class com.personal.dsl224manager.WebUIScrapeResult { *; }
-keep class com.personal.dsl224manager.PickedElementInfo { *; }
-keep class com.personal.dsl224manager.WebUIButtonInfo { *; }
-keep class com.personal.dsl224manager.AppReleaseInfo { *; }

# OkHttp Rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coroutines Rules
-dontwarn kotlinx.coroutines.**
