import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import '../error/failures.dart';

/// UseCase 베이스 클래스
/// 모든 UseCase는 이 클래스를 상속받아야 함
///
/// Params: 입력 파라미터 타입
/// Type: 반환 타입
abstract class UseCase<Type, Params> {
  /// UseCase 실행
  /// Either<Failure, Type> 반환
  ///   - Left: 실패 (Failure)
  ///   - Right: 성공 (Type)
  Future<Either<Failure, Type>> call(Params params);
}

/// 파라미터가 없는 UseCase
class NoParams extends Equatable {
  @override
  List<Object?> get props => [];
}
