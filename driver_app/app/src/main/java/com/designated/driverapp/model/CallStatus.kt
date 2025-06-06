package com.designated.driverapp.model

enum class CallStatus(val firestoreValue: String, val displayName: String) {
    WAITING("대기중", "배정대기"),             // 기사 배정 대기중
    ASSIGNED("배정됨", "기사배정"),            // 기사에게 배정됨
    ACCEPTED("수락됨", "배차수락"),            // 기사가 배차 수락
    INPROGRESS("운행시작", "운행중"),          // 운행 시작됨 (Firestore에는 "운행시작"으로 저장, UI에는 "운행중" 표시 가능)
    AWAITING_SETTLEMENT("정산대기중", "정산대기"), // 기사가 운행 완료 후 정산 정보 입력 대기
    COMPLETED("완료", "운행완료"),           // 최종 운행 완료
    CANCELED("취소됨", "취소"),            // 취소됨
    UNKNOWN("알수없음", "알수없음");             // 알 수 없거나 처리되지 않은 상태.

    companion object {
        fun fromFirestoreValue(value: String?): CallStatus {
            return entries.find { it.firestoreValue == value } ?: UNKNOWN
        }
        fun fromDisplayName(value: String?): CallStatus {
            return entries.find { it.displayName == value } ?: UNKNOWN
        }
    }
    // 기사 앱 UI용 한글 변환 함수 (필요시 추가)
    // fun toKorean(): String { ... }
} 