---
name: 손님앱 개선 잔여 작업
description: 대시보드 UI 개선 완료 후 남은 손님앱 개선 작업 — 이용내역 월별 개선, Play Store 심사 준비
type: project
---

## 완료된 작업 (2026-04-08)
- ✅ 만보기 기능 전체 제거 (8개 파일 삭제, 권한 제거, Room DB 제거)
- ✅ 홈 화면: 만보기 카드 → 포인트/등급 카드로 교체
- ✅ 포인트카드 좌우 여백/라운드 제거 (엣지 투 엣지)
- ✅ 배너 광고 리스너 제거 → Firestore `settings/branding` 1회 조회 슬로건으로 전환
- ✅ 슬로건 배너: "당신만의 기사가 모십니다" (Pretendard Bold, #F5F5F5, 보케 이미지 배경)
- ✅ 폰트 추가: Pretendard Bold (`res/font/pretendard_bold.otf`), Noto Serif KR Bold (`res/font/noto_serif_kr_bold.otf`)
- ✅ 커밋: `8198f267`
- ✅ 기사앱 AAB 빌드 (versionCode 5, 1.0.3) — Play Store 공개 테스트 업로드 완료
- ✅ 손님앱 AAB 빌드 (versionCode 12, 1.0.11) — Play Store 공개 테스트 업로드 완료

## 슬로건 시스템
- Firestore 경로: `offices/{officeId}/settings/branding` → `slogan` 필드
- 값 있으면 해당 문구 표시, 없으면 기본값 "당신만의 기사가 모십니다"
- 앱 업데이트 없이 Firestore 콘솔에서 문구 교체 가능

## 남은 작업
1. **이용내역 페이지 개선** (플랜 승인됨)
   - 월별 탐색: `< 2026년 4월 >` 이동 버튼
   - 간략 카드: 날짜 | 상태 | 요금 한 줄 → 클릭 시 상세 펼침
   - 수정 파일: CallHistoryScreen.kt, CallHistoryViewModel.kt, CallService.kt

2. **Play Store 심사 준비**
   - 개인정보처리방침 URL 등록 (calllink.io.kr에 호스팅)
   - 데이터 보안 섹션 신고 (전화번호, 위치, FCM 토큰)
   - Firebase Console에 Play Store 앱 서명 키 SHA-256 등록:
     - SHA-1: `71:FB:60:26:BA:28:A4:B7:63:25:96:BB:38:9E:33:48:6B:12:7B:C9`
     - SHA-256: `10:FF:C3:9E:FF:3B:FD:AB:17:88:76:D9:D7:E0:AF:54:78:B8:BC:A5:51:09:2A:52:2D:49:4B:2A:59:FC:B1:56`

3. **S22 테스트 중 확인 필요**
   - 양평 vip 사무실 정보 주입 완료 (gyeonggi/yangpyeong/nEkf0X9g3LZtRX94Mrzu)
   - 사무실명 "vip" 표시 확인 필요
   - 포인트/등급 카드 UI 확인 필요
   - 슬로건 배너 UI 확인 필요

## 참고
- 같은 기기에서 기사앱+손님앱 FCM 토큰 충돌 → 서로 다른 기기에서 테스트할 것
- 만보기 제거로 FOREGROUND_SERVICE_DATA_SYNC 영상 제출 불필요
- 손님앱 versionCode: Play Console에서 이미 사용된 코드 재사용 불가 (11까지 사용됨)
- 미사용 폰트(Noto Serif KR Bold)는 나중에 다른 곳에서 활용 가능, 삭제 보류
