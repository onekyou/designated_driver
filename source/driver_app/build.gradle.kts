// Top-level build file where plugins are applied.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    // --- Hilt 플러그인 추가 ---
    id("com.google.dagger.hilt.android") version "2.48" apply false // 최신 안정 버전 확인 필요
    // --- ---
}

// task clean(type: Delete) {  <-- 삭제
//     delete rootProject.buildDir <-- 삭제
// } <-- 삭제 