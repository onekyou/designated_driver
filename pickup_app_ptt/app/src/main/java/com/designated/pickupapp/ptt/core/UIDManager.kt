package com.designated.pickupapp.ptt.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * PTT 시스템의 UID 관리자
 * 사용자별로 고유한 UID를 생성하고 영구 저장
 * 
 * UID 범위:
 * - Call Manager: 1000-1999
 * - Pickup App: 2000-2999
 * - Reserved: 3000-9999
 */
object UIDManager {
    private const val TAG = "UIDManager"
    private const val PREF_NAME = "ptt_uid_storage"
    private const val KEY_PERMANENT_UID = "permanent_uid"
    
    // UID 범위 정의
    private const val CALL_MANAGER_BASE = 1000
    private const val CALL_MANAGER_MAX = 1999
    private const val PICKUP_APP_BASE = 2000
    private const val PICKUP_APP_MAX = 2999
    private const val RESERVED_BASE = 3000
    private const val RESERVED_MAX = 9999
    
    // 메모리 캐시
    private val uidCache = ConcurrentHashMap<String, Int>()
    
    /**
     * 사용자의 UID를 가져오거나 새로 생성
     * @param context Android Context
     * @param userType 사용자 타입 ("call_manager", "pickup_driver", etc.)
     * @param userId 사용자 고유 ID (Firebase Auth UID 등)
     * @return 생성되거나 기존의 UID
     */
    fun getOrCreateUID(context: Context, userType: String, userId: String): Int {
        val key = "${userType}_${userId}"
        
        // 1. 메모리 캐시 확인
        uidCache[key]?.let { 
            Log.d(TAG, "UID from memory cache: $it for $key")
            return it 
        }
        
        // 2. SharedPreferences 확인
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existingUID = prefs.getInt(key, -1)
        if (existingUID != -1) {
            Log.d(TAG, "UID from storage: $existingUID for $key")
            uidCache[key] = existingUID
            return existingUID
        }
        
        // 3. 새 UID 생성
        val newUID = generateNewUID(prefs, userType)
        
        // 4. 저장
        prefs.edit().putInt(key, newUID).apply()
        uidCache[key] = newUID
        
        Log.i(TAG, "New UID generated: $newUID for $key")
        return newUID
    }
    
    /**
     * 특정 사용자의 저장된 UID 가져오기 (생성하지 않음)
     * @return UID 또는 null (없는 경우)
     */
    fun getExistingUID(context: Context, userType: String, userId: String): Int? {
        val key = "${userType}_${userId}"
        
        // 캐시 확인
        uidCache[key]?.let { return it }
        
        // Storage 확인
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getInt(key, -1)
        return if (uid != -1) {
            uidCache[key] = uid
            uid
        } else {
            null
        }
    }
    
    /**
     * 새로운 UID 생성 (중복 방지)
     */
    private fun generateNewUID(prefs: SharedPreferences, userType: String): Int {
        val (minRange, maxRange) = when (userType) {
            "call_manager" -> CALL_MANAGER_BASE to CALL_MANAGER_MAX
            "pickup_driver" -> PICKUP_APP_BASE to PICKUP_APP_MAX
            else -> RESERVED_BASE to RESERVED_MAX
        }
        
        // 기존에 사용 중인 모든 UID 수집
        val usedUIDs = getAllUsedUIDs(prefs)
        
        // 범위 내에서 사용 가능한 UID 찾기
        for (uid in minRange..maxRange) {
            if (!usedUIDs.contains(uid)) {
                return uid
            }
        }
        
        // 모든 UID가 사용 중인 경우 (거의 불가능)
        throw IllegalStateException("No available UID in range $minRange-$maxRange")
    }
    
    /**
     * SharedPreferences에 저장된 모든 UID 가져오기
     */
    private fun getAllUsedUIDs(prefs: SharedPreferences): Set<Int> {
        return prefs.all.values
            .filterIsInstance<Int>()
            .filter { it in CALL_MANAGER_BASE..RESERVED_MAX }
            .toSet()
    }
    
    /**
     * UID 유효성 검증
     * @param uid 검증할 UID
     * @return 유효한 경우 true
     */
    fun validateUID(uid: Int): Boolean {
        return uid in CALL_MANAGER_BASE..RESERVED_MAX
    }
    
    /**
     * UID가 Call Manager 범위인지 확인
     */
    fun isCallManagerUID(uid: Int): Boolean {
        return uid in CALL_MANAGER_BASE..CALL_MANAGER_MAX
    }
    
    /**
     * UID가 Pickup App 범위인지 확인
     */
    fun isPickupAppUID(uid: Int): Boolean {
        return uid in PICKUP_APP_BASE..PICKUP_APP_MAX
    }
    
    /**
     * UID로부터 사용자 타입 추론
     */
    fun getUserTypeFromUID(uid: Int): String? {
        return when (uid) {
            in CALL_MANAGER_BASE..CALL_MANAGER_MAX -> "call_manager"
            in PICKUP_APP_BASE..PICKUP_APP_MAX -> "pickup_driver"
            in RESERVED_BASE..RESERVED_MAX -> "reserved"
            else -> null
        }
    }
    
    /**
     * 특정 사용자의 UID 삭제
     */
    fun removeUID(context: Context, userType: String, userId: String) {
        val key = "${userType}_${userId}"
        
        // 캐시에서 제거
        uidCache.remove(key)
        
        // Storage에서 제거
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()
        
        Log.d(TAG, "UID removed for $key")
    }
    
    /**
     * 모든 UID 캐시 클리어 (메모리만)
     */
    fun clearCache() {
        uidCache.clear()
        Log.d(TAG, "UID cache cleared")
    }
    
    /**
     * 디버그용: 현재 저장된 모든 UID 출력
     */
    fun debugPrintAllUIDs(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "=== All Stored UIDs ===")
        prefs.all.forEach { (key, value) ->
            if (value is Int) {
                Log.d(TAG, "$key -> $value (${getUserTypeFromUID(value)})")
            }
        }
        Log.d(TAG, "=== Cache UIDs ===")
        uidCache.forEach { (key, uid) ->
            Log.d(TAG, "$key -> $uid (cached)")
        }
    }
}