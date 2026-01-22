import 'package:equatable/equatable.dart';

/// 모든 Failure의 베이스 클래스
/// Either<Failure, Success> 패턴에서 사용
abstract class Failure extends Equatable {
  final String message;

  const Failure(this.message);

  @override
  List<Object?> get props => [message];
}

/// 서버/Firebase 에러
class ServerFailure extends Failure {
  const ServerFailure([String message = '서버 오류가 발생했습니다.']) : super(message);
}

/// 캐시/로컬 DB 에러
class CacheFailure extends Failure {
  const CacheFailure([String message = '로컬 데이터 오류가 발생했습니다.']) : super(message);
}

/// 네트워크 연결 에러
class NetworkFailure extends Failure {
  const NetworkFailure([String message = '네트워크 연결을 확인해주세요.']) : super(message);
}

/// 인증 에러
class AuthFailure extends Failure {
  const AuthFailure([String message = '인증에 실패했습니다.']) : super(message);
}

/// 권한 에러
class PermissionFailure extends Failure {
  const PermissionFailure([String message = '권한이 필요합니다.']) : super(message);
}

/// 유효성 검사 에러
class ValidationFailure extends Failure {
  const ValidationFailure([String message = '입력값이 올바르지 않습니다.']) : super(message);
}

/// 위치 에러
class LocationFailure extends Failure {
  const LocationFailure([String message = '위치 정보를 가져올 수 없습니다.']) : super(message);
}

/// 알 수 없는 에러
class UnknownFailure extends Failure {
  const UnknownFailure([String message = '알 수 없는 오류가 발생했습니다.']) : super(message);
}
