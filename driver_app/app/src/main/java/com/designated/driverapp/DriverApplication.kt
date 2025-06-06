package com.designated.driverapp // 패키지 이름 확인

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp // Hilt 애플리케이션 클래스임을 명시
class DriverApplication : Application() {
    // 필요한 경우 추가적인 애플리케이션 초기화 코드 작성
    override fun onCreate() {
        super.onCreate()
        Log.d("DriverApplication", "onCreate: Application class is created.")
        // 예: 앱 전역 설정, 라이브러리 초기화 등
    }
} 