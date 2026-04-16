# 공통 로직 명세 (Flutter 트랙)

> **본 문서 내용은 `ios/SHARED_LOGIC.md`와 동일합니다.**
> Swift / Flutter / Kotlin 공통 명세이므로 단일 원본 유지를 위해 포인터로만 운용합니다.
>
> **참조 위치**: `C:\Users\kala1\designated_driver\ios\SHARED_LOGIC.md`
>
> 포함 내용:
> 1. Firestore 경로 (`provinces/{p}/cities/{c}/offices/{o}/...`)
> 2. 콜 상태 전이 (WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED)
> 3. 기사 상태 전이 (ONLINE → ASSIGNED → PREPARING → ON_TRIP → WAITING)
> 4. 정산 공식 (officeDeposit / driverShare / finalDeposit / realDeposit / carryOver)
> 5. FCM 메시지 타입 6종 (call_assigned / call_cancelled / SETTLEMENT_FINALIZED / SETTLEMENT_CONFIRMED / SETTLEMENT_REJECTED / CARRYOVER_TRANSFERRED)
> 6. Presence 패턴 (Firebase Realtime DB onDisconnect)
> 7. Firestore 트랜잭션 패턴 (낙관적 UI + 롤백)
> 8. 로그인 플로우 (collectionGroup + SharedPreferences/UserDefaults)
> 9. 손님앱 온보딩 플로우 (QR → Anonymous Auth → Phone Auth → 프로필 설정)
> 10. 고객 포인트/등급 (BRONZE 3% / SILVER 5% / GOLD 7% / VIP 9%)

**Flutter 트랙에서 다른 부분이 발견되면** 이 파일에 보충하지 말고 `ios/SHARED_LOGIC.md` 본문을 직접 수정하세요. 단일 원본 유지가 드리프트 방지의 핵심입니다.
