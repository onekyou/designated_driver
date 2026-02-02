package com.designated.driverapp.service

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Firebase Realtime Database를 사용한 Presence (온라인 상태) 관리
 *
 * 기능:
 * - 앱 실행 시 "online" 상태로 설정
 * - 앱 백그라운드 시 "background" 상태로 설정
 * - 연결 끊김 시 자동으로 "offline" 상태로 변경 (onDisconnect)
 */
object PresenceManager {

    private const val TAG = "PresenceManager"
    private const val PRESENCE_PATH = "presence/drivers"

    private val database = FirebaseDatabase.getInstance()
    private var presenceRef: com.google.firebase.database.DatabaseReference? = null
    private var connectedRef: com.google.firebase.database.DatabaseReference? = null
    private var connectionListener: ValueEventListener? = null

    enum class Status(val value: String) {
        ONLINE("online"),
        BACKGROUND("background"),
        OFFLINE("offline")
    }

    /**
     * Presence 시스템 초기화
     * 앱 시작 시 호출
     */
    fun initialize(context: Context) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "사용자 로그인 안됨 - Presence 초기화 스킵")
            return
        }

        presenceRef = database.getReference("$PRESENCE_PATH/$userId")
        connectedRef = database.getReference(".info/connected")

        // 연결 상태 리스너
        connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false

                if (connected) {
                    Log.d(TAG, "Realtime DB 연결됨 - Presence 설정")

                    // 연결 끊김 시 자동으로 offline 설정
                    presenceRef?.onDisconnect()?.setValue(
                        mapOf(
                            "status" to Status.OFFLINE.value,
                            "lastSeen" to ServerValue.TIMESTAMP
                        )
                    )

                    // 현재 상태를 online으로 설정
                    setStatus(Status.ONLINE)
                } else {
                    Log.d(TAG, "Realtime DB 연결 끊김")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Presence 연결 에러: ${error.message}")
            }
        }

        connectedRef?.addValueEventListener(connectionListener!!)
        Log.d(TAG, "Presence 초기화 완료: userId=$userId")
    }

    /**
     * 상태 업데이트
     */
    fun setStatus(status: Status) {
        presenceRef?.setValue(
            mapOf(
                "status" to status.value,
                "lastSeen" to ServerValue.TIMESTAMP
            )
        )?.addOnSuccessListener {
            Log.d(TAG, "상태 업데이트: ${status.value}")
        }?.addOnFailureListener { e ->
            Log.e(TAG, "상태 업데이트 실패", e)
        }
    }

    /**
     * 앱이 포그라운드로 전환 시 호출
     */
    fun onAppForeground() {
        setStatus(Status.ONLINE)
    }

    /**
     * 앱이 백그라운드로 전환 시 호출
     */
    fun onAppBackground() {
        setStatus(Status.BACKGROUND)
    }

    /**
     * 로그아웃 시 호출
     */
    fun onLogout() {
        setStatus(Status.OFFLINE)
        cleanup()
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        connectionListener?.let {
            connectedRef?.removeEventListener(it)
        }
        connectionListener = null
        presenceRef = null
        connectedRef = null
        Log.d(TAG, "Presence 정리 완료")
    }

    /**
     * 로그인 후 다시 초기화
     */
    fun reinitialize(context: Context) {
        cleanup()
        initialize(context)
    }
}
