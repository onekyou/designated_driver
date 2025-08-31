# Push-to-Talk 전환 계획서

## 📋 프로젝트 개요
- **목표**: Agora 비용 90% 절감 및 실시간 양방향 통신 유지
- **핵심**: PTT 방식 전환 + 자동 채널 참여 시스템
- **예상 기간**: 3일 (Call Manager 1.5일, Pickup App 1일, 테스트 0.5일)

## 🎯 핵심 목표
1. **비용 절감**: 상시 연결 → PTT 방식 (실제 통화시만 과금)
2. **실시간 통신**: 상대방 PTT 시 자동 채널 참여
3. **연결 지연 최소화**: 0.5초 이내 연결 목표
4. **배터리 최적화**: 유휴시 연결 해제

## 📐 시스템 아키텍처

```
┌──────────────┐     Firebase Realtime DB      ┌──────────────┐
│ Call Manager │ ◄─────────────────────────────► │  Pickup App  │
│     (PTT)    │      PTT Session Sync          │     (PTT)    │
└──────┬───────┘                                └──────┬───────┘
       │                                                │
       └────────────────► Agora SDK ◄──────────────────┘
                         (Audio Channel)
```

## 🔄 PTT 동작 흐름

### 1. 평상시 (대기 상태)
- 모든 사용자 Agora 연결 해제
- Firebase PTT 세션 모니터링만 활성
- 비용: 0원

### 2. PTT 시작 (A가 버튼 누름)
```
A: PTT 버튼 누름
   ↓
Firebase에 세션 생성 {active: true, speakerId: A}
   ↓
B, C, D가 Firebase 변경 감지
   ↓
B, C, D 자동 채널 참여 (수신 모드)
   ↓
A 음성 송신 시작
```

### 3. PTT 종료 (A가 버튼 놓음)
```
A: PTT 버튼 놓음
   ↓
Firebase 세션 업데이트 {active: false}
   ↓
10초 타이머 시작
   ↓
10초 내 다른 PTT 없으면 모두 연결 해제
```

## 📊 Firebase 데이터 구조

```javascript
// Realtime Database 구조
ptt_sessions/
└── {regionId}/
    └── {officeId}/
        ├── active: boolean          // PTT 활성 여부
        ├── speakerId: string        // 현재 말하는 사람 ID
        ├── speakerName: string      // 현재 말하는 사람 이름
        ├── channelName: string      // Agora 채널명
        ├── timestamp: number        // 시작 시간
        ├── participants/            // 참여자 목록
        │   └── {userId}/
        │       ├── name: string
        │       ├── joinedAt: number
        │       └── status: string  // listening, speaking, idle
        └── lastActivityTime: number // 마지막 활동 시간
```

## 🛠️ 구현 단계

### Phase 1: 기반 구조 구축 (Day 1 오전)
1. **Firebase Realtime Database 설정**
   - PTT 세션 구조 생성
   - 보안 규칙 설정
   - 실시간 동기화 테스트

2. **PTTOptimizedConnectionManager 완성**
   - 빠른 연결 최적화
   - 캐싱 시스템 구현
   - 자동 재연결 로직

### Phase 2: Call Manager 구현 (Day 1 오후 ~ Day 2 오전)
1. **PTTAutoJoinManager 구현**
   - Firebase 세션 모니터링
   - 자동 채널 참여 로직
   - 상태 관리

2. **기존 시스템 통합**
   - PTTConnectionManager → PTTOptimizedConnectionManager 교체
   - PTTManager 리팩토링
   - BackgroundPTTService 최적화

3. **UI 업데이트**
   - PTT 상태 표시
   - 참여자 목록 표시
   - 연결 상태 인디케이터

### Phase 3: Pickup App 구현 (Day 2 오후)
1. **동일한 PTT 시스템 적용**
   - PTTAutoJoinManager 포팅
   - PTTOptimizedConnectionManager 통합
   - Firebase 세션 동기화

2. **픽업 기사 특화 기능**
   - 우선순위 PTT (긴급 호출)
   - 그룹별 채널 분리

### Phase 4: 최적화 및 테스트 (Day 3)
1. **성능 최적화**
   - 연결 속도 측정
   - 메모리 사용량 최적화
   - 배터리 소모 테스트

2. **통합 테스트**
   - 다중 사용자 시나리오
   - 네트워크 불안정 상황
   - 백그라운드/포그라운드 전환

## 💰 예상 효과

### 비용 절감
- **현재**: 24시간 × 60분 × 30일 = 43,200분/월 과금
- **PTT 전환 후**: 실제 통화 시간만 (약 4,320분/월 예상)
- **절감률**: 90% 이상

### 성능 개선
- **연결 지연**: 2-3초 → 0.5초
- **배터리 사용**: 70% 감소
- **네트워크 트래픽**: 80% 감소

## ⚠️ 주의사항

1. **Firebase 실시간 동기화**
   - 네트워크 지연 고려
   - 오프라인 처리

2. **Agora 토큰 관리**
   - 토큰 만료 대비
   - 사전 갱신 로직

3. **동시 PTT 처리**
   - 우선순위 관리
   - 충돌 방지

## 📈 성공 지표

- [ ] 첫 연결 시간 < 0.5초
- [ ] 재연결 시간 < 0.2초
- [ ] 월 Agora 비용 90% 절감
- [ ] 배터리 사용량 70% 감소
- [ ] 사용자 체감 지연 없음

## 🔧 기술 스택
- **Android**: Kotlin, Coroutines
- **실시간 통신**: Agora SDK 4.5.0
- **데이터 동기화**: Firebase Realtime Database
- **푸시 알림**: Firebase Cloud Messaging

## 📅 일정
- **Day 1**: Firebase 구조 + Call Manager PTT 시스템
- **Day 2**: Call Manager 완성 + Pickup App 시작
- **Day 3**: Pickup App 완성 + 테스트

---
작성일: 2025-08-28
버전: 1.0