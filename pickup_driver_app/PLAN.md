# 픽업기사앱 (Pickup Driver App) 기획안 v2

## Context

현재 시스템에서 픽업기사는 콜매니저의 **음성 지시(전화)**에 의존하여 움직인다.
기사 5명 + 픽업기사 3명 체제에서, 관리자가 머릿속으로 픽업 경로를 계산하고 전화로 지시하는 비효율이 존재한다.

### 핵심 설계 원칙
1. **매니저는 매칭만 한다** — "픽업기사 A → 대리기사 B" 연결이 유일한 조작
2. **DELIVER/RETRIEVE 구분은 시스템이 자동 결정** — 대리기사의 콜 상태로 판단
3. **상황판 공유** — 픽업기사가 콜매니저 대시보드 읽기 전용으로 상황 파악
4. **지도 불필요** — 지역 대리는 길을 숙지, 주소 텍스트만으로 충분
5. **1:N 유연한 매칭** — 픽업기사 1명이 여러 기사 동시 담당, 이동 중 추가/재배정 가능

---

## 1. 업무 흐름

### 관리자 (콜매니저)
```
대리기사 목록에서 기사 탭 → [픽업기사 배정] → 픽업기사 선택 → 끝
```
- DELIVER/RETRIEVE 선택 없음
- 한 픽업기사에게 여러 기사 동시 매칭 가능
- 이동 중에도 새 매칭 추가 가능
- 매칭 취소/재배정 가능

### 픽업기사
```
① 매칭 알림 수신: "홍길동 기사 담당"
② 상황판에서 홍길동 상태 확인:
   ├─ ASSIGNED/ACCEPTED → 고객 위치로 데려다줘야 함 (송출)
   ├─ IN_PROGRESS → 뒤따르거나 다른 임무 먼저 처리
   └─ COMPLETED/AWAITING_SETTLEMENT → 데리러 가야 함 (회수)
③ 여러 기사가 매칭되면 상황판 보고 순서/경로를 스스로 판단
④ 완료되면 매칭 해제
```

### 자동 판단 로직 (시스템)
| 대리기사 콜 상태 | 픽업 임무 유형 | 목적지 |
|-----------------|--------------|--------|
| ASSIGNED / ACCEPTED | DELIVER (송출) | 콜의 customerAddress (고객 주소) |
| IN_PROGRESS | 대기 (뒤따르기 또는 다른 임무) | — |
| AWAITING_SETTLEMENT / COMPLETED | RETRIEVE (회수) | 콜의 destination (운행 목적지) 또는 사무실 |

→ 픽업기사앱 상황판에서 대리기사 상태 옆에 자동으로 표시:
- "송출 필요 → 양평읍 양평리 123"
- "회수 대기 → 운행중"
- "회수 필요 → 강상면 병산리 45"

---

## 2. 데이터 모델

### 매칭 문서 (신규: pickup_assignments)
```
provinces/{p}/cities/{c}/offices/{o}/pickup_assignments/{assignmentId}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `pickupDriverId` | string | 픽업기사 UID |
| `pickupDriverName` | string | 픽업기사 이름 |
| `driverId` | string | 대리기사 UID |
| `driverName` | string | 대리기사 이름 |
| `status` | string | ACTIVE / COMPLETED / CANCELLED |
| `assignedAt` | timestamp | 매칭 시간 |
| `completedAt` | timestamp? | 완료 시간 |

- **type 필드 없음** — DELIVER/RETRIEVE는 대리기사의 콜 상태로 자동 판단
- **callId 필드 없음** — 매칭은 기사 단위, 콜 단위가 아님 (기사가 여러 콜을 처리할 수 있음)

### 기존 컬렉션 활용
```
provinces/{p}/cities/{c}/offices/{o}/
  ├── pickup_drivers/{uid}       ← 이미 존재 ✅
  ├── designated_drivers/{uid}   ← 기존 (상태 조회용)
  └── calls/{callId}             ← 기존 (상황판 표시용, 읽기만)
```

### 픽업기사 문서에 추가할 필드
```
pickup_drivers/{uid}
  ├── (기존 필드들)
  ├── fcmToken: string           ← FCM 토큰 (신규)
  └── status: string             ← WAITING / ON_TASK / OFFLINE (신규)
```

---

## 3. 픽업기사앱 화면 구성

### (1) 로그인
- Firebase Auth (기존 driver_app과 동일)
- `collectionGroup("pickup_drivers")` 로 기사 문서 조회

### (2) 메인 — 2탭 구조
```
┌──────────────────────────────┐
│  [내 담당]    [상황판]         │  ← 탭
├──────────────────────────────┤
```

#### [내 담당] 탭
```
┌──────────────────────────────┐
│  [대기중 ●]  [임무중]  [퇴근]  │  ← 본인 상태
├──────────────────────────────┤
│                              │
│  홍길동 기사                   │
│  상태: 배차됨 (ASSIGNED)       │
│  → 송출 필요                  │
│  목적지: 양평읍 양평리 123      │
│  [전화] [완료]                │
│                              │
│  김철수 기사                   │
│  상태: 운행완료 (COMPLETED)    │
│  → 회수 필요                  │
│  위치: 강상면 병산리 45         │
│  [전화] [완료]                │
│                              │
├──────────────────────────────┤
│  오늘 완료: 8건               │
└──────────────────────────────┘
```
- 본인에게 매칭된 기사만 표시
- 각 기사의 콜 상태를 실시간 조회 → "송출 필요" / "회수 필요" 자동 표시
- 목적지 = 콜의 customerAddress 또는 destination (주소 텍스트)
- [완료] = 해당 매칭을 COMPLETED로 변경

#### [상황판] 탭 — 콜매니저 대시보드 읽기 전용
```
┌──────────────────────────────┐
│  전체 대리기사 현황             │
├──────────────────────────────┤
│  홍길동: 운행중 → 강상면 병산리  │
│  김철수: 대기중 (사무실)        │
│  이영희: 배차됨 → 수성구 범어동  │
│  박민수: 운행완료 → 양평읍      │
│  최지원: 대기중 (사무실)        │
├──────────────────────────────┤
│  내 담당 ★ 표시               │
└──────────────────────────────┘
```
- 같은 사무실 소속 전체 대리기사 상태
- 기사 이름 + 콜 상태 + 목적지 주소 (텍스트만)
- 본인 담당 기사는 ★ 표시로 구분
- 고객 전화번호 등 민감정보 제외

---

## 4. 콜매니저 추가 기능

### 배차 팝업 또는 기사 목록에서
```
대리기사 [홍길동] 롱프레스 또는 메뉴
  → [픽업기사 배정]
  → 픽업기사 목록 (WAITING/ON_TASK 표시)
  → 선택 → 매칭 완료 (pickup_assignments 문서 생성)
```

### 대시보드에 픽업기사 현황
```
┌──────────────────────────┐
│  픽업기사                  │
│  박기사: 대기중             │
│  이기사: 임무중 (홍길동, 김철수) │
│  최기사: 임무중 (이영희)    │
└──────────────────────────┘
```

### 매칭 해제/재배정
- 매칭된 기사를 다른 픽업기사로 재배정 가능
- 매칭 취소 가능

---

## 5. 통신 흐름

### 매칭 생성 (Manager → Pickup Driver)
```
콜매니저: pickup_assignments 문서 생성 (status: ACTIVE)
  → Cloud Functions 트리거 (onPickupAssignmentCreated)
  → FCM push → 픽업기사앱: "홍길동 기사 담당 배정"
```

### 상황판 실시간 업데이트
```
픽업기사앱: Firestore 실시간 리스너
  - pickup_assignments (본인 매칭 목록)
  - calls (같은 사무실, 당일 콜) ← 상황판용
  - designated_drivers (기사 상태) ← 상황판용
```
※ 비용 최적화: 상황판은 영업시간(오후5시~새벽2시)에만 리스너 활성화

### 매칭 완료/취소
```
픽업기사: [완료] → pickup_assignments status = COMPLETED
콜매니저: [취소] → pickup_assignments status = CANCELLED
  → Cloud Functions → FCM 알림
```

---

## 6. Firestore 보안 규칙

```
// pickup_assignments: 같은 사무실 소속만 읽기/쓰기
match /pickup_assignments/{assignmentId} {
  allow read: if isOfficeStaff();    // 관리자, 픽업기사
  allow write: if isAdmin();         // 관리자만 생성/수정
  allow update: if isPickupDriver(); // 픽업기사는 완료만
}

// calls, designated_drivers: 픽업기사에게 읽기 허용 (상황판)
// 단, phoneNumber 등 민감 필드는 앱 레벨에서 제외
```

---

## 7. Cloud Functions 추가

| 함수 | 트리거 | 동작 |
|------|--------|------|
| `onPickupAssignmentCreated` | pickup_assignments 문서 생성 | 픽업기사에게 FCM 전송 |
| `onPickupAssignmentUpdated` | pickup_assignments 상태 변경 | 콜매니저에게 FCM 전송 (완료/취소) |

---

## 8. 구현 우선순위

### Phase 1 — MVP
1. **Firestore**: pickup_assignments 컬렉션 + 보안 규칙
2. **콜매니저**: 기사 목록에서 [픽업기사 배정] 기능 + 픽업기사 현황 표시
3. **픽업기사앱**: 로그인 + [내 담당] 탭 + [상황판] 탭
4. **Cloud Functions**: onPickupAssignmentCreated (FCM)
5. **pickup_drivers 문서**: fcmToken, status 필드 추가

### Phase 2 — 편의 기능
6. 매칭 재배정/취소 UI
7. 콜 배차 시 픽업기사 동시 배정 옵션
8. 완료 이력 조회

### Phase 3 — 고도화 (향후)
9. 픽업기사 실시간 위치 공유
10. 최적 경로 제안
11. 픽업 통계/리포트

---

## 9. 기존 시스템 영향도

| 항목 | 영향 |
|------|------|
| driver_app | 변경 없음 |
| call_detector | 변경 없음 |
| customer_app | 변경 없음 |
| call_manager | 픽업 배정 UI + 현황 표시 추가 |
| Cloud Functions | 2개 함수 추가 |
| Firestore Rules | pickup_assignments 규칙 추가, calls/drivers 읽기 규칙 확장 |
| pickup_drivers 문서 | fcmToken, status 필드 추가 |

---

## 10. 확정 사항

1. **정산 불필요** — 픽업기사는 정산 대상 아님
2. **픽업기사 개인폰 사용** — 별도 기기 불필요
3. **매칭 완료 흐름**: 픽업기사가 [완료] 클릭 → 관리자 콜매니저 화면에 상태 변경 반영
4. **기사가 직접 사무실로 오는 경우**: 매칭 없이 처리 (현행 유지, 앱 개입 없음)
