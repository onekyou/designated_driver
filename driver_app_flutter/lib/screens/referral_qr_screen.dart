import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../core/providers.dart';
import '../core/constants/app_constants.dart';
import '../core/theme.dart';

/// 고객 추천 QR 코드 화면
class ReferralQRScreen extends ConsumerStatefulWidget {
  const ReferralQRScreen({super.key});

  @override
  ConsumerState<ReferralQRScreen> createState() => _ReferralQRScreenState();
}

class _ReferralQRScreenState extends ConsumerState<ReferralQRScreen> {
  String? _qrUrl;
  String _driverName = '기사님';
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadQrData();
  }

  Future<void> _loadQrData() async {
    try {
      final session = ref.read(currentSessionProvider);
      if (session == null) {
        setState(() { _isLoading = false; _error = '로그인이 필요합니다.'; });
        return;
      }

      final doc = await FirebaseFirestore.instance
          .collection(AppConstants.collectionProvinces).doc(session.provinceId)
          .collection(AppConstants.collectionCities).doc(session.cityId)
          .collection(AppConstants.collectionOffices).doc(session.officeId)
          .collection(AppConstants.collectionDrivers).doc(session.driverId)
          .get();

      if (!doc.exists) {
        setState(() { _isLoading = false; _error = '기사 정보를 찾을 수 없습니다.'; });
        return;
      }

      final data = doc.data()!;
      final url = data['referralQrUrl'] as String?;
      final name = data[AppConstants.fieldName] as String? ?? '기사님';

      if (url == null || url.isEmpty) {
        setState(() { _isLoading = false; _error = 'QR 코드 URL이 생성되지 않았습니다.\n관리자에게 문의하세요.'; });
        return;
      }

      setState(() { _qrUrl = url; _driverName = name; _isLoading = false; });
    } catch (e) {
      setState(() { _isLoading = false; _error = '로드 실패: $e'; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: const Text('고객 추천하기', style: TextStyle(color: Colors.white)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.red, fontSize: 16)))
              : _buildContent(),
    );
  }

  Widget _buildContent() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        children: [
          const Text('고객 추천 QR 코드', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
          const SizedBox(height: 8),
          Text('$_driverName 기사님', style: const TextStyle(fontSize: 16, color: Colors.white70)),
          const SizedBox(height: 24),

          // QR 코드
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            child: QrImageView(
              data: _qrUrl!,
              version: QrVersions.auto,
              size: 280,
              backgroundColor: Colors.white,
            ),
          ),
          const SizedBox(height: 24),

          // 사용 방법
          Card(
            color: const Color(0xFF2A2A2A),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text('사용 방법', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.primaryColor)),
                  SizedBox(height: 12),
                  Text('1. 고객에게 QR 코드를 보여주세요', style: TextStyle(color: Colors.white70, height: 1.8)),
                  Text('2. 고객이 카메라로 QR을 스캔합니다', style: TextStyle(color: Colors.white70, height: 1.8)),
                  Text('3. 앱 설치 후 자동으로 사무실에 연결됩니다', style: TextStyle(color: Colors.white70, height: 1.8)),
                  Text('4. 고객이 앱으로 콜을 요청할 수 있습니다', style: TextStyle(color: Colors.white70, height: 1.8)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 통계 (준비중)
          Card(
            color: const Color(0xFF2A2A2A),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
              side: BorderSide(color: Colors.grey.shade700),
            ),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _statItem('추천 고객', '준비중'),
                  Container(width: 1, height: 40, color: Colors.grey.shade700),
                  _statItem('이번 달', '준비중'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _statItem(String label, String value) {
    return Column(
      children: [
        Text(label, style: const TextStyle(fontSize: 12, color: Colors.grey)),
        const SizedBox(height: 4),
        Text(value, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white)),
      ],
    );
  }
}
