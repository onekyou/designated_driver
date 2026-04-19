// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'banner_ad_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$BannerAdDataImpl _$$BannerAdDataImplFromJson(Map<String, dynamic> json) =>
    _$BannerAdDataImpl(
      id: json['id'] as String? ?? '',
      text: json['text'] as String? ?? '스마트 대리운전 통합 시스템',
      imageUrl: json['imageUrl'] as String? ?? '',
      linkUrl: json['linkUrl'] as String? ?? '',
      backgroundColor1: json['backgroundColor1'] as String? ?? '#FF6B35',
      backgroundColor2: json['backgroundColor2'] as String? ?? '#F7931E',
      textColor: json['textColor'] as String? ?? '#FFFFFF',
      isActive: json['isActive'] as bool? ?? true,
      priority: (json['priority'] as num?)?.toInt() ?? 0,
      createdAt: const TimestampConverter().fromJson(json['createdAt']),
      updatedAt: const TimestampConverter().fromJson(json['updatedAt']),
    );

Map<String, dynamic> _$$BannerAdDataImplToJson(_$BannerAdDataImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'text': instance.text,
      'imageUrl': instance.imageUrl,
      'linkUrl': instance.linkUrl,
      'backgroundColor1': instance.backgroundColor1,
      'backgroundColor2': instance.backgroundColor2,
      'textColor': instance.textColor,
      'isActive': instance.isActive,
      'priority': instance.priority,
      'createdAt': const TimestampConverter().toJson(instance.createdAt),
      'updatedAt': const TimestampConverter().toJson(instance.updatedAt),
    };
