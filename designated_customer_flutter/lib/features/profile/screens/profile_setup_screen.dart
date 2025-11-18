import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_functions/cloud_functions.dart';
import '../../../core/services/storage_service.dart';

/// 프로필 설정 화면 상태
class ProfileSetupState {
  final String nickname;
  final String phoneNumber;
  final String address;
  final bool isLoading;
  final String? error;

  ProfileSetupState({
    this.nickname = '',
    this.phoneNumber = '',
    this.address = '',
    this.isLoading = false,
    this.error,
  });

  ProfileSetupState copyWith({
    String? nickname,
    String? phoneNumber,
    String? address,
    bool? isLoading,
    String? error,
  }) {
    return ProfileSetupState(
      nickname: nickname ?? this.nickname,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      address: address ?? this.address,
      isLoading: isLoading ?? this.isLoading,
      error: error,
    );
  }
}

/// 프로필 설정 Notifier
class ProfileSetupNotifier extends StateNotifier<ProfileSetupState> {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final StorageService _storage = StorageService();
  final FirebaseAuth _auth = FirebaseAuth.instance;

  ProfileSetupNotifier() : super(ProfileSetupState());

  void updateNickname(String value) {
    state = state.copyWith(nickname: value, error: null);
  }

  void updatePhoneNumber(String value) {
    state = state.copyWith(phoneNumber: value, error: null);
  }

  void updateAddress(String value) {
    state = state.copyWith(address: value, error: null);
  }

  void clearError() {
    state = state.copyWith(error: null);
  }

  /// 프로필 저장
  Future<bool> saveProfile({
    required String regionId,
    required String officeId,
    String? attributionToken,
    String? referralDriverId,
    String? referralDriverName,
  }) async {
    // 유효성 검사
    if (state.nickname.trim().isEmpty) {
      state = state.copyWith(error: '닉네임을 입력해주세요');
      return false;
    }

    if (state.phoneNumber.trim().isEmpty) {
      state = state.copyWith(error: '전화번호를 입력해주세요');
      return false;
    }

    // 전화번호 형식 간단 검증
    final phonePattern = RegExp(r'^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$');
    if (!phonePattern.hasMatch(state.phoneNumber.replaceAll('-', ''))) {
      state = state.copyWith(error: '올바른 전화번호 형식이 아닙니다');
      return false;
    }

    state = state.copyWith(isLoading: true, error: null);

    try {
      final user = _auth.currentUser;
      if (user == null) {
        state = state.copyWith(
          isLoading: false,
          error: '로그인 정보가 없습니다',
        );
        return false;
      }

      // Firestore에 저장
      final customerRef = _firestore
          .collection('regions')
          .doc(regionId)
          .collection('offices')
          .doc(officeId)
          .collection('customers')
          .doc(user.uid);

      final customerData = {
        'id': user.uid,
        'phoneNumber': state.phoneNumber,
        'name': state.nickname,
        'homeAddress': state.address.trim(),
        'grade': 'bronze',
        'points': 0,
        'totalRides': 0,
        'totalSpent': 0,
        'linkedOfficeId': officeId,
        'primaryOfficeId': officeId,
        'attributionSource': attributionToken != null ? 'token' : 'fingerprint',
        'registeredAt': FieldValue.serverTimestamp(),
        'lastActiveAt': FieldValue.serverTimestamp(),
      };

      // 추천 정보가 있으면 추가
      if (referralDriverId != null && referralDriverName != null) {
        customerData['referralDriverId'] = referralDriverId;
        customerData['referralDriverName'] = referralDriverName;
      }

      await customerRef.set(customerData);

      // SharedPreferences에 전화번호 저장
      await _storage.setString('phoneNumber', state.phoneNumber);

      // 토큰이 있으면 클레임 처리
      if (attributionToken != null) {
        await _claimToken(attributionToken, state.phoneNumber);
      }

      state = state.copyWith(isLoading: false);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        error: '프로필 저장 중 오류가 발생했습니다: $e',
      );
      return false;
    }
  }

  /// 토큰 클레임
  Future<void> _claimToken(String token, String phoneNumber) async {
    try {
      final functionsInstance = FirebaseFunctions.instanceFor(
        region: 'asia-northeast3',
      );

      await functionsInstance.httpsCallable('claimToken').call({
        'token': token,
        'phoneNumber': phoneNumber,
      });

      debugPrint('[ProfileSetup] 토큰 클레임 완료: $token');
    } catch (e) {
      debugPrint('[ProfileSetup] 토큰 클레임 실패: $e');
    }
  }
}

/// ProfileSetupNotifier Provider
final profileSetupNotifierProvider =
    StateNotifierProvider<ProfileSetupNotifier, ProfileSetupState>((ref) {
  return ProfileSetupNotifier();
});

/// 프로필 설정 화면
class ProfileSetupScreen extends ConsumerWidget {
  final String regionId;
  final String officeId;
  final String? attributionToken;
  final String? referralDriverId;
  final String? referralDriverName;
  final VoidCallback onProfileComplete;

  const ProfileSetupScreen({
    super.key,
    required this.regionId,
    required this.officeId,
    this.attributionToken,
    this.referralDriverId,
    this.referralDriverName,
    required this.onProfileComplete,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(profileSetupNotifierProvider);
    final notifier = ref.read(profileSetupNotifierProvider.notifier);

    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const SizedBox(height: 80),

              // 환영 메시지
              Text(
                '환영합니다! 🎉',
                style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: Theme.of(context).primaryColor,
                    ),
              ),

              const SizedBox(height: 8),

              Text(
                '간단한 정보만 입력하면\n바로 시작할 수 있어요',
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                textAlign: TextAlign.center,
              ),

              const SizedBox(height: 48),

              // 닉네임 입력
              TextField(
                decoration: const InputDecoration(
                  labelText: '닉네임',
                  hintText: '예: 홍길동',
                  border: OutlineInputBorder(),
                ),
                enabled: !state.isLoading,
                onChanged: notifier.updateNickname,
                textInputAction: TextInputAction.next,
              ),

              const SizedBox(height: 16),

              // 전화번호 입력
              TextField(
                decoration: const InputDecoration(
                  labelText: '전화번호',
                  hintText: '010-1234-5678',
                  border: OutlineInputBorder(),
                ),
                enabled: !state.isLoading,
                keyboardType: TextInputType.phone,
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[0-9-]')),
                ],
                onChanged: notifier.updatePhoneNumber,
                textInputAction: TextInputAction.next,
              ),

              const SizedBox(height: 16),

              // 주소 입력 (선택사항)
              TextField(
                decoration: const InputDecoration(
                  labelText: '주소 (선택)',
                  hintText: '예: 서울시 강남구',
                  border: OutlineInputBorder(),
                ),
                enabled: !state.isLoading,
                onChanged: notifier.updateAddress,
                textInputAction: TextInputAction.done,
              ),

              const SizedBox(height: 32),

              // 시작하기 버튼
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  onPressed: state.isLoading ||
                          state.nickname.trim().isEmpty ||
                          state.phoneNumber.trim().isEmpty
                      ? null
                      : () async {
                          final success = await notifier.saveProfile(
                            regionId: regionId,
                            officeId: officeId,
                            attributionToken: attributionToken,
                            referralDriverId: referralDriverId,
                            referralDriverName: referralDriverName,
                          );

                          if (success) {
                            onProfileComplete();
                          }
                        },
                  child: state.isLoading
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            valueColor:
                                AlwaysStoppedAnimation<Color>(Colors.white),
                          ),
                        )
                      : const Text('시작하기'),
                ),
              ),

              const SizedBox(height: 16),

              // 안내 문구
              Text(
                '* 닉네임과 전화번호는 필수입니다',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),

              // 에러 메시지
              if (state.error != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.errorContainer,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        Icons.error_outline,
                        color: Theme.of(context).colorScheme.error,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          state.error!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
