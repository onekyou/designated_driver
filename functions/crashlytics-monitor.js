const functions = require('firebase-functions');
const admin = require('firebase-admin');

/**
 * device_status 컬렉션 모니터링을 통한 크래시 감지 시스템
 * 
 * Crashlytics 직접 트리거 대신, 앱에서 device_status 컬렉션에 
 * 크래시 정보를 기록하면 이를 감지하여 처리하는 방식
 */

// device_status 컬렉션의 크래시 상태 변경 감지 (중복 처리 방지)
exports.onDeviceStatusCrash = functions.firestore
    .document('device_status/{deviceId}')
    .onWrite(async (change, context) => {
        try {
            const deviceId = context.params.deviceId;
            
            // 문서가 생성되거나 업데이트된 경우만 처리
            if (!change.after.exists) {
                return null;
            }
            
            const statusData = change.after.data();
            console.log(`📱 Device Status 변경 감지: ${deviceId}`, statusData);
            
            // 크래시 상태인지 확인
            if (statusData.status !== 'SERVICE_CRASHED' && statusData.type !== 'CRASH') {
                return null; // 크래시가 아니면 무시
            }
            
            // 🔥 중복 처리 방지: _async, _retry가 포함된 deviceId는 무시
            if (deviceId.includes('_async') || deviceId.includes('_retry')) {
                console.log(`⏭️ 중복 처리 방지: ${deviceId} 무시`);
                return null;
            }
            
            // 🔥 추가 중복 방지: 같은 crashTime의 emergency_alerts가 이미 있는지 확인
            const crashTime = statusData.crashTime;
            if (crashTime) {
                const existingAlerts = await admin.firestore()
                    .collection('emergency_alerts')
                    .where('deviceId', '==', deviceId)
                    .where('crashTime', '==', crashTime)
                    .limit(1)
                    .get();
                
                if (!existingAlerts.empty) {
                    console.log(`⏭️ 이미 처리된 크래시: deviceId=${deviceId}, crashTime=${crashTime}`);
                    return null;
                }
            }
            
            console.log('🚨 콜디텍터 크래시 감지됨!');
            
            // device_alerts 문서 생성
            const finalCrashTime = crashTime || Date.now();
            const deviceAlert = {
                // 디바이스 정보
                deviceId: deviceId,
                regionId: statusData.regionId || 'unknown_region',
                officeId: statusData.officeId || 'unknown_office',
                
                // 크래시 정보
                type: 'CRASH',
                status: 'SERVICE_CRASHED',
                priority: 'HIGH',
                
                // 타임스탬프
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                crashTime: finalCrashTime,
                
                // 크래시 세부 정보
                crashInfo: {
                    message: statusData.message || 'Service crashed unexpectedly',
                    className: statusData.className || 'Unknown',
                    stackTrace: statusData.stackTrace || ['Stack trace not available']
                },
                
                // 디바이스 정보
                deviceInfo: {
                    model: statusData.deviceModel || 'unknown',
                    manufacturer: statusData.deviceManufacturer || 'unknown',
                    androidVersion: statusData.androidVersion || 0,
                    appVersion: statusData.appVersion || 'unknown'
                },
                
                // 메타데이터
                source: 'DEVICE_STATUS_MONITOR',
                processedAt: admin.firestore.FieldValue.serverTimestamp(),
                
                // 알림 플래그
                requiresImmediateAction: true,
                emergencyNotificationNeeded: true
            };
            
            // 문서 ID 생성
            const documentId = `status_${deviceId}_${finalCrashTime}`;
            
            // device_alerts에 저장
            await admin.firestore()
                .collection('device_alerts')
                .doc(documentId)
                .set(deviceAlert);
            
            console.log(`✅ device_alerts에 저장 완료: ${documentId}`);
            
            // emergency_alerts도 생성 (긴급 알림용)
            const emergencyAlert = {
                deviceId: deviceId,
                regionId: deviceAlert.regionId,
                officeId: deviceAlert.officeId,
                type: 'EMERGENCY_CRASH_ALERT',
                priority: 'CRITICAL',
                timestamp: admin.firestore.FieldValue.serverTimestamp(),
                crashTime: finalCrashTime,
                message: `⚠️ 콜디텍터 [${deviceId}] 강제종료 발생!`,
                crashInfo: deviceAlert.crashInfo,
                deviceInfo: deviceAlert.deviceInfo,
                requiresImmediateAction: true,
                source: 'AUTO_GENERATED_FROM_STATUS'
            };
            
            const emergencyDocId = `emergency_status_${deviceId}_${finalCrashTime}`;
            
            await admin.firestore()
                .collection('emergency_alerts')
                .doc(emergencyDocId)
                .set(emergencyAlert);
            
            console.log(`🚨 emergency_alerts에 저장 완료: ${emergencyDocId}`);
            
            // 관리자들에게 FCM 알림 전송
            await sendEmergencyNotificationToAdmins(emergencyAlert);
            
            return null;
            
        } catch (error) {
            console.error('❌ Device Status 처리 중 오류:', error);
            return null;
        }
});

/**
 * 관리자들에게 긴급 알림 전송
 */
async function sendEmergencyNotificationToAdmins(emergencyAlert) {
    try {
        // 해당 지역/사무실의 관리자들 찾기
        const adminsSnapshot = await admin.firestore()
            .collection('admins')
            .where('associatedRegionId', '==', emergencyAlert.regionId)
            .where('associatedOfficeId', '==', emergencyAlert.officeId)
            .get();
        
        if (adminsSnapshot.empty) {
            console.log('해당 지역/사무실의 관리자가 없음');
            return;
        }
        
        const fcmTokens = [];
        adminsSnapshot.forEach(doc => {
            const adminData = doc.data();
            if (adminData.fcmToken) {
                fcmTokens.push(adminData.fcmToken);
            }
        });
        
        if (fcmTokens.length === 0) {
            console.log('FCM 토큰이 있는 관리자가 없음');
            return;
        }
        
        // FCM 메시지 생성
        const message = {
            notification: {
                title: '🚨 콜디텍터 크래시 알림',
                body: `${emergencyAlert.deviceId} 기기에서 강제종료가 발생했습니다.`
            },
            data: {
                type: 'CRASH_ALERT',
                deviceId: emergencyAlert.deviceId,
                regionId: emergencyAlert.regionId,
                officeId: emergencyAlert.officeId,
                crashTime: emergencyAlert.crashTime.toString(),
                priority: 'CRITICAL'
            },
            tokens: fcmTokens
        };
        
        // 멀티캐스트 전송
        const response = await admin.messaging().sendMulticast(message);
        
        console.log(`📨 FCM 알림 전송 완료: 성공 ${response.successCount}, 실패 ${response.failureCount}`);
        
        // 실패한 토큰들 정리
        if (response.failureCount > 0) {
            const failedTokens = [];
            response.responses.forEach((resp, idx) => {
                if (!resp.success) {
                    failedTokens.push(fcmTokens[idx]);
                    console.log(`FCM 전송 실패: ${resp.error}`);
                }
            });
            
            // 무효한 토큰들을 admin 문서에서 제거
            for (const token of failedTokens) {
                const adminQuery = await admin.firestore()
                    .collection('admins')
                    .where('fcmToken', '==', token)
                    .get();
                
                adminQuery.forEach(doc => {
                    doc.ref.update({ fcmToken: admin.firestore.FieldValue.delete() });
                });
            }
        }
        
    } catch (error) {
        console.error('❌ 관리자 알림 전송 실패:', error);
    }
}

/**
 * 수동으로 Crashlytics 이슈를 체크하는 함수 (테스트용)
 * 
 * Firebase 콘솔이나 REST API를 통해 호출 가능
 */
exports.checkCrashlyticsIssues = functions.https.onRequest(async (req, res) => {
    try {
        console.log('🔍 수동 Crashlytics 이슈 체크 시작');
        
        // 여기서는 Firestore의 device_alerts를 확인하여 
        // 누락된 크래시가 있는지 체크하는 로직을 구현할 수 있습니다
        
        const recentAlertsSnapshot = await admin.firestore()
            .collection('device_alerts')
            .where('type', '==', 'CRASH')
            .orderBy('timestamp', 'desc')
            .limit(10)
            .get();
        
        const alerts = [];
        recentAlertsSnapshot.forEach(doc => {
            alerts.push({
                id: doc.id,
                ...doc.data(),
                timestamp: doc.data().timestamp?.toDate()
            });
        });
        
        console.log(`📊 최근 크래시 알림 ${alerts.length}개 발견`);
        
        res.json({
            success: true,
            recentCrashAlerts: alerts,
            message: `최근 크래시 알림 ${alerts.length}개를 확인했습니다.`
        });
        
    } catch (error) {
        console.error('❌ 수동 체크 중 오류:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});