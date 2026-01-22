/// 서버/Firebase Exception
class ServerException implements Exception {
  final String message;

  ServerException([this.message = 'Server error occurred']);

  @override
  String toString() => 'ServerException: $message';
}

/// 캐시/로컬 DB Exception
class CacheException implements Exception {
  final String message;

  CacheException([this.message = 'Cache error occurred']);

  @override
  String toString() => 'CacheException: $message';
}

/// 네트워크 연결 Exception
class NetworkException implements Exception {
  final String message;

  NetworkException([this.message = 'Network error occurred']);

  @override
  String toString() => 'NetworkException: $message';
}

/// 인증 Exception
class AuthException implements Exception {
  final String message;

  AuthException([this.message = 'Authentication error occurred']);

  @override
  String toString() => 'AuthException: $message';
}

/// 권한 Exception
class PermissionException implements Exception {
  final String message;

  PermissionException([this.message = 'Permission denied']);

  @override
  String toString() => 'PermissionException: $message';
}

/// 유효성 검사 Exception
class ValidationException implements Exception {
  final String message;

  ValidationException([this.message = 'Validation error occurred']);

  @override
  String toString() => 'ValidationException: $message';
}

/// 위치 Exception
class LocationException implements Exception {
  final String message;

  LocationException([this.message = 'Location error occurred']);

  @override
  String toString() => 'LocationException: $message';
}

/// 구현되지 않음 Exception
class UnimplementedException implements Exception {
  final String message;

  UnimplementedException([this.message = 'Not implemented yet']);

  @override
  String toString() => 'UnimplementedException: $message';
}
