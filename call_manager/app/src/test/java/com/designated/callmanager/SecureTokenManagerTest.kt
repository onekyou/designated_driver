package com.designated.callmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.designated.callmanager.service.SecureTokenManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Phase 1 검증: 보안 강화된 토큰 저장 시스템 테스트
 */
@RunWith(AndroidJUnit4::class)
class SecureTokenManagerTest {
    
    private lateinit var context: Context
    private lateinit var tokenManager: SecureTokenManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tokenManager = SecureTokenManager(context)
        
        // 기존 토큰 정리
        tokenManager.clearAllTokens()
    }
    
    @Test
    fun test_토큰_암호화_저장_및_조회() {
        // Given: 테스트용 토큰 생성
        val testToken = SecureTokenManager.SecureToken(
            token = "test_token_12345",
            channelName = "test_channel",
            generatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24시간 후
            regionId = "test_region",
            officeId = "test_office",
            userType = "call_manager"
        )
        
        // When: 토큰 저장
        tokenManager.saveToken(testToken)
        
        // Then: 토큰 조회 및 검증
        val retrievedToken = tokenManager.getToken("test_region", "test_office", "call_manager")
        assertNotNull("토큰이 조회되어야 함", retrievedToken)
        assertEquals("토큰 값이 일치해야 함", testToken.token, retrievedToken?.token)
        assertEquals("채널명이 일치해야 함", testToken.channelName, retrievedToken?.channelName)
        assertTrue("토큰이 유효해야 함", retrievedToken?.isValid() == true)
    }
    
    @Test
    fun test_만료된_토큰_자동_삭제() {
        // Given: 만료된 토큰
        val expiredToken = SecureTokenManager.SecureToken(
            token = "expired_token",
            channelName = "expired_channel",
            generatedAt = System.currentTimeMillis() - (25 * 60 * 60 * 1000), // 25시간 전
            expiresAt = System.currentTimeMillis() - (60 * 60 * 1000), // 1시간 전 만료
            regionId = "test_region",
            officeId = "test_office",
            userType = "call_manager"
        )
        
        // When: 만료된 토큰 저장 후 조회
        tokenManager.saveToken(expiredToken)
        val retrievedToken = tokenManager.getToken("test_region", "test_office", "call_manager")
        
        // Then: 만료된 토큰은 null 반환
        assertNull("만료된 토큰은 조회되지 않아야 함", retrievedToken)
    }
    
    @Test
    fun test_토큰_갱신_필요_여부_확인() {
        // Given: 23시간 30분 후 만료되는 토큰 (30분 버퍼)
        val soonExpiringToken = SecureTokenManager.SecureToken(
            token = "soon_expiring_token",
            channelName = "test_channel",
            generatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (23.5 * 60 * 60 * 1000).toLong(),
            regionId = "test_region",
            officeId = "test_office",
            userType = "call_manager"
        )
        
        // When: 토큰 저장
        tokenManager.saveToken(soonExpiringToken)
        
        // Then: 갱신 필요
        assertTrue(
            "만료 임박 토큰은 갱신이 필요해야 함",
            tokenManager.needsRefresh("test_region", "test_office", "call_manager")
        )
    }
    
    @Test
    fun test_토큰_무효화() {
        // Given: 정상 토큰 저장
        val validToken = SecureTokenManager.SecureToken(
            token = "valid_token",
            channelName = "test_channel",
            generatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000),
            regionId = "test_region",
            officeId = "test_office",
            userType = "call_manager"
        )
        tokenManager.saveToken(validToken)
        
        // When: 토큰 무효화
        tokenManager.invalidateToken("test_region", "test_office", "call_manager")
        
        // Then: 토큰 조회 시 null
        val retrievedToken = tokenManager.getToken("test_region", "test_office", "call_manager")
        assertNull("무효화된 토큰은 조회되지 않아야 함", retrievedToken)
    }
    
    @Test
    fun test_다중_사용자_토큰_격리() {
        // Given: 서로 다른 사용자의 토큰
        val managerToken = SecureTokenManager.SecureToken(
            token = "manager_token",
            channelName = "manager_channel",
            generatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000),
            regionId = "region1",
            officeId = "office1",
            userType = "call_manager"
        )
        
        val driverToken = SecureTokenManager.SecureToken(
            token = "driver_token",
            channelName = "driver_channel",
            generatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000),
            regionId = "region1",
            officeId = "office1",
            userType = "driver"
        )
        
        // When: 두 토큰 모두 저장
        tokenManager.saveToken(managerToken)
        tokenManager.saveToken(driverToken)
        
        // Then: 각자의 토큰만 조회
        val retrievedManager = tokenManager.getToken("region1", "office1", "call_manager")
        val retrievedDriver = tokenManager.getToken("region1", "office1", "driver")
        
        assertEquals("매니저 토큰이 정확히 조회되어야 함", "manager_token", retrievedManager?.token)
        assertEquals("드라이버 토큰이 정확히 조회되어야 함", "driver_token", retrievedDriver?.token)
        assertNotEquals("서로 다른 토큰이어야 함", retrievedManager?.token, retrievedDriver?.token)
    }
}