/// 앱 전체에서 사용하는 상수들
class AppConstants {
  // Firebase Collections
  static const String collectionProvinces = 'provinces';
  static const String collectionCities = 'cities';
  static const String collectionOffices = 'offices';
  static const String collectionDrivers = 'designated_drivers';
  static const String collectionCalls = 'calls';
  static const String collectionCustomerInfo = 'customerInfo';
  static const String collectionPendingDrivers = 'pending_drivers';
  static const String collectionSettlementSessions = 'settlementSessions';
  static const String collectionAdmins = 'admins';
  static const String collectionManagerTokens = 'managerTokens';
  static const String collectionSharedCalls = 'shared_calls';

  // Firebase Fields
  static const String fieldAuthUid = 'authUid';
  static const String fieldProvinceId = 'provinceId';
  static const String fieldCityId = 'cityId';
  static const String fieldOfficeId = 'officeId';
  static const String fieldName = 'name';
  static const String fieldEmail = 'email';
  static const String fieldPhoneNumber = 'phoneNumber';
  static const String fieldStatus = 'status';
  static const String fieldApprovalStatus = 'approvalStatus';
  static const String fieldFcmToken = 'fcmToken';
  static const String fieldFcmTokenPlatform = 'fcmTokenPlatform';
  static const String fieldPlatform = 'platform';
  static const String fieldAssignedDriverId = 'assignedDriverId';
  static const String fieldAssignedDriverName = 'assignedDriverName';
  static const String fieldCallTime = 'callTime';
  static const String fieldAssignedTime = 'assignedTime';
  static const String fieldAcceptedTime = 'acceptedTime';
  static const String fieldCompletedTime = 'completedTime';
  static const String fieldCustomerName = 'customerName';
  static const String fieldPickupLocation = 'pickupLocation';
  static const String fieldDestination = 'destination';
  static const String fieldFare = 'fare';
  static const String fieldPointsUsed = 'pointsUsed';
  static const String fieldPointsEarned = 'pointsEarned';
  static const String fieldCurrentPoints = 'currentPoints';
  static const String fieldGrade = 'grade';
  static const String fieldTotalCalls = 'totalCalls';
  static const String fieldCashReceived = 'cashReceived';
  static const String fieldPaymentMethod = 'paymentMethod';

  // Driver Status
  static const String statusOffline = 'OFFLINE';
  static const String statusOnline = 'ONLINE';
  static const String statusWaiting = 'WAITING';
  static const String statusAssigned = 'ASSIGNED';
  static const String statusAccepted = 'ACCEPTED';
  static const String statusPreparing = 'PREPARING';
  static const String statusOnTrip = 'ON_TRIP';
  static const String statusInProgress = 'IN_PROGRESS';
  static const String statusAwaitingSettlement = 'AWAITING_SETTLEMENT';
  static const String statusCompleted = 'COMPLETED';

  // Approval Status
  static const String approvalPending = 'PENDING';
  static const String approvalApproved = 'APPROVED';
  static const String approvalRejected = 'REJECTED';

  // Platform Values (FCM 집계용 — functions/src/analytics/acceptanceEvents.ts 소비)
  static const String platformIos = 'ios';
  static const String platformAndroid = 'android';

  // Call Status (Kotlin과 완전 일치)
  static const String callStatusWaiting = 'WAITING';
  static const String callStatusAssigned = 'ASSIGNED';
  static const String callStatusAccepted = 'ACCEPTED';
  static const String callStatusInProgress = 'IN_PROGRESS';
  static const String callStatusAwaitingSettlement = 'AWAITING_SETTLEMENT';
  static const String callStatusCompleted = 'COMPLETED';
  static const String callStatusCanceled = 'CANCELED';
  static const String callStatusCancelledByDriver = 'CANCELLED_BY_DRIVER';
  static const String callStatusCancelledByCustomer = 'CANCELLED_BY_CUSTOMER';
  static const String callStatusHold = 'HOLD';
  static const String callStatusSharedWaiting = 'SHARED_WAITING';
  static const String callStatusClaimed = 'CLAIMED';

  // SharedPreferences Keys
  static const String keyProvinceId = 'provinceId';
  static const String keyCityId = 'cityId';
  static const String keyOfficeId = 'officeId';
  static const String keyDriverId = 'driverId';
  static const String keyUserSession = 'user_session';

  // Session
  static const int sessionValidityDays = 7;
}
