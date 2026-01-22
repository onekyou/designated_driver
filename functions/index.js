const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

// TypeScript로 작성된 새 함수들 import
const tsIndexModule = require('./lib/index.js');

// TypeScript 함수들 export
exports.oncallassigned = tsIndexModule.oncallassigned;
exports.onSharedCallCreated = tsIndexModule.onSharedCallCreated;
exports.onSharedCallClaimed = tsIndexModule.onSharedCallClaimed;
exports.onSharedCallCancelledByDriver = tsIndexModule.onSharedCallCancelledByDriver;
exports.sendNewCallNotification = tsIndexModule.sendNewCallNotification;
exports.onCallStatusChanged = tsIndexModule.onCallStatusChanged;
exports.onSharedCallStatusSync = tsIndexModule.onSharedCallStatusSync;
exports.onSharedCallCompleted = tsIndexModule.onSharedCallCompleted;
exports.finalizeWorkDay = tsIndexModule.finalizeWorkDay;

// Attribution 시스템 함수들 export
exports.matchAttribution = tsIndexModule.matchAttribution;
exports.saveManualAttribution = tsIndexModule.saveManualAttribution;

// Call Detector 종료 알림 전송
exports.sendCallDetectorTerminationNotification = functions.firestore
    .document('notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        const data = snap.data();
        
        // CALL_DETECTOR_TERMINATED 타입만 처리
        if (data.type !== 'CALL_DETECTOR_TERMINATED') {
            return null;
        }
        
        // 과거 알림 방지를 위한 매우 엄격한 시간 체크
        const THIRTY_SECONDS = 30 * 1000; // 30초로 대폭 단축
        const now = Date.now();
        const alertTime = data.timestamp?.toMillis ? data.timestamp.toMillis() : (data.timestamp || now);
        const ageInSeconds = Math.round((now - alertTime) / 1000);
        
        if (now - alertTime > THIRTY_SECONDS) {
            console.log(`⏰ Old notification detected (${ageInSeconds}s old), deleting without processing...`);
            await snap.ref.delete();
            return null;
        }
        
        console.log(`⏱️ Fresh notification (${ageInSeconds}s old), processing...`);
        
        // provinceId/cityId 우선, regionId는 하위 호환용
        const provinceId = data.provinceId || data.regionId;
        const cityId = data.cityId || '';
        const officeId = data.officeId;
        const deviceName = data.deviceName;
        const message = data.message || `콜 디텍터 [${deviceName}]가 종료되었습니다.`;

        // 간소화: 30초 시간 체크만으로 충분함 (위에서 이미 체크됨)

        try {
            // 해당 사무실의 모든 관리자 FCM 토큰 가져오기
            let adminsQuery = admin.firestore()
                .collection('admins')
                .where('associatedProvinceId', '==', provinceId)
                .where('associatedOfficeId', '==', officeId);

            // cityId가 있으면 추가 필터링
            if (cityId) {
                adminsQuery = adminsQuery.where('associatedCityId', '==', cityId);
            }

            const adminsSnapshot = await adminsQuery.get();
            
            const tokens = [];
            adminsSnapshot.forEach(doc => {
                const adminData = doc.data();
                if (adminData.fcmToken) {
                    tokens.push(adminData.fcmToken);
                }
            });
            
            if (tokens.length === 0) {
                console.log('No FCM tokens found for notification');
                return null;
            }
            
            // FCM 메시지 구성
            const fcmMessage = {
                notification: {
                    title: '⚠️ 콜 디텍터 종료',
                    body: message
                },
                data: {
                    type: 'CALL_DETECTOR_TERMINATED',
                    deviceName: deviceName,
                    provinceId: provinceId,
                    cityId: cityId,
                    officeId: officeId,
                    timestamp: Date.now().toString()
                },
                tokens: tokens
            };
            
            // FCM 전송
            const response = await admin.messaging().sendMulticast(fcmMessage);
            console.log(`Successfully sent ${response.successCount} messages`);
            
            // 실패한 토큰 처리
            if (response.failureCount > 0) {
                const failedTokens = [];
                response.responses.forEach((resp, idx) => {
                    if (!resp.success) {
                        failedTokens.push(tokens[idx]);
                    }
                });
                console.log('Failed tokens:', failedTokens);
            }
            
            console.log(`✅ Notification processed for device ${deviceName}`);
            
            // 처리 완료 후 알림 문서 삭제
            await snap.ref.delete();
            
            return null;
        } catch (error) {
            console.error('Error sending FCM notification:', error);
            return null;
        }
    });

// 테스트용 함수 - 강제종료 알림 테스트
exports.testTerminationNotification = functions.https.onRequest(async (req, res) => {
    try {
        const { provinceId = 'test-province', cityId = 'test-city', officeId = 'test-office', deviceName = 'TEST-DEVICE', minutesOld = 0 } = req.query;

        // 테스트 시간 계산 (현재 시간에서 지정된 분만큼 이전)
        const testTime = admin.firestore.Timestamp.fromMillis(Date.now() - (minutesOld * 60 * 1000));

        // 테스트 알림 문서 생성
        const notificationData = {
            type: 'CALL_DETECTOR_TERMINATED',
            deviceName: deviceName,
            provinceId: provinceId,
            cityId: cityId,
            officeId: officeId,
            timestamp: testTime,
            message: `테스트: 콜 디텍터 [${deviceName}]가 종료되었습니다. (${minutesOld}분 전 알림)`
        };
        
        const docRef = await admin.firestore().collection('notifications').add(notificationData);
        
        res.json({
            success: true,
            message: `테스트 알림이 생성되었습니다.`,
            documentId: docRef.id,
            testTime: testTime.toDate(),
            minutesOld: minutesOld,
            note: minutesOld > 5 ? '5분 이상 된 알림이므로 자동 삭제됩니다.' : '최신 알림이므로 FCM이 전송됩니다.'
        });
        
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Crashlytics 모니터링 함수들 추가
const crashlyticsMonitor = require('./crashlytics-monitor');

// emergency_alerts 컬렉션 전체 삭제 함수
exports.deleteEmergencyAlerts = functions.https.onRequest(async (req, res) => {
    try {
        const db = admin.firestore();
        
        console.log('🗑️ emergency_alerts 컬렉션 삭제 시작...');
        
        // emergency_alerts 컬렉션의 모든 문서 가져오기
        const emergencyAlertsSnapshot = await db.collection('emergency_alerts').get();
        
        if (emergencyAlertsSnapshot.empty) {
            console.log('📭 삭제할 emergency_alerts 문서가 없습니다.');
            return res.json({ 
                success: true, 
                deleted: 0, 
                message: 'No emergency_alerts documents found' 
            });
        }
        
        console.log(`📄 ${emergencyAlertsSnapshot.size}개의 emergency_alerts 문서를 발견했습니다.`);
        
        // 배치로 삭제 (최대 500개씩)
        const batch = db.batch();
        let deleteCount = 0;
        
        emergencyAlertsSnapshot.docs.forEach((doc) => {
            batch.delete(doc.ref);
            deleteCount++;
        });
        
        // 배치 실행
        await batch.commit();
        
        console.log(`✅ ${deleteCount}개의 emergency_alerts 문서를 성공적으로 삭제했습니다.`);
        
        res.json({ 
            success: true, 
            deleted: deleteCount, 
            message: `Successfully deleted ${deleteCount} emergency_alerts documents` 
        });
        
    } catch (error) {
        console.error('❌ emergency_alerts 삭제 중 오류:', error);
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

// 오래된 notifications 정리 함수
exports.cleanupOldNotifications = functions.https.onRequest(async (req, res) => {
    try {
        const db = admin.firestore();
        const cutoffTime = Date.now() - (10 * 60 * 1000); // 10분 이전
        
        // 오래된 notifications 문서들 조회
        const oldNotifications = await db.collection('notifications')
            .where('timestamp', '<', admin.firestore.Timestamp.fromMillis(cutoffTime))
            .get();
        
        if (oldNotifications.empty) {
            console.log('🧹 No old notifications to clean up');
            return res.json({ 
                success: true, 
                cleaned: 0, 
                message: 'No old notifications found' 
            });
        }
        
        // 배치로 삭제
        const batch = db.batch();
        let count = 0;
        
        oldNotifications.docs.forEach(doc => {
            batch.delete(doc.ref);
            count++;
        });
        
        await batch.commit();
        
        console.log(`🧹 Cleaned up ${count} old notifications`);
        res.json({ 
            success: true, 
            cleaned: count, 
            message: `Successfully cleaned up ${count} old notifications` 
        });
        
    } catch (error) {
        console.error('❌ Cleanup error:', error);
        res.status(500).json({ 
            success: false, 
            error: error.message 
        });
    }
});

// Device Status 기반 크래시 감지
exports.onDeviceStatusCrash = crashlyticsMonitor.onDeviceStatusCrash;

// 수동 Crashlytics 체크 (테스트용)
exports.checkCrashlyticsIssues = crashlyticsMonitor.checkCrashlyticsIssues;

// device_alerts 컬렉션에 새 문서 생성 시 관리자에게 알림
exports.onDeviceAlert = functions.firestore
    .document('device_alerts/{alertId}')
    .onCreate(async (snap, context) => {
        try {
            const alertData = snap.data();
            
            // 크래시 알림만 처리 (다른 타입 무시)
            if (alertData.type !== 'CRASH' && alertData.type !== 'CONNECTION_TEST') {
                return null;
            }
            
            console.log(`📢 새 device_alert 감지: ${context.params.alertId}`);
            console.log('Alert data:', alertData);
            
            // 크래시 알림인 경우에만 FCM 전송
            if (alertData.type === 'CRASH' && alertData.requiresImmediateAction) {
                // provinceId/cityId 우선, regionId는 하위 호환용
                const provinceId = alertData.provinceId || alertData.regionId || 'unknown';
                const cityId = alertData.cityId || '';
                const officeId = alertData.officeId || 'unknown';
                const deviceId = alertData.deviceId || 'unknown';

                // 해당 지역/사무실의 관리자들 찾기
                let adminsQuery = admin.firestore()
                    .collection('admins')
                    .where('associatedProvinceId', '==', provinceId)
                    .where('associatedOfficeId', '==', officeId);

                // cityId가 있으면 추가 필터링
                if (cityId) {
                    adminsQuery = adminsQuery.where('associatedCityId', '==', cityId);
                }

                const adminsSnapshot = await adminsQuery.get();
                
                const tokens = [];
                adminsSnapshot.forEach(doc => {
                    const adminData = doc.data();
                    if (adminData.fcmToken) {
                        tokens.push(adminData.fcmToken);
                    }
                });
                
                if (tokens.length > 0) {
                    const fcmMessage = {
                        notification: {
                            title: '🚨 콜디텍터 크래시 감지',
                            body: `${deviceId} 기기에서 크래시가 발생했습니다.`
                        },
                        data: {
                            type: 'DEVICE_CRASH_ALERT',
                            deviceId: deviceId,
                            provinceId: provinceId,
                            cityId: cityId,
                            officeId: officeId,
                            alertId: context.params.alertId,
                            timestamp: Date.now().toString()
                        },
                        tokens: tokens
                    };
                    
                    const response = await admin.messaging().sendMulticast(fcmMessage);
                    console.log(`📨 FCM 크래시 알림 전송: 성공 ${response.successCount}, 실패 ${response.failureCount}`);
                }
            }
            
            return null;
            
        } catch (error) {
            console.error('❌ device_alerts 처리 중 오류:', error);
            return null;
        }
    });