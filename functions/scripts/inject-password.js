/**
 * inject-password.js — 특정 Firebase Auth 계정에 비밀번호를 주입하는 관리용 스크립트
 *
 * 용도: H2 설계 전환 시점(2026-04-20) 기존 Google OAuth 계정 소유자에게
 *       email+비밀번호 로그인을 허용시키기 위해 Admin SDK 로 비밀번호 필드만 설정한다.
 *       주입 이후 해당 계정은 Google / Email+비밀번호 양쪽으로 로그인 가능.
 *
 * 사용법:
 *   cd functions
 *   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> \
 *     node scripts/inject-password.js <uid> <newPassword>
 *
 *   Windows PowerShell:
 *   $env:GOOGLE_APPLICATION_CREDENTIALS = "C:\\path\\to\\service-account.json"
 *   node scripts/inject-password.js <uid> <newPassword>
 *
 *   Windows Git Bash:
 *   GOOGLE_APPLICATION_CREDENTIALS=/c/path/to/service-account.json \
 *     node scripts/inject-password.js <uid> <newPassword>
 *
 * 주의:
 *   - 반드시 유선 전화 등 보안 채널로 사용자에게 비밀번호를 전달할 것.
 *   - 주입 후 사용자가 로그인해서 본인 비밀번호로 변경하도록 안내.
 */

const admin = require('firebase-admin');

const PROJECT_ID = 'calldetector-5d61e';

async function main() {
  const [uid, newPassword] = process.argv.slice(2);

  if (!uid || !newPassword) {
    console.error('사용법: node inject-password.js <uid> <newPassword>');
    console.error('예: node inject-password.js hpKCSyX7ABCDEF Temp1234!');
    process.exit(1);
  }

  if (newPassword.length < 6) {
    console.error('비밀번호는 6자 이상이어야 합니다.');
    process.exit(1);
  }

  if (!admin.apps.length) {
    admin.initializeApp({ projectId: PROJECT_ID });
  }

  try {
    const before = await admin.auth().getUser(uid);
    console.log(`[before] uid=${uid} email=${before.email || '(없음)'} providers=${before.providerData.map(p => p.providerId).join(',')}`);

    await admin.auth().updateUser(uid, { password: newPassword });
    const after = await admin.auth().getUser(uid);

    console.log(`[after]  uid=${uid} email=${after.email || '(없음)'}`);
    console.log('✅ 비밀번호 주입 완료. 사용자에게 이메일 + 새 비밀번호를 안전한 채널로 전달하세요.');
  } catch (err) {
    console.error('❌ 오류:', err.code || '', err.message || err);
    process.exit(2);
  }
}

main();
