/**
 * 배너 광고 초기 데이터 세팅 스크립트
 * 
 * 사용법:
 * node setup_banner_ads.js <regionId> <officeId>
 * 
 * 예시:
 * node setup_banner_ads.js seoul Hongchon
 */

const admin = require('firebase-admin');

// Firebase Admin 초기화
try {
    const serviceAccount = require('./service-account-key.json');
    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
} catch (e) {
    console.error('Firebase Admin already initialized or service account not found');
}

const db = admin.firestore();

// 커맨드 라인 인자
const regionId = process.argv[2] || 'seoul';
const officeId = process.argv[3];

if (!officeId) {
    console.error('사용법: node setup_banner_ads.js <regionId> <officeId>');
    console.error('예시: node setup_banner_ads.js seoul Hongchon');
    process.exit(1);
}

/**
 * 초기 배너 광고 데이터
 */
const initialBanner = {
    id: 'banner_default_001',
    text: '스마트 대리운전 통합 시스템',
    imageUrl: '',
    linkUrl: '',
    backgroundColor1: '#FF6B35',
    backgroundColor2: '#F7931E',
    textColor: '#FFFFFF',
    isActive: true,
    priority: 1,
    createdAt: admin.firestore.Timestamp.now(),
    updatedAt: admin.firestore.Timestamp.now()
};

/**
 * 배너 광고 데이터를 Firestore에 추가
 */
async function setupBannerAds() {
    try {
        console.log('\n=== 배너 광고 초기화 ===\n');
        console.log('Region:', regionId);
        console.log('Office:', officeId);
        console.log('');

        // Firestore 경로
        const bannerRef = db
            .collection('regions').doc(regionId)
            .collection('offices').doc(officeId)
            .collection('bannerAds').doc(initialBanner.id);

        // 기존 배너 확인
        const existing = await bannerRef.get();
        if (existing.exists) {
            console.log('배너가 이미 존재합니다. 업데이트합니다...');
        }

        // 배너 데이터 저장
        await bannerRef.set(initialBanner, { merge: true });

        console.log('배너 광고 생성 완료!\n');
        console.log('생성된 배너 정보:');
        console.log('  ID:', initialBanner.id);
        console.log('  텍스트:', initialBanner.text);
        console.log('  활성화:', initialBanner.isActive);
        console.log('  우선순위:', initialBanner.priority);
        console.log('\n디자인 정보:');
        console.log('  배경 그라디언트:', initialBanner.backgroundColor1, '→', initialBanner.backgroundColor2);
        console.log('  텍스트 색상:', initialBanner.textColor);
        console.log('');

    } catch (error) {
        console.error('배너 광고 생성 중 오류:', error);
        process.exit(1);
    }
}

// 스크립트 실행
setupBannerAds()
    .then(() => {
        console.log('완료!');
        process.exit(0);
    })
    .catch((error) => {
        console.error('오류:', error);
        process.exit(1);
    });
