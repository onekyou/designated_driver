/**
 * set-deposit-account.js — 콜마당 본부 단일 입금계좌 정보를 Firestore에 set 한다.
 *
 * 용도: PR 3 wallet UI 의 DepositGuideScreen 가 읽는 system_config/deposit_account 문서 운영.
 *       call_manager OFFICE_OWNER 가 read 하므로 firestore.rules 의 system_config 규칙도
 *       사전에 deploy 되어 있어야 한다.
 *
 * 사용법:
 *   cd functions
 *   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> \
 *     node scripts/set-deposit-account.js <bankName> <accountNumber> <accountHolder> [contactPhone]
 *
 *   Windows PowerShell:
 *   $env:GOOGLE_APPLICATION_CREDENTIALS = "C:\\path\\to\\service-account.json"
 *   node scripts/set-deposit-account.js 농협 000-0000-0000-00 "콜마당 (임시)" 010-1234-5678
 *
 *   Windows Git Bash:
 *   GOOGLE_APPLICATION_CREDENTIALS=/c/path/to/service-account.json \
 *     node scripts/set-deposit-account.js 농협 000-0000-0000-00 "콜마당 (임시)"
 *
 * 멱등성: set(merge:false) — 매번 전체 덮어씀. 동일 인자로 재실행 안전.
 *
 * call_manager UX: 화면 재진입 시 LaunchedEffect 가 loadDepositAccount() 호출 → 변경 즉시 반영.
 *                  앱 재시작 / 재배포 불필요.
 */

const admin = require('firebase-admin');

const PROJECT_ID = 'calldetector-5d61e';
const DOC_PATH = 'system_config/deposit_account';

async function main() {
  const [bankName, accountNumber, accountHolder, contactPhone] = process.argv.slice(2);

  if (!bankName || !accountNumber || !accountHolder) {
    console.error('사용법: node set-deposit-account.js <bankName> <accountNumber> <accountHolder> [contactPhone]');
    console.error('예: node set-deposit-account.js 농협 000-0000-0000-00 "콜마당 (임시)" 010-1234-5678');
    process.exit(1);
  }

  if (!admin.apps.length) {
    admin.initializeApp({ projectId: PROJECT_ID });
  }

  const payload = {
    bankName,
    accountNumber,
    accountHolder,
    contactPhone: contactPhone || null,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  try {
    const ref = admin.firestore().doc(DOC_PATH);
    const before = await ref.get();
    if (before.exists) {
      const b = before.data();
      console.log(`[before] ${b.bankName} / ${b.accountNumber} / ${b.accountHolder}${b.contactPhone ? ' / ' + b.contactPhone : ''}`);
    } else {
      console.log('[before] (문서 없음, 신규 생성)');
    }

    await ref.set(payload);

    console.log(`[after]  ${payload.bankName} / ${payload.accountNumber} / ${payload.accountHolder}${payload.contactPhone ? ' / ' + payload.contactPhone : ''}`);
    console.log(`✅ ${DOC_PATH} 갱신 완료. call_manager 매니저는 화면 재진입 시 즉시 반영됩니다.`);
  } catch (err) {
    console.error('❌ 오류:', err.code || '', err.message || err);
    process.exit(2);
  }
}

main();
