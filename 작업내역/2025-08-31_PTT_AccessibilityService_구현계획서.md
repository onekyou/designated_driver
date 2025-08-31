# PTT AccessibilityService 구현 계획서

## 📅 작성일시
2025-08-31

## 🎯 목표
Call Manager 앱에 AccessibilityService 기반 볼륨 PTT 시스템을 구현하여 안정적이고 효율적인 무전 기능 제공

## 🔑 핵심 원칙
1. **권한 최소화 (Least Privilege)**: PTT 기능에 필요한 최소 권한만 요청
2. **Agora SDK 베스트 프랙티스**: 백그라운드 오디오 처리 최적화
3. **사용자 친화적 설정**: 수동 설정을 쉽게 할 수 있는 가이드 제공
4. **안정성**: Android 15 대응 및 서비스 자동 복구

## 📋 구현 단계

### Step 1: 권한 최소화된 설정 파일 구현
- **목표**: 불필요한 권한 제거하고 PTT에 필요한 최소 권한만 설정
- **주요 작업**:
  - ptt_accessibility_service_config.xml 수정
  - AndroidManifest.xml 권한 정리
  - strings.xml에 서비스 설명 추가

### Step 2: PTTManagerService Agora SDK 최적화
- **목표**: Agora SDK 백그라운드 모드 최적화 및 오디오 포커스 관리
- **주요 작업**:
  - 오디오 포커스 관리 시스템 구현
  - 백그라운드 오디오 처리 활성화
  - 웨이크락 관리
  - 에코 캔슬레이션 및 노이즈 억제

### Step 3: 접근성 서비스 가이드 UI 구현
- **목표**: 사용자가 쉽게 접근성 서비스를 설정할 수 있도록 가이드 제공
- **주요 작업**:
  - PTTAccessibilityGuideActivity 생성
  - 단계별 설정 가이드 UI
  - 실시간 서비스 상태 확인
  - 자동 설정 이동 기능

### Step 4: 서비스 모니터링 시스템 구현
- **목표**: 서비스 상태 실시간 모니터링 및 자동 복구
- **주요 작업**:
  - AccessibilityServiceMonitor 클래스 구현
  - 서비스 비활성화 자동 감지
  - 재활성화 알림 시스템
  - Quick Settings Tile 추가

### Step 5: 통합 테스트 및 검증
- **목표**: 전체 시스템 통합 테스트 및 안정성 검증
- **테스트 항목**:
  - 볼륨키 이벤트 캡처 테스트
  - 백그라운드 PTT 송수신 테스트
  - 서비스 자동 복구 테스트
  - 다양한 Android 버전 호환성 테스트

## 📁 파일 구조

```
call_manager/
├── app/src/main/
│   ├── java/com/designated/callmanager/
│   │   ├── ptt/
│   │   │   ├── service/
│   │   │   │   ├── PTTAccessibilityService.kt (수정)
│   │   │   │   ├── PTTManagerService.kt (수정)
│   │   │   │   └── PTTServiceMonitor.kt (신규)
│   │   │   └── ui/
│   │   │       └── PTTAccessibilityGuideActivity.kt (신규)
│   │   └── ui/
│   │       └── settings/
│   │           └── PTTSettingsScreen.kt (수정)
│   └── res/
│       ├── xml/
│       │   └── ptt_accessibility_service_config.xml (수정)
│       ├── layout/
│       │   └── ptt_debug_overlay.xml (신규)
│       └── values/
│           └── strings.xml (수정)
└── AndroidManifest.xml (수정)
```

## 🧪 테스트 계획

### 단위 테스트
- [ ] 볼륨키 이벤트 디바운싱
- [ ] 오디오 포커스 획득/해제
- [ ] 서비스 상태 확인 로직

### 통합 테스트
- [ ] AccessibilityService ↔ PTTManager 통신
- [ ] Agora SDK 백그라운드 송수신
- [ ] 서비스 모니터링 및 복구

### 시나리오 테스트
- [ ] 화면 꺼진 상태에서 PTT
- [ ] 다른 앱 사용 중 PTT
- [ ] 네트워크 전환 시 PTT
- [ ] 서비스 강제 종료 후 복구

## 📊 성공 지표
1. 볼륨키 이벤트 100% 캡처율
2. 백그라운드 PTT 지연시간 < 200ms
3. 서비스 자동 복구 시간 < 30초
4. 사용자 설정 완료율 > 90%

## ⚠️ 리스크 및 대응 방안

### 리스크 1: Android 15 접근성 서비스 자동 비활성화
- **대응**: 주기적 상태 체크 및 사용자 알림

### 리스크 2: 볼륨키 이벤트 차단
- **대응**: FLAG_REQUEST_FILTER_KEY_EVENTS 확실한 설정

### 리스크 3: 백그라운드 오디오 제한
- **대응**: Foreground Service 연동 및 웨이크락 관리

## 📝 구현 순서

1. **Day 1**: Step 1 - 권한 최소화 설정
2. **Day 2**: Step 2 - Agora SDK 최적화
3. **Day 3**: Step 3 - 가이드 UI 구현
4. **Day 4**: Step 4 - 모니터링 시스템
5. **Day 5**: Step 5 - 통합 테스트

## 🔄 진행 상태

- [ ] Step 1: 권한 최소화된 설정 파일 구현
- [ ] Step 2: PTTManagerService Agora SDK 최적화
- [ ] Step 3: 접근성 서비스 가이드 UI 구현
- [ ] Step 4: 서비스 모니터링 시스템 구현
- [ ] Step 5: 통합 테스트 및 검증

## 📌 참고사항
- 모든 변경사항은 git commit으로 관리
- 각 단계별 테스트 결과 문서화
- 사용자 피드백 즉시 반영