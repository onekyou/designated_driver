/**
 * seed-gazetteer.js — PTT 전사 지명 교정용 사전(settings/gazetteer)을 1회 시드한다.
 *
 * 용도: GazetteerCorrector(call_manager·pickup)가 읽는 offices/{o}/settings/gazetteer 의
 *       places 배열을 양평 행정지명으로 초기 시드. 이후 사장은 운영하며 추가만(매번 수동 X —
 *       변형은 클라 편집거리가 자동 흡수). firestore.rules 의 settings/gazetteer read 완화가
 *       먼저 deploy 되어 있어야 픽업기사도 읽을 수 있다.
 *
 * 사용법:
 *   cd functions
 *   node scripts/seed-gazetteer.js [officeId] [provinceId] [cityId]
 *   (기본 = 1004 테스트 사무실 RUbeBEvGGYP5wMhJHhMF / gyeonggi / yangpyeong)
 *
 * 멱등성: set(merge:true) — places 만 갱신, 문서 내 다른 필드 보존. 재실행 안전.
 *
 * 클라 UX: 앱이 sendPttText 첫 호출 시 1회 로드·캐시 → 다음 세션부터 반영.
 */

const admin = require('firebase-admin');

const PROJECT_ID = 'calldetector-5d61e';

// 양평군 행정지명 1회 시드. "양평"·"용문" 단독은 모호(짧고 흔함)라 제외하고 구체 지명만.
const YANGPYEONG_PLACES = [
  // 1읍 11면
  '양평읍', '강상면', '강하면', '양서면', '서종면', '옥천면',
  '단월면', '청운면', '양동면', '지평면', '용문면', '개군면',
  // 주요 역·마을
  '양평역', '용문역', '양수리', '아신리', '국수역', '신원역',
  '오빈역', '원덕역', '지평역', '양동역',
];

async function main() {
  const officeId = process.argv[2] || 'RUbeBEvGGYP5wMhJHhMF';
  const provinceId = process.argv[3] || 'gyeonggi';
  const cityId = process.argv[4] || 'yangpyeong';
  const docPath = `provinces/${provinceId}/cities/${cityId}/offices/${officeId}/settings/gazetteer`;

  if (!admin.apps.length) {
    admin.initializeApp({ projectId: PROJECT_ID });
  }

  const payload = {
    places: YANGPYEONG_PLACES,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  try {
    const ref = admin.firestore().doc(docPath);
    const before = await ref.get();
    if (before.exists) {
      const b = before.data() || {};
      console.log(`[before] places ${Array.isArray(b.places) ? b.places.length : 0}개`);
    } else {
      console.log('[before] (문서 없음, 신규 생성)');
    }

    await ref.set(payload, { merge: true });

    console.log(`[after]  places ${payload.places.length}개: ${payload.places.join(', ')}`);
    console.log(`✅ ${docPath} 시드 완료.`);
  } catch (err) {
    console.error('❌ 오류:', err.code || '', err.message || err);
    process.exit(2);
  }
}

main();
