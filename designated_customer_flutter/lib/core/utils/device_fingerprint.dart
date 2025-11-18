import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:device_info_plus/device_info_plus.dart';

/// 기기 고유 식별자 생성 유틸리티
class DeviceFingerprint {
  static final DeviceInfoPlugin _deviceInfo = DeviceInfoPlugin();

  /// Android 기기 정보로 핑거프린트 생성
  static Future<String> generateAndroidFingerprint() async {
    try {
      final androidInfo = await _deviceInfo.androidInfo;

      // 기기 고유 정보 결합
      final deviceData = {
        'brand': androidInfo.brand,
        'device': androidInfo.device,
        'model': androidInfo.model,
        'product': androidInfo.product,
        'hardware': androidInfo.hardware,
        'manufacturer': androidInfo.manufacturer,
        'androidId': androidInfo.id, // Android ID
      };

      // JSON 문자열로 변환
      final jsonString = jsonEncode(deviceData);

      // SHA-256 해시 생성
      final bytes = utf8.encode(jsonString);
      final digest = sha256.convert(bytes);

      return digest.toString();
    } catch (e) {
      throw Exception('Android 핑거프린트 생성 실패: $e');
    }
  }

  /// iOS 기기 정보로 핑거프린트 생성
  static Future<String> generateIosFingerprint() async {
    try {
      final iosInfo = await _deviceInfo.iosInfo;

      // 기기 고유 정보 결합
      final deviceData = {
        'name': iosInfo.name,
        'model': iosInfo.model,
        'systemName': iosInfo.systemName,
        'systemVersion': iosInfo.systemVersion,
        'identifierForVendor': iosInfo.identifierForVendor ?? '',
        'utsname': {
          'machine': iosInfo.utsname.machine,
          'nodename': iosInfo.utsname.nodename,
          'release': iosInfo.utsname.release,
          'sysname': iosInfo.utsname.sysname,
          'version': iosInfo.utsname.version,
        },
      };

      // JSON 문자열로 변환
      final jsonString = jsonEncode(deviceData);

      // SHA-256 해시 생성
      final bytes = utf8.encode(jsonString);
      final digest = sha256.convert(bytes);

      return digest.toString();
    } catch (e) {
      throw Exception('iOS 핑거프린트 생성 실패: $e');
    }
  }

  /// 플랫폼에 맞는 핑거프린트 생성
  static Future<String> generate() async {
    try {
      if (Platform.isAndroid) {
        return await generateAndroidFingerprint();
      } else if (Platform.isIOS) {
        return await generateIosFingerprint();
      } else {
        throw UnsupportedError('지원하지 않는 플랫폼입니다');
      }
    } catch (e) {
      throw Exception('핑거프린트 생성 실패: $e');
    }
  }

  /// 기기 ID 생성 (간단한 버전)
  static Future<String> getDeviceId() async {
    try {
      if (Platform.isAndroid) {
        final androidInfo = await _deviceInfo.androidInfo;
        return androidInfo.id; // Android ID
      } else if (Platform.isIOS) {
        final iosInfo = await _deviceInfo.iosInfo;
        return iosInfo.identifierForVendor ?? 'unknown_ios_device';
      } else {
        return 'unknown_device';
      }
    } catch (e) {
      return 'error_device';
    }
  }
}
