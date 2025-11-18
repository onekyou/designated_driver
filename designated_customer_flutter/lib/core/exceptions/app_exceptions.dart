/// 앱 전역 예외 클래스

/// 일반 앱 예외
class AppException implements Exception {
  final String message;
  final String? code;

  AppException(this.message, {this.code});

  @override
  String toString() => 'AppException: $message${code != null ? ' (code: $code)' : ''}';
}

/// 인증 관련 예외
class AuthException implements Exception {
  final String message;
  final String? code;

  AuthException(this.message, {this.code});

  @override
  String toString() => 'AuthException: $message${code != null ? ' (code: $code)' : ''}';
}

/// Attribution 관련 예외
class AttributionException implements Exception {
  final String message;
  final String? code;

  AttributionException(this.message, {this.code});

  @override
  String toString() => 'AttributionException: $message${code != null ? ' (code: $code)' : ''}';
}

/// Firestore 관련 예외
class FirestoreException implements Exception {
  final String message;
  final String? code;

  FirestoreException(this.message, {this.code});

  @override
  String toString() => 'FirestoreException: $message${code != null ? ' (code: $code)' : ''}';
}

/// 네트워크 관련 예외
class NetworkException implements Exception {
  final String message;

  NetworkException(this.message);

  @override
  String toString() => 'NetworkException: $message';
}

/// 저장소 관련 예외
class StorageException implements Exception {
  final String message;

  StorageException(this.message);

  @override
  String toString() => 'StorageException: $message';
}
