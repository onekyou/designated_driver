# Deployment Checklist – SharedPrefs Sync & lastUpdate

## 1. SharedPreferences ⇄ admins doc 자동 동기화 확인
- 앱 실행 후 Firestore 의 `admins/{uid}` 문서에서 `associatedOfficeId` 를 수정하면 앱이 자동으로 재연결되어야 합니다.

## 2. `lastUpdate` 필드 검증
- Cloud Functions (`onSharedCallClaimed`, `onCallCompleted`, `onSettlementUpdated`) 실행 후 관련 문서에 `lastUpdate` 가 서버 타임스탬프로 기록되는지 확인.

## 3. Firestore 규칙
- `lastUpdate` 필드는 Functions / 관리자만 쓸 수 있도록 현행 규칙 유지.

## 4. 회귀 테스트
1. 관리자 로그인 → 사무실 변경 시 자동 동기화.
2. 콜 완료 → `settlements` 문서 생성 + `lastUpdate` 포함.
3. 정산 완료 → `settlements` 문서 `lastUpdate` 갱신, 포인트 트랜잭션 기록.

## 5. 배포 절차
```bash
firebase deploy --only functions
```
- 업데이트된 매니저 앱 내부 트랙 배포 → 실기기 테스트 완료 후 전면 배포. 