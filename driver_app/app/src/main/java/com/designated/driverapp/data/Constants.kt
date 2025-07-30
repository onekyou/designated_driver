package com.designated.driverapp.data

object Constants {

    // Firestore Collection Names
    const val COLLECTION_REGIONS = "regions"
    const val COLLECTION_OFFICES = "offices"
    const val COLLECTION_CALLS = "calls"
    const val COLLECTION_DRIVERS = "designated_drivers"
    const val COLLECTION_PENDING_DRIVERS = "pending_drivers"
    const val COLLECTION_ADMINS = "admins"

    // Points & Settlement
    const val COLLECTION_POINTS = "points"
    const val COLLECTION_POINT_TX = "point_transactions"

    // Settlements
    const val COLLECTION_SETTLEMENTS = "settlements"

    // Settlement Fields & Values
    const val FIELD_SETTLEMENT_STATUS = "settlementStatus"
    const val FIELD_SETTLEMENT_ID = "settlementId"

    const val SETTLEMENT_STATUS_PENDING = "PENDING"
    const val SETTLEMENT_STATUS_SETTLED = "SETTLED"


    // Firestore Field Names
    const val FIELD_STATUS = "status"
    const val FIELD_ASSIGNED_DRIVER_ID = "assignedDriverId"
    const val FIELD_FCM_TOKEN = "fcmToken" // FCM 토큰 필드
    const val FIELD_UPDATED_AT = "updatedAt"

    // Fields for trip settlement and completion
    const val FIELD_PAYMENT_METHOD = "paymentMethod"
    const val FIELD_FARE_FINAL = "fareFinal"
    const val FIELD_TRIP_SUMMARY_FINAL = "tripSummaryFinal"
    const val FIELD_COMPLETED_AT = "completedAt"
    const val FIELD_CASH_RECEIVED = "cashReceived"
    // ... other field names can be added here

    // SharedPreferences Keys
    const val PREFS_NAME = "driver_app_prefs"
    const val PREF_KEY_REGION_ID = "pref_region_id"
    const val PREF_KEY_OFFICE_ID = "pref_office_id"

    // Standardized Call Status Values (as per Firestore Rules)
    const val STATUS_WAITING = "WAITING"
    const val STATUS_ASSIGNED = "ASSIGNED"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_IN_PROGRESS = "IN_PROGRESS"
    const val STATUS_AWAITING_SETTLEMENT = "AWAITING_SETTLEMENT"
    const val STATUS_COMPLETED = "COMPLETED"
    const val STATUS_CANCELED = "CANCELED"


    // Standardized Driver Approval Status Values
    const val APPROVAL_STATUS_PENDING = "PENDING"
    const val APPROVAL_STATUS_APPROVED = "APPROVED"
    const val APPROVAL_STATUS_REJECTED = "REJECTED"


    // Standardized Driver Status Values
    const val DRIVER_STATUS_ONLINE = "ONLINE"
    const val DRIVER_STATUS_OFFLINE = "OFFLINE"


    // Intent Actions & Extras
    const val ACTION_SHOW_CALL_DIALOG = "com.designated.driverapp.ACTION_SHOW_CALL_DIALOG"
    const val EXTRA_CALL_INFO = "com.designated.driverapp.EXTRA_CALL_INFO"
} 