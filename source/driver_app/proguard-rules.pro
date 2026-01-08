# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.messaging.** { *; }

# Keep data classes
-keep class com.designated.driverapp.data.** { *; }
-keepclassmembers class com.designated.driverapp.data.** { *; }

# Keep model classes (특히 CallInfo) - Firebase 직렬화 충돌 방지
-keep class com.designated.driverapp.model.CallInfo { *; }
-keepclassmembers class com.designated.driverapp.model.CallInfo { *; }

# 다른 모델 클래스들
-keep class com.designated.driverapp.model.** { *; }
-keepclassmembers class com.designated.driverapp.model.** { *; }

# Keep Firebase Messaging Service
-keep class com.designated.driverapp.MyFirebaseMessagingService { *; }

# Keep MainActivity for intent handling
-keep class com.designated.driverapp.MainActivity { *; }

# Keep UI state classes
-keep class com.designated.driverapp.ui.state.** { *; }

# Keep ViewModels
-keep class com.designated.driverapp.viewmodel.** { *; }

# Keep service classes
-keep class com.designated.driverapp.service.** { *; }

# Keep util classes (Permission Manager 등)
-keep class com.designated.driverapp.util.** { *; }
-keepclassmembers class com.designated.driverapp.util.** { *; }

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.firestore.**

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Prevent stripping of methods/fields annotated with specific annotations
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <fields>;
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Compose
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Keep generic type information
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# If you use reflection
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes EnclosingMethod

# Retrofit (if used)
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp (if used)
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Gson (if used)
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*

# General Android
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Parcelables
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Suppress warnings
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe