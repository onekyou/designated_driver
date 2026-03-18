# 검증 포인트

---

## 1. 매 콜 검증 (파이어베이스 담당)

- [ ] 콜 상태 전이 정확성 (WAITING→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED)
- [ ] 기사 상태 변화 정확성 (WAITING→ASSIGNED→ACCEPTED→ON_TRIP→WAITING)
- [ ] CF 트리거 발동 여부 (oncallassigned, onCallStatusChanged)
- [ ] FCM 발송 대상 정확성 (기사/고객/매니저)

## 2. 매일 마감 검증

- [ ] settlementSessions 정합성 (totalFare = totalCash + totalCard + totalCredit + totalPoints)
- [ ] 기사별 dailySettlement 정확성
- [ ] carryOver 계산 일치 (Manager vs Driver)
- [ ] 포인트 잔액 정합성

## 3. 공유콜 검증

- [ ] shared_calls 문서 정확한 생성 (sourceOfficeId, status, callData)
- [ ] claimSharedCall 트랜잭션 정상 동작
- [ ] 이중 수임 방지
- [ ] 공유콜 정산이 수임 사무실에 귀속

## 4. 최종 검증 (Day 10)

- [ ] 10일간 총 1000콜 상태 전이 정합성
- [ ] 전 기사 이월금 최종 잔액 0원
- [ ] 포인트 총 적립/사용/환불 일치
- [ ] 공유콜 원본 ↔ 수임 사무실 데이터 일관성

---

## 구현 항목 체크리스트

- [x] 마스터 에이전트 프롬프트 (`prompts/master.md`)
- [x] 매니저 에이전트 프롬프트 (`prompts/manager.md`)
- [x] 기사 에이전트 프롬프트 (`prompts/driver.md`)
- [x] 파이어베이스 에이전트 프롬프트 (`prompts/firebase.md`)
- [ ] Day별 시나리오 스크립트 (`days/dayNN.md`) - 실행 시 자동 생성
- [ ] Firestore 초기 데이터 설정 (3개 사무실, 6명 기사, 포인트)
