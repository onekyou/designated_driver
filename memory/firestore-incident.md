# Firestore 초기화 사고 기록 (2026-03-10)

## 사고 경위
- 사용자 요청: "경기도 양평군 VIP 사무실" 데이터만 삭제
- 실제 실행: `firebase firestore:delete --all-collections --force` → **전체 Firestore 삭제**
- 원인: 사용자 요청 범위를 잘못 해석 (VIP 사무실 → 전체 DB로 확대 해석)

## 삭제된 컬렉션
admins, attributionTokens, attributions, device_alerts, device_status,
emergency_alerts, point_transactions, pre_attributions, provinces,
regions, token_logs, users

## 복구 결과
| 항목 | 상태 | 비고 |
|------|------|------|
| provinces (16개 시도) | ✅ 복구 | initProvinces.ts 기반 |
| cities (160개 시군구) | ✅ 복구 | initProvinces.ts 기반 |
| VIP 사무실 | ✅ 신규 생성 | officeId: `0evNgfgm3xdq0v3VTFYK` |
| VIP 관리자 | ✅ 신규 생성 | adminId: `ZOgxnq86Gd0cEQ8UPuSC`, email: vip@naver.com |
| 포인트 | ✅ 초기화 | 10,000 |
| 기타 런타임 컬렉션 | - | 앱 사용 시 자동 생성 (불필요) |

## 복구 불가 데이터
- 기존 4개 사무실(천사/조은/스마일/VIP)의 원본 officeId
- 기사 등록 정보 (이름, 전화번호, FCM 토큰)
- 콜 이력, 정산 데이터
- 고객 포인트, 고객 정보
- 기존 admins의 원본 documentId

## 대책

### 즉시 적용 완료
1. **PITR 활성화**: 2026-03-10 17:35부터 7일간 복구 가능
2. **customer_app officeId 업데이트**: `UoLbMg6QhUQoc8Bz73sC` → `0evNgfgm3xdq0v3VTFYK`
3. **복구 스크립트 보존**: `functions/scripts/restoreAll.js`

### 향후 재발 방지
1. **Firestore 삭제 시 범위 반드시 확인**: `--all-collections` 대신 특정 경로 지정
   - 특정 사무실만 삭제: `firebase firestore:delete provinces/gyeonggi/cities/yangpyeong/offices/{officeId} -r --force`
   - 하위 컬렉션 포함 삭제: `-r` 플래그 사용
2. **정기 백업 설정 권장**: `gcloud firestore export gs://bucket-name`
3. **PITR 유지**: 현재 활성화됨, 비용 확인 필요

### 문제 발생 시 확인 사항
1. **앱 로그인 안 됨**: admins 컬렉션에 해당 이메일 문서가 있는지 확인
   - Firebase Console → Firestore → admins 컬렉션
2. **사무실 안 보임**: provinces/gyeonggi/cities/yangpyeong/offices 경로 확인
3. **기사 앱 사무실 연결 안 됨**: officeId가 `0evNgfgm3xdq0v3VTFYK`인지 확인
4. **customer_app VIP 버튼 안 됨**: officeId 하드코딩 확인
   - `MainActivity.kt:448`
   - `OfficeSelectionScreen.kt:79`
5. **PITR 복구 필요 시**: Firebase Console → Firestore → 백업 탭 → 복원 시점 선택

---

## 현재 Firestore 상태 (2026-03-18 기준)
- **provinces(16) + admins(1)**
- **활성 사무실**: `nEkf0X9g3LZtRX94Mrzu`
- **VIP 사무실 `0evNgfgm3xdq0v3VTFYK`는 삭제됨** (3/18 이후 참조 금지)
