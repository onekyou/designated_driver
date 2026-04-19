import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

import '../../../../core/domain/converters/timestamp_converter.dart';

part 'banner_ad_data.freezed.dart';
part 'banner_ad_data.g.dart';

/// 배너 광고 데이터 (Kotlin `BannerAdData.kt:9-61` 포팅).
///
/// ⚠️ **R2 — Phase 1 미사용**. OVERVIEW.md §R2 `R2_BANNER_AD` 발동 시 Notifier 연동.
/// Firestore 경로 미확정 (`banners/{id}` 전역 또는 사무실별 — `BannerAdService.kt` 상세 분석 선행 필요).
@freezed
class BannerAdData with _$BannerAdData {
  const BannerAdData._();

  const factory BannerAdData({
    @Default('') String id,
    @Default('스마트 대리운전 통합 시스템') String text,
    @Default('') String imageUrl,
    @Default('') String linkUrl,
    @Default('#FF6B35') String backgroundColor1,
    @Default('#F7931E') String backgroundColor2,
    @Default('#FFFFFF') String textColor,
    @Default(true) bool isActive,
    @Default(0) int priority,
    @TimestampConverter() DateTime? createdAt,
    @TimestampConverter() DateTime? updatedAt,
  }) = _BannerAdData;

  factory BannerAdData.fromJson(Map<String, Object?> json) =>
      _$BannerAdDataFromJson(json);

  /// Firestore 문서 → BannerAdData. doc.id 주입.
  factory BannerAdData.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? <String, dynamic>{};
    return BannerAdData.fromJson({...data, 'id': doc.id});
  }
}
