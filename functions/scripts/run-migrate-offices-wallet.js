/**
 * run-migrate-offices-wallet.js — 기존 사무실 wallet SIGNUP_BONUS 30,000 일괄 적립.
 *
 * `migrateExistingOfficesWallet` callable CF 와 동일 로직 + 동일 멱등 키
 * (`signup_bonus_${officeId}`) 사용 → 추후 callable 재호출해도 skip 처리됨.
 *
 * 용도: PR 1 deploy 직후 1회 실행 (양평 등 기존 사무실 +30,000).
 *
 * 사용법:
 *   cd functions
 *   node scripts/run-migrate-offices-wallet.js [--dry-run]
 *
 * 멱등성: signup_bonus_${officeId} 트랜잭션 doc 존재 시 skip.
 */

const admin = require('firebase-admin');

const PROJECT_ID = 'calldetector-5d61e';
const SIGNUP_BONUS = 30000;

async function main() {
  const dryRun = process.argv.includes('--dry-run');

  if (!admin.apps.length) {
    admin.initializeApp({ projectId: PROJECT_ID });
  }
  const db = admin.firestore();
  const FieldValue = admin.firestore.FieldValue;

  const officesSnap = await db.collectionGroup('offices').get();
  console.log(`[migrate] 사무실 ${officesSnap.size}개 발견 (dryRun: ${dryRun})`);

  let migrated = 0;
  let skipped = 0;
  const errors = [];

  for (const officeDoc of officesSnap.docs) {
    const officeRef = officeDoc.ref;
    const officeId = officeDoc.id;
    const pointsRef = officeRef.collection('points').doc('points');
    const txId = `signup_bonus_${officeId}`;
    const txRef = officeRef.collection('point_transactions').doc(txId);

    try {
      const result = await db.runTransaction(async (tx) => {
        const existingTx = await tx.get(txRef);
        if (existingTx.exists) {
          return { action: 'skipped', before: 0, after: 0 };
        }

        const pointsSnap = await tx.get(pointsRef);
        const before = pointsSnap.data()?.balance || 0;
        const after = before + SIGNUP_BONUS;

        if (!dryRun) {
          tx.set(pointsRef, {
            balance: after,
            updatedAt: FieldValue.serverTimestamp(),
          }, { merge: true });

          tx.set(txRef, {
            type: 'SIGNUP_BONUS',
            amount: SIGNUP_BONUS,
            balanceAfter: after,
            description: '마이그레이션 가입 보너스',
            status: 'COMPLETED',
            timestamp: FieldValue.serverTimestamp(),
            createdBy: 'admin-script',
          });
        }

        return { action: dryRun ? 'would migrate' : 'migrated', before, after };
      });

      const detail = result.action === 'skipped'
        ? `${officeId}: skipped (already migrated)`
        : `${officeId}: ${result.action} ${result.before} → ${result.after}`;
      console.log(`  ${detail}`);

      if (result.action === 'skipped') skipped++;
      else migrated++;
    } catch (e) {
      errors.push({ officeId, error: e.message });
      console.error(`  ${officeId}: ERROR ${e.message}`);
    }
  }

  console.log(`\n[완료] migrated: ${migrated}, skipped: ${skipped}, errors: ${errors.length}`);
  if (errors.length > 0) {
    console.error('errors:', JSON.stringify(errors, null, 2));
    process.exit(2);
  }
  process.exit(0);
}

main().catch((e) => {
  console.error('❌ 치명적 오류:', e);
  process.exit(3);
});
