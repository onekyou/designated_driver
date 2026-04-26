---
name: 식당업소앱 변환 PLAN (손님앱 in-place 변환)
description: 2026-04-27 결정(owner_app_pivot)의 실 작업 단계 명세. 백업 → 변환 범위 4영역 → 결정 필요 5개 → 작업 순서 → 영업과 보조 맞추기.
type: project
date: 2026-04-27
supersedes: (없음, 신규)
parent_decision: memory/restaurant/owner_app_pivot_2026-04-27.md
---

# 식당업소앱 변환 PLAN (2026-04-27)

> 결정 출처: `owner_app_pivot_2026-04-27.md`
> 본 PLAN은 그 결정의 **실 작업 단계 명세**. 코드 변경 범위·작업 순서·원규씨 결정 필요 항목 5개를 1쪽으로 정리.
> 코드 변경 0. 결정 받은 뒤 백업·코드 작업 진입.

---

## 0. 변환 목표 (한 줄)
손님앱 두 코드베이스(Kotlin + Flutter)를 모두 백업 후 **in-place로** 식당업소앱으로 변환. MVP = 호출(대리/택시) 1탭 + 포인트 잔액 + 가입 화면(식당명·전화·주소).

---

## 1. 백업 단계 (코드 변경 0, 안전·가역)

```bash
cp -r customer_app/         customer_app_backup_2026-04-27/
cp -r customer_app_flutter/ customer_app_flutter_backup_2026-04-27/
```

- 백업 사이즈 검증: `du -sh customer_app_backup_2026-04-27/ customer_app_flutter_backup_2026-04-27/`
- `.gitignore` 추가: `customer_app_backup_*/` `customer_app_flutter_backup_*/` (백업 폴더 git 추적 제외)

**전제**: `customer_app_flutter/`는 78건 unstaged 안에 변경 다수 포함. 백업은 *워킹카피 그대로* 복사됨 → R2 결정 필요.

---

## 2. 변환 코드 변경 범위 4영역

### 2.1 가입 흐름
| 손님앱 (현) | 식당업소앱 (변환 후) |
|---|---|
| Anonymous Auth + QR/Install Referrer 자동 매칭 | 사장님용 인증 (R1 결정 필요) |
| `offices/{o}/customers/{uid}` + `customerInfo/{phone}` | `restaurants/{rid}` (R3 결정 필요) |
| 약관·프로필(이름·전화) | 식당명·대표 전화·위치(주소) |

→ Install Referrer 귀속 메커니즘 **제거** (식당앱은 사무실 코드 또는 사장님 ID로 직접 귀속).

### 2.2 메인 UI
| 손님앱 | 식당업소앱 |
|---|---|
| 다중 화면 (콜 호출·이용내역·등급·포인트) | **단일 화면** (호출만) + 설정 화면 (가입 시 1회) |
| BottomNav·Drawer | 없음 (호출 화면 100%) |

→ 손님앱 잔여 기능(이용내역·등급·푸시 설정·로그아웃·온보딩) 전부 제거. 마스터 §10.2 *호출버튼 1탭 후 더 이상 어떤 작업도 필요 없게* 원칙.

### 2.3 Firestore 데이터 모델
- 식당 컬렉션 위치 → R3 결정 필요
- 포인트 잔액 위치:
  - 옵션 X: 식당 문서 안 필드 (`restaurants/{rid}.points` Number) — 단순, 트랜잭션 가벼움
  - 옵션 Y: 별도 문서 누적 (`restaurants/{rid}/pointTransactions/{epoch}`) — 이력 보존, 환금성 0 검증 가능
  - **권장**: Y. 환금성 0 정당성 + 단골 선물 흐름 추후 추가 시 이력 필요.

### 2.4 FCM·권한
| 항목 | MVP (v0) | v1+ |
|---|---|---|
| FCM 수신 | **없음** (호출만 보냄, 응답 알림 X) | "기사 배정", "도착 5분 전" |
| 위치 권한 | 가입 시 1회만 (지속 권한 X) | 동일 |
| 전화 권한 | **필요** (택시 v0 단순 dial) | API 연동 후 dial 불필요 |
| 푸시 권한 | **불필요** (v0 FCM 없음) | v1+ 필요 |

→ 손님앱 권한 매니페스트에서 **3개 제거** (푸시·연속위치·전화수신), 1개 추가 또는 유지(전화 dial = `CALL_PHONE`).

---

## 3. 작업 순서 (Flutter 우선)

1. **백업** (Kotlin + Flutter, R2 결정 후)
2. **Flutter 식당앱 MVP** — 가입·호출 화면·포인트 표시 (Android+iOS 동시)
3. **Kotlin 식당앱 MVP** — Android만, 빠른 필드 테스트 (Flutter 빌드 대기 0). Firestore 경로·FCM payload는 Flutter와 동일
4. **(맥미니 도착 후) iOS 부분 Swift 교체** — Flutter iOS → Swift 네이티브

---

## 4. 결정 — 원규씨 (5개, 2026-04-27 확정)

**전부 권장 그대로 채택**.

| # | 항목 | 채택 | 의미 |
|---|------|------|------|
| **R1** | 식당앱 인증 방식 | **(b) 사무실 발급 코드** | 영업 시 현장 직접 설치 흐름 가장 가벼움 |
| **R2** | customer_app_flutter 78건 unstaged 변경 백업 처리 | **(a) 그대로 백업** | Flutter 트랙 보류라 변경 보존 가치 낮음 |
| **R3** | Firestore 식당 컬렉션 위치 | **(a) `offices/{o}/restaurants/{rid}`** | 사무실 4중 노드(§3.4) 정합. v1+에서 (b)로 전환 가능 |
| **R4** | 식당앱 호출 시 콜 생성 경로 | **(a) 기존 `offices/{o}/calls` 그대로** | call_manager·call_detector·driver_app 흐름 변경 0. 식당이 *생성자*로 추가될 뿐 |
| **R5** | 택시 v0 단순 dial — 콜택시 회사 번호 매핑 | **(c) 식당이 직접 입력** | 가입 시 식당이 자기 단골 콜택시 입력. 협상 비용 0 |

---

## 5. 영업과 보조 맞추기 (가속·감속 트리거)

| 영업 진척 | 코드 작업 |
|---|---|
| 영업 0곳 (현재) | 본 PLAN 작성 + 결정 받기까지 |
| 첫 식당 미팅 약속 | 백업 실행 + Flutter MVP 진입 |
| 첫 식당 깔림 | Kotlin 병행, R3(누름 카운트) 측정 시작 |
| 5곳 깔림 | 안정화·v1 진입 |

→ 식당이 0곳 깔린 지금 코드 변환 가속은 시기상조. PLAN은 *준비물*이고, 실 코드 작업은 영업 미팅(체크리스트 B-1, B-2) 진척과 보조 맞춰.

---

## 6. 다음 1단계 (PLAN 작성 후)

1. 원규씨에게 R1~R5 결정 요청 (1회)
2. 결정 후 **백업 명령 실행** (안전·가역, 코드 변경 0)
3. 백업 검증 → 변환 범위 4영역을 sub-agent 위임:
   - manager-analyst (Firestore 경로·콜 생성 흐름)
   - firebase-analyst (FCM 변경·CF 영향)
   - kotlin-expert (customer_app Kotlin 변환)
   - flutter-expert (customer_app_flutter 변환)
4. **단, 영업 첫 미팅 약속 전엔 코드 변경 진입 보류**. 영업 트리거 받고 가속.

---

## 7. 본 PLAN의 한계 (정직)

- 손님앱(Kotlin) versionCode 12가 운영 중인데 *실 사용자 수 미확인*. 백업 후 in-place 변환 시 기존 사용자 영향은 추측 — 백업 단계에서 Play Console 대시보드 확인 필요 (R2 결정과 보조).
- Flutter 트랙 보류로 customer_app_flutter는 *덜 검증된 인프라*. 변환 시 그 한계 그대로 옮겨감 — Flutter MVP 진입 시 재검증 필요.
- v1+ 기능(FCM 알림·다중 식당 이동·자동 배차)은 본 PLAN 범위 외. MVP 굳은 뒤 별도 PLAN.

---

**PLAN 끝.** 다음: 원규씨 R1~R5 결정 → 백업 명령 → MVP 코드 작업.
