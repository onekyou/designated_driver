# Cloud Functions 미사용 함수 목록 (2026-03-07)

## 즉시 제거 가능 (2개)
| 함수명 | 유형 | 사유 |
|--------|------|------|
| migratePickupDrivers | Callable | 1회성 마이그레이션, 완료 후 불필요 |
| testFcmMessage | HTTP | 테스트/디버깅용, 앱에서 호출 안 함 |

## 향후 사용 예정 보류 (8개)
| 함수명 | 유형 | 비고 |
|--------|------|------|
| getArchivedStats | Callable | 총관리자앱용 (미구현) |
| searchArchivedCalls | Callable | 총관리자앱용 (미구현) |
| getOfficeReport | Callable | 총관리자앱용 (미구현) |
| manualCheckSettlementDiscrepancy | Callable | Manager앱 수동 호출 예정 |
| matchAttribution | Callable | Attribution 시스템 (미구현) |
| saveManualAttribution | Callable | Attribution 시스템 (미구현) |
| matchByToken | Callable | Attribution 시스템 (미구현) |
| claimToken | Callable | Attribution 시스템 (미구현) |

## 사용 중 (31개)
- Callable 5개, Firestore 트리거 19개, Scheduled 7개
- 전체 41개 중 31개 활성 사용
