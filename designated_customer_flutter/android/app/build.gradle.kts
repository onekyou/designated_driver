plugins {
    id("com.android.application")
    // START: FlutterFire Configuration
    id("com.google.gms.google-services")
    // END: FlutterFire Configuration
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Release 서명 설정 로드
val keystorePropertiesFile: File = rootProject.file("key.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.designated.designated_customer_flutter"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.designated.designated_customer_flutter"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile") ?: "")
                storePassword = keystoreProperties.getProperty("storePassword") ?: ""
                keyAlias = keystoreProperties.getProperty("keyAlias") ?: ""
                keyPassword = keystoreProperties.getProperty("keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        release {
            // Release 서명 설정 사용 (key.properties 파일이 있으면)
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                // key.properties 없으면 debug 서명 사용 (개발 중)
                signingConfigs.getByName("debug")
            }
        }
    }
}

flutter {
    source = "../.."
}
