---
name: 픽업기사앱 기획안
description: pickup_driver_app 기획 - 매칭 기반 설계, 상황판 공유, DELIVER/RETRIEVE 자동 판단
type: project
---

## 픽업기사앱 기획안 (2026-03-17 승인)

**Why:** 관리자가 전화로 픽업기사에게 지시하는 비효율을 디지털화
**How to apply:** 구현 시 `C:\Users\kala1\.claude\plans\magical-hopping-sphinx.md` 참조

### 핵심 설계 원칙
1. 매니저는 "픽업기사↔대리기사" **매칭만** 한다 (DELIVER/RETRIEVE 선택 없음)
2. 송출/회수 판단은 대리기사의 콜 상태로 **시스템이 자동 결정**
3. 픽업기사는 **상황판**(콜매니저 대시보드 읽기 전용)으로 상황 파악
4. **지도 불필요** — 주소 텍스트만 표시
5. **1:N 유연 매칭** — 이동 중 추가/재배정 가능

### 확정 사항
- 정산 불필요
- 픽업기사 개인폰 사용
- 완료 클릭 → 관리자 화면 상태 변경 반영
- 기사 직접 복귀 시 매칭 없이 처리

### Firestore
- 신규: `pickup_assignments/{id}` (pickupDriverId, driverId, status: ACTIVE/COMPLETED/CANCELLED)
- 기존: `pickup_drivers/{uid}`에 fcmToken, status 필드 추가

### 구현 Phase
1. MVP: Firestore + 콜매니저 배정 UI + 픽업기사앱 (로그인+내담당+상황판) + CF FCM
2. 편의: 재배정/취소 UI, 배차 시 동시 배정, 이력 조회
3. 고도화: 실시간 위치, 경로 최적화, 통계

### 상세 기획 파일
`C:\Users\kala1\.claude\plans\magical-hopping-sphinx.md`
