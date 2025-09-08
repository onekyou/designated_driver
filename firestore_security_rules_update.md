# Firestore 보안 규칙 수정 가이드

## 현재 오류
`PERMISSION_DENIED: Missing or insufficient permissions` 오류가 발생하여 사무실 status 필드를 업데이트할 수 없습니다.

## 해결 방법

Firebase Console → Firestore Database → Rules에서 다음과 같이 수정하세요:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // 기존 규칙들...
    
    // 사무실 문서 접근 규칙 (status 필드 업데이트 포함)
    match /regions/{regionId}/offices/{officeId} {
      allow read, write: if request.auth != null && 
        (exists(/databases/$(database)/documents/admins/$(request.auth.uid)) &&
         get(/databases/$(database)/documents/admins/$(request.auth.uid)).data.associatedRegionId == regionId &&
         get(/databases/$(database)/documents/admins/$(request.auth.uid)).data.associatedOfficeId == officeId);
      
      // 특별히 status 필드 업데이트 허용
      allow update: if request.auth != null &&
        resource.data.keys().hasAll(['status']) == false || // status 필드가 없는 경우
        (request.resource.data.diff(resource.data).affectedKeys().hasOnly(['status'])); // status 필드만 변경하는 경우
    }
  }
}
```

## 또는 임시 테스트용 규칙

테스트 목적으로 임시로 모든 읽기/쓰기를 허용하려면:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## 단계별 수정 방법

1. Firebase Console 접속
2. 프로젝트 선택
3. Firestore Database → Rules 탭 클릭
4. 위 규칙 중 하나를 복사하여 붙여넣기
5. "게시" 버튼 클릭

## 권장사항

- 프로덕션 환경에서는 구체적인 권한 규칙 사용
- 테스트 중에는 임시 규칙으로 문제 해결 확인 후 보안 규칙 재설정