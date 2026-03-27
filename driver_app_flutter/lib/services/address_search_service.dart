import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

/// 주소 검색 서비스 (카카오 API)
/// API 키는 환경 변수 또는 Firebase Remote Config에서 로드
class AddressSearchService {
  static const String _baseUrl = 'https://dapi.kakao.com/v2/local/search/keyword.json';
  final String? _apiKey;

  AddressSearchService({String? apiKey}) : _apiKey = apiKey;

  /// 키워드 검색
  Future<List<AddressResult>> search(String query) async {
    if (_apiKey == null || _apiKey!.isEmpty) {
      debugPrint('[AddressSearch] API 키가 설정되지 않았습니다');
      return [];
    }
    if (query.trim().isEmpty) return [];

    try {
      final uri = Uri.parse('$_baseUrl?query=${Uri.encodeComponent(query)}&size=10');
      final response = await http.get(uri, headers: {
        'Authorization': 'KakaoAK $_apiKey',
      });

      if (response.statusCode == 200) {
        final data = json.decode(response.body) as Map<String, dynamic>;
        final documents = data['documents'] as List<dynamic>? ?? [];
        return documents.map((doc) => AddressResult.fromKakao(doc as Map<String, dynamic>)).toList();
      } else {
        debugPrint('[AddressSearch] 검색 실패: ${response.statusCode}');
        return [];
      }
    } catch (e) {
      debugPrint('[AddressSearch] 검색 에러: $e');
      return [];
    }
  }
}

/// 주소 검색 결과
class AddressResult {
  final String placeName;
  final String addressName;
  final String roadAddressName;
  final double latitude;
  final double longitude;

  const AddressResult({
    required this.placeName,
    required this.addressName,
    required this.roadAddressName,
    required this.latitude,
    required this.longitude,
  });

  factory AddressResult.fromKakao(Map<String, dynamic> json) {
    return AddressResult(
      placeName: json['place_name'] as String? ?? '',
      addressName: json['address_name'] as String? ?? '',
      roadAddressName: json['road_address_name'] as String? ?? '',
      latitude: double.tryParse(json['y'] as String? ?? '') ?? 0,
      longitude: double.tryParse(json['x'] as String? ?? '') ?? 0,
    );
  }

  @override
  String toString() => '$placeName ($addressName)';
}
