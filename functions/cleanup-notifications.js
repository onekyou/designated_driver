const admin = require('firebase-admin');

// Firebase Admin 초기화
admin.initializeApp();

async function deleteOldNotifications() {
    try {
        console.log('🧹 오래된 알림 문서들을 정리합니다...');
        
        const db = admin.firestore();
        const notificationsRef = db.collection('notifications');
        
        // 모든 notifications 문서 가져오기
        const snapshot = await notificationsRef.get();
        
        if (snapshot.empty) {
            console.log('📭 삭제할 알림 문서가 없습니다.');
            return;
        }
        
        console.log(`📄 ${snapshot.size}개의 알림 문서를 발견했습니다.`);
        
        // 배치로 삭제 (최대 500개씩)
        const batch = db.batch();
        let deleteCount = 0;
        
        snapshot.docs.forEach((doc) => {
            batch.delete(doc.ref);
            deleteCount++;
        });
        
        // 배치 실행
        await batch.commit();
        
        console.log(`✅ ${deleteCount}개의 알림 문서를 성공적으로 삭제했습니다.`);
        console.log('🎉 정리 완료!');
        
    } catch (error) {
        console.error('❌ 정리 중 오류 발생:', error);
    }
    
    // 프로세스 종료
    process.exit(0);
}

deleteOldNotifications();