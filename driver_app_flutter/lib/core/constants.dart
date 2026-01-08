class AppConstants {
  // Firebase Collections
  static const String collectionRegions = 'regions';
  static const String collectionOffices = 'offices';
  static const String collectionDrivers = 'designated_drivers';
  static const String collectionPendingDrivers = 'pending_drivers';
  static const String collectionCalls = 'calls';

  // Firestore Fields
  static const String fieldFcmToken = 'fcmToken';
  static const String fieldAuthUid = 'authUid';
  static const String fieldStatus = 'status';
  static const String fieldApprovalStatus = 'approvalStatus';
  static const String fieldRegionId = 'regionId';
  static const String fieldOfficeId = 'officeId';
  static const String fieldName = 'name';

  // Driver Status
  static const String statusOnline = 'ONLINE';
  static const String statusOffline = 'OFFLINE';
  static const String statusBusy = 'BUSY';

  // Approval Status
  static const String approvalPending = 'PENDING';
  static const String approvalApproved = 'APPROVED';
  static const String approvalRejected = 'REJECTED';

  // Session Settings
  static const int sessionValidityDays = 7;

  // SharedPreferences Keys
  static const String keyAutoLogin = 'auto_login';
  static const String keyUserSession = 'user_session';
  static const String keyRegionId = 'regionId';
  static const String keyOfficeId = 'officeId';
  static const String keyDriverId = 'driverId';
}
