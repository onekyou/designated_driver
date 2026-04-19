import 'package:customer_app_flutter/features/ad/domain/entities/banner_ad_data.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('BannerAdData (R2 뼈대)', () {
    test('빈 JSON에서 Defaults 적용 (Kotlin 원본 일치)', () {
      final banner = BannerAdData.fromJson({});
      expect(banner.id, '');
      expect(banner.text, '스마트 대리운전 통합 시스템');
      expect(banner.imageUrl, '');
      expect(banner.linkUrl, '');
      expect(banner.backgroundColor1, '#FF6B35');
      expect(banner.backgroundColor2, '#F7931E');
      expect(banner.textColor, '#FFFFFF');
      expect(banner.isActive, isTrue);
      expect(banner.priority, 0);
      expect(banner.createdAt, isNull);
      expect(banner.updatedAt, isNull);
    });

    test('fromJson/toJson round-trip', () {
      final json = <String, Object?>{
        'id': 'banner1',
        'text': '새 프로모션',
        'imageUrl': 'https://example.com/banner.png',
        'linkUrl': 'https://example.com',
        'backgroundColor1': '#000000',
        'backgroundColor2': '#FFFFFF',
        'textColor': '#FF0000',
        'isActive': false,
        'priority': 5,
      };
      final banner = BannerAdData.fromJson(json);
      expect(banner.id, 'banner1');
      expect(banner.isActive, isFalse);
      expect(banner.priority, 5);

      final back = banner.toJson();
      expect(back['id'], 'banner1');
      expect(back['text'], '새 프로모션');
      expect(back['priority'], 5);
    });
  });
}
