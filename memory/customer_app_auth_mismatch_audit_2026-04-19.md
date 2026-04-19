# Flutter MVP ↔ Kotlin 원본 불일치 통합 감사 (2026-04-19)

> **감사 범위**: customer_app_flutter/MVP 9개 + driver_app_flutter/MVP 10개 = **총 19개 문서**
> **감사 결과**: **High 4건 / Medium 7건 / Low 4건 (총 15건)**
> **중단 결정**: 병렬 Chunk 5 세션 로컬 변경 커밋 금지, MVP 문서 재작성 선행 확정
> **근본 원인**: "의제 N 결정 = 개선안" 이라는 이름의 Kotlin 이탈 + "Phase 2 로 미룸" 으로 Kotlin 핵심 기능 제거 + 문서간 일관된 누락

---

## 1. Executive Summary

### 1.1 사태 발견 경위
사용자가 "우리는 익명가입이야. 전화는 재가입시 번호가 존재하면 그때만 확인해서 기존 포인트들을 복구하는거야" 라고 지적 → Kotlin customer_app AUTH 영역 전수 확인 결과 사용자 판단 100% 정확, Flutter MVP 문서 + Chunk 4 AuthNotifier + Chunk 5 결정이 3층 모두 Kotlin 과 어긋나 있음을 확인 → customer MVP 8 + driver MVP 10 전수 감사 실시 → **동일 패턴이 앱 경계를 넘어 Flutter 포팅 방법론 전반에 박혀있음 확인**.

### 1.2 핵심 결론
- **Flutter 포팅은 "Kotlin 을 정확히 매핑" 하는 것이 목적**이어야 하는데, MVP 문서가 "의제 결정" 이라는 이름으로 **Kotlin 동작을 임의 수정하거나 축소함**
- 이 왜곡이 Chunk 4 구현(이미 원격 푸시)과 Chunk 5 결정(로컬 변경, 미커밋)에 그대로 반영되어 있음
- driver_app_flutter 도 동일 패턴 확인 — 이미 Phase A 까지 구현된 상태라 **기존 driver Flutter 코드 재검증 필요**

### 1.3 즉시 중단·대기 결정
- 병렬 Chunk 5 세션: 로컬 변경 유지 + staging/커밋/되돌리기 금지, 대기
- 신규 코드 작성 전면 중단: MVP 문서 재작성이 선행 조건
- 다음 세션 진입점: **통합 수정 플랜 승인 + MVP 문서 재작성** (이 파일 §5 참조)

---

## 2. Customer App 감사 결과 (MVP 9개)

**심각도 합계: High 3 / Medium 4 / Low 2**

### 2.1 AUTH.md — 선행 감사 (이미 확인)
| 심각도 | 지점 | 내용 |
|--------|------|------|
| High | §3, §3.4 | "의제 9 linkWithCredential 로 개선" — Kotlin 은 `signInWithCredential` 사용 |
| High | §4.3 | CF `checkPhoneNumberDuplicate` 를 Phase 2 로 미룸 — Kotlin 은 가입 필수 진입점 |
| High | 전 영역 | CF `recoverCustomerAccount` 호출 완전 누락 — Kotlin `MainActivity.kt:568-624` |

### 2.2 NOTIFIERS.md
| 심각도 | 지점 | 내용 |
|--------|------|------|
| **High** | 전 영역 | Account Recovery (recoverCustomerAccount CF 호출) 완전 누락 |
| Medium | §4.3.6 ProfileNotifier.acceptTerms | TermsAgreementViewModel 원본 참조 없음 |
| Medium | §2.3.5 PointNotifier.refresh | 500ms 지연 또는 실시간 리스너 — Kotlin MainViewModel.kt:559-585 는 race condition 완화 로직 자체가 없음 |

### 2.3 SCREENS.md
| 심각도 | 지점 | 내용 |
|--------|------|------|
| **High** | §3.2 SplashScreen _bootstrap | 재설치 복구 분기 미구현 |
| Medium | §4.1 OfficeCodeScreen | QR 결과 Firestore offices/{o} 조회 로직 누락 |

### 2.4 MODELS.md
| 심각도 | 지점 | 내용 |
|--------|------|------|
| Medium | §6 BannerAdData | R2 분류로 Phase 1 제외 — Phase 2 진입 시 Kotlin BannerAdService.kt 재대조 필수 |

### 2.5 OVERVIEW.md / ENUMS.md / FCM.md / ATTRIBUTION.md / PHASE2_APPCLIP.md
- 구조적 불일치 없음 (Low 2건 — OVERVIEW 탭 표기 오기, FCM 라인 번호 재확인)
- ENUMS / ATTRIBUTION / PHASE2_APPCLIP 은 Kotlin 매핑 양호

### 2.6 Customer High 3건의 공통 원인
**Kotlin `MainActivity.kt:568-624` 의 `recoverCustomerAccount` CF 호출이 MVP 문서 3개에서 일관되게 누락**. 즉 하나의 Kotlin 기능이 Flutter 설계 전반에서 사라짐. 재설치 후 같은 번호 재가입 시 기존 주문 내역·포인트 전부 손실.

---

## 3. Driver App 감사 결과 (MVP 10개)

**심각도 합계: High 1 / Medium 3 / Low 2**

### 3.1 AUTH.md
| 심각도 | 지점 | 내용 |
|--------|------|------|
| **High** | §2.1, 시퀀스 다이어그램 | `checkPendingStatusAndProceed` 누락 — Kotlin `LoginViewModel.kt:154-168` 은 로그인 성공 직후 `pending_drivers/{uid}` 존재 확인 → 승인 대기 시 "관리자 승인 대기 중" 에러 + signOut. **MVP 에는 이 단계 없음 → 승인 대기 계정이 기사앱 진입 가능** |
| Medium | §4.1 옵션 A/B | Kotlin `LoginViewModel.kt:54-68` 은 **저장된 비밀번호 복구 + 자동 로그인 (옵션 A 패턴)** 인데 MVP 는 옵션 B (currentUser 자동 상속, 비밀번호 저장 금지) 권장 → **customer AUTH 의 linkWithCredential 과 동일 패턴의 "개선안 침해"** |

### 3.2 NOTIFIERS.md
| 심각도 | 지점 | 내용 |
|--------|------|------|
| Medium | §1.5 CarryOverNotifier | 유일한 실시간 리스너 (driver 문서 전체 구독, carryOver + dailySettlement 공유) — MVP 는 구조만 기재, 구현 세부 없음. PENDING_CONFIRM 시 calculatedCarryOver 복합 로직 누락 가능성 |
| Low | §1.5 PresenceNotifier | 기존 `presence_service.dart` 재사용 여부 모호 |

### 3.3 FIRESTORE.md
| 심각도 | 지점 | 내용 |
|--------|------|------|
| Medium | §2 트랜잭션 6곳 | MVP 주장 "6곳" 중 실제 상세 기술 4곳만 (acceptCall / rejectCall / cancelTrip / startDriving). `submitDailySettlement` 포함 나머지 미제시 |

### 3.4 FCM.md
| 심각도 | 지점 | 내용 |
|--------|------|------|
| Medium | §1.2 백그라운드 isolate | Delivery ACK 호출을 "가능" 이라고 기재했으나 Dart BG isolate 30초 제한 하 완료 보장 전략 부재 |

### 3.5 OVERVIEW.md (Low)
- FVM 설치 순서 모호 (CI/CD 에서 직렬화하면 해결)

### 3.6 MODELS.md / ENUMS.md / SCREENS.md / PLATFORM_CHANNELS.md / BUILD.md
- 구조적 불일치 없음 — Kotlin 매핑 양호

---

## 4. 세 가지 오류 패턴 재현 현황 (종합)

| 패턴 | customer AUTH | customer 기타 | driver AUTH | driver 기타 |
|------|--------------|--------------|-------------|------------|
| **① 개선안 침해 (Kotlin 이탈)** | 의제 9 linkWithCredential | — | 의제 9 옵션 B currentUser 자동 상속 | — |
| **② Phase 2 로 미룸 (기능 제거)** | checkPhoneNumberDuplicate | (동 패턴) | — | — |
| **③ 완전 누락** | recoverCustomerAccount | NOTIFIERS · SCREENS 동시 누락 | checkPendingStatusAndProceed | CarryOverNotifier 세부 / 트랜잭션 2곳 / BG isolate ACK 전략 |

**인사이트**: 패턴 ①·③ 이 두 앱 모두 동일하게 나타남. 즉 **특정 문서 작성자의 스타일 문제가 아니라, Flutter 포팅 방법론 자체의 결함**. MVP 문서가 Kotlin 을 "그대로 옮기는 것" 이 아니라 "이 기회에 개선" 하려 했고, 그 개선이 근본적 기능 훼손이었음.

---

## 5. 통합 수정 플랜

### 5.1 Phase 0 — 중단·대기 (완료)
- [x] 병렬 Chunk 5 세션 중단 신호 전달
- [x] MVP 문서 감사 완료
- [x] 감사 보고서 저장

### 5.2 Phase 1 — MVP 문서 재작성 (신규 코드 작성 전 필수 선행)

#### A. customer_app MVP (3 문서 전면 재작성)
- [ ] **AUTH.md §3, §3.4** 재작성: Phone Auth = 재설치 복구 전용 / `signInWithCredential` 사용 / 의제 9 결정 철회
- [ ] **AUTH.md §4.3** 재작성: CF `checkPhoneNumberDuplicate` 를 Phase 1 MVP 필수로 복권
- [ ] **AUTH.md §5 신규**: CF `recoverCustomerAccount` 호출 플로우 (MainActivity:568-624 기반)
- [ ] **NOTIFIERS.md §2, §5**: AuthNotifier · ProfileNotifier 에 Account Recovery 분기 삽입
- [ ] **SCREENS.md §3.2 SplashScreen**: 재설치 복구 분기 라우팅 명시
- [ ] **NOTIFIERS.md §2.3.5**: PointNotifier race condition 완화 로직 제거 (Kotlin 에 없음)

#### B. customer_app MVP (Medium 재검토)
- [ ] **NOTIFIERS.md §4.3.6**: TermsAgreementViewModel.kt 직접 읽고 acceptTerms 시그니처 재작성
- [ ] **SCREENS.md §4.1 OfficeCodeScreen**: Firestore offices/{o} 조회 로직 추가

#### C. driver_app MVP (2 문서 수정)
- [ ] **AUTH.md §2.1, 시퀀스 다이어그램**: `checkPendingStatusAndProceed` → `pending_drivers/{uid}` 체크 단계 복원
- [ ] **AUTH.md §4.1 옵션 A/B**: 의제 9 결정 재검토. Kotlin `LoginViewModel.kt:54-68` 는 옵션 A 동작 → MVP 를 옵션 A 로 수정할지 의제 9 결정을 유지할지 사용자 판단 요청
- [ ] **NOTIFIERS.md §1.5 CarryOverNotifier**: dailySettlement PENDING_CONFIRM 시 calculatedCarryOver 복합 로직 세부 명시
- [ ] **FIRESTORE.md §2**: 트랜잭션 6곳 전수 정의 (submitDailySettlement 포함)
- [ ] **FCM.md §1.2**: BG isolate 30초 제한 하 ACK 완료 전략 추가

### 5.3 Phase 2 — Chunk 4 패치 (customer_app_flutter 원격 커밋 위)
- [ ] `linkWithCredential` → `signInWithCredential` 교체 (AuthNotifier line 141)
- [ ] `credential-already-in-use` fallback 제거 (linkWithCredential 특유 에러)
- [ ] 기본 플로우에서 Phone Auth 호출 분리 → 복구 전용 분기
- [ ] `recoverAccount(phone, newUid, ...)` 메서드 신규 (CF recoverCustomerAccount 호출)
- [ ] 테스트 재작성: linkWithCredential 관련 → signInWithCredential 기반

### 5.4 Phase 3 — Chunk 5 재설계 (로컬 변경 일부 유지 + 확장)
- [ ] ProfileNotifier.updateProfile 에 CF `checkPhoneNumberDuplicate` 호출 삽입
- [ ] isDuplicate 이벤트 emit → 라우터가 PHONE_AUTH 화면으로 전환
- [ ] Account Recovery 플로우 조율 (AuthNotifier.recoverAccount 연계)
- [ ] 테스트 추가: duplicate 분기 + recover 호출 검증

### 5.5 Phase 4 — driver_app_flutter 기존 구현 영향 평가 (중요)
driver_app_flutter 는 이미 Phase A 까지 구현된 상태. MVP 결함이 실제 코드에 반영됐을 가능성 높음. 감사 필요 범위:
- [ ] **driver_app_flutter/lib 에 LoginNotifier 가 존재한다면**, `pending_drivers/{uid}` 체크가 구현되어 있는지 확인
- [ ] **driver_app_flutter 자동 로그인 로직** 확인: 저장된 비밀번호 복구 (옵션 A) vs currentUser 자동 상속 (옵션 B) 중 무엇이 구현됐는지
- [ ] CarryOverNotifier 구현 존재 시: PENDING_CONFIRM 분기 반영 여부
- [ ] 이 평가는 **코드 읽기만, 수정 없이**. 결함 발견 시 별도 수정 플랜 수립

---

## 6. 서버측 상태 (CF) — 재확인

두 CF 모두 이미 구현 완료. **클라이언트 포팅만 남아 있음**.

| CF | 역할 | 위치 |
|----|------|------|
| `checkPhoneNumberDuplicate` | 번호 기반 기존 계정 조회 | `functions/src/index.ts:123-160` |
| `recoverCustomerAccount` | oldUid → newUid 데이터 이관 + FCM 갱신 | `functions/src/index.ts:166-240` |

---

## 7. 증거 파일/라인 인덱스

### customer_app Kotlin
- `MainActivity.kt:382-476` — 앱 진입 signInAnonymously + 프로필 확인
- `MainActivity.kt:568-624` — PhoneAuth 성공 후 recoverCustomerAccount 호출 (customer High 3건 원인)
- `MainActivity.kt:626-638` — PROFILE_SETUP onPhoneNumberDuplicate → PHONE_AUTH 전환
- `ui/profile/ProfileSetupViewModel.kt:55-206` — saveProfile + CF checkPhoneNumberDuplicate + Firestore 저장
- `ui/profile/ProfileSetupScreen.kt:42-47` — isDuplicate 감지 시 onPhoneNumberDuplicate 콜백
- `ui/auth/PhoneAuthViewModel.kt:107-125` — signInWithCredential (linkWithCredential 아님)

### driver_app Kotlin
- `ui/login/LoginViewModel.kt:54-68` — 자동 로그인 시 저장된 비밀번호 로드 (옵션 A 패턴)
- `ui/login/LoginViewModel.kt:154-168` — checkPendingStatusAndProceed (driver High 1건)
- `viewmodel/DriverViewModel.kt:427, 502, 552, 632` — runTransaction 4곳 검증
- `viewmodel/DriverViewModel.kt:1265-1312` — CarryOverListener + dailySettlement 복합 로직

### 서버측 CF
- `functions/src/index.ts:123-160` — checkPhoneNumberDuplicate
- `functions/src/index.ts:166-240` — recoverCustomerAccount

---

## 8. 이 감사의 범위 한계

- Kotlin 소스 중 본 감사에서 직접 열어 확인한 파일 외(예: TermsAgreementViewModel, BannerAdService, MyFirebaseMessagingService 정확 라인, driver_app 의 Service/Broadcast/Repository 등) 는 **미검증**. MVP 문서 재작성 시 해당 파일도 라인별 대조 필요.
- driver_app_flutter/lib 의 **이미 구현된 Phase A 코드** 는 본 감사에서 확인하지 않음. Phase 4 (§5.5) 에서 별도 평가 필요.
- 서버측 CF 의 구현 상세 (recoverCustomerAccount 가 어떤 데이터 이관을 수행하는지) 는 본 감사에서 라인 수준까지 확인하지 않음. MVP 문서 재작성 전 CF 코드 정독 필요.

---

## 9. 사용자 판단 기록

> "무언가 잘못 진행되고 있는것 같아. 코틀린앱을 매핑하는 작업이 진짜 목적이야 나머지는 ios 효율성에 관한거고"
>
> "기사앱플러터도 같은 문제가 있을 수 있을것 같은데"

→ 두 판단 모두 코드/서버 증거로 확정됨. 이 감사는 사용자 판단을 실증한 기록.
