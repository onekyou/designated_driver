/**
 * 정합성 검증 함수 모음
 */

import { db } from "../setup/emulator-config";
import {
  Office,
  callPath,
  driverPath,
  settlementPath,
  customerPointsPath,
  pointBalancePath,
  getTodayWorkDate,
} from "../setup/constants";

export class AssertionError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AssertionError";
  }
}

function assert(condition: boolean, message: string): void {
  if (!condition) {
    throw new AssertionError(message);
  }
}

// ==========================================
// 콜 검증
// ==========================================

export async function assertCallStatus(
  office: Office,
  callId: string,
  expectedStatus: string
): Promise<void> {
  const doc = await db.doc(callPath(office, callId)).get();
  assert(doc.exists, `콜 ${callId} 문서 없음`);
  const actual = doc.data()?.status;
  assert(
    actual === expectedStatus,
    `콜 ${callId} 상태: expected=${expectedStatus}, actual=${actual}`
  );
}

export async function assertCallField(
  office: Office,
  callId: string,
  field: string,
  expectedValue: any
): Promise<void> {
  const doc = await db.doc(callPath(office, callId)).get();
  assert(doc.exists, `콜 ${callId} 문서 없음`);
  const actual = doc.data()?.[field];
  assert(
    actual === expectedValue,
    `콜 ${callId}.${field}: expected=${expectedValue}, actual=${actual}`
  );
}

// ==========================================
// 기사 검증
// ==========================================

export async function assertDriverStatus(
  office: Office,
  driverId: string,
  expectedStatus: string
): Promise<void> {
  const doc = await db.doc(driverPath(office, driverId)).get();
  assert(doc.exists, `기사 ${driverId} 문서 없음`);
  const actual = doc.data()?.status;
  assert(
    actual === expectedStatus,
    `기사 ${driverId} 상태: expected=${expectedStatus}, actual=${actual}`
  );
}

// ==========================================
// 정산 검증
// ==========================================

export async function assertSettlementSession(
  office: Office,
  workDate: string | undefined,
  expected: {
    callCount?: number;
    totalFare?: number;
    isFinalized?: boolean;
    containsCallId?: string;
    notContainsCallId?: string;
  }
): Promise<void> {
  const date = workDate ?? getTodayWorkDate();
  const doc = await db.doc(settlementPath(office, date)).get();
  assert(doc.exists, `정산 세션 ${date} 문서 없음`);

  const data = doc.data()!;

  if (expected.callCount !== undefined) {
    const actual = data.totals?.callCount ?? data.calls?.length ?? 0;
    assert(
      actual === expected.callCount,
      `정산 callCount: expected=${expected.callCount}, actual=${actual}`
    );
  }

  if (expected.totalFare !== undefined) {
    const actual = data.totals?.totalFare ?? 0;
    assert(
      actual === expected.totalFare,
      `정산 totalFare: expected=${expected.totalFare}, actual=${actual}`
    );
  }

  if (expected.isFinalized !== undefined) {
    const actual = data.metadata?.isFinalized ?? false;
    assert(
      actual === expected.isFinalized,
      `정산 isFinalized: expected=${expected.isFinalized}, actual=${actual}`
    );
  }

  if (expected.containsCallId) {
    const calls = data.calls ?? [];
    const found = calls.some((c: any) => c.callId === expected.containsCallId);
    assert(found, `정산 세션에 ${expected.containsCallId} 없음`);
  }

  if (expected.notContainsCallId) {
    const calls = data.calls ?? [];
    const found = calls.some((c: any) => c.callId === expected.notContainsCallId);
    assert(!found, `정산 세션에 ${expected.notContainsCallId}이 있으면 안 됨`);
  }
}

// ==========================================
// 포인트 검증
// ==========================================

export async function assertPointBalance(
  office: Office,
  expectedBalance: number
): Promise<void> {
  const doc = await db.doc(pointBalancePath(office)).get();
  assert(doc.exists, `포인트 문서 없음`);
  const actual = doc.data()?.balance ?? 0;
  assert(
    actual === expectedBalance,
    `포인트 잔액: expected=${expectedBalance}, actual=${actual}`
  );
}

export async function assertCustomerPoints(
  office: Office,
  normalizedPhone: string,
  expected: {
    currentPoints?: number;
    grade?: string;
    totalCalls?: number;
    totalEarned?: number;
  }
): Promise<void> {
  const doc = await db
    .doc(`${customerPointsPath(office, normalizedPhone)}`)
    .get();

  // customerPointsPath already normalizes, but normalizedPhone is already clean
  // Use direct path to avoid double normalization
  const directDoc = await db
    .collection(`provinces/${office.provinceId}/cities/${office.cityId}/offices/${office.officeId}/customerPoints`)
    .doc(normalizedPhone)
    .get();

  const data = directDoc.exists ? directDoc.data() : doc.data();
  assert(directDoc.exists || doc.exists, `고객 포인트 ${normalizedPhone} 문서 없음`);

  if (expected.currentPoints !== undefined) {
    const actual = data?.currentPoints ?? 0;
    assert(
      actual === expected.currentPoints,
      `고객 포인트: expected=${expected.currentPoints}, actual=${actual}`
    );
  }

  if (expected.grade !== undefined) {
    const actual = data?.grade ?? "BRONZE";
    assert(
      actual === expected.grade,
      `고객 등급: expected=${expected.grade}, actual=${actual}`
    );
  }

  if (expected.totalCalls !== undefined) {
    const actual = data?.totalCalls ?? 0;
    assert(
      actual === expected.totalCalls,
      `고객 총 콜수: expected=${expected.totalCalls}, actual=${actual}`
    );
  }

  if (expected.totalEarned !== undefined) {
    const actual = data?.totalEarned ?? 0;
    assert(
      actual === expected.totalEarned,
      `고객 총 적립: expected=${expected.totalEarned}, actual=${actual}`
    );
  }
}

// ==========================================
// 공유콜 검증
// ==========================================

export async function assertSharedCallStatus(
  callId: string,
  expectedStatus: string
): Promise<void> {
  const doc = await db.doc(`shared_calls/${callId}`).get();
  assert(doc.exists, `공유콜 ${callId} 문서 없음`);
  const actual = doc.data()?.status;
  assert(
    actual === expectedStatus,
    `공유콜 ${callId} 상태: expected=${expectedStatus}, actual=${actual}`
  );
}

export async function assertSharedCallField(
  callId: string,
  field: string,
  expectedValue: any
): Promise<void> {
  const doc = await db.doc(`shared_calls/${callId}`).get();
  assert(doc.exists, `공유콜 ${callId} 문서 없음`);
  const actual = doc.data()?.[field];
  assert(
    actual === expectedValue,
    `공유콜 ${callId}.${field}: expected=${expectedValue}, actual=${actual}`
  );
}

// ==========================================
// 범용 검증
// ==========================================

export async function assertDocExists(path: string): Promise<void> {
  const doc = await db.doc(path).get();
  assert(doc.exists, `문서 없음: ${path}`);
}

export async function assertDocNotExists(path: string): Promise<void> {
  const doc = await db.doc(path).get();
  assert(!doc.exists, `문서가 존재하면 안 됨: ${path}`);
}
