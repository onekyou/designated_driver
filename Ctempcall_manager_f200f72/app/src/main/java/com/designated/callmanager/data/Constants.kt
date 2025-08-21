package com.designated.callmanager.data

object Constants {

    // Firestore Collection Names
    const val COLLECTION_REGIONS = "regions"
    const val COLLECTION_OFFICES = "offices"
    const val COLLECTION_CALLS = "calls"
    const val COLLECTION_DRIVERS = "designated_drivers"
    const val COLLECTION_PENDING_DRIVERS = "pending_drivers"
    const val COLLECTION_ADMINS = "admins"


    // Call Status Fields & Values
    const val FIELD_STATUS = "status"
    const val FIELD_ASSIGNED_DRIVER_ID = "assignedDriverId"
    const val FIELD_UPDATED_AT = "updatedAt"
    // ... other field names can be added here

    // Standardized Call Status Values (as per Firestore Rules)
    const val STATUS_WAITING = "WAITING" // This is a presumed initial status
    const val STATUS_ASSIGNED = "ASSIGNED"
    const val STATUS_ACCEPTED = "ACCEPTED"
    const val STATUS_IN_PROGRESS = "INPROGRESS"
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
    const val DRIVER_STATUS_PREPARING = "PREPARING"
    const val DRIVER_STATUS_ON_TRIP = "ON_TRIP"
    const val DRIVER_STATUS_WAITING = "WAITING" // This is different from call waiting.

    // Office Status
    const val OFFICE_STATUS_OPERATING = "operating"
    const val OFFICE_STATUS_CLOSED_SHARING = "closed_sharing"

    // ViewModel Status
    const val STATUS_LOADING = "loading"

    // Intent Actions & Extras
    const val ACTION_SHOW_CALL_DIALOG = "com.designated.driverapp.ACTION_SHOW_CALL_DIALOG"
    const val EXTRA_CALL_INFO = "com.designated.driverapp.EXTRA_CALL_INFO"
} 