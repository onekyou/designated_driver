# iOS 손님앱 (Swift + App Clip) 개발 계획

## 개요
iOS 손님앱을 Swift 네이티브로 개발. App Clip으로 QR 귀속 문제를 근본적으로 해결.
"설치 전 귀속"이 아니라 **"설치 없이 귀속"**.

---

## App Clip 플로우

```
1. 기사가 고객에게 QR 보여줌
2. 고객 iPhone 카메라로 스캔
3. App Clip 카드 표시 → "열기" 탭
4. App Clip 즉시 실행 (URL 파라미터에서 사무실 코드 자동 추출)
5. 사무실 자동 연결 → 콜 요청 가능
6. 풀앱 설치 유도 배너 표시
7. 풀앱 설치 시 App Group으로 데이터 공유 → 재로그인 불필요
```

---

## 화면 구성

### 풀앱 (~5개)
| 화면 | 기능 |
|------|------|
| HomeScreen | 콜 요청 + 실시간 배차/기사 상태 |
| ProfileScreen | 닉네임, 주소, 전화번호 |
| HistoryScreen | 이용 내역 |
| PointsScreen | 포인트 잔액 + 등급 (브론즈/실버/골드/VIP) + 적립/사용 내역 |
| SettingsScreen | 푸시 설정, 로그아웃 |

### App Clip (~2개, 10MB 제한)
| 화면 | 기능 |
|------|------|
| WelcomeScreen | 사무실 연결 확인 + 간단 프로필 입력 (닉네임, 전화번호) |
| CallRequestScreen | 콜 요청 + 상태 확인 + 풀앱 설치 배너 |

### 제외 기능
- 만보기
- 술먹기 게임
- 기타 부가 기능

---

## App Clip → 풀앱 데이터 이관 (★ 핵심)

### App Group 컨테이너
```
App Group ID: group.com.designated.customer
공유 저장소: UserDefaults(suiteName: "group.com.designated.customer")
```

### 공유 데이터
| 키 | 값 | 용도 |
|---|---|---|
| `provinceId` | Firestore 경로 | 사무실 위치 |
| `cityId` | Firestore 경로 | 사무실 위치 |
| `officeId` | Firestore 경로 | 사무실 매칭 |
| `authUid` | Firebase Auth UID | 고객 식별 |
| `customToken` | Firebase Custom Token | 풀앱 재인증 |
| `phoneNumber` | 인증된 전화번호 | 프로필 |
| `driverId` | 추천 기사 ID | 귀속 추적 |

### Firebase Auth 토큰 이관
Firebase Auth의 `currentUser`는 App Group으로 직접 공유 안 됨.

**해결 방법**:
1. App Clip에서 Anonymous Auth로 로그인 → UID 획득
2. Cloud Function `generateCustomToken(uid)` 호출 → Custom Token 반환
3. Custom Token을 App Group UserDefaults에 저장
4. 풀앱 첫 실행 → App Group에서 Custom Token 읽기
5. `Auth.auth().signIn(withCustomToken:)` → 같은 UID로 인증
6. 재로그인 없이 기존 데이터 접근 가능

---

## QR URL + App Clip 설정

### 기존 QR URL (변경 없음)
```
https://calldetector-5d61e.web.app/?r={provinceId}&o={officeId}&d={driverId}&dn={driverName}
```

### Apple App Site Association (AASA)
`calldetector-5d61e.web.app/.well-known/apple-app-site-association`:
```json
{
  "appclips": {
    "apps": ["TEAMID.com.designated.customer.ios.Clip"]
  }
}
```

### App Clip Card (App Store Connect)
- Title: "콜마당 - 대리운전"
- Subtitle: "QR 스캔으로 바로 콜 요청"
- Action: "열기"
- URL 패턴: `https://calldetector-5d61e.web.app/?*`

### 랜딩페이지 메타태그 추가
```html
<meta name="apple-itunes-app"
      content="app-clip-bundle-id=com.designated.customer.ios.Clip,
               app-id=XXXXXXXXXX">
```

---

## Kotlin 손님앱 참조 매핑

| Kotlin | Swift (예상) | 핵심 로직 |
|--------|-------------|----------|
| `MainActivity.kt` (온보딩) | `AppCoordinator.swift` | 상태 머신 (QR→약관→인증→프로필→메인) |
| `HomeScreen.kt` (콜 요청) | `HomeView.swift` | 콜 생성 + 실시간 상태 |
| `ProfileSetupViewModel.kt` | `ProfileViewModel.swift` | 프로필 CRUD |
| `PhoneAuthViewModel.kt` | `PhoneAuthViewModel.swift` | 전화번호 인증 |
| `PreferencesManager.kt` | `PreferencesManager.swift` | SharedPreferences → UserDefaults |

---

## 기술 스택

- **UI**: SwiftUI
- **아키텍처**: MVVM
- **Firebase**: firebase-ios-sdk (Auth + Firestore + Messaging)
- **QR 처리**: App Clip Invocation URL에서 자동 추출
- **로컬 저장**: UserDefaults + App Group UserDefaults + Keychain
- **최소 타겟**: iOS 16.0

---

## Apple Developer 설정 체크리스트

- [ ] App ID 등록: `com.designated.customer.ios`
- [ ] App Clip ID 등록: `com.designated.customer.ios.Clip`
- [ ] App Group 등록: `group.com.designated.customer`
- [ ] Associated Domains 활성화
- [ ] APNs 인증서 (또는 APNs Auth Key)
- [ ] AASA 파일 호스팅 (Firebase Hosting)
- [ ] App Store Connect에서 App Clip Experience 등록

---

## 개발 순서 (제안)

1. Xcode 프로젝트 생성 (풀앱 + App Clip 타겟)
2. Firebase 연동 + Auth (Anonymous + Phone)
3. App Clip: URL 파라미터 추출 → 사무실 연결
4. App Clip: 콜 요청 + 상태 확인
5. 풀앱: 프로필 + 이용내역 + 포인트/등급
6. App Group 데이터 공유 (App Clip → 풀앱)
7. Custom Token 이관 (CF 생성 필요)
8. FCM 푸시 알림
9. AASA + App Clip Card 설정
10. 랜딩페이지 iOS 메타태그 추가
