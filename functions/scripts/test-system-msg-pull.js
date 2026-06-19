/**
 * test-system-msg-pull.js — 블랙박스 system 메시지 푸시→풀 전환 검증용 임시 스크립트.
 *
 * postSystemMessage 와 동형의 system 메시지를 사무실 채팅에 1건 write → onChatMessageCreated 발화.
 * 기대: functions 로그 "[onChatMessageCreated] system(블랙박스) — 팬아웃 skip" + FCM 0.
 *       S21 logcat 에 system 메시지 FCM INSERT 없음. 채팅 시트 열면 syncSince 가 당겨와 표시.
 *
 *   node scripts/test-system-msg-pull.js
 *
 * 대상 office = gyeonggi/yangpyeong/RUbeBEvGGYP5wMhJHhMF (S21+ 로그인)
 */
const admin = require('firebase-admin');
if (!admin.apps.length) admin.initializeApp({ projectId: 'calldetector-5d61e' });
const db = admin.firestore();
const { FieldValue } = require('firebase-admin/firestore');

const PATH = 'provinces/gyeonggi/cities/yangpyeong/offices/RUbeBEvGGYP5wMhJHhMF/chatRoom/main/messages';

(async () => {
  const ref = db.collection(PATH).doc();
  const text = '★풀테스트 시스템 메시지 ' + new Date().toLocaleTimeString('ko-KR');
  await ref.set({
    id: ref.id,
    type: 'system',
    senderId: 'SYSTEM',
    senderName: '시스템',
    senderRole: 'SYSTEM',
    text,
    createdAt: FieldValue.serverTimestamp(),
    clientCreatedAt: Date.now(),
    status: 'SENT',
  });
  console.log('system 메시지 write 완료:', ref.id, '|', text);
  process.exit(0);
})().catch((e) => { console.error('실패:', e); process.exit(1); });
