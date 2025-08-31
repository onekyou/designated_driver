# Security Policy

## 🔒 보안 정책

이 프로젝트는 대리운전 서비스라는 중요한 인프라를 관리하므로 보안을 최우선으로 합니다.

## Supported Versions

현재 지원되는 버전:

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |

## Reporting a Vulnerability

### 보안 취약점 신고

보안 취약점을 발견하신 경우, **공개적으로 이슈를 등록하지 마세요**. 
대신 다음 방법으로 비공개로 신고해주세요:

1. **GitHub Security Advisories** 사용 (권장)
   - 이 저장소의 "Security" 탭으로 이동
   - "Report a vulnerability" 클릭
   
2. **직접 연락**
   - 메일: [보안 담당자 이메일 주소]
   - 제목: `[SECURITY] Designated Driver Platform Vulnerability`

### 신고 시 포함해야 할 정보

- 취약점의 상세 설명
- 재현 단계 (가능한 경우)
- 예상되는 영향 범위
- 제안하는 해결책 (선택사항)

## Security Measures

### 현재 구현된 보안 조치

#### 1. 인증 & 권한 관리
- Firebase Authentication을 통한 사용자 인증
- 역할 기반 접근 제어 (RBAC)
- Firestore 보안 규칙을 통한 데이터 접근 제한

#### 2. 데이터 보호
- 민감한 정보는 환경변수 또는 Firebase Secret Manager 사용
- 개인정보 처리 최소화 원칙 준수
- HTTPS 통신 강제

#### 3. PTT 시스템 보안
- Agora Token 기반 인증
- 채널별 접근 권한 검증
- 무단 접근 방지를 위한 UID 검증

#### 4. 모바일 앱 보안
- ProGuard/R8을 통한 코드 난독화
- Certificate Pinning (권장사항)
- Root Detection (고려중)

### 보안 모범 사례

#### 개발자를 위한 가이드

1. **Secrets 관리**
   ```bash
   # 올바른 방법 - Secret Manager 사용
   firebase functions:secrets:set API_KEY
   
   # 잘못된 방법 - 코드에 하드코딩 하지 마세요
   const API_KEY = "your-secret-key" // ❌
   ```

2. **Firestore 보안 규칙**
   - 모든 컬렉션에 대해 명시적인 접근 규칙 정의
   - 사용자별, 역할별 데이터 접근 제한
   - 정기적인 보안 규칙 검토

3. **입력 검증**
   - 모든 사용자 입력에 대한 검증
   - SQL Injection, XSS 등 공격 방지
   - 파일 업로드 시 타입 및 크기 제한

4. **로깅 & 모니터링**
   - 민감한 정보를 로그에 출력하지 않기
   - 보안 이벤트 모니터링
   - 비정상적인 접근 패턴 감지

## Vulnerability Response

### 취약점 대응 프로세스

1. **접수** (24시간 내)
   - 취약점 신고 확인
   - 초기 영향도 평가

2. **분석** (48-72시간)
   - 상세 분석 및 재현
   - 영향 범위 확정
   - 우선순위 결정

3. **패치** (심각도에 따라)
   - 긴급 (24시간 내): 서비스 중단 위험
   - 높음 (1주일 내): 데이터 유출 위험  
   - 중간 (2주일 내): 기능 악용 가능
   - 낮음 (1개월 내): 경미한 보안 이슈

4. **배포 & 공지**
   - 패치 배포
   - 사용자 공지 (필요한 경우)
   - 취약점 공개 (수정 후)

## Compliance

### 규정 준수

- **개인정보보호법** (한국)
- **GDPR** (유럽연합, 해당되는 경우)
- **CCPA** (캘리포니아, 해당되는 경우)

### 데이터 처리 원칙

1. **최소 수집**: 필요한 정보만 수집
2. **목적 제한**: 수집 목적 외 사용 금지  
3. **보관 제한**: 필요 기간 후 자동 삭제
4. **안전성 확보**: 적절한 보안 조치 구현

## Contact

보안 관련 문의:
- 일반 문의: GitHub Issues
- 보안 취약점: GitHub Security Advisories (비공개)
- 긴급 상황: [긴급 연락처]

---

**마지막 업데이트**: 2025년 8월 31일