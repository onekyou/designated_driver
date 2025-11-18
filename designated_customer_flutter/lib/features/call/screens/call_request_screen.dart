import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/call_provider.dart';
import '../providers/location_provider.dart';
import '../../attribution/providers/attribution_provider.dart';

/// 콜 요청 화면
class CallRequestScreen extends ConsumerStatefulWidget {
  const CallRequestScreen({super.key});

  @override
  ConsumerState<CallRequestScreen> createState() => _CallRequestScreenState();
}

class _CallRequestScreenState extends ConsumerState<CallRequestScreen> {
  final _formKey = GlobalKey<FormState>();
  final _currentLocationController = TextEditingController();
  final _destinationController = TextEditingController();
  final _notesController = TextEditingController();

  static const platform = MethodChannel('com.designated.customer/speech');
  bool _isListening = false;

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    _currentLocationController.dispose();
    _destinationController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  /// 현재 위치 자동 입력
  Future<void> _loadCurrentLocation() async {
    try {
      // 로딩 표시
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('현재 위치를 가져오는 중...')),
      );

      // 위치 권한 확인
      final locationService = ref.read(locationServiceProvider);
      final hasPermission = await locationService.checkLocationPermission();

      if (!hasPermission) {
        final granted = await locationService.requestLocationPermission();
        if (!granted) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('위치 권한이 필요합니다')),
            );
          }
          return;
        }
      }

      // 현재 위치 가져오기
      final position = await locationService.getCurrentPosition();
      if (position.latitude == null || position.longitude == null) {
        throw Exception('위치 좌표를 가져올 수 없습니다');
      }

      final address = await locationService.getAddressFromCoordinates(
        position.latitude!,
        position.longitude!,
      );

      setState(() {
        _currentLocationController.text = address;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('현재 위치를 가져왔습니다')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('위치를 가져올 수 없습니다: $e')),
        );
      }
    }
  }

  /// 음성 인식 시작 (Native Android Platform Channel 사용)
  Future<void> _startListening() async {
    if (_isListening) return;

    setState(() => _isListening = true);

    try {
      final String result = await platform.invokeMethod('startListening');
      if (mounted) {
        setState(() {
          _destinationController.text = result;
          _isListening = false;
        });
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => _isListening = false);
        if (e.code != 'CANCELLED') {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('음성 인식 오류: ${e.message}')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isListening = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('음성 인식 오류: $e')),
        );
      }
    }
  }

  /// 콜 요청 제출
  Future<void> _submitCallRequest() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    try {
      // 로딩 표시
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => const Center(
          child: CircularProgressIndicator(),
        ),
      );

      // 콜 요청
      final callId = await ref.read(callProvider.notifier).requestCall(
            currentLocation: _currentLocationController.text,
            destinationLocation: _destinationController.text,
            notes: _notesController.text.isEmpty ? null : _notesController.text,
          );

      if (mounted) {
        Navigator.pop(context); // 로딩 다이얼로그 닫기
        Navigator.pop(context); // 화면 닫기

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('콜 요청이 완료되었습니다 (ID: $callId)')),
        );
      }
    } catch (e) {
      if (mounted) {
        Navigator.pop(context); // 로딩 다이얼로그 닫기
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('콜 요청 실패: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final attribution = ref.watch(attributionNotifierProvider).value;
    final officeName = attribution?.officeName ?? '알 수 없음';

    return Scaffold(
      appBar: AppBar(
        title: const Text('콜 요청'),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 사무실 정보
              Card(
                color: Theme.of(context).colorScheme.primaryContainer,
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      Icon(
                        Icons.business,
                        size: 48,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        officeName,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '대리운전 서비스',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 24),

              // 현재 위치 (출발지)
              Row(
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _currentLocationController,
                      decoration: const InputDecoration(
                        labelText: '출발지',
                        hintText: '현재 위치 또는 출발지를 입력하세요',
                        prefixIcon: Icon(Icons.my_location),
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return '출발지를 입력해주세요';
                        }
                        return null;
                      },
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    onPressed: _loadCurrentLocation,
                    icon: const Icon(Icons.gps_fixed),
                    tooltip: '현재 위치',
                  ),
                ],
              ),

              const SizedBox(height: 16),

              // 목적지
              Row(
                children: [
                  Expanded(
                    child: TextFormField(
                      controller: _destinationController,
                      decoration: const InputDecoration(
                        labelText: '목적지',
                        hintText: '목적지를 입력하세요',
                        prefixIcon: Icon(Icons.location_on),
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return '목적지를 입력해주세요';
                        }
                        return null;
                      },
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    onPressed: _startListening,
                    icon: Icon(_isListening ? Icons.mic : Icons.mic_none),
                    tooltip: '음성 입력',
                    style: IconButton.styleFrom(
                      backgroundColor: _isListening
                          ? Colors.red
                          : Theme.of(context).colorScheme.primary,
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 16),

              // 메모
              TextFormField(
                controller: _notesController,
                decoration: const InputDecoration(
                  labelText: '메모 (선택사항)',
                  hintText: '추가 요청사항이 있으면 입력하세요',
                  prefixIcon: Icon(Icons.note),
                  border: OutlineInputBorder(),
                ),
                maxLines: 3,
              ),

              const SizedBox(height: 24),

              // 요청 버튼
              FilledButton.icon(
                onPressed: _submitCallRequest,
                icon: const Icon(Icons.local_taxi),
                label: const Text('대리운전 요청하기'),
                style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  textStyle: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),

              const SizedBox(height: 16),

              // 안내 문구
              Card(
                color: Colors.blue.shade50,
                child: Padding(
                  padding: const EdgeInsets.all(12.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.info_outline,
                              size: 20,
                              color: Colors.blue.shade700),
                          const SizedBox(width: 8),
                          Text(
                            '이용 안내',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: Colors.blue.shade700,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        '• 요청하신 콜은 관리자가 확인 후 기사를 배정합니다\n'
                        '• 배정 완료 시 앱에서 알림을 받으실 수 있습니다\n'
                        '• 기사 배정 전까지 콜을 취소할 수 있습니다',
                        style: TextStyle(fontSize: 13),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
