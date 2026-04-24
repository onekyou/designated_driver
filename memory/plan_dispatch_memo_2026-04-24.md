# 배차 STT 메모 기능 구현 이력 (2026-04-24)

## 목적

일반전화로 걸려오는 콜은 전화번호/이름만 확보됨 — 출발지·목적지·요금이 없어 기사에게 전달 불가. 매니저가 **정보카드 길게 누르기 한 번**으로 STT 녹음 → 자동 파싱 → Firestore 4필드(`departure_set`/`waypoints_set`/`destination_set`/`fare_set`) + `memoText` 동시 저장 → 기사앱 `TripPreparationScreen` 자동 pre-fill.

매니저 조작 시간: "탭 → 마이크 버튼 → 녹음" 3스텝 → **롱프레스 한 번**으로 단축.

## 최종 운영 흐름

1. 매니저: 통화 종료 → 팝업 → 정보카드 길게 누르기 → "시장에서 용문까지 이만오천원" 발화 → 놓기
2. 파싱: "까지" 앞 토큰 → 도착지, "에서" 앞 토큰 → 출발지, "원" 으로 끝나는 마지막 토큰 → 요금, "경유" 앞/뒤 → 경유지
3. 출발지 폴백: "에서" 없으면 `customerAddress`(통화 발신자 주소)
4. Firestore 저장 (runTransaction + status==WAITING 가드)
5. 프리뷰 "📋 시장 → 용문 / 25,000원" 정보카드 하단 표시 + 원문 memoText
6. 기사 선택 → 배차 → 기사앱 NewCallPopup `memoText` 노출 → 수락 → TripPreparationScreen 4필드 pre-fill
7. 기사가 손님 대면 후 현장 확정 입력 → 운행 시작

## 범위

- **콜매니저** (NewCallAssignmentDialog)
- **콜디텍터** (DispatchDialog) — 동형 포팅
- **기사앱** (NewCallPopup + TripPreparationScreen) — memoText 표시, **운행준비 필드는 수정 0** (이미 `departure_set`/`destination_set`/`waypoints_set`/`fare_set` 읽음)
- **Cloud Functions** (`CallData` interface + 공유콜/원본복구 주석)
- **MVP 문서** (Flutter + iOS MODELS.md)

## 주요 수정 파일 (21개)

### 콜매니저 (9)
| 파일 | 변경 |
|------|------|
| `data/CallInfo.kt:30` | `memo` dead field → `memoText` + `@Exclude` 해제 + `@PropertyName("memoText")` |
| `data/local/LocalCallInfo.kt` | memoText 필드 + `toCallInfo()`/`toLocalCallInfo()` 양방향 매핑 |
| `data/local/AppDatabase.kt` | v4→v5 + `MIGRATION_4_5` (ALTER TABLE calls ADD COLUMN memoText TEXT) |
| `data/repository/CallRepository.kt:162-178` | `refreshData` Firestore→LocalCallInfo 매핑에 memoText |
| `data/repository/CallRepository.kt:255-299` | `insertCallFromFCM` 기존 memoText 보존 (DB 조회 후 유지) |
| `ui/dashboard/MemoInputDialog.kt` (신규) | 프리뷰 탭 진입 전용 편집 다이얼로그 + 실시간 파싱 프리뷰 |
| `ui/dashboard/DashboardScreen.kt:1480-` | `NewCallAssignmentDialog` `combinedClickable(onLongClick)` + `MemoSttState` + `DisposableEffect(voiceHelper.destroy)` + createdFrom 가드 + actionsDisabled |
| `ui/dashboard/DashboardScreen.kt:1555-` | `fromCallManager=true` 분기 memoText TextField + `onDriverSelectWithInfo` 시그니처 5필드 확장 |
| `ui/dashboard/DashboardViewModel.kt` | `updateCallMemo` 신규 (runTransaction + WAITING 가드 + memoText/4필드 update) |
| `ui/dashboard/DashboardViewModel.kt` | `assignNewCallWithInfo` 시그니처 memoText 추가 |
| `MainActivity.kt` | `onMemoUpdate` 콜백 주입 |

### 콜매니저 util (신규)
- `util/CallMemoParser.kt` — 토큰 기반 파서 (에서/경유/까지/원 + customerAddress 폴백)
- `util/ParsedMemo` data class + `toPreview()`

### 기사앱 (2)
- `model/CallInfo.kt:49` `memo: String = ""` → `memoText: String? = null`
- `ui/screens/home/NewCallPopup.kt` 메모 Card 추가 (surfaceVariant, Icons.Default.Note, maxLines 4)

### 콜디텍터 (5)
- `util/VoiceInputHelper.kt` (신규, call_manager 복사 + 패키지 교체)
- `util/CallMemoParser.kt` (신규, 동일)
- `ui/MemoInputDialog.kt` (신규, 동일)
- `ui/DispatchDialog.kt` — 동형 UI + `combinedClickable(onLongClick)` + `MemoSttState` + `DisposableEffect` + **`rememberLauncherForActivityResult(RECORD_AUDIO)` 런타임 권한 요청** (MainActivity 경유 안 하는 경로 대응)
- `ui/DispatchActivity.kt` — `updateCallMemo` suspend 함수 + runTransaction + WAITING 가드
- `AndroidManifest.xml` — RECORD_AUDIO 권한 선언

### Cloud Functions (1)
- `functions/src/index.ts:36-56` `CallData` interface `memoText?: string`
- `functions/src/index.ts:1292-1308` `onSharedCallClaimed` spread 복사 주석 (공유콜 memoText 포함)
- `functions/src/index.ts:1528-1540` 원본 복구 시 memoText 보존 주석

### MVP 문서 (2)
- `flutter/driver_app/MVP/MODELS.md` memo → memoText (3개소)
- `ios/driver_app/MVP/MODELS.md` memo → memoText (3개소)

## 파싱 엔진 (CallMemoParser)

토큰 기반:
1. 공백 split → tokens
2. **요금**: 마지막 토큰이 "원" 끝 → `removeSuffix("원").replace(",","")` → `VoiceInputHelper.convertKoreanNumberToDigit()` → Long
3. **도착지**: "까지" 끝 토큰 (마지막 매치) → `removeSuffix("까지")`. 없으면 마지막 비숫자 토큰 (숫자 토큰 가드 `looksLikeNumber()`)
4. **출발지**: "에서" 끝 토큰 (첫 매치) → `removeSuffix("에서")`. 없으면 `customerAddress` 폴백
5. **경유지**: "경유" 단독 토큰 앞/뒤 또는 "X경유" 접미사

검증된 입력:
- "시장에서 용문까지 이만오천원" → dep=시장, dest=용문, fare=25000
- "용문에서 시장까지 25,000원" → dep=용문, dest=시장, fare=25000
- "시장에서 양평 경유 용문까지 삼만원" → dep=시장, waypoints=양평, dest=용문, fare=30000
- "용문까지 2만5천원" → dep=customerAddress, dest=용문, fare=25000
- "시장에서 용문 이만오천원" (까지 생략) → dep=시장, dest=용문(마지막 비숫자), fare=25000

실패 케이스 (memoText 에만 저장):
- "용문 이만오천" (마커 전부 없음 + 원 생략)
- STT 오인식으로 "경유" → "경우" 변환된 경우 경유지 파싱 실패

## Race / 안전장치

| 영역 | 가드 |
|------|------|
| 타 매니저 동시 배차 | `runTransaction + status==WAITING` |
| STT 비동기 vs 배차 FCM | STT 상태(Recording/Saving) 중 팝업 버튼 disabled + `updateCallMemo.await()` 완료 후 재활성화 |
| SpeechRecognizer leak | `DisposableEffect { onDispose { voiceHelper.destroy() } }` |
| Snackbar/프리뷰 재진입 | `MemoSttState` 상태머신 |
| 기사앱 `tertiaryContainer` 미정의 | `surfaceVariant` 로 교체 (Theme.kt 무변경) |
| Local-First 파이프라인 memoText 소실 | LocalCallInfo + Migration + refreshData 매핑 + insertCallFromFCM 보존 — 4군데 동시 수정 |
| **콜디텍터 RECORD_AUDIO 런타임 권한** | DispatchDialog `rememberLauncherForActivityResult` (MainActivity 미경유 케이스 대응) |
| 손님앱 경유 콜 | `createdFrom == "customer_app"` 가드 — 메모 UI 노출 안 함 |

## 커밋 체인

| SHA | 내용 |
|-----|------|
| `f9d0f0d0` | feat(dispatch-memo): 콜매니저 STT 파싱 + 운행준비 필드 자동 분배 (13 파일, +1332) |
| `78e31fd1` | chore(apk): driver_app 배포 스냅샷 |
| `ae89b970` | feat(call-detector): 배차 STT 메모 파싱 포트 (6 파일, +811) |
| `b63ce85f` | fix(call-detector): DispatchDialog RECORD_AUDIO 런타임 권한 요청 |

브랜치: `manager-direct-drive`. 원격 푸시 완료.

## 미커밋 남겨둔 것 (이번 세션 무관 WIP)

- `functions/src/index.ts` 에 내 CallData interface 추가 + 주석 2개 외에도 이전 세션 H2 WIP (homepage Basic Auth express+defineSecret) 가 섞여 있음 → 분리 불가. **미커밋 유지**. runtime 영향 없으므로 기능 작동에 무영향.

## 배포 상태

**Firebase Hosting `calldetector-5d61e` 타겟** 에 3개 APK 배포 완료:

| APK | 크기 | SHA256 (verified server=local build) |
|-----|------|--------------------------------------|
| `call_manager-debug.apk` | 30.9MB | `70f9dc85...f471` |
| `driver_app-debug.apk` | 29.2MB | `5ba51f8d...7aa1` |
| `call_detector-debug.apk` | 35.7MB | `dbc0bee6...f696` |

공개 다운로드 URL:
- `https://calldetector-5d61e.web.app/apk_downloads/call_manager-debug.apk`
- `https://calldetector-5d61e.web.app/apk_downloads/driver_app-debug.apk`
- `https://calldetector-5d61e.web.app/apk_downloads/call_detector-debug.apk`

**`calllink.io.kr` 은 Basic Auth 의도적 차단** (homepage/auth-gate 타겟이 homepageGate CF 로 rewrite) → APK 다운로드 불가. 기본 `.web.app` URL 사용.

## 실기기 설치 상태

| 기기 | 시리얼 | 설치된 앱 (최신) |
|------|--------|-----------------|
| S21+ | R3CR312MB1L | call_manager + driver_app |
| Z Flip4 | R3CT80K78NP | driver_app |
| S22 | R5CT41TJZFP | call_detector (RECORD_AUDIO adb 수동 부여 + 재설치로 권한 유지) |

## 검증 완료

- ✅ 콜매니저 실기기 S21+ 정형 발화 → 4필드 저장 → 기사앱 TripPreparationScreen pre-fill
- ✅ Firestore 콘솔 `calls/{id}` 4필드 저장 확인
- ✅ STT 중 배차 버튼 disabled 확인
- ✅ 콜디텍터 S22 최초 설치 시 RECORD_AUDIO granted=false 발견 → adb 부여 후 테스트 대기
- ⏳ 콜디텍터 권한 launcher 버전 재-설치 후 STT 실기기 검증 (사용자 확인 대기)

## 다음 세션 진입점

1. **S22 call_detector STT 최종 검증**: 전화 수신 → DispatchActivity → 정보카드 롱프레스 → 파싱 결과 Firestore 저장 확인 → Z Flip4 TripPreparationScreen pre-fill
2. **`.web.app` vs `calllink.io.kr` 다운로드 접근성 전략** 재검토 (선택): calllink.io.kr 에서도 APK 다운 받게 하려면 homepageGate CF 에 `/apk_downloads/**` static 파일 서빙 미들웨어 추가 or callmadang-web 타겟 rewrites 에 `/apk_downloads/**` 제외 + `homepage/auth-gate/apk_downloads/` 에 APK 복사
3. **functions/src/index.ts WIP 분리 커밋**: H2 homepage Basic Auth + 내 memoText 주석을 분리해서 각각 커밋

## 비작업 영역 (의도적 제외)

- 배차 후 메모 수정 (WAITING 가드)
- LockScreenActivity/CallDetailsScreen 메모 표시 (NewCallPopup 에서만 확인 사용자 결정)
- 손님앱 경유 콜 메모 (구조화 정보 이미 있음)
- 대시보드 콜 리스트 메모 아이콘
- VoiceInputHelper `convertKoreanNumberToDigit` 의 "2만5천" 혼합 표기 처리 개선 (별도 이슈)
