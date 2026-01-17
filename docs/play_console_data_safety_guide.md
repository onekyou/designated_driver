# Google Play Console - Data Safety 섹션 작성 가이드

**작성일:** 2026-01-17

이 문서는 Play Console에서 Data Safety 섹션을 작성할 때 참고할 내용입니다.

---

## 1. 손님앱 (Customer App)

### Data Collection

| 질문 | 답변 |
|------|------|
| Does your app collect or share any of the required user data types? | **Yes** |

### Data Types Collected

#### Location
- [x] **Approximate location**
  - Collected: Yes
  - Shared: No
  - Purpose: App functionality
  - Required: Yes
  - User request deletion: Yes

- [x] **Precise location**
  - Collected: Yes
  - Shared: Yes (with assigned driver)
  - Purpose: App functionality
  - Required: Yes
  - User request deletion: Yes

#### Personal Info
- [x] **Phone number**
  - Collected: Yes
  - Shared: Yes (with assigned driver)
  - Purpose: App functionality, Account management
  - Required: Yes
  - User request deletion: Yes

#### App Activity
- [x] **App interactions**
  - Collected: Yes
  - Shared: No
  - Purpose: Analytics
  - Required: No
  - User request deletion: Yes

#### Device or other IDs
- [x] **Device or other IDs**
  - Collected: Yes (FCM Token)
  - Shared: No
  - Purpose: App functionality (Push notifications)
  - Required: Yes
  - User request deletion: Yes

### Security Practices

| 질문 | 답변 |
|------|------|
| Is data encrypted in transit? | **Yes** |
| Do you provide a way for users to request deletion? | **Yes** |

---

## 2. 기사앱 (Driver App)

### Data Types Collected

#### Location
- [x] **Precise location**
  - Collected: Yes
  - Shared: Yes (with customers, office)
  - Purpose: App functionality
  - Required: Yes
  - User request deletion: Yes

#### Personal Info
- [x] **Name**
  - Collected: Yes
  - Shared: Yes (with customers)
  - Purpose: App functionality
  - Required: Yes
  - User request deletion: Yes

- [x] **Phone number**
  - Collected: Yes
  - Shared: Yes (with customers)
  - Purpose: App functionality, Account management
  - Required: Yes
  - User request deletion: Yes

#### Financial Info
- [x] **Other financial info** (Settlement data)
  - Collected: Yes
  - Shared: No
  - Purpose: App functionality
  - Required: Yes
  - User request deletion: Yes

#### Device or other IDs
- [x] **Device or other IDs**
  - Collected: Yes (FCM Token)
  - Shared: No
  - Purpose: App functionality
  - Required: Yes
  - User request deletion: Yes

### Security Practices

| 질문 | 답변 |
|------|------|
| Is data encrypted in transit? | **Yes** |
| Do you provide a way for users to request deletion? | **Yes** |

---

## 3. 콜매니저 (Call Manager)

### Data Types Collected

#### Location
- [x] **Precise location** (Driver locations)
  - Collected: Yes
  - Shared: No (internal use)
  - Purpose: App functionality
  - Required: Yes

#### Personal Info
- [x] **Phone number** (Customer, Driver)
  - Collected: Yes
  - Shared: No
  - Purpose: App functionality
  - Required: Yes

- [x] **Name** (Driver)
  - Collected: Yes
  - Shared: No
  - Purpose: App functionality
  - Required: Yes

#### Contacts
- [x] **Contacts**
  - Collected: Yes (for caller ID)
  - Shared: No
  - Purpose: App functionality
  - Required: Yes

#### Device or other IDs
- [x] **Device or other IDs**
  - Collected: Yes
  - Shared: No
  - Purpose: App functionality
  - Required: Yes

### Security Practices

| 질문 | 답변 |
|------|------|
| Is data encrypted in transit? | **Yes** |
| Do you provide a way for users to request deletion? | **Yes** |

---

## 4. 콜디텍터 (Call Detector)

### Data Types Collected

#### Personal Info
- [x] **Phone number** (Incoming calls)
  - Collected: Yes
  - Shared: Yes (with Call Manager)
  - Purpose: App functionality
  - Required: Yes

#### Contacts
- [x] **Contacts**
  - Collected: Yes (for caller ID)
  - Shared: No
  - Purpose: App functionality
  - Required: Yes

#### Device or other IDs
- [x] **Device or other IDs**
  - Collected: Yes
  - Shared: No
  - Purpose: App functionality
  - Required: Yes

### Security Practices

| 질문 | 답변 |
|------|------|
| Is data encrypted in transit? | **Yes** |
| Do you provide a way for users to request deletion? | **Yes** |

---

## 5. 공통 질문 답변

### Data Handling

| 질문 | 답변 |
|------|------|
| Is all of the user data collected by your app encrypted in transit? | **Yes** (TLS/SSL) |
| Do you provide a way for users to request that their data be deleted? | **Yes** |

### Account Deletion
- 앱 내 "설정 > 회원 탈퇴" 메뉴 제공
- 탈퇴 시 모든 개인정보 즉시 삭제

---

## 6. 권한 선언 설명 (Play Console 제출 시)

### READ_PHONE_STATE
```
This app uses READ_PHONE_STATE permission to detect incoming phone calls
and automatically register them as service requests. This is essential
for the core functionality of our designated driver dispatch service.
```

### READ_CONTACTS
```
This app uses READ_CONTACTS permission to display the caller's name
when receiving calls, helping dispatchers identify regular customers
and provide better service.
```

### RECORD_AUDIO
```
This app uses RECORD_AUDIO permission for voice input functionality,
allowing users to enter addresses and other information using voice
commands for hands-free operation.
```

### SYSTEM_ALERT_WINDOW
```
This app uses SYSTEM_ALERT_WINDOW permission to display a dispatch
popup when receiving calls, enabling quick driver assignment without
leaving the current screen.
```

### ACCESS_FINE_LOCATION
```
This app uses ACCESS_FINE_LOCATION permission to:
- Customer App: Determine the user's current location for pickup
- Driver App: Share real-time location for customer pickup and
  dispatch optimization
```

---

## 7. 체크리스트

### Play Console 제출 전 확인사항

- [ ] Privacy Policy URL이 유효한지 확인
- [ ] 앱 내 Privacy Policy 링크가 작동하는지 확인
- [ ] Data Safety 섹션 모든 항목 작성 완료
- [ ] 권한 사용 설명이 Play Console 정책에 부합하는지 확인
- [ ] 회원 탈퇴 기능이 정상 작동하는지 확인
- [ ] 데이터 삭제 요청 처리 프로세스 준비 완료

---

**문서 끝**
