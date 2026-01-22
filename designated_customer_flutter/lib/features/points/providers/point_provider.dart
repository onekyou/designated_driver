import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/customer_points.dart';
import '../models/point_transaction.dart';
import '../repositories/point_repository.dart';
import '../services/point_service.dart';
import '../../attribution/providers/attribution_provider.dart';
import '../../auth/providers/auth_provider.dart';

/// PointRepository Provider
final pointRepositoryProvider = Provider<PointRepository>((ref) {
  final attribution = ref.watch(attributionNotifierProvider).value;

  if (attribution == null) {
    throw Exception('Attribution이 없습니다');
  }

  return PointRepository(
    regionId: attribution.regionId,
    officeId: attribution.officeId,
  );
});

/// PointService Provider
final pointServiceProvider = Provider<PointService>((ref) {
  final repository = ref.watch(pointRepositoryProvider);

  return PointService(
    repository: repository,
  );
});

/// 현재 고객의 포인트 정보 Provider (실시간 스트림)
final customerPointsProvider = StreamProvider<CustomerPoints?>((ref) {
  final auth = ref.watch(authNotifierProvider).value;

  if (auth?.phoneNumber == null) {
    return Stream.value(null);
  }

  final service = ref.watch(pointServiceProvider);
  return service.observeCustomerPoints(auth!.phoneNumber!);
});

/// 포인트 거래 내역 Provider (실시간 스트림)
final pointTransactionsProvider = StreamProvider.family<List<PointTransaction>, int>(
  (ref, limit) {
    final auth = ref.watch(authNotifierProvider).value;

    if (auth?.phoneNumber == null) {
      return Stream.value([]);
    }

    final service = ref.watch(pointServiceProvider);
    return service.observePointTransactions(
      auth!.phoneNumber!,
      limit: limit,
    );
  },
);

/// 포인트 작업을 위한 Notifier
class PointNotifier extends StateNotifier<AsyncValue<void>> {
  final PointService _service;
  final String? _phoneNumber;

  PointNotifier(this._service, this._phoneNumber)
      : super(const AsyncValue.data(null));

  /// 포인트 적립
  Future<bool> earnPoints({
    required String callId,
    required int fare,
    String? description,
  }) async {
    if (_phoneNumber == null) {
      state = AsyncValue.error('전화번호가 없습니다', StackTrace.current);
      return false;
    }

    state = const AsyncValue.loading();

    try {
      final success = await _service.earnPoints(
        phoneNumber: _phoneNumber!,
        callId: callId,
        fare: fare,
        description: description,
      );

      state = const AsyncValue.data(null);
      return success;
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
      return false;
    }
  }

  /// 포인트 사용
  Future<bool> usePoints({
    required int amount,
    String? callId,
    String description = '포인트 사용',
  }) async {
    if (_phoneNumber == null) {
      state = AsyncValue.error('전화번호가 없습니다', StackTrace.current);
      return false;
    }

    state = const AsyncValue.loading();

    try {
      final success = await _service.usePoints(
        phoneNumber: _phoneNumber!,
        amount: amount,
        callId: callId,
        description: description,
      );

      state = const AsyncValue.data(null);
      return success;
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
      return false;
    }
  }

  /// 포인트 적립 미리보기
  Future<Map<String, dynamic>?> previewEarnPoints(int fare) async {
    if (_phoneNumber == null) return null;

    try {
      return await _service.previewEarnPoints(_phoneNumber!, fare);
    } catch (e) {
      return null;
    }
  }

  /// 사용 가능한 최대 포인트
  Future<int> getAvailablePoints() async {
    if (_phoneNumber == null) return 0;

    try {
      return await _service.getAvailablePoints(_phoneNumber!);
    } catch (e) {
      return 0;
    }
  }

  /// 포인트 사용 가능 여부
  Future<bool> canUsePoints(int amount) async {
    if (_phoneNumber == null) return false;

    try {
      return await _service.canUsePoints(_phoneNumber!, amount);
    } catch (e) {
      return false;
    }
  }
}

/// PointNotifier Provider
final pointNotifierProvider =
    StateNotifierProvider<PointNotifier, AsyncValue<void>>((ref) {
  final service = ref.watch(pointServiceProvider);
  final auth = ref.watch(authNotifierProvider).value;

  return PointNotifier(service, auth?.phoneNumber);
});

/// 특정 기간의 포인트 거래 내역 조회 Provider
final pointTransactionsByDateRangeProvider = FutureProvider.family<
    List<PointTransaction>,
    ({DateTime startDate, DateTime endDate})>((ref, params) async {
  final auth = ref.watch(authNotifierProvider).value;

  if (auth?.phoneNumber == null) {
    return [];
  }

  final service = ref.watch(pointServiceProvider);
  return await service.getPointTransactionsByDateRange(
    auth!.phoneNumber!,
    params.startDate,
    params.endDate,
  );
});

/// 포인트 거래 타입별 조회 Provider
final pointTransactionsByTypeProvider = FutureProvider.family<
    List<PointTransaction>,
    ({TransactionType type, int limit})>((ref, params) async {
  final auth = ref.watch(authNotifierProvider).value;

  if (auth?.phoneNumber == null) {
    return [];
  }

  final service = ref.watch(pointServiceProvider);
  return await service.getPointTransactionsByType(
    auth!.phoneNumber!,
    params.type,
    limit: params.limit,
  );
});
