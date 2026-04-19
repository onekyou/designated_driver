/**
 * Firebase Auth Custom Claims에 role을 설정한다.
 * Storage/Firestore rules에서 `request.auth.token.role`로 참조 가능하게 만든다.
 *
 * 사용 전 준비:
 *   1. Firebase 프로젝트가 Blaze 플랜인지 확인 (Cloud Functions 쓰고 있으면 이미 Blaze)
 *   2. Application Default Credentials 설정:
 *        gcloud auth application-default login
 *      (gcloud가 Python 이슈로 막히면, 대안으로 서비스 계정 키 파일 사용:
 *        $env:GOOGLE_APPLICATION_CREDENTIALS="C:\path\to\sa-key.json"
 *      — Firebase Console → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성)
 *   3. functions 디렉토리에서 `npm install` 완료 (firebase-admin 필요)
 *
 * 사용법:
 *   cd functions
 *   node scripts/set-custom-claims.js <uid> <role>
 *
 * 예:
 *   node scripts/set-custom-claims.js vK9X9OJ9dZWgUvlvwT48KTG3ljS2 HEAD_MANAGER
 *
 * 지원 role:
 *   HEAD_MANAGER | SUPER_ADMIN | OFFICE_OWNER | ADMIN
 *
 * 적용 후:
 *   대상 사용자는 반드시 로그아웃 → 재로그인해야 새 ID 토큰에 claim이 실린다.
 *   이후 Storage rules를 다음과 같이 바꿔 cross-service 조회 없이 검증 가능:
 *     allow write: if request.auth.token.role in ['HEAD_MANAGER', 'SUPER_ADMIN'];
 */

const admin = require("firebase-admin");

const VALID_ROLES = ["HEAD_MANAGER", "SUPER_ADMIN", "OFFICE_OWNER", "ADMIN"];
const PROJECT_ID = "calldetector-5d61e";

async function main() {
  const [, , uid, role] = process.argv;

  if (!uid || !role) {
    console.error("Usage: node scripts/set-custom-claims.js <uid> <role>");
    console.error(`Valid roles: ${VALID_ROLES.join(", ")}`);
    process.exit(1);
  }
  if (!VALID_ROLES.includes(role)) {
    console.error(`Invalid role: ${role}`);
    console.error(`Valid roles: ${VALID_ROLES.join(", ")}`);
    process.exit(1);
  }

  admin.initializeApp({ projectId: PROJECT_ID });
  const auth = admin.auth();

  const user = await auth.getUser(uid);
  const existingClaims = user.customClaims || {};
  const newClaims = { ...existingClaims, role };

  await auth.setCustomUserClaims(uid, newClaims);

  console.log(`✅ Custom claims updated`);
  console.log(`   uid    : ${uid}`);
  console.log(`   email  : ${user.email || "(none)"}`);
  console.log(`   claims : ${JSON.stringify(newClaims)}`);
  console.log("");
  console.log("⚠️  대상 사용자는 로그아웃 → 재로그인해야 새 토큰이 발급됩니다.");
}

main().catch((e) => { console.error("❌ Error:", e.message); process.exit(1); });
