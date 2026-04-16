# Cloud Functions / Firestore 서버측 작업 사양

> **목적**: Flutter 전환을 위해 **서버측에서 선행 또는 동시 진행해야 할 작업** 종합. 클라이언트 코드(Flutter)와 분리된 별도 작업 트랙.
> **착수 시점**: Phase 6 클라이언트 코드 수정 **전 또는 동시**. 일부 작업은 CF 배포 후 클라이언트 빌드 가능.
> **작성일**: 2026-04-16
> **참조**: `flutter/WORKING_DOC.md` §5 의제 4, 5, 11 결정 + `flutter/driver_app/MVP/FIRESTORE.md` §6~§9

---

## 작업 인벤토리 (총 4개 대분류)

| 작업 | 의제 | 우선순위 | 작업량 | Phase |
|------|------|---------|--------|-------|
| **A. CF FCM payload `apns` 블록 추가** | 4 | 🔴 P0 (필수) | 28곳+ audit + 수정 | Phase 6 빌드 전 |
| **B. CF `acceptanceEvents` 컬렉션 + 월 집계** | 11 | 🟠 P1 (R1 평가 필수) | 신규 모듈 ~300 LOC | Phase 6 빌드 ~ 출시 전 |
| **C. CF `generateCustomToken` 신규** | 13 | 🟢 P2 (Phase 2) | 신규 모듈 ~50 LOC | Mac 도착 후 |
| **D. Firestore 보안 규칙 업데이트** | 4, 5, 11 | 🔴 P0 | rules 파일 수정 | A 배포 전 |

---

## A. CF FCM payload `apns` 블록 추가 (의제 4)

### A.1 결정 요약

`functions/src/index.ts:560-573` `oncallassigned` payload가 현재 `apns` 블록 없음 → iOS Flutter 백그라운드 data-only 메시지 수신 불안정.

**해결**: 모든 FCM 송신 함수에 `apns` 블록 추가. **`data` 블록은 100% 보존** (Android Kotlin 이중 알림 회귀 방지). 최상위 `notification` 블록 추가 금지.

### A.2 audit 대상 28곳+ (functions/src/index.ts)

`admin.messaging().send(...)` 또는 `admin.messaging().sendEach(...)` 호출 위치 전수 Grep 후 각각 `apns` 블록 추가:

**기사 대상 송신** (4곳+):
- `oncallassigned` line 558-589 — call_assigned (P0, 가장 중요)
- `onCallStatusChanged` 기사 측 분기 — call_cancelled
- 정산 관련 SETTLEMENT_FINALIZED / SETTLEMENT_CONFIRMED / SETTLEMENT_REJECTED
- `transferCarryOver` → CARRYOVER_TRANSFERRED

**손님 대상 송신** (5곳+):
- onCallCreated → CALL_RECEIVED
- onCallAssigned → DRIVER_ASSIGNED
- onCallCompleted → RIDE_COMPLETED
- onCallCancelled → CALL_CANCELLED
- onCallStatusChanged → call_status_update

**관리자 대상 송신** (5곳+):
- DRIVER_STATUS_UPDATE
- NEW_CALL
- CALL_STATUS_UPDATE
- STATUS_CHANGE
- DRIVER_APPROVAL_REQUEST

**기타** (10곳+):
- checkAssignedTimeout 관리자 알림
- notifyCustomerOnComplete
- 공유콜 NEW_SHARED_CALL
- ... (Grep으로 정확 인벤토리)

### A.3 표준 apns 블록 템플릿

#### A.3.1 일반 알림 (Time Sensitive)
```typescript
// 배차/취소 등 즉시 사용자 가시 알림
const standardApnsBlock = {
  apns: {
    headers: {
      "apns-push-type": "alert" as const,
      "apns-priority": "10" as const,
      "apns-expiration": String(Math.floor(Date.now() / 1000) + 30),
    },
    payload: {
      aps: {
        alert: { title, body },
        sound: "default" as const,
        "content-available": 1,
        "mutable-content": 1,
        "interruption-level": "time-sensitive" as const,  // iOS 15+
      },
    },
  },
};
```

**적용 대상**: call_assigned, call_cancelled, DRIVER_ASSIGNED, CALL_CANCELLED

#### A.3.2 일반 알림 (Active level)
```typescript
const activeApnsBlock = {
  apns: {
    headers: {
      "apns-push-type": "alert" as const,
      "apns-priority": "10" as const,
    },
    payload: {
      aps: {
        alert: { title, body },
        sound: "default" as const,
        "content-available": 1,
        "interruption-level": "active" as const,
      },
    },
  },
};
```

**적용 대상**: SETTLEMENT_FINALIZED / CONFIRMED / REJECTED, CARRYOVER_TRANSFERRED, RIDE_COMPLETED, CALL_RECEIVED, call_status_update

#### A.3.3 silent push (alert 없음, content-available만)
```typescript
const silentApnsBlock = {
  apns: {
    headers: {
      "apns-push-type": "background" as const,  // alert 아님
      "apns-priority": "5" as const,             // background는 5 강제
    },
    payload: {
      aps: {
        "content-available": 1,
      },
    },
  },
};
```

**적용 대상**: 백그라운드 데이터 동기화만 필요한 경우 (Phase 1에선 거의 사용 안 함)

### A.4 헬퍼 함수 도입 (권장)

DRY 원칙 — 28곳+ 직접 수정 대신 헬퍼:

```typescript
// functions/src/utils/fcmPayload.ts (신규)

import * as admin from "firebase-admin";
import { Timestamp } from "firebase-admin/firestore";

export type NotificationLevel = "time-sensitive" | "active";

export interface BuildPayloadInput {
  data: Record<string, string>;
  title: string;
  body: string;
  level: NotificationLevel;
  ttlSeconds?: number;
}

export function buildFcmPayload(input: BuildPayloadInput, token: string): admin.messaging.Message {
  const ttl = input.ttlSeconds ?? 30;
  const expiration = Math.floor(Date.now() / 1000) + ttl;

  return {
    data: input.data,
    android: {
      priority: "high" as const,
      ttl: ttl * 1000,
    },
    apns: {
      headers: {
        "apns-push-type": "alert" as const,
        "apns-priority": "10" as const,
        "apns-expiration": String(expiration),
      },
      payload: {
        aps: {
          alert: { title: input.title, body: input.body },
          sound: "default" as const,
          "content-available": 1,
          "mutable-content": 1,
          "interruption-level": input.level,
        },
      },
    },
    token,
  };
}

export function buildMulticastFcmPayload(
  input: BuildPayloadInput,
  tokens: string[]
): admin.messaging.MulticastMessage {
  return {
    ...buildFcmPayload(input, ""),
    tokens,
  } as admin.messaging.MulticastMessage;
}
```

### A.5 적용 예시 — `oncallassigned` 수정

**Before** (`functions/src/index.ts:559-589`):
```typescript
const driverPayload = {
  data: {
    callId, notificationId,
    type: "call_assigned",
    title: "새로운 콜 배정",
    body: "새로운 콜이 배정되었습니다."
  },
  android: { priority: "high" as const, ttl: 30000 },
  token: driverFcmToken,
};
await admin.messaging().send(driverPayload);
```

**After**:
```typescript
import { buildFcmPayload } from "./utils/fcmPayload";

const driverPayload = buildFcmPayload({
  data: {
    callId,
    notificationId,
    type: "call_assigned",
    title: "새로운 콜 배정",
    body: "새로운 콜이 배정되었습니다.",
  },
  title: "새로운 콜 배정",
  body: "새로운 콜이 배정되었습니다.",
  level: "time-sensitive",
  ttlSeconds: 30,
}, driverFcmToken);

await admin.messaging().send(driverPayload);
```

### A.6 검증 절차

1. **Firebase Emulator FCM 송신 테스트**:
   - `npm run emulators:start` (functions + firestore)
   - 테스트 스크립트로 Android/iOS 더미 토큰 양쪽 송신
   - APNs sandbox 토큰 + Google Play 서비스 토큰 둘 다 검증

2. **Staging 프로젝트에서 실기기 테스트**:
   - Android Kotlin 기사앱: data 블록만 파싱, 이중 알림 없는지 확인
   - iOS Flutter 기사앱 (Phase 6 빌드 후): Time Sensitive 배너 정상 표시 확인

3. **회귀 체크리스트**:
   - [ ] Kotlin Android 기사앱 모든 FCM 6종 정상 수신
   - [ ] Kotlin Android 손님앱 모든 FCM 5종 정상 수신
   - [ ] Kotlin Android 콜매니저 모든 FCM 정상 수신
   - [ ] iOS Flutter 기사앱 (출시 후) 모든 FCM 정상 수신
   - [ ] FCM 도달률 모니터링 (notifications 컬렉션 ACK 카운트)

### A.7 배포 순서 (롤백 가능)

1. **Day 1**: `utils/fcmPayload.ts` 헬퍼 + `oncallassigned` 1건만 수정 → 배포 → 24시간 모니터링
2. **Day 2**: 정산 관련 4곳 일괄 수정 → 배포 → 24시간 모니터링
3. **Day 3**: 손님 송신 5곳 → 배포 → 24시간 모니터링
4. **Day 4**: 관리자 송신 5곳 → 배포 → 24시간 모니터링
5. **Day 5**: 나머지 14곳+ → 배포 → 48시간 모니터링
6. **Day 7**: Phase 6 클라이언트 코드 구현 시작 가능

**롤백 트리거**: notifications 컬렉션 ACK 비율이 기존 대비 5% 이상 하락 시 즉시 git revert.

### A.8 위험 신호

- **`apns-push-type: "alert"` + `apns-priority: 10` 조합** = alert 있을 때만 허용. silent push(`background`)는 priority 5 필수. 헬퍼에서 명확히 구분.
- **iOS deployment target < 15에서 `interruption-level: time-sensitive` 무시됨** → 의제 1 결정 (iOS 15.0)으로 해소
- **`mutable-content: 1`은 Notification Service Extension 필요** → Phase 1 MVP에선 NSE 미작성, content는 그대로 표시 (확장은 R1)
- **APNs Authentication Key (.p8) Firebase Console 등록 필수** → 미등록 시 iOS FCM 전체 실패

---

## B. CF `acceptanceEvents` 컬렉션 + 월 집계 (의제 11)

### B.1 결정 요약

R1_LOCKSCREEN 발동 기준 "수락률 5%p 하락"을 측정하려면 ASSIGNED → ACCEPTED 전이 카운트 + 플랫폼별 집계가 필요. 현재 CF에 부재.

**해결**: 신규 컬렉션 `acceptanceEvents` + 신규 함수 2개 (`recordAcceptanceEvent` + `aggregateMonthlyStats`).

### B.2 신규 컬렉션 스키마

```
/acceptanceEvents/{eventId}
{
  callId: string,
  assignedDriverId: string,
  platform: "android" | "ios",      // driver 문서 platform 필드 기반
  officeId: string,                  // 사무실별 분석용
  provinceId: string,
  cityId: string,
  assignedAt: Timestamp,             // CF가 ASSIGNED로 변경한 시점
  outcome: "accepted" | "rejected" | "timeout",
  outcomeAt: Timestamp,              // 수락/거절/타임아웃 시점
  latencyMs: number,                 // outcomeAt - assignedAt (수락만)
  rejectReason?: string,             // 거절 사유 (있으면)
  createdAt: Timestamp,              // 문서 생성 시점 (서버)
}
```

### B.3 신규 모듈 — `functions/src/analytics/acceptanceEvents.ts`

```typescript
import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { Timestamp, FieldValue } from "firebase-admin/firestore";

const db = admin.firestore();

interface RecordEventInput {
  callId: string;
  assignedDriverId: string;
  outcome: "accepted" | "rejected" | "timeout";
  assignedAt: Timestamp;
  rejectReason?: string;
}

/**
 * acceptanceEvents 컬렉션에 이벤트 기록.
 * onCallStatusChanged, checkAssignedTimeout에서 호출.
 */
export async function recordAcceptanceEvent(input: RecordEventInput): Promise<void> {
  const driverSnap = await db.doc(`<driver_path>/${input.assignedDriverId}`).get();
  const driverData = driverSnap.data();
  const platform = (driverData?.platform ?? "android") as "android" | "ios";
  const officeId = driverData?.officeId ?? "";
  const provinceId = driverData?.provinceId ?? "";
  const cityId = driverData?.cityId ?? "";

  const outcomeAt = Timestamp.now();
  const latencyMs = input.outcome === "accepted"
    ? outcomeAt.toMillis() - input.assignedAt.toMillis()
    : 0;

  await db.collection("acceptanceEvents").add({
    callId: input.callId,
    assignedDriverId: input.assignedDriverId,
    platform,
    officeId,
    provinceId,
    cityId,
    assignedAt: input.assignedAt,
    outcome: input.outcome,
    outcomeAt,
    latencyMs,
    rejectReason: input.rejectReason ?? null,
    createdAt: FieldValue.serverTimestamp(),
  });
}
```

### B.4 호출 위치 변경 (`functions/src/index.ts`)

#### B.4.1 `onCallStatusChanged` (line 1810~)

ASSIGNED → ACCEPTED 또는 ASSIGNED → WAITING(거절) 전이 감지 시:

```typescript
// 기존 매니저 FCM 송신 후
import { recordAcceptanceEvent } from "./analytics/acceptanceEvents";

if (beforeStatus === "ASSIGNED" && afterStatus === "ACCEPTED") {
  await recordAcceptanceEvent({
    callId,
    assignedDriverId: afterData.assignedDriverId,
    outcome: "accepted",
    assignedAt: beforeData.assignedTime ?? Timestamp.now(),
  });
}

if (beforeStatus === "ASSIGNED" && afterStatus === "WAITING" && afterData.rejectedByDriver) {
  await recordAcceptanceEvent({
    callId,
    assignedDriverId: afterData.rejectedByDriver,
    outcome: "rejected",
    assignedAt: beforeData.assignedTime ?? Timestamp.now(),
    rejectReason: "driver_rejected",
  });
}
```

#### B.4.2 `checkAssignedTimeout` (line 3469~)

3분 타임아웃 발생 시:

```typescript
// 기존 WAITING 복귀 후
await recordAcceptanceEvent({
  callId: doc.id,
  assignedDriverId: callData.assignedDriverId,
  outcome: "timeout",
  assignedAt: callData.assignedTime ?? Timestamp.now(),
  rejectReason: "assigned_timeout_3min",
});
```

### B.5 월 집계 스케줄러 — `aggregateMonthlyStats`

```typescript
// functions/src/analytics/aggregateMonthly.ts

export const aggregateMonthlyStats = functions.pubsub
  .schedule("0 0 1 * *")  // 매월 1일 00:00 KST
  .timeZone("Asia/Seoul")
  .onRun(async () => {
    const now = new Date();
    const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const thisMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    const yearMonth = `${lastMonth.getFullYear()}-${String(lastMonth.getMonth() + 1).padStart(2, "0")}`;

    const eventsSnap = await db.collection("acceptanceEvents")
      .where("createdAt", ">=", Timestamp.fromDate(lastMonth))
      .where("createdAt", "<", Timestamp.fromDate(thisMonth))
      .get();

    const stats = {
      yearMonth,
      total: eventsSnap.size,
      byPlatform: {
        android: { accepted: 0, rejected: 0, timeout: 0, totalLatencyMs: 0 },
        ios: { accepted: 0, rejected: 0, timeout: 0, totalLatencyMs: 0 },
      },
      byOffice: {} as Record<string, { accepted: number; rejected: number; timeout: number }>,
      computedAt: FieldValue.serverTimestamp(),
    };

    eventsSnap.forEach(doc => {
      const data = doc.data();
      const p = data.platform as "android" | "ios";
      const o = data.outcome as "accepted" | "rejected" | "timeout";
      stats.byPlatform[p][o]++;
      if (o === "accepted") {
        stats.byPlatform[p].totalLatencyMs += data.latencyMs ?? 0;
      }

      const office = data.officeId as string;
      if (!stats.byOffice[office]) {
        stats.byOffice[office] = { accepted: 0, rejected: 0, timeout: 0 };
      }
      stats.byOffice[office][o]++;
    });

    await db.doc(`monthlyStats/${yearMonth}`).set(stats);

    // R1 발동 기준 자동 평가
    const androidTotal = stats.byPlatform.android.accepted + stats.byPlatform.android.rejected + stats.byPlatform.android.timeout;
    const iosTotal = stats.byPlatform.ios.accepted + stats.byPlatform.ios.rejected + stats.byPlatform.ios.timeout;

    if (androidTotal >= 100 && iosTotal >= 100) {
      const androidRate = stats.byPlatform.android.accepted / androidTotal;
      const iosRate = stats.byPlatform.ios.accepted / iosTotal;
      const diffPp = (androidRate - iosRate) * 100;

      if (diffPp >= 5) {
        // R1_LOCKSCREEN 발동 기준 도달
        functions.logger.warn(`[R1_LOCKSCREEN_TRIGGER] iOS 수락률 ${iosRate.toFixed(3)} vs Android ${androidRate.toFixed(3)}, ${diffPp.toFixed(1)}pp 차이`);
        // TODO: 관리자 알림 송신 (Slack webhook 또는 별도 채널)
      }
    }

    return null;
  });
```

### B.6 신규 컬렉션 보안 규칙

`firestore.rules` 추가:

```
match /acceptanceEvents/{eventId} {
  // CF만 write 가능, 클라이언트 read 불가 (admin SDK 전용)
  allow read: if false;
  allow write: if false;
}

match /monthlyStats/{yearMonth} {
  // CF write, 관리자(admins) read 가능
  allow read: if request.auth != null && exists(/databases/$(database)/documents/admins/$(request.auth.uid));
  allow write: if false;
}
```

### B.7 기사 문서 `platform` 필드 1회성 백필

기존 기사 문서에 `platform: "android"` 추가 (모든 기존 기사는 Kotlin Android 사용 중 가정):

```javascript
// functions/scripts/backfill-platform.js (1회성)

const admin = require("firebase-admin");
admin.initializeApp({ credential: admin.credential.applicationDefault() });
const db = admin.firestore();

async function backfill() {
  const snap = await db.collectionGroup("designated_drivers").get();
  const batch = db.batch();
  let count = 0;

  snap.forEach(doc => {
    const data = doc.data();
    if (!data.platform) {
      batch.update(doc.ref, { platform: "android", fcmTokenPlatform: "android" });
      count++;
      if (count % 400 === 0) {
        // Firestore 배치 한도 500
        batch.commit();
      }
    }
  });

  await batch.commit();
  console.log(`Backfill complete: ${count} drivers updated`);
}

backfill().catch(console.error);
```

**실행**: `node functions/scripts/backfill-platform.js` (1회만, Firestore 보안 규칙 변경 전 admin SDK로 실행)

---

## C. CF `generateCustomToken` 신규 (의제 13, Phase 2)

### C.1 결정 요약

App Clip → 풀앱 데이터 이관 위해 Custom Token 발급 CF 필요. **Mac 도착 후 Phase 2** 작업.

상세 사양은 `flutter/customer_app/MVP/PHASE2_APPCLIP.md` §4 참조.

### C.2 신규 모듈 — `functions/src/customClip.ts`

```typescript
import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const generateCustomToken = functions
  .region("asia-northeast3")
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Anonymous Auth 필요");
    }

    const uid = context.auth.uid;
    const claims = data.claims ?? {};

    // 60분 만료 토큰 발급 (Apple 공식 권장)
    const customToken = await admin.auth().createCustomToken(uid, claims);

    return { customToken, expiresInSeconds: 3600 };
  });
```

### C.3 호출 시점

App Clip Swift 코드에서 Anonymous Auth 성공 후 즉시 호출:

```swift
let result = try await Functions.functions().httpsCallable("generateCustomToken").call()
let token = (result.data as? [String: Any])?["customToken"] as? String
AppGroupStorage.saveCustomToken(token)
```

### C.4 보안 규칙

`auth` context 필수 (Anonymous도 허용). 미인증 호출 차단.

---

## D. Firestore 보안 규칙 업데이트 (의제 4, 5, 11)

### D.1 결정 요약

신규 컬렉션 + 신규 필드 + 클라이언트 권한 변경 통합.

### D.2 변경 사항

`firestore.rules`:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // 기존 규칙 유지
    // ...

    // 신규 (의제 11) — acceptanceEvents 컬렉션
    match /acceptanceEvents/{eventId} {
      allow read: if false;  // 관리자도 직접 read 불가
      allow write: if false; // CF admin SDK만
    }

    // 신규 (의제 11) — monthlyStats 컬렉션
    match /monthlyStats/{yearMonth} {
      allow read: if request.auth != null
                  && exists(/databases/$(database)/documents/admins/$(request.auth.uid));
      allow write: if false; // CF admin SDK만
    }

    // 수정 (의제 5) — designated_drivers fcmTokenPlatform 필드 write 권한
    // 기사 본인 + admin SDK 모두 update 허용
    match /provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{driverId} {
      allow update: if request.auth != null
                    && request.auth.uid == driverId
                    && (
                      request.resource.data.diff(resource.data).affectedKeys()
                        .hasOnly(['fcmToken', 'fcmTokenPlatform', 'platform', 'lastLoginTime', 'status'])
                      || /* 기존 권한 */
                    );
    }

    // 수정 (의제 5) — customerInfo fcmTokenPlatform 필드 write 권한
    match /provinces/{p}/cities/{c}/offices/{o}/customerInfo/{phone} {
      allow update: if request.auth != null
                    && (
                      request.resource.data.diff(resource.data).affectedKeys()
                        .hasOnly(['fcmToken', 'fcmTokenPlatform'])
                      || /* 기존 권한 */
                    );
    }

    // 수정 (ATTRIBUTION 의제) — office_codes 컬렉션 (사무실 단축 코드 매핑)
    match /office_codes/{code} {
      allow read: if true;  // 누구나 사무실 코드 조회 가능 (publicly known)
      allow write: if request.auth != null
                   && exists(/databases/$(database)/documents/admins/$(request.auth.uid));
    }
  }
}
```

### D.3 배포 순서 (중요)

**잘못된 순서 시 회귀 발생**:

1. **Day 0** — backfill-platform.js 실행 (모든 기존 기사 문서에 platform 필드 추가)
2. **Day 1** — Kotlin 기사앱 업데이트 배포 (platform 필드 저장 시작) — Play Store
3. **Day 2** — Firestore 보안 규칙 업데이트 (`firestore.rules` deploy)
4. **Day 3** — Flutter 기사앱 빌드 + TestFlight (platform 저장 + read)
5. **Day 4** — CF acceptanceEvents 신규 함수 배포

**역순으로 하면**:
- Day 1을 Day 4 뒤에 하면, Flutter 기사앱이 platform 필드를 저장 시도하나 보안 규칙 차단 → 모든 fcmToken 저장 실패 → FCM 도달 0%

---

## 요약 — Phase 6 진입 직전 서버 작업 체크리스트

### Apple Developer 승인 후 즉시 (Day 0~4)
- [ ] `backfill-platform.js` 실행 (1회성)
- [ ] Kotlin 기사앱 platform 필드 저장 코드 추가 + Play Store 배포
- [ ] `firestore.rules` 업데이트 + deploy
- [ ] `functions/src/utils/fcmPayload.ts` 헬퍼 작성
- [ ] `oncallassigned` apns 블록 추가 + deploy + 24시간 모니터링

### Phase 6 클라이언트 코드 작업 시작 전 (Day 5~10)
- [ ] CF FCM 송신 28곳+ 일괄 audit + apns 블록 추가
- [ ] `functions/src/analytics/acceptanceEvents.ts` 신규
- [ ] `onCallStatusChanged` + `checkAssignedTimeout`에 recordAcceptanceEvent 호출 추가
- [ ] `aggregateMonthlyStats` 스케줄러 등록
- [ ] Firebase Emulator 종합 테스트

### Phase 1 출시 전 (TestFlight 단계)
- [ ] R1 발동 기준 자동 평가 로직 검증
- [ ] monthlyStats 첫 집계 (다음 달 1일)

### Phase 2 (Mac 도착 후)
- [ ] `functions/src/customClip.ts` 신규 (`generateCustomToken`)
- [ ] AASA `homepage/public/.well-known/apple-app-site-association` 호스팅

---

## 참조

- `flutter/WORKING_DOC.md` §5 의제 4, 5, 11 결정 전문
- `flutter/driver_app/MVP/FIRESTORE.md` §6, §7, §8, §9 — 클라이언트 측 호환 사양
- `flutter/customer_app/MVP/PHASE2_APPCLIP.md` §4 — generateCustomToken 클라이언트 호출
- `flutter/customer_app/MVP/ATTRIBUTION.md` §2 — office_codes 컬렉션 사용
- `functions/src/index.ts:560-573` — 현재 oncallassigned payload (수정 대상)
- `firestore.rules` — 보안 규칙 원본
