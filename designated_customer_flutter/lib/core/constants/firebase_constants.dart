/// Firebase/Firestore 컬렉션 및 필드 이름
class FirebaseConstants {
  // Firestore 컬렉션 이름
  static const String colCustomers = 'customers';
  static const String colAttributions = 'attributions';
  static const String colCalls = 'calls';
  static const String colPoints = 'points';
  static const String colSteps = 'steps';
  static const String colOffices = 'offices';

  // Customer 필드
  static const String fieldDeviceId = 'deviceId';
  static const String fieldFingerprint = 'fingerprint';
  static const String fieldOfficeId = 'officeId';
  static const String fieldCreatedAt = 'createdAt';
  static const String fieldUpdatedAt = 'updatedAt';
  static const String fieldTotalPoints = 'totalPoints';
  static const String fieldIsAnonymous = 'isAnonymous';

  // Attribution 필드
  static const String fieldAttributionToken = 'token';
  static const String fieldAttributionExpiry = 'expiresAt';
  static const String fieldAttributionUsed = 'used';

  // Call 필드
  static const String fieldCallStatus = 'status';
  static const String fieldCallTimestamp = 'timestamp';
  static const String fieldCustomerId = 'customerId';

  // Points 필드
  static const String fieldPointAmount = 'amount';
  static const String fieldPointType = 'type';
  static const String fieldPointDescription = 'description';

  // Steps 필드
  static const String fieldStepCount = 'count';
  static const String fieldStepDate = 'date';

  // FCM 토큰
  static const String fieldFcmToken = 'fcmToken';
}
