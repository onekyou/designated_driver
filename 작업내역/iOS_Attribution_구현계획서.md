# iOS Attribution 구현 계획서

작성일: 2025-11-26

## 목차
1. [iOS와 Android의 차이점](#ios와-android의-차이점)
2. [iOS 구현 방법 옵션](#ios-구현-방법-옵션)
3. [추천 전략](#추천-전략)
4. [상세 구현 계획](#상세-구현-계획)
5. [코드 예시](#코드-예시)

---

## iOS와 Android의 차이점

### Android
```
✅ Play Store Install Referrer API 제공
✅ QR 스캔 → Play Store → 설치 버튼 클릭 시 referrer 자동 저장
✅ 앱 실행 시 Install Referrer API로 정보 자동 수신
✅ 100% 정확한 사무실 매칭
```

### iOS
```
❌ App Store에 Install Referrer API 없음
❌ Apple이 공식 지원하지 않음
⚠️ 대안 방법 필요
```

---

## iOS 구현 방법 옵션

### 방법 1: Universal Links ⭐ 추천

**원리:**
- iOS 9+ 표준 기술
- 특정 도메인 URL이 앱을 실행하도록 설정
- `https://calldetector-5d61e.web.app/install?r=Hongchon&o=xxx` 클릭 시 앱 실행

**앱이 설치된 경우:**
```
QR 스캔 → URL 클릭
→ ✅ 앱 자동 실행 (URL 파라미터 전달)
→ r=Hongchon&o=xxx 파싱
→ 사무실 정보 저장
```

**앱이 미설치인 경우:**
```
QR 스캔 → URL 클릭
→ Safari 웹 페이지 열림
→ "App Store에서 다운로드" 버튼
→ App Store → 설치
→ ⚠️ 설치 후 다시 QR 스캔 필요 (또는 링크 저장)
```

**장점:**
- ✅ iOS 표준 방식
- ✅ 앱 설치 후 완벽하게 작동
- ✅ 100% 정확
- ✅ 추가 SDK 불필요

**단점:**
- ⚠️ 첫 설치 시 2단계 필요 (설치 → 재스캔)
- ⚠️ 사용자가 링크를 다시 클릭해야 함

**구현 복잡도:** 중간

---

### 방법 2: Clipboard (클립보드)

**원리:**
- 랜딩 페이지에서 사무실 정보를 클립보드에 자동 복사
- 앱 실행 시 클립보드 내용 읽어서 파싱

**흐름:**
```
QR 스캔 → 랜딩 페이지
→ JavaScript: navigator.clipboard.writeText("r=Hongchon&o=xxx")
→ App Store → 설치
→ 앱 실행 → UIPasteboard 읽기
→ "r=Hongchon&o=xxx" 파싱
→ 사무실 정보 저장
```

**장점:**
- ✅ 1번의 QR 스캔으로 완료
- ✅ 재스캔 불필요
- ✅ 구현 간단

**단점:**
- ⚠️ 사용자가 다른 것을 복사하면 실패
- ⚠️ iOS 14+ 클립보드 접근 시 알림 표시 ("앱이 클립보드를 읽었습니다")
- ⚠️ 프라이버시 우려
- ⚠️ 클립보드 내용이 덮어씌워질 수 있음

**구현 복잡도:** 낮음

---

### 방법 3: 서드파티 SDK (Branch, AppsFlyer, Adjust)

**원리:**
- 전문 Attribution 서비스 사용
- 기기 Fingerprinting + 서버 추적

**Branch 예시:**
```
QR 코드: https://designateddriver.app.link/Hongchon_qwfde123
→ Branch 서버로 리다이렉트
→ 기기 정보 수집 (IP, User Agent, 시간 등)
→ App Store → 설치
→ 앱 실행 시 Branch SDK가 서버와 통신
→ Fingerprint 매칭 (정확도 80-90%)
→ 사무실 정보 전달
```

**장점:**
- ✅ iOS + Android 통일된 방식
- ✅ 자동 매칭 (재스캔 불필요)
- ✅ 대기업들이 사용하는 검증된 방법
- ✅ 풍부한 Analytics

**단점:**
- ⚠️ 외부 SDK 의존
- ⚠️ 무료 티어 제한
  - Branch: 월 10,000 설치
  - AppsFlyer: 무료 10,000 설치
- ⚠️ 설정 복잡
- ⚠️ 100% 정확도 보장 안 됨 (80-90%)

**비용:**
- Branch Basic: 무료 (월 10K 설치)
- Branch Pro: $299/월
- AppsFlyer: 커스텀 가격

**구현 복잡도:** 높음

---

### 방법 4: Firestore Fallback (현재 방식 유지)

**원리:**
- 랜딩 페이지에서 Firestore에 Attribution 저장
- 앱 실행 시 Firestore에서 매칭

**흐름:**
```
QR 스캔 → 랜딩 페이지
→ Firestore 저장: {
    officeId: "qwfde123",
    regionId: "Hongchon",
    timestamp: ServerTimestamp,
    claimed: false
  }
→ App Store → 설치
→ 앱 실행 → Firestore 조회
→ 최근 5분 이내 + officeId 매칭
→ 사무실 정보 저장
```

**장점:**
- ✅ iOS + Android 동일한 코드
- ✅ 이미 구현되어 있음
- ✅ 외부 의존성 없음
- ✅ 무료
- ✅ 1번의 QR 스캔으로 완료

**단점:**
- ⚠️ 정확도 ~90% (시간 기반 매칭)
- ⚠️ 동시 스캔 시 혼동 가능
- ⚠️ Firestore 읽기 비용

**구현 복잡도:** 낮음 (이미 구현됨)

---

## 추천 전략

### 단계별 접근 (하이브리드)

#### Phase 1: 현재 (테스트 단계)
```
iOS + Android: Firestore 방식
- 시간 기반 매칭 간소화
- screenResolution 제거
- 90%+ 정확도로 충분
```

#### Phase 2: Play Store 배포
```
Android: Install Referrer (100% 정확) ✅
iOS: Firestore 유지 (90%+ 정확)

이유:
- Android는 공식 API로 완벽하게
- iOS는 Firestore로도 충분히 정확 (소규모)
```

#### Phase 3: App Store 배포 (선택)
```
iOS: Universal Links 추가
- 앱 설치 후 재스캔 안내
- 100% 정확도

또는

iOS: Firestore 계속 유지
- 규모가 작으면 90%로도 충분
```

#### Phase 4: 대규모 확장 시 (선택)
```
iOS + Android: Branch SDK
- 수만 명 사용자 규모
- 자동화된 Attribution
- 풍부한 Analytics
```

---

## 상세 구현 계획

### Flutter iOS: Universal Links

#### 1. Apple Developer 설정

**Associated Domains 추가:**
```
1. Apple Developer Console 접속
2. Identifiers → App ID 선택
3. Associated Domains 활성화
4. 저장
```

#### 2. Xcode 설정

**파일:** `ios/Runner/Runner.entitlements`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.developer.associated-domains</key>
    <array>
        <string>applinks:calldetector-5d61e.web.app</string>
    </array>
</dict>
</plist>
```

**파일:** `ios/Runner/Info.plist`
```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleTypeRole</key>
        <string>Editor</string>
        <key>CFBundleURLName</key>
        <string>com.designated.customer</string>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>designateddriver</string>
        </array>
    </dict>
</array>
```

#### 3. 서버 설정

**파일:** `customer_app/landing/public/.well-known/apple-app-site-association`
```json
{
  "applinks": {
    "apps": [],
    "details": [
      {
        "appID": "TEAM_ID.com.designated.customer",
        "paths": [
          "/install",
          "/install/*"
        ]
      }
    ]
  }
}
```

**주의:**
- `TEAM_ID`는 Apple Developer Team ID로 교체
- HTTPS 필수
- Content-Type: application/json
- 확장자 없음

#### 4. Flutter 코드

**패키지 추가:** `pubspec.yaml`
```yaml
dependencies:
  app_links: ^3.5.0
```

**파일:** `lib/core/services/app_links_service.dart`
```dart
import 'package:app_links/app_links.dart';
import 'package:flutter/foundation.dart';
import 'storage_service.dart';

class AppLinksService {
  final _appLinks = AppLinks();
  final _storage = StorageService();

  Future<void> initialize() async {
    // 앱이 종료된 상태에서 링크로 실행
    final initialLink = await _appLinks.getInitialAppLink();
    if (initialLink != null) {
      await _handleAppLink(initialLink);
    }

    // 앱 실행 중 링크 수신
    _appLinks.uriLinkStream.listen((uri) {
      _handleAppLink(uri);
    });
  }

  Future<void> _handleAppLink(Uri uri) async {
    debugPrint('[AppLinks] 수신: $uri');

    if (uri.path != '/install') return;

    final regionId = uri.queryParameters['r'];
    final officeId = uri.queryParameters['o'];
    final driverId = uri.queryParameters['driver'];
    final driverName = uri.queryParameters['driverName'];

    if (regionId != null && officeId != null) {
      await _storage.writeSecure('regionId', regionId);
      await _storage.writeSecure('officeId', officeId);

      if (driverId != null) {
        await _storage.setString('referralDriverId', driverId);
      }
      if (driverName != null) {
        await _storage.setString('referralDriverName', driverName);
      }

      debugPrint('[AppLinks] 사무실 정보 저장: $regionId/$officeId');
    }
  }
}
```

---

### Flutter iOS: Firestore Fallback

**파일:** `lib/features/attribution/repositories/attribution_repository.dart`

```dart
Future<AttributionResult?> matchAttribution() async {
  try {
    // 1. 저장된 사무실 정보 확인 (Universal Link로 받은 것)
    final savedAttribution = await getSavedAttribution();
    if (savedAttribution != null) {
      return savedAttribution;
    }

    // 2. Firestore 시간 기반 매칭 (Fallback)
    final fiveMinutesAgo = DateTime.now().subtract(const Duration(minutes: 5));

    // 모든 지역/사무실 순회
    final regionsSnapshot = await _firestore.collection('regions').get();

    for (final regionDoc in regionsSnapshot.docs) {
      final regionId = regionDoc.id;
      final officesSnapshot = await _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .get();

      for (final officeDoc in officesSnapshot.docs) {
        final officeId = officeDoc.id;

        // 최근 5분 이내 미사용 Attribution 조회
        final query = await _firestore
            .collection('regions')
            .doc(regionId)
            .collection('offices')
            .doc(officeId)
            .collection('attributions')
            .where('claimed', isEqualTo: false)
            .where('createdAt', isGreaterThan: Timestamp.fromDate(fiveMinutesAgo))
            .orderBy('createdAt', descending: true)
            .limit(1)
            .get();

        if (query.docs.isNotEmpty) {
          final doc = query.docs.first;

          // claimed = true로 업데이트
          await doc.reference.update({'claimed': true});

          final result = AttributionResult(
            regionId: regionId,
            officeId: officeId,
            officeName: officeDoc.data()?['name'] as String? ?? 'Unknown',
            officePhone: officeDoc.data()?['phoneNumber'] as String? ?? '',
            referralDriverId: doc.data()['referralDriverId'] as String?,
            referralDriverName: doc.data()['referralDriverName'] as String?,
          );

          await _saveAttributionData(result);
          return result;
        }
      }
    }

    // 매칭 실패
    return null;
  } catch (e) {
    debugPrint('[Attribution] 매칭 오류: $e');
    return null;
  }
}
```

---

## 사용자 경험 시나리오

### Android (Play Store) - 최종 목표
```
사용자: QR 스캔
→ Play Store 페이지 열림
→ "설치" 버튼 클릭
→ 설치 중... (Install Referrer 자동 저장)
→ "열기" 버튼 클릭
→ ✅ 앱 실행, 사무실 자동 등록!

⭐ 1번의 QR 스캔으로 완료!
```

### iOS - 옵션 A: Universal Links
```
사용자: QR 스캔 (앱 미설치)
→ Safari 웹 페이지 열림
→ "App Store에서 다운로드" 버튼
→ App Store → 설치
→ 앱 실행
→ "QR 코드를 다시 스캔해주세요" 안내
→ QR 다시 스캔
→ ✅ 앱 실행, 사무실 자동 등록!

⚠️ 2번의 QR 스캔
✅ 하지만 100% 정확
```

### iOS - 옵션 B: Firestore (추천)
```
사용자: QR 스캔
→ Safari 웹 페이지 열림 (Firestore에 Attribution 저장)
→ "App Store에서 다운로드" 버튼
→ App Store → 설치
→ 앱 실행
→ ✅ Firestore 자동 매칭, 사무실 등록!

⭐ 1번의 QR 스캔으로 완료!
⚠️ 90%+ 정확도 (시간 기반)
```

---

## 성능 및 비용

### Firestore 비용 (월간 예상)

**전제:**
- 2000개 사무실
- 각 사무실 월 10명 신규 고객
- 총 20,000명/월

**Attribution 저장:**
```
쓰기: 20,000회
비용: $0.18/100K writes = $0.036
```

**Attribution 조회 (최악의 경우):**
```
앱 실행 시 모든 사무실 조회: 2000 reads
20,000명 × 2000 reads = 40M reads
비용: $0.06/100K reads = $24/월
```

**최적화 후:**
```
- 인덱스 활용
- 지역별로 먼저 필터링
- claimed=true 문서는 조회 안 함

예상 비용: $5-10/월
```

---

## 테스트 계획

### iOS 개발 환경 테스트

**Universal Links 테스트:**
```bash
# 시뮬레이터에서
xcrun simctl openurl booted "https://calldetector-5d61e.web.app/install?r=Hongchon&o=qwfde123"

# 실제 기기에서
- Safari에서 URL 입력
- 메모 앱에서 링크 클릭
- QR 코드 스캔
```

**Firestore 테스트:**
```
1. 랜딩 페이지에서 Attribution 생성
2. 앱 실행
3. 로그 확인: "사무실 정보 저장: Hongchon/qwfde123"
```

---

## 향후 고려사항

### Branch SDK 전환 시점

**고려 시점:**
- 월 사용자 10,000명 초과
- Attribution 실패율 5% 초과
- Analytics 필요
- A/B 테스트 필요

**전환 작업:**
- Branch SDK 추가 (1-2일)
- QR 코드 Branch 링크로 변경
- 테스트 (1-2일)

**예상 비용:**
- Branch Basic: 무료 (월 10K)
- Branch Growth: $119/월 (월 25K)
- Branch Premium: $299/월 (월 50K)

---

## 체크리스트

### Universal Links 구현
- [ ] Apple Developer Console에 Associated Domains 추가
- [ ] Xcode에 entitlements 파일 생성
- [ ] Info.plist 설정
- [ ] apple-app-site-association 파일 생성 및 배포
- [ ] app_links 패키지 추가
- [ ] AppLinksService 구현
- [ ] main.dart에 초기화 추가
- [ ] 테스트 (시뮬레이터 + 실제 기기)

### Firestore Fallback
- [ ] AttributionRepository 간소화
- [ ] 시간 기반 매칭 구현
- [ ] claimed flag 로직 추가
- [ ] 테스트

### 사용자 안내
- [ ] 첫 실행 시 안내 메시지 (Universal Links 사용 시)
- [ ] 매칭 실패 시 수동 입력 화면
- [ ] FAQ 작성

---

## 참고 자료

### 공식 문서
- [Apple Universal Links](https://developer.apple.com/ios/universal-links/)
- [Flutter app_links 패키지](https://pub.dev/packages/app_links)
- [Branch.io 문서](https://help.branch.io/developers-hub/docs/flutter)

### 유용한 링크
- [Universal Links Validator](https://branch.io/resources/aasa-validator/)
- [AASA File 생성기](https://limitless-sierra-4673.herokuapp.com/)

---

## 결론

**최종 추천:**
1. **지금**: Firestore 방식 유지 및 간소화
2. **Play Store 배포 시**: Android에만 Install Referrer 추가
3. **App Store 배포 시**: iOS는 Firestore 계속 사용 (충분히 정확)
4. **장기적**: 규모 확장 시 Branch SDK 고려

**이유:**
- ✅ 개발 시간 최소화
- ✅ iOS/Android 코드 대부분 공통
- ✅ 충분한 정확도 (90%+)
- ✅ 무료
- ✅ 유지보수 쉬움

---

작성자: Claude
최종 수정: 2025-11-26
