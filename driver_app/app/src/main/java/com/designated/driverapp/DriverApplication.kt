package com.designated.driverapp

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp // Hilt 애플리케이션 클래스임을 명시
class DriverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}