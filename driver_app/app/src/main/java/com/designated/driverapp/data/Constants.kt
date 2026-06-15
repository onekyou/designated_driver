package com.designated.driverapp.data

object Constants {

    const val COLLECTION_PROVINCES = "provinces"
    const val COLLECTION_CITIES = "cities"
    const val COLLECTION_OFFICES = "offices"
    const val COLLECTION_CALLS = "calls"
    const val COLLECTION_DRIVERS = "designated_drivers"
    const val COLLECTION_PENDING_DRIVERS = "pending_drivers"
    const val COLLECTION_ADMINS = "admins"

    const val COLLECTION_POINTS = "points"
    const val COLLECTION_POINT_TX = "point_transactions"

    const val COLLECTION_SETTLEMENTS = "settlements"

    const val FIELD_SETTLEMENT_STATUS = "settlementStatus"
    const val FIELD_SETTLEMENT_ID = "settlementId"

    const val SETTLEMENT_STATUS_PENDING = "PENDING"
    const val SETTLEMENT_STATUS_SETTLED = "SETTLED"

    const val FIELD_STATUS = "status"
    const val FIELD_ASSIGNED_DRIVER_ID = "assignedDriverId"
    const val FIELD_FCM_TOKEN = "fcmToken"
    const val FIELD_FCM_TOKEN_PLATFORM = "fcmTokenPlatform"
    const val FIELD_PLATFORM = "platform"
    const val FIELD_UPDATED_AT = "updatedAt"

    const val PLATFORM_ANDROID = "android"

    const val FIELD_PAYMENT_METHOD = "paymentMethod"
    const val FIELD_FARE_FINAL = "fareFinal"
    const val FIELD_TRIP_SUMMARY_FINAL = "tripSummaryFinal"
    const val FIELD_COMPLETED_AT = "completedAt"
    const val FIELD_CASH_RECEIVED = "cashReceived"

    const val PREFS_NAME = "driver_app_prefs"
    const val PREF_KEY_PROVINCE_ID = "pref_province_id"
    const val PREF_KEY_CITY_ID = "pref_city_id"
    const val PREF_KEY_OFFICE_ID = "pref_office_id"
    const val PREF_KEY_PENDING_FCM_TOKEN = "pref_pending_fcm_token"
    const val PREF_KEY_PTT_BLOCK_ONTRIP = "ptt_block_ontrip"  // 운행 중(콜 IN_PROGRESS) PTT 음성 수신 차단 플래그

    const val STATUS_WAITING = "WAITING"
    const val STATUS_ASSIGNED = "ASSIGNED"
    const val STATUS_RESERVED = "RESERVED"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_IN_PROGRESS = "IN_PROGRESS"
    const val STATUS_AWAITING_SETTLEMENT = "AWAITING_SETTLEMENT"
    const val STATUS_COMPLETED = "COMPLETED"
    const val STATUS_CANCELED = "CANCELED"

    const val ACTION_RESERVATION_RECEIVED = "com.designated.driverapp.ACTION_RESERVATION_RECEIVED"

    const val APPROVAL_STATUS_PENDING = "PENDING"
    const val APPROVAL_STATUS_APPROVED = "APPROVED"
    const val APPROVAL_STATUS_REJECTED = "REJECTED"

    const val DRIVER_STATUS_ONLINE = "ONLINE"
    const val DRIVER_STATUS_OFFLINE = "OFFLINE"

    const val ACTION_SHOW_CALL_DIALOG = "com.designated.driverapp.ACTION_SHOW_CALL_DIALOG"
    const val ACTION_CALL_CANCELLED = "com.designated.driverapp.ACTION_CALL_CANCELLED"
    const val ACTION_SETTLEMENT_FINALIZED = "com.designated.driverapp.ACTION_SETTLEMENT_FINALIZED"
    const val EXTRA_CALL_INFO = "com.designated.driverapp.EXTRA_CALL_INFO"
}