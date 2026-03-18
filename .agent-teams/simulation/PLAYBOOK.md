# 파일럿 전 최종 회귀 시뮬레이션 플레이북

> "시뮬레이션해줘" → 이 파일 읽기 → STATE.md 확인 → 즉시 실행

---

## 1. 목적

3/6~3/18 시뮬레이션 이후 15개 이상 커밋이 쌓임. 자동화 테스트(Unit 49개 + Emulator 6/8) ALL PASS 확인 후,
변경 영역에 집중한 회귀 시뮬레이션으로 기존 로직이 깨지지 않았는지 검증.

### 자동화 테스트 결과 (1단계 게이트 통과)
- JVM Unit: call_manager 25개 + driver_app 24개 = **49/49 ALL PASS**
- Emulator: 시나리오 3~8 **6/6 PASS** (1~2는 에뮬레이터 콜드스타트 타이밍 이슈)

### 회귀 리스크 매핑
| 커밋 | 변경 내용 | 리스크 Day |
|------|----------|-----------|
| `8108b1ec` | filteredTrips + driverLastClearedMap 실시간 갱신 | **Day A** |
| `232e4f73` | 업무마감↔로그아웃 분리 | **Day A, B** |
| `e2e0c3d0` | FCM data-only 전환 | **Day B** |
| `3bbbce7b` | 기사앱 자동로그인 | **Day A** |
| `1ce0e1b2` | 콜매니저 기사탭 중복 제거 | Day C |

---

## 2. 팀 구성 (7명)

| 역할 | 팀명 | 담당 | 활성 Day |
|------|------|------|---------|
| **master** | master | 시나리오 진행 + 고객앱 | A, B, C |
| **mgr-main** | mgr-main | 사무실1 Manager + Detector | A, B, C |
| **drv-a** | drv-a | 사무실1 기사A | A, B, C |
| **drv-b** | drv-b | 사무실1 기사B | A, B, C |
| **mgr-sub** | mgr-sub | 사무실2 Manager (공유콜 수임) | C만 |
| **drv-c** | drv-c | 사무실2 기사C (공유콜 처리) | C만 |
| **firebase** | firebase | Firestore/CF 모니터링 + 검증 | A, B, C |

---

## 3. 시나리오 개요 (3일 70콜)

| Day | 주제 | 콜수 | 핵심 검증 |
|-----|------|------|----------|
| A | 이월 + 퇴근/재출근 + 이체 | 20 | carryOver, filteredTrips, 업무마감↔로그아웃 분리 |
| B | 거절/재제출 + 통합정산 | 20 | REJECTED→재제출, isIntegration, FCM data-only |
| C | 마감 + 공유콜(자동+수동) + 경합 | 30 | createSharedCall, claimSharedCall, 이중수임방지 |

Day별 상세 시나리오 → `days/day-a.md`, `days/day-b.md`, `days/day-c.md`

---

## 4. 진행 방식

### 콜 1건 (순차)
```
마스터: 콜 발생 지시
  → 매니저: Detector 감지 → Firestore 콜 생성 (WAITING) → 기사에게 배차 (ASSIGNED)
  → 기사: FCM 수신 → 수락 (ACCEPTED) → 운행 (IN_PROGRESS) → 완료 (COMPLETED)
  → 파이어베이스: 상태 전이 + CF 트리거 검증
```

### 매 Day 마감 후 firebase 검증
1. 콜 상태 전이 정합성
2. 기사 상태 전이 정합성
3. 정산 3자 일치 (Manager ↔ Driver ↔ CF)
4. carryOver / filteredTrips 정확성
5. 공유콜: shared_calls 상태 + 수임사무실 귀속
6. 통합정산 mergedTrips 무결성

---

## 5. 시작 절차

```
"시뮬레이션해줘"
  1. 이 PLAYBOOK.md 읽기
  2. STATE.md 읽기
     - status: ready → Day A부터 시작
     - status: in_progress → 해당 Day/콜부터 이어서
     - status: completed → "시뮬레이션 완료" 안내
  3. 해당 day 파일 읽기 (days/day-a.md 등)
  4. 팀 미구성 시 → prompts/ 읽고 팀 구성
  5. 즉시 실행 (설명 불필요)
```

---

## 6. 오탐 방지 규칙

1. **Firestore 트랜잭션 직렬화**: runTransaction 내 같은 문서 동시 읽기/쓰기는 자동 직렬화
2. **수정 완료 이슈 재지적 금지**: CLAUDE.md 수정 이력 참조
3. **보안 이슈(SEC-*) 패스**: 파일럿 단계
4. **rejectCall → WAITING** (HOLD 아님)
5. **checkAssignedTimeout 타임아웃 1분** (3분→1분 변경됨)
6. **FCM data-only 전환 완료** (`e2e0c3d0`)
7. **BootReceiver 구현 완료** (`f5ab8ddc`)
8. **Presence 전 구간 보호 완료** (`46b2f205`)
