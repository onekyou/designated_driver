/// 앱 전체에서 사용하는 상수들
class AppConstants {
  // Firebase Collections
  static const String collectionRegions = 'regions';
  static const String collectionOffices = 'offices';
  static const String collectionDrivers = 'designated_drivers';
  static const String collectionCalls = 'calls';
  static const String collectionCustomerInfo = 'customerInfo';
  static const String collectionCustomerPoints = 'customerPoints';
  static const String collectionPendingDrivers = 'pending_drivers';

  // Firebase Fields
  static const String fieldAuthUid = 'authUid';
  static const String fieldRegionId = 'regionId';
  static const String fieldOfficeId = 'officeId';
  static const String fieldName = 'name';
  static const String fieldEmail = 'email';
  static const String fieldPhoneNumber = 'phoneNumber';
  static const String fieldStatus = 'status';
  static const String fieldApprovalStatus = 'approvalStatus';
  static const String fieldFcmToken = 'fcmToken';
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

  // Driver Status
  static const String statusOffline = 'OFFLINE';
  static const String statusOnline = 'ONLINE';
  static const String statusWaiting = 'WAITING';
  static const String statusAssigned = 'ASSIGNED';
  static const String statusAccepted = 'ACCEPTED';
  static const String statusInProgress = 'IN_PROGRESS';
  static const String statusAwaitingSettlement = 'AWAITING_SETTLEMENT';
  static const String statusCompleted = 'COMPLETED';

  // Approval Status
  static const String approvalPending = 'PENDING';
  static const String approvalApproved = 'APPROVED';
  static const String approvalRejected = 'REJECTED';

  // Call Status
  static const String callStatusPending = 'PENDING';
  static const String callStatusAssigned = 'ASSIGNED';
  static const String callStatusAccepted = 'ACCEPTED';
  static const String callStatusPickedUp = 'PICKED_UP';
  static const String callStatusCompleted = 'COMPLETED';
  static const String callStatusCancelled = 'CANCELLED';

  // Customer Grades
  static const String gradeBronze = 'BRONZE';
  static const String gradeSilver = 'SILVER';
  static const String gradeGold = 'GOLD';
  static const String gradeVip = 'VIP';

  // Points Earning Rates
  static const double bronzeEarningRate = 0.03; // 3%
  static const double silverEarningRate = 0.05; // 5%
  static const double goldEarningRate = 0.07;   // 7%
  static const double vipEarningRate = 0.09;    // 9%

  // SharedPreferences Keys
  static const String keyRegionId = 'regionId';
  static const String keyOfficeId = 'officeId';
  static const String keyDriverId = 'driverId';
  static const String keyUserSession = 'user_session';

  // Session
  static const int sessionValidityDays = 7;

  // Cache TTL
  static const int cacheCustomerPointsTTL = 5 * 60; // 5분 (초)
  static const int cacheOfficeInfoTTL = 24 * 60 * 60; // 24시간 (초)
}
