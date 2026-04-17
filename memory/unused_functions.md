# Cloud Functions 미사용 함수 목록 (2026-03-07 / 2026-04-18 갱신)

## 즉시 제거 가능 (2개) — Phase 6 후반 정리 대상
| 함수명 | 유형 | 사유 | 2026-04-18 상태 |
|--------|------|------|--------|
| migratePickupDrivers | Callable | 1회성 마이그레이션, 완료 후 불필요 | Phase 6 ① 배포(`745070fa`) 시 동반 제거 안 함. 다음 CF 배포 사이클에 제거 권장 |
| testFcmMessage | HTTP | 테스트/디버깅용, 앱에서 호출 안 함 | Phase 6 ① apns 블록 적용에서 **스킵 2곳** 중 하나로 명시 (나머지 1곳은 재전송 payload 재사용). 함수 자체는 유지. 제거 시 호출 여부 재확인 필요 |

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
