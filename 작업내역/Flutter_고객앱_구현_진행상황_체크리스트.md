# Flutter 고객앱 구현 진행상황 체크리스트

**최종 업데이트**: 2025-11-18
**현재 완성도**: Phase 0 완료 (약 65%)
**다음 단계**: Phase 4B 통합 테스트 (실기기 테스트)

---

## ✅ Phase 0: 플로우 수정 (완료 - 2025-11-18)

### 문제 발견
- ❌ Flutter 앱 플로우가 Android 앱과 달랐음
- ❌ PhoneInputScreen만 있고 ProfileSetupScreen이 없었음
- ❌ DeviceFingerprint의 플랫폼 확인 로직 오류

### Android 앱 플로우 (정상)
```
앱 실행 → 익명 인증 → Attribution 매칭 → Firestore 프로필 확인
  ↓
프로필 없음 → ProfileSetupScreen (닉네임/전화번호/주소 입력)
  ↓
프로필 있음 → MainNavigation
```

### 기존 Flutter 앱 플로우 (잘못됨)
```
앱 실행 → PhoneInputScreen (전화번호만 입력) → 익명 인증 → Attribution 매칭
  ↓
MainNavigation (닉네임, 주소 입력 없음)
```

### 수정 작업
- [x] DeviceFingerprint.dart 수정
  - [x] `dart:io` import 추가
  - [x] `Platform.isAndroid` 사용으로 변경
  - [x] `Platform.isIOS` 사용으로 변경

- [x] ProfileSetupScreen.dart 생성 (Android 앱과 동일)
  - [x] ProfileSetupState 클래스 생성
  - [x] ProfileSetupNotifier 생성 (Riverpod)
  - [x] 닉네임 입력 필드 (필수)
  - [x] 전화번호 입력 필드 (필수)
  - [x] 주소 입력 필드 (선택)
  - [x] Firestore customers 컬렉션에 저장
  - [x] 토큰 클레임 기능
  - [x] 기사 추천 정보 저장

- [x] main.dart 플로우 수정
  - [x] PhoneInputScreen 제거
  - [x] 익명 인증 → Attribution → 프로필 확인 순서
  - [x] _checkProfile() 메서드 추가
  - [x] ProfileSetupScreen 연동
  - [x] AttributionResult import 추가

- [x] PhoneInputScreen.dart 삭제
  - [x] 파일 삭제 완료

### 빌드 및 배포
- [x] Flutter analyze (9 issues - 모두 info/warning)
- [x] Debug APK 빌드 성공 (42.8s)
- [x] APK 복사 (public/customer_app_flutter.apk)
- [x] 랜딩 페이지 빌드
- [x] Firebase Hosting 배포

### 수정된 Flutter 앱 플로우 (정상)
```
앱 실행 → 익명 인증 → Attribution 매칭 → Firestore 프로필 확인
  ↓
프로필 없음 → ProfileSetupScreen (닉네임/전화번호/주소 입력)
  ↓
프로필 있음 → MainNavigation
```

**이제 Android 앱과 100% 동일한 플로우입니다!**

---

## ✅ Phase 4A: UI 골격 완성 (완료)

### MainNavigation 구현
- [x] MainNavigation.dart 생성
- [x] BottomNavigationBar 4개 탭 구성
- [x] IndexedStack으로 화면 관리
- [x] 4개 화면 전환 확인

### 화면 골격 생성
- [x] HomeScreen 레이아웃 수정
  - [x] StepCounterCard 추가 (하드코딩)
  - [x] 포인트 카드 추가 (하드코딩)
  - [x] 사무실 정보 표시
  - [x] 콜 요청 버튼
  - [x] 활성 콜 상태 표시

- [x] PointScreen 레이아웃 생성 (하드코딩)
  - [x] 포인트 요약 헤더
  - [x] 등급 진행률 카드
  - [x] 등급별 혜택 안내
  - [x] 포인트 사용 안내

- [x] ProfileScreen 레이아웃 생성
  - [x] 사무실 정보 표시 (Attribution 연결)
  - [x] 앱 버전 표시
  - [x] 전화 걸기 버튼 (url_launcher)
  - [x] 로그아웃 버튼

- [x] CallHistoryScreen 기존 유지

### 패키지 추가
- [x] url_launcher: ^6.3.1
- [x] package_info_plus: 5.0.1

### 빌드 테스트
- [x] Flutter analyze (10 issues - info/warning만)
- [x] Debug APK 빌드 성공

---

## ✅ Phase 4C: 기능 실제 구현 (완료)

### 1단계: 포인트 데이터 모델
- [x] CustomerGrade enum 생성
  - [x] 4개 등급 (Bronze/Silver/Gold/VIP)
  - [x] 등급별 아이콘, 색상, 적립률
  - [x] fromCallCount() 메서드
  - [x] calculatePoints() 메서드

- [x] CustomerPoints 모델 생성
  - [x] 8개 필드 (currentPoints, totalEarned, totalUsed, grade, totalCalls, etc.)
  - [x] Firestore 직렬화/역직렬화
  - [x] calculateEarnPoints() 메서드
  - [x] canUsePoints() 메서드
  - [x] getCallsToNextGrade() 메서드

- [x] PointTransaction 모델 생성
  - [x] TransactionType enum (earn/use/expire/cancel/admin)
  - [x] 거래 내역 필드
  - [x] factory 생성자 (earn, use)
  - [x] Firestore 직렬화/역직렬화

### 2단계: Repository 계층
- [x] PointRepository 구현
  - [x] Firestore CRUD 작업
  - [x] getCustomerPoints()
  - [x] createCustomerPoints()
  - [x] updateCustomerPoints()
  - [x] addPointTransaction()
  - [x] getPointTransactions()
  - [x] observeCustomerPoints() - 실시간 스트림
  - [x] observePointTransactions() - 실시간 스트림
  - [x] getPointTransactionsByDateRange()
  - [x] getPointTransactionsByType()

### 3단계: Service 계층 (비즈니스 로직)
- [x] PointService 구현
  - [x] getOrCreateCustomerPoints()
  - [x] earnPoints() - Firestore 트랜잭션 사용
    - [x] 등급별 적립률 계산
    - [x] 등급 자동 업그레이드
    - [x] CustomerPoints 업데이트
    - [x] PointTransaction 기록
  - [x] usePoints() - Firestore 트랜잭션 사용
    - [x] 잔액 확인
    - [x] CustomerPoints 차감
    - [x] PointTransaction 기록
  - [x] previewEarnPoints() - 미리보기
  - [x] getAvailablePoints()
  - [x] canUsePoints()
  - [x] observeCustomerPoints() - 스트림 전달
  - [x] observePointTransactions() - 스트림 전달

### 4단계: Provider 계층 (상태 관리)
- [x] PointProvider 구현
  - [x] pointRepositoryProvider
  - [x] pointServiceProvider
  - [x] customerPointsProvider (StreamProvider - 실시간)
  - [x] pointTransactionsProvider (StreamProvider - 실시간)
  - [x] PointNotifier (StateNotifier)
    - [x] earnPoints()
    - [x] usePoints()
    - [x] previewEarnPoints()
    - [x] getAvailablePoints()
    - [x] canUsePoints()
  - [x] pointNotifierProvider
  - [x] pointTransactionsByDateRangeProvider (FutureProvider)
  - [x] pointTransactionsByTypeProvider (FutureProvider)

### 5단계: UI 데이터 연결
- [x] PointScreen 실제 데이터 연결
  - [x] customerPointsProvider 사용
  - [x] 실시간 포인트 정보 표시
  - [x] 등급 아이콘/색상 표시
  - [x] 통계 정보 (총 적립, 총 사용, 이용 횟수)
  - [x] 등급 진행률 표시
  - [x] 다음 등급까지 남은 횟수
  - [x] 등급별 혜택 안내
  - [x] 에러 처리 및 로딩 상태

- [x] HomeScreen 포인트 카드 연결
  - [x] customerPointsProvider 사용
  - [x] 실시간 포인트 표시
  - [x] 등급 아이콘 표시
  - [x] 에러 처리 및 로딩 상태

### 빌드 테스트
- [x] Flutter analyze (10 issues - info/warning만)
- [x] Debug APK 빌드 성공

---

## ✅ Attribution 토큰 방식 구현 (추가 완료)

### Android 앱과 동일한 토큰 방식 구현
- [x] `_getAttributionTokenFromFirestore()` 메서드 추가
  - [x] 화면 해상도로 Firestore 검색
  - [x] 24시간 이내 attribution 검색
  - [x] token 필드 추출
  - [x] 모든 regions/offices 순회

- [x] `matchAttribution()` 4단계 매칭 로직
  - [x] 1단계: 파라미터 토큰 확인
  - [x] 2단계: 캐시된 토큰 확인
  - [x] 3단계: Firestore에서 화면 해상도로 attribution 검색 → token 추출
  - [x] 4단계: Fingerprint 방식 fallback

- [x] `_matchByToken()` 응답 파싱 수정
  - [x] Cloud Function 실제 응답 구조 적용
  - [x] AttributionResult 생성

- [x] deprecated 코드 수정
  - [x] window → platformDispatcher.views.first

### 동작 플로우
```
QR 스캔 → 랜딩 페이지 (token 포함)
  ↓
FingerprintJS + token 저장
  regions/{regionId}/offices/{officeId}/attributions/{id}
  { screenResolution, visitorId, token }
  ↓
APK 다운로드 & 설치
  ↓
앱 실행 → matchAttribution()
  ↓
화면 해상도로 attribution 검색 → token 추출
  ↓
matchByToken(token) → attributionTokens/{token} 조회
  ↓
정확한 사무실 정보 반환
```

---

## ✅ 랜딩 페이지 Flutter 앱 다운로드 버튼 추가 (완료)

- [x] Flutter APK를 public 폴더로 복사
  - [x] `customer_app_flutter.apk` 생성

- [x] download/page.tsx 수정
  - [x] "고객앱 다운로드 (기존)" 버튼
  - [x] "고객앱 다운로드 (Flutter 신규) ⭐" 버튼 추가

- [x] Firebase Hosting 배포
  - [x] 랜딩 페이지 빌드
  - [x] `firebase deploy --only hosting`
  - [x] 배포 URL: https://calldetector-5d61e.web.app

---

## ⚠️ Phase 4B: 통합 테스트 (진행 중)

### 테스트 체크리스트
- [x] QR 코드 스캔 가능 여부
- [x] 랜딩 페이지 이동
- [ ] Flutter APK 다운로드 (신규 버튼)
- [ ] APK 설치
- [ ] 앱 실행 후 Attribution 매칭
- [ ] 사무실명 표시 확인
- [ ] 전화번호 입력 후 HomeScreen 진입
- [ ] 포인트 화면 실시간 데이터 확인
- [ ] 프로필 화면 사무실 정보 확인

### Firebase 연동 검증
- [x] Firestore 읽기 권한
- [x] Firestore 쓰기 권한
- [x] Cloud Functions 호출 (matchByToken)
- [x] Anonymous Auth 인증
- [ ] FCM 토큰 저장 (미구현)
- [ ] FCM 메시지 수신 (미구현)

---

## ❌ Phase 5: UI/UX 개선 (미구현)

### 집주소 저장 (미구현)
- [ ] SharedPreferences 설정
- [ ] HomeAddressDialog 생성
- [ ] CallRequestScreen에 "집" 버튼 추가
- [ ] 집주소 빠른 입력 기능

### Geocoding 개선 (미구현)
- [ ] geocoding 패키지 추가
- [ ] LocationService 좌표 → 주소 변환
- [ ] 현재 위치 주소 표시

### 포인트 거래 내역 화면 (미구현)
- [ ] PointHistoryScreen 생성
- [ ] 거래 유형별 필터
- [ ] 기간별 조회
- [ ] 무한 스크롤

### CallRequestScreen 개선 (미구현)
- [ ] 포인트 사용 UI
- [ ] 포인트 사용 가능 금액 표시
- [ ] 포인트 적용 후 최종 금액 계산

### CallHistoryScreen 개선 (미구현)
- [ ] 통계 요약 카드
  - [ ] 총 이용 횟수
  - [ ] 총 이용 금액
  - [ ] 이번 달 이용 횟수
  - [ ] 평균 요금

---

## ❌ Phase 6: 만보기 (미구현)

### Android 만보기 구현 (미구현)
- [ ] Native StepCounterService.kt 작성
- [ ] Foreground Service 구현
- [ ] MethodChannel/EventChannel 연결
- [ ] 걸음수 감지 및 전송

### Flutter 만보기 연동 (미구현)
- [ ] StepCounterService (Dart) 생성
- [ ] MethodChannel 연결
- [ ] Stream 구독
- [ ] 로컬 DB 저장 (sqflite)

### UI 연결 (미구현)
- [ ] StepCounterCard 실제 데이터 연결
- [ ] 일일 목표 표시
- [ ] 진행률 표시
- [ ] 주간/월간 통계

---

## ❌ FCM 푸시 알림 (미구현)

### FCM 토큰 관리 (미구현)
- [ ] FCM 토큰 저장 (Firestore)
- [ ] 토큰 갱신 처리
- [ ] 로그아웃 시 토큰 삭제

### FCM 메시지 수신 (미구현)
- [ ] Foreground 메시지 처리
- [ ] Background 메시지 처리
- [ ] 메시지 타입별 처리
  - [ ] CALL_ASSIGNED (콜 배정)
  - [ ] CALL_ACCEPTED (기사 수락)
  - [ ] CALL_COMPLETED (운행 완료)

### 알림 표시 (미구현)
- [ ] 로컬 알림 생성
- [ ] 알림 클릭 시 화면 이동
- [ ] 알림 아이콘/사운드 설정

---

## 📊 전체 진행상황 요약

### 완료된 Phase
- ✅ **Phase 1-3**: 기본 인증, Attribution, 콜 요청/취소/내역 (100%)
- ✅ **Phase 4A**: UI 골격 완성 (100%)
- ✅ **Phase 4C**: 포인트 시스템 구현 (100%)
- ✅ **추가**: 토큰 방식 Attribution 구현 (100%)
- ✅ **추가**: 랜딩 페이지 Flutter 다운로드 버튼 (100%)

### 진행 중인 Phase
- ⚠️ **Phase 4B**: 통합 테스트 (50% - 실기기 테스트 필요)

### 미완료 Phase
- ❌ **Phase 5**: UI/UX 개선 (0%)
- ❌ **Phase 6**: 만보기 (0%)
- ❌ **FCM**: 푸시 알림 (0%)

### 완성도 통계
- **전체 완성도**: 약 60%
- **핵심 기능**: 75% (인증, Attribution, 콜, 포인트)
- **UI/UX**: 65% (골격 완성, 세부 개선 필요)
- **부가 기능**: 0% (만보기, FCM)

---

## 🎯 다음 작업 우선순위

### 즉시 수행 (High Priority)
1. **Phase 4B 완료**: 실기기에서 Flutter 앱 테스트
   - QR 스캔 → APK 다운로드 → 설치 → 실행
   - Attribution 매칭 확인
   - 포인트 시스템 동작 확인

2. **Phase 5 시작**: UI/UX 개선
   - 집주소 저장 기능
   - 포인트 사용 UI (CallRequestScreen)
   - 거래 내역 화면

### 중기 계획 (Medium Priority)
3. **FCM 구현**: 푸시 알림
   - 토큰 저장
   - 메시지 수신 처리

4. **통계 기능**: CallHistoryScreen 개선
   - 통계 요약 카드

### 장기 계획 (Low Priority)
5. **Phase 6**: 만보기 구현
   - Android Native 서비스
   - Flutter 연동

---

## 📝 테스트 방법

### QR 코드로 테스트하기
1. 콜매니저 앱에서 QR 코드 생성
2. 스마트폰 카메라로 QR 스캔
3. 랜딩 페이지 열림
4. **"고객앱 다운로드 (Flutter 신규) ⭐"** 버튼 클릭
5. `customer_app_flutter.apk` 다운로드
6. APK 설치
7. 앱 실행 → Attribution 자동 매칭 확인

### 직접 APK 다운로드
- URL: https://calldetector-5d61e.web.app/customer_app_flutter.apk

---

## 🔗 관련 문서

- [Flutter 마스터 구현 가이드](./Flutter_마스터_구현_가이드.md)
- [Flutter 전환 마스터플랜 2025](./Flutter_전환_마스터플랜_2025.md)
- [Phase 4C 작업내역](./Flutter_고객앱_Phase4C_구현_작업내역.md)

---

**작성자**: Claude Code
**마지막 업데이트**: 2025-11-18
