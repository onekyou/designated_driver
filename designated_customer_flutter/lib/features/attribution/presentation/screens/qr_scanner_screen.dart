import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../models/attribution_result.dart';

/// QR 코드 스캔 화면
///
/// 사무실 QR 코드를 스캔하여 Attribution 정보 추출
/// URL 형식: https://example.com?r=Hongchon&o=office123&d=driver456&dn=김기사
class QrScannerScreen extends StatefulWidget {
  final Function(AttributionResult) onQrCodeScanned;

  const QrScannerScreen({
    super.key,
    required this.onQrCodeScanned,
  });

  @override
  State<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends State<QrScannerScreen> {
  final MobileScannerController _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
  );

  bool _isProcessing = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  /// QR 코드 스캔 결과 처리
  void _handleBarcode(BarcodeCapture barcodeCapture) {
    if (_isProcessing) return;

    final List<Barcode> barcodes = barcodeCapture.barcodes;
    if (barcodes.isEmpty) return;

    final String? code = barcodes.first.rawValue;
    if (code == null) return;

    setState(() => _isProcessing = true);

    debugPrint('[QR Scanner] 스캔 결과: $code');

    try {
      // URL 파싱
      final uri = Uri.parse(code);
      final params = uri.queryParameters;

      final regionId = params['r'];
      final officeId = params['o'];
      final driverId = params['d'];
      final driverName = params['dn'];

      if (regionId == null || officeId == null) {
        _showError('유효하지 않은 QR 코드입니다.');
        setState(() => _isProcessing = false);
        return;
      }

      // Attribution 결과 생성
      final result = AttributionResult(
        regionId: regionId,
        officeId: officeId,
        referralDriverId: driverId,
        referralDriverName: driverName,
        token: null, // QR 스캔은 토큰 없음
        method: 'qr_scan',
      );

      // 콜백 호출
      widget.onQrCodeScanned(result);

      // 화면 닫기
      if (mounted) {
        Navigator.of(context).pop(result);
      }
    } catch (e) {
      debugPrint('[QR Scanner] 파싱 실패: $e');
      _showError('QR 코드를 읽을 수 없습니다.');
      setState(() => _isProcessing = false);
    }
  }

  /// 에러 메시지 표시
  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.red,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('QR 코드 스캔'),
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
      ),
      body: Stack(
        children: [
          // QR 스캐너
          MobileScanner(
            controller: _controller,
            onDetect: _handleBarcode,
          ),

          // 스캔 가이드 오버레이
          Center(
            child: Container(
              width: 250,
              height: 250,
              decoration: BoxDecoration(
                border: Border.all(
                  color: Colors.white,
                  width: 3,
                ),
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),

          // 안내 문구
          Positioned(
            bottom: 100,
            left: 0,
            right: 0,
            child: Container(
              padding: const EdgeInsets.all(16),
              color: Colors.black.withOpacity(0.7),
              child: const Text(
                'QR 코드를 화면 중앙에 맞춰주세요',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),

          // 로딩 인디케이터
          if (_isProcessing)
            Container(
              color: Colors.black.withOpacity(0.5),
              child: const Center(
                child: CircularProgressIndicator(
                  valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
