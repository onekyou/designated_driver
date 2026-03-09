/**
 * 시드 데이터 주입: 10개 사무실 + 기사 50명 + 관리자 + 포인트 초기값
 */

import { db, FieldValue, clearEmulatorData } from "./emulator-config";
import { OFFICES, DRIVERS, getDriversForOffice, officePath } from "./constants";

export async function seedAll(): Promise<void> {
  console.log("🔄 시드 데이터 주입 시작...");

  // 배치 쓰기 (500개 제한이므로 분할)
  let batch = db.batch();
  let batchCount = 0;
  const MAX_BATCH = 450;

  async function flushBatch() {
    if (batchCount > 0) {
      await batch.commit();
      batch = db.batch();
      batchCount = 0;
    }
  }

  async function addToBatch(ref: FirebaseFirestore.DocumentReference, data: any) {
    batch.set(ref, data);
    batchCount++;
    if (batchCount >= MAX_BATCH) {
      await flushBatch();
    }
  }

  // 1. Province + City
  await addToBatch(db.doc("provinces/test_province"), { name: "테스트도" });
  await addToBatch(db.doc("provinces/test_province/cities/test_city"), {
    name: "테스트시",
  });

  // 2. 사무실 10개
  for (const office of OFFICES) {
    const oPath = officePath(office);

    await addToBatch(db.doc(oPath), {
      name: office.name,
      depositRatio: office.depositRatio,
      assignedTimeoutMinutes: 3,
      provinceId: office.provinceId,
      cityId: office.cityId,
      officeId: office.officeId,
      status: "OPEN",
    });

    // 3. 사무실별 기사 5명
    const drivers = getDriversForOffice(OFFICES.indexOf(office));
    for (const driver of drivers) {
      await addToBatch(db.doc(`${oPath}/designated_drivers/${driver.driverId}`), {
        name: driver.name,
        phoneNumber: driver.phoneNumber,
        fcmToken: `fake_token_${driver.driverId}`,
        status: "WAITING",
        authUid: driver.authUid,
        isLoggedIn: true,
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    // 4. 사무실 포인트 초기값
    await addToBatch(db.doc(`${oPath}/points/points`), {
      balance: 10000,
    });

    // 5. 매니저 토큰
    const adminId = `admin_${office.officeId}`;
    await addToBatch(db.doc(`${oPath}/managerTokens/${adminId}`), {
      token: `fake_manager_token_${office.officeId}`,
    });

    // 6. 관리자 문서
    await addToBatch(db.doc(`admins/${adminId}`), {
      associatedProvinceId: office.provinceId,
      associatedCityId: office.cityId,
      associatedOfficeId: office.officeId,
      role: "ADMIN",
      fcmToken: `fake_admin_token_${office.officeId}`,
    });
  }

  await flushBatch();

  // 검증
  const officeSnap = await db.doc(officePath(OFFICES[0])).get();
  const driverSnap = await db
    .collection(`${officePath(OFFICES[0])}/designated_drivers`)
    .get();

  console.log(`✅ 시드 데이터 주입 완료`);
  console.log(`   사무실: ${OFFICES.length}개`);
  console.log(`   기사: ${DRIVERS.length}명 (사무실당 ${driverSnap.size}명)`);
  console.log(`   사무실1 확인: ${officeSnap.data()?.name}`);
}

// 직접 실행 시
if (require.main === module) {
  (async () => {
    try {
      await clearEmulatorData();
      console.log("🗑️  에뮬레이터 데이터 초기화 완료");
      await seedAll();
    } catch (error) {
      console.error("❌ 시드 데이터 주입 실패:", error);
      process.exit(1);
    }
  })();
}
