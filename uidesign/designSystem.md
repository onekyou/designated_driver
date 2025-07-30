# Designated Driver 통합 디자인 시스템

## 1. 디자인 시스템 핵심 원칙

1. **일관성**: 모든 앱에서 동일한 색상, 폰트, 컴포넌트 스타일을 사용해 브랜드 아이덴티티와 사용성 유지
2. **명확한 계층 구조**: 배경/카드/버튼/입력창 등 시각적 구분이 명확하게 드러나도록 설계
3. **접근성과 가독성**: 충분한 명도 대비, 큰 터치 영역, 명확한 폰트 크기와 두께 사용
4. **즉각적 피드백**: 버튼/입력창/모달 등 상호작용 시 색상 변화, 그림자, 애니메이션 등으로 즉각적 반응 제공
5. **모던 & 심플**: 불필요한 장식 없이, 여백과 정렬을 활용한 깔끔한 레이아웃

---

## 2. 색상 팔레트

| 이름         | HEX      | 용도                |
|--------------|----------|---------------------|
| Primary      | #FFAB00  | 주요 강조/포인트    |
| Secondary    | #6650A4  | 보조 포인트         |
| Background   | #121212  | 기본 배경           |
| Surface      | #1E1E1E  | 카드/모달 등 서피스 |
| OnPrimary    | #000000  | Primary 위 텍스트   |
| OnBackground | #E0E0E0  | 배경 위 텍스트      |
| Disabled     | #404040  | 비활성화            |
| Error        | #CF6679  | 에러/경고           |

---

## 3. 타이포그래피

- **글꼴:** 'Noto Sans KR', 'Roboto', 'Apple SD Gothic Neo', sans-serif
- **기본 텍스트:** 16px, 보통(Regular)
- **제목(Headline):** 24px, 굵게(Bold)
- **서브타이틀:** 18px, Semi-Bold
- **버튼:** 16px, Medium, 대문자
- **설명/라벨:** 13px, Regular

---

## 4. 컴포넌트 예시
- Button, Input, Card, Modal: React + styled-components 기반 구현
- 다양한 스타일(variant)과 크기(size) props 지원 