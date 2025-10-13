# 손님앱 프로젝트 설정 가이드

## 🚀 Android Studio에서 프로젝트 생성하기

### 1. 새 프로젝트 생성
1. Android Studio 실행
2. "New Project" 선택
3. "Empty Activity" 템플릿 선택
4. 다음 설정 입력:
   - Name: `DesignatedCustomer`
   - Package name: `com.designated.customer`
   - Save location: `C:\app_dev\designated_driver\customer_app\app`
   - Language: Kotlin
   - Minimum SDK: API 24 (Android 7.0)

### 2. Gradle 설정
`app/build.gradle.kts`에 다음 의존성 추가:

```kotlin
dependencies {
    // 기존 의존성...

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // 위치 서비스
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // QR 스캔
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // 네트워크
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 권한 처리
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
}
```

### 3. 프로젝트 구조 생성
다음 패키지 구조를 생성하세요:

```
com.designated.customer/
├── data/
│   ├── model/
│   ├── repository/
│   └── remote/
├── domain/
│   ├── usecase/
│   └── repository/
├── presentation/
│   ├── ui/
│   │   ├── splash/
│   │   ├── landing/
│   │   ├── auth/
│   │   ├── main/
│   │   └── common/
│   ├── viewmodel/
│   └── navigation/
├── util/
│   ├── fingerprint/
│   └── attribution/
└── di/
```

## 📝 다음 단계
1. Android Studio에서 위 설정으로 프로젝트 생성
2. Firebase 프로젝트 연동
3. 랜딩 페이지 개발 시작