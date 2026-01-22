/// 앱 전역 상수 정의
class AppConstants {
  // 앱 정보
  static const String appName = '대리운전 고객';
  static const String appVersion = '1.0.0';

  // 패키지 정보
  static const String packageName = 'com.designated.customer';

  // API 타임아웃 (밀리초)
  static const int apiTimeout = 30000;

  // 로컬 저장소 키
  static const String keyDeviceId = 'device_id';
  static const String keyFingerprint = 'device_fingerprint';
  static const String keyUserId = 'user_id';
  static const String keyOfficeId = 'office_id';
  static const String keyOfficeName = 'office_name';
  static const String keyOfficePhone = 'office_phone';
  static const String keyLastAttributionToken = 'last_attribution_token';

  // Attribution 관련
  static const int attributionExpiryHours = 24;

  // 기본값
  static const String defaultOfficeName = '알 수 없음';
  static const String defaultOfficePhone = '정보 없음';
}
