# Google Apps Script + Sheets 설정 가이드

> 계정: calllinkdrive@gmail.com 으로 진행

---

## 1단계: Google Sheets 생성

1. **calllinkdrive@gmail.com** 으로 로그인
2. [sheets.new](https://sheets.new) 접속 → 새 스프레드시트 생성
3. 시트 이름을 **"콜마당 파일럿 신청"** 으로 변경 (좌측 상단 "제목 없는 스프레드시트" 클릭)
4. **A1~H1** 셀에 아래 헤더를 입력:

| A1 | B1 | C1 | D1 | E1 | F1 | G1 | H1 |
|---|---|---|---|---|---|---|---|
| 신청일시 | 사무실이름 | 대표자 | 연락처 | 지역 | 일일콜수 | 문의사항 | 유입경로 |

5. 헤더 행 선택 → **굵게(Ctrl+B)** 처리 (선택사항)

---

## 2단계: Apps Script 생성

1. 스프레드시트 상단 메뉴에서 **확장 프로그램 → Apps Script** 클릭
2. 기존 코드(`function myFunction()...`)를 **전부 삭제**
3. 아래 코드를 **그대로 복사 붙여넣기**:

```javascript
function doPost(e) {
  try {
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    var data = JSON.parse(e.postData.contents);

    sheet.appendRow([
      data.timestamp || new Date().toLocaleString('ko-KR'),
      data.officeName || '',
      data.ownerName || '',
      data.phone || '',
      data.region || '',
      data.dailyCalls || '',
      data.message || '',
      data.ref || 'direct'
    ]);

    return ContentService
      .createTextOutput(JSON.stringify({ result: 'success' }))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService
      .createTextOutput(JSON.stringify({ result: 'error', message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
```

4. 좌측 상단 프로젝트 이름을 **"콜마당 신청폼"** 으로 변경
5. **Ctrl+S** 로 저장

---

## 3단계: 웹앱으로 배포

1. 우측 상단 **배포 → 새 배포** 클릭
2. 좌측 톱니바퀴(⚙) 아이콘 → **웹 앱** 선택
3. 설정:
   - **설명**: 콜마당 파일럿 신청 폼
   - **실행 사용자**: **나(calllinkdrive@gmail.com)**
   - **액세스 권한**: **모든 사용자** (⚠ 반드시 "모든 사용자"로 설정!)
4. **배포** 클릭
5. "액세스 승인" 팝업 → **calllinkdrive 계정 선택**
6. "이 앱은 확인되지 않았습니다" 경고 → **고급 → 콜마당 신청폼(으)로 이동** 클릭
7. **허용** 클릭

---

## 4단계: URL 복사

배포 완료 후 나오는 **웹 앱 URL**을 복사합니다.

형식: `https://script.google.com/macros/s/AKfycb.../exec`

> 이 URL을 저에게 알려주시면 홈페이지에 적용하겠습니다.

---

## 5단계: 테스트 (선택)

배포 후 시트에 제대로 들어오는지 확인하려면, 브라우저 주소창에 아래를 붙여넣기:

이건 홈페이지 적용 후 신청폼을 직접 제출해보는 것으로 대체해도 됩니다.

---

## 주의사항

- Apps Script를 **수정**한 후에는 반드시 **새 배포**(버전 업데이트)를 해야 반영됩니다
- "모든 사용자" 액세스가 아니면 외부에서 접근 불가합니다
- 스프레드시트를 삭제하면 스크립트도 함께 사라집니다
