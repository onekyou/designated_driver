import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../repositories/attribution_repository.dart';
import '../models/attribution_result.dart';

/// AttributionRepository Provider
final attributionRepositoryProvider = Provider<AttributionRepository>((ref) {
  return AttributionRepository();
});

/// Attribution 상태 Provider
class AttributionNotifier extends StateNotifier<AsyncValue<AttributionResult?>> {
  final AttributionRepository _repository;

  AttributionNotifier(this._repository) : super(const AsyncValue.loading()) {
    _initialize();
  }

  /// 초기화 - 저장된 Attribution 정보 로드
  Future<void> _initialize() async {
    state = const AsyncValue.loading();
    try {
      final savedAttribution = await _repository.getSavedAttribution();
      state = AsyncValue.data(savedAttribution);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  /// Attribution 매칭
  Future<void> matchAttribution({String? token}) async {
    state = const AsyncValue.loading();
    try {
      final result = await _repository.matchAttribution(token: token);
      state = AsyncValue.data(result);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  /// Attribution 정보 새로고침
  Future<void> refresh() async {
    try {
      final savedAttribution = await _repository.getSavedAttribution();
      state = AsyncValue.data(savedAttribution);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  /// Attribution 정보 삭제
  Future<void> clearAttribution() async {
    try {
      await _repository.clearAttribution();
      state = const AsyncValue.data(null);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}

/// AttributionNotifier Provider
final attributionNotifierProvider =
    StateNotifierProvider<AttributionNotifier, AsyncValue<AttributionResult?>>((ref) {
  final repository = ref.watch(attributionRepositoryProvider);
  return AttributionNotifier(repository);
});

/// 저장된 Attribution 정보 Provider
final savedAttributionProvider = FutureProvider<AttributionResult?>((ref) async {
  final repository = ref.watch(attributionRepositoryProvider);
  return await repository.getSavedAttribution();
});
