package com.designated.callmanager.ptt.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.designated.callmanager.ptt.ui.PTTAccessibilityGuideActivity

/**
 * PTT Quick Settings Tile Service
 * Android N+ 빠른 설정에서 PTT 서비스 제어
 */
@RequiresApi(Build.VERSION_CODES.N)
class PTTQuickSettingsTileService : TileService() {
    
    companion object {
        private const val TAG = "PTTQuickSettingsTile"
    }
    
    private lateinit var serviceMonitor: AccessibilityServiceMonitor
    
    override fun onCreate() {
        super.onCreate()
        serviceMonitor = AccessibilityServiceMonitor(this)
        Log.d(TAG, "PTT Quick Settings Tile created")
    }
    
    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Tile start listening")
        updateTileState()
    }
    
    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Tile stop listening")
    }
    
    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked")
        
        val isServiceEnabled = serviceMonitor.isAccessibilityServiceEnabled()
        
        if (isServiceEnabled) {
            // 서비스가 활성화되어 있으면 PTT 토글
            togglePTTEnabled()
        } else {
            // 서비스가 비활성화되어 있으면 설정 가이드로 이동
            openAccessibilityGuide()
        }
        
        updateTileState()
    }
    
    /**
     * PTT 활성화/비활성화 토글
     */
    private fun togglePTTEnabled() {
        try {
            val currentEnabled = PTTAccessibilityService.isPTTEnabled(this)
            val newEnabled = !currentEnabled
            
            PTTAccessibilityService.setPTTEnabled(this, newEnabled)
            
            Log.i(TAG, "PTT toggled: $currentEnabled -> $newEnabled")
            
            // 토스트 메시지 표시 (선택사항)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                showDialog(
                    if (newEnabled) 
                        android.app.AlertDialog.Builder(this)
                            .setTitle("PTT 활성화됨")
                            .setMessage("볼륨키로 PTT를 사용할 수 있습니다")
                            .setPositiveButton("확인", null)
                            .create()
                    else
                        android.app.AlertDialog.Builder(this)
                            .setTitle("PTT 비활성화됨")
                            .setMessage("PTT가 비활성화되었습니다")
                            .setPositiveButton("확인", null)
                            .create()
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle PTT", e)
        }
    }
    
    /**
     * 접근성 가이드 열기
     */
    private fun openAccessibilityGuide() {
        val intent = Intent(this, PTTAccessibilityGuideActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            startActivityAndCollapse(intent)
            Log.i(TAG, "Accessibility guide opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility guide", e)
        }
    }
    
    /**
     * 타일 상태 업데이트
     */
    private fun updateTileState() {
        val tile = qsTile ?: return
        
        try {
            val isServiceEnabled = serviceMonitor.isAccessibilityServiceEnabled()
            val isPTTEnabled = if (isServiceEnabled) {
                PTTAccessibilityService.isPTTEnabled(this)
            } else {
                false
            }
            
            // 타일 상태 설정
            tile.state = when {
                !isServiceEnabled -> Tile.STATE_UNAVAILABLE
                isPTTEnabled -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            
            // 타일 레이블 설정
            tile.label = when {
                !isServiceEnabled -> "PTT 설정 필요"
                isPTTEnabled -> "PTT 활성"
                else -> "PTT 비활성"
            }
            
            // 서브타이틀 설정 (Android Q+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = when {
                    !isServiceEnabled -> "접근성 서비스 비활성화"
                    isPTTEnabled -> "볼륨키로 사용 가능"
                    else -> "볼륨키 사용 불가"
                }
            }
            
            // 상태 설명 설정
            tile.contentDescription = when {
                !isServiceEnabled -> "PTT 접근성 서비스가 비활성화되어 있습니다. 탭하여 설정하세요."
                isPTTEnabled -> "PTT가 활성화되어 있습니다. 탭하여 비활성화하세요."
                else -> "PTT가 비활성화되어 있습니다. 탭하여 활성화하세요."
            }
            
            tile.updateTile()
            Log.v(TAG, "Tile updated - service: $isServiceEnabled, ptt: $isPTTEnabled")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update tile state", e)
            
            // 에러 시 기본 상태로 설정
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "PTT 오류"
            tile.updateTile()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PTT Quick Settings Tile destroyed")
    }
}