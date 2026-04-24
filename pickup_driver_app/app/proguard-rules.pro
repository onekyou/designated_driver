# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# Data / model
-keep class com.designated.pickupdriver.data.** { *; }
-keepclassmembers class com.designated.pickupdriver.data.** { *; }
-keep class com.designated.pickupdriver.model.** { *; }
-keepclassmembers class com.designated.pickupdriver.model.** { *; }

# MainActivity / Application
-keep class com.designated.pickupdriver.MainActivity { *; }
-keep class com.designated.pickupdriver.PickupDriverApplication { *; }

# Firestore property name
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}

# Compose / Navigation
-keep class androidx.compose.** { *; }
-keep class androidx.navigation.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Enum
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Attributes
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Standard android
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application

-dontwarn javax.annotation.**
-dontwarn javax.inject.**
