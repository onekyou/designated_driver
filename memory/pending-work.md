# 잔여 작업

## 보안 강화 (6건) - 플레이스토어 배포 전 필수
| 이슈 | 내용 |
|------|------|
| NEW-12 | Callable Functions 14개 request.auth 검증 |
| SEC-C01 | 콜 생성 비인증 (firestore.rules) |
| SEC-C02 | customerPoints 소유권 미검증 |
| SEC-C03 | pointTransactions 위조 생성 방지 |
| SEC-C04 | customerInfo FCM 토큰 탈취 방지 |
| RTDB | read/write:true → 인증 기반 규칙 |

## 후순위 (2건)
| 이슈 | 내용 |
|------|------|
| NEW-08/10 | Crashlytics 설정 + 크래시 감지 |
| STL-10/11 | 구 정산 시스템 제거 + 마감 정리 |

## 미개발
- 픽업기사 앱 (CallStatus에 PICKUP_COMPLETE enum만 존재)
- Customer App QR코드 고객정보 수집
- Driver App 부팅 자동시작 (BootCompletedReceiver 미구현, 기사 수동 실행 필요)

## 배포 상태
- Cloud Functions: 3/12 전체 재배포 완료 (40개 함수)
- 정산 스케줄러(`checkSettlementDiscrepanciesScheduled`) 삭제 완료
- 모든 앱 미배포 (call_detector, call_manager, driver_app, customer_app)
- 실기기 테스트 진행 중
