# 알림 신뢰성 강화 시스템 (ACK + Presence)

## 1. 개요

### 1.1 현재 문제점
- FCM 알림만 사용 중 → 실패율 10~20%
- 알림 실패 시 콜매니저가 인지 불가
- 실패 원인 파악 불가 (네트워크? 앱 문제? 기사 미확인?)

### 1.2 해결 방안
- **Realtime Database Presence**: 앱 온/오프라인 상태 실시간 추적
- **도착 ACK 시스템**: FCM 수신 시 자동 확인 응답
- **자동 재전송**: 미도착 시 최대 2회 재전송
- **콜매니저 경고**: 2회 실패 시에만 화면에 알림

### 1.3 예상 결과
| 항목 | 현재 | 적용 후 |
|------|------|---------|
| 알림 실패율 | 10~20% | 0.35% |
| 콜매니저 미인지율 | 10~20% | 0.01~0.05% |

---

## 2. 현재 알림 구조

### 2.1 알림 방향

```
┌─────────────────────────────────────────────────────────┐
│                    Cloud Functions                       │
│                         │                                │
│     ┌───────────────────┼───────────────────┐           │
│     ▼                   ▼                   ▼           │
│  기사앱            콜매니저앱            고객앱          │
└─────────────────────────────────────────────────────────┘
```

### 2.2 주요 알림 목록

#### 서버 → 기사앱
| 타입 | 설명 | 중요도 |
|------|------|--------|
| `call_assigned` | 콜 배차 알림 | 최상 |
| `SETTLEMENT_FINALIZED` | 업무 마감 알림 | 보통 |

#### 서버 → 콜매니저앱
| 타입 | 설명 | 중요도 |
|------|------|--------|
| `STATUS_CHANGE` | 운행 상태 변경 (수락/시작/완료) | 최상 |
| `DRIVER_STATUS_UPDATE` | 기사 상태 변경 | 높음 |
| `NEW_CALL` | 새 콜 접수 | 높음 |
| `NEW_SHARED_CALL` | 공유콜 알림 | 높음 |
| `SHARED_CALL_CANCELLED_POPUP` | 공유콜 취소 | 높음 |
| `DRIVER_APPROVAL_REQUEST` | 기사 승인 요청 | 보통 |
| `new_customer` | 신규 고객 가입 | 보통 |

#### 서버 → 고객앱
| 타입 | 설명 | 중요도 |
|------|------|--------|
| `CALL_RECEIVED` | 콜 접수 확인 | 높음 |
| `DRIVER_ASSIGNED` | 기사 배정 알림 | 높음 |
| `RIDE_COMPLETED` | 운행 완료 | 높음 |
| `POINTS_EARNED` | 포인트 적립 | 보통 |

### 2.3 현재 상태 업데이트 방식
- FCM 수신 → 로컬 DB 저장 → UI 반영
- Firestore 직접 읽기 없음 (비용 절감)

---

## 3. 신규 시스템 설계

### 3.1 Firebase Realtime Database Presence

#### 데이터 구조
```
/presence
  /drivers
    /{driverId}
      - status: "online" | "background" | "offline"
      - lastSeen: timestamp
      - deviceInfo: string
  /managers
    /{managerId}
      - status: "online" | "background" | "offline"
      - lastSeen: timestamp
  /customers
    /{customerId}
      - status: "online" | "background" | "offline"
      - lastSeen: timestamp
```

#### 앱 동작
```kotlin
// 앱 실행 시
presenceRef.setValue(mapOf(
    "status" to "online",
    "lastSeen" to ServerValue.TIMESTAMP
))

// 연결 끊김 시 자동 실행 (onDisconnect)
presenceRef.onDisconnect().setValue(mapOf(
    "status" to "offline",
    "lastSeen" to ServerValue.TIMESTAMP
))

// 백그라운드 진입 시
presenceRef.setValue(mapOf(
    "status" to "background",
    "lastSeen" to ServerValue.TIMESTAMP
))
```

### 3.2 알림 상태 저장 (Firestore)

#### 데이터 구조
```
/notifications/{notificationId}
  - type: string (알림 타입)
  - targetId: string (수신자 ID)
  - targetType: "driver" | "manager" | "customer"
  - callId: string (관련 콜 ID, 있는 경우)
  - status: "pending" | "delivered" | "acknowledged"
  - sentAt: timestamp
  - deliveredAt: timestamp (도착 시)
  - acknowledgedAt: timestamp (수락 시)
  - retryCount: number
  - lastRetryAt: timestamp
  - failureReason: string (실패 시)
```

### 3.3 도착 ACK 흐름

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   Cloud      │      │   Firestore  │      │   앱         │
│   Functions  │      │              │      │              │
└──────┬───────┘      └──────┬───────┘      └──────┬───────┘
       │                     │                     │
       │ 1. FCM 전송 ────────────────────────────>│
       │                     │                     │
       │ 2. notification 저장│                     │
       │    status: pending ─>                     │
       │                     │                     │
       │                     │<── 3. 도착 ACK ─────│
       │                     │    status: delivered│
       │                     │                     │
       │                     │<── 4. 수락 ACK ─────│
       │                     │    status: acknowledged
       │                     │                     │
```

### 3.4 재전송 로직

```
배차 FCM 전송
     │
     ├─ notification 저장 (status: pending, retryCount: 0)
     │
     ▼
[10초 대기]
     │
     ├─ 도착 ACK 있음? ─── Yes ──> 종료 (성공)
     │
     No
     │
     ├─ retryCount < 2? ─── Yes ──> 재전송, retryCount++
     │                              └─> [10초 대기]로 돌아감
     No
     │
     ▼
Presence 확인
     │
     ├─ offline ──> 콜매니저에 경고: "기사 연결 안됨"
     │
     └─ online ───> 콜매니저에 경고: "알림 전달 실패"
```

### 3.5 콜매니저 경고 UI

#### 표시 조건
- 2회 재전송 후에도 도착 ACK 없음
- 기사/고객 앱이 오프라인 상태

#### UI 디자인
```
┌────────────────────────────────────────────┐
│ ⚠️ 홍길동 기사 알림 전달 실패              │
│                                            │
│ 상태: 오프라인                             │
│ (앱 꺼짐 또는 네트워크 연결 끊김)          │
│                                            │
│ [📞 전화걸기]  [👤 다른 기사 배차]         │
└────────────────────────────────────────────┘
```

---

## 4. 구현 상세

### 4.1 파일 변경 목록

#### Cloud Functions (`functions/src/index.ts`)
| 변경 | 설명 |
|------|------|
| 추가 | `saveNotificationStatus()` - 알림 상태 저장 함수 |
| 추가 | `checkAndRetryNotifications()` - 재전송 스케줄러 |
| 추가 | `sendNotificationFailureAlert()` - 실패 알림 전송 |
| 수정 | 기존 FCM 전송 함수들에 상태 저장 로직 추가 |

#### 기사앱 (`driver_app`)
| 파일 | 변경 |
|------|------|
| `DriverApplication.kt` | Realtime DB Presence 초기화 |
| `MainActivity.kt` | 포그라운드/백그라운드 상태 업데이트 |
| `MyFirebaseMessagingService.kt` | 도착 ACK 전송 로직 추가 |
| `build.gradle` | Realtime Database 의존성 추가 |

#### 콜매니저앱 (`call_manager`)
| 파일 | 변경 |
|------|------|
| `CallManagerApplication.kt` | Realtime DB Presence 초기화 |
| `MainActivity.kt` | 포그라운드/백그라운드 상태 업데이트 |
| `MyFirebaseMessagingService.kt` | 도착 ACK 전송 + 실패 알림 처리 |
| `DashboardScreen.kt` | 실패 경고 팝업 UI 추가 |
| `build.gradle` | Realtime Database 의존성 추가 |

#### 고객앱 (`customer_app`)
| 파일 | 변경 |
|------|------|
| `CustomerApplication.kt` | Realtime DB Presence 초기화 |
| `MainActivity.kt` | 포그라운드/백그라운드 상태 업데이트 |
| `MyFirebaseMessagingService.kt` | 도착 ACK 전송 로직 추가 |
| `build.gradle` | Realtime Database 의존성 추가 |

### 4.2 신규 Firestore 보안 규칙

```javascript
// notifications 컬렉션
match /notifications/{notificationId} {
  // 서버만 생성/수정 가능 (Cloud Functions)
  allow read: if request.auth != null;
  allow write: if false; // Cloud Functions admin SDK 사용
}
```

### 4.3 신규 Realtime Database 보안 규칙

```json
{
  "rules": {
    "presence": {
      "drivers": {
        "$driverId": {
          ".read": true,
          ".write": "$driverId === auth.uid"
        }
      },
      "managers": {
        "$managerId": {
          ".read": true,
          ".write": "$managerId === auth.uid"
        }
      },
      "customers": {
        "$customerId": {
          ".read": true,
          ".write": "$customerId === auth.uid"
        }
      }
    }
  }
}
```

---

## 5. 구현 순서

### Phase 1: Realtime DB Presence (우선순위: 높음)
1. Firebase Console에서 Realtime Database 활성화
2. 보안 규칙 설정
3. 기사앱에 Presence 구현
4. 콜매니저앱에 Presence 구현
5. 고객앱에 Presence 구현

### Phase 2: 도착 ACK 시스템 (우선순위: 높음)
1. Cloud Functions에 알림 상태 저장 로직 추가
2. 기사앱에 도착 ACK 전송 구현
3. 콜매니저앱에 도착 ACK 전송 구현
4. 고객앱에 도착 ACK 전송 구현

### Phase 3: 자동 재전송 (우선순위: 높음)
1. Cloud Functions에 재전송 스케줄러 구현
2. Presence 확인 로직 구현
3. 실패 알림 전송 구현

### Phase 4: 콜매니저 경고 UI (우선순위: 중간)
1. 실패 알림 FCM 수신 처리
2. 경고 팝업 UI 구현
3. 전화걸기/다른 기사 배차 버튼 연동

---

## 6. 테스트 시나리오

### 6.1 정상 케이스
1. 배차 → 기사앱에서 수신 → 도착 ACK → 수락 → 완료
2. 상태 변경 → 콜매니저에서 수신 → 도착 ACK → UI 업데이트

### 6.2 재전송 케이스
1. 배차 → 10초 내 ACK 없음 → 재전송 → ACK → 완료
2. 배차 → 2회 재전송 후 ACK → 완료

### 6.3 실패 케이스
1. 배차 → 2회 재전송 실패 → 기사 오프라인 → 콜매니저 경고
2. 배차 → 2회 재전송 실패 → 기사 온라인 → 콜매니저 경고 (다른 메시지)

### 6.4 엣지 케이스
1. 재전송 중 기사가 온라인 전환 → 다음 재전송에서 성공
2. ACK 전송 중 네트워크 끊김 → 재연결 후 재전송

---

## 7. 비용 예상 (3개 사무실 기준)

### Realtime Database
| 항목 | 무료 티어 | 예상 사용량 |
|------|----------|------------|
| 동시 연결 | 100개 | ~20개 |
| 저장 용량 | 1GB | ~1MB |
| 다운로드 | 10GB/월 | ~100MB |

### Firestore (notifications)
| 항목 | 무료 티어 | 예상 사용량 |
|------|----------|------------|
| 쓰기 | 20,000/일 | ~2,000/일 |
| 읽기 | 50,000/일 | ~1,000/일 |

### 총 비용: **$0** (무료 티어 내)

---

## 8. 롤백 계획

문제 발생 시 빠른 롤백을 위한 계획:

1. **Presence 비활성화**: 앱에서 Presence 코드 주석 처리
2. **ACK 비활성화**: Cloud Functions에서 상태 저장 스킵
3. **재전송 비활성화**: 스케줄러 함수 비활성화
4. **경고 UI 비활성화**: 팝업 표시 조건 false로 변경

각 기능은 독립적으로 비활성화 가능하도록 설계

---

## 9. 향후 확장

### 2000개 사무실 규모 도달 시
1. Realtime Database → 전용 WebSocket 서버로 전환 검토
2. Firestore notifications → 별도 알림 서버 구축 검토
3. 모니터링 대시보드 구축 (알림 성공률, 평균 응답 시간 등)

---

## 10. 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-02-02 | 1.0 | 최초 작성 |
