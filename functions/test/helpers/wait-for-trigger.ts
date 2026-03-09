/**
 * CF 트리거 완료 대기 유틸리티
 * Firestore 문서 변경 후 트리거가 비동기로 실행되므로 폴링으로 대기
 */

import { db } from "../setup/emulator-config";
import { Office, officePath, settlementPath, getTodayWorkDate } from "../setup/constants";

export interface WaitOptions {
  timeout?: number;   // 최대 대기 시간 (ms), 기본 15초
  interval?: number;  // 폴링 간격 (ms), 기본 500ms
  description?: string;
}

/**
 * 조건이 true가 될 때까지 폴링
 */
export async function waitForCondition(
  checkFn: () => Promise<boolean>,
  options: WaitOptions = {}
): Promise<boolean> {
  const timeout = options.timeout ?? 15000;
  const interval = options.interval ?? 500;
  const description = options.description ?? "condition";

  const startTime = Date.now();

  while (Date.now() - startTime < timeout) {
    try {
      const result = await checkFn();
      if (result) return true;
    } catch {
      // 문서가 아직 없을 수 있으므로 무시
    }
    await sleep(interval);
  }

  console.warn(`  ⚠ 타임아웃: ${description} (${timeout}ms)`);
  return false;
}

/**
 * 정산 세션에 특정 callId가 추가될 때까지 대기
 */
export async function waitForSettlementCall(
  office: Office,
  callId: string,
  workDate?: string
): Promise<boolean> {
  const date = workDate ?? getTodayWorkDate();
  return waitForCondition(
    async () => {
      const doc = await db.doc(settlementPath(office, date)).get();
      if (!doc.exists) return false;
      const data = doc.data();
      return data?.calls?.some((c: any) => c.callId === callId) ?? false;
    },
    { description: `정산 세션에 ${callId} 추가 대기` }
  );
}

/**
 * 공유콜이 특정 상태가 될 때까지 대기
 */
export async function waitForSharedCallStatus(
  callId: string,
  expectedStatus: string,
  timeout?: number
): Promise<boolean> {
  return waitForCondition(
    async () => {
      const doc = await db.doc(`shared_calls/${callId}`).get();
      return doc.data()?.status === expectedStatus;
    },
    { description: `공유콜 ${callId} → ${expectedStatus} 대기`, timeout }
  );
}

/**
 * 특정 사무실에 콜 문서가 생성될 때까지 대기 (공유콜 수임 시)
 */
export async function waitForCallInOffice(
  office: Office,
  checkFn: (calls: any[]) => boolean
): Promise<boolean> {
  return waitForCondition(
    async () => {
      const snap = await db.collection(`${officePath(office)}/calls`).get();
      const calls = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      return checkFn(calls);
    },
    { description: `사무실 ${office.officeId}에 콜 생성 대기` }
  );
}

/**
 * notifications 컬렉션에 특정 조건의 문서가 생성될 때까지 대기
 */
export async function waitForNotification(
  checkFn: (notifications: any[]) => boolean
): Promise<boolean> {
  return waitForCondition(
    async () => {
      const snap = await db.collection("notifications").get();
      const notifications = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      return checkFn(notifications);
    },
    { description: "알림 생성 대기", timeout: 10000 }
  );
}

/**
 * 고객 포인트 문서가 업데이트될 때까지 대기
 */
export async function waitForCustomerPoints(
  office: Office,
  normalizedPhone: string,
  checkFn: (data: any) => boolean
): Promise<boolean> {
  return waitForCondition(
    async () => {
      const doc = await db
        .doc(`${officePath(office)}/customerPoints/${normalizedPhone}`)
        .get();
      if (!doc.exists) return false;
      return checkFn(doc.data());
    },
    { description: `고객 포인트 ${normalizedPhone} 업데이트 대기` }
  );
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
