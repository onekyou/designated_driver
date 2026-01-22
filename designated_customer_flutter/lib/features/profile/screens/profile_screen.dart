import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../../auth/providers/auth_provider.dart';
import '../../attribution/providers/attribution_provider.dart';

/// 프로필/내정보 화면
class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key});

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  String _appVersion = '';

  @override
  void initState() {
    super.initState();
    _loadAppInfo();
  }

  Future<void> _loadAppInfo() async {
    final packageInfo = await PackageInfo.fromPlatform();
    setState(() {
      _appVersion = '${packageInfo.version} (${packageInfo.buildNumber})';
    });
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authNotifierProvider);
    final attributionState = ref.watch(attributionNotifierProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('내 정보'),
        elevation: 0,
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            // 프로필 헤더
            Container(
              width: double.infinity,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Theme.of(context).primaryColor,
                    Theme.of(context).primaryColor.withOpacity(0.7),
                  ],
                ),
              ),
              padding: const EdgeInsets.all(24),
              child: Column(
                children: [
                  const CircleAvatar(
                    radius: 50,
                    backgroundColor: Colors.white,
                    child: Icon(
                      Icons.person,
                      size: 60,
                      color: Colors.grey,
                    ),
                  ),
                  const SizedBox(height: 16),
                  authState.when(
                    data: (user) {
                      if (user?.phoneNumber != null) {
                        return Text(
                          user!.phoneNumber!,
                          style: const TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        );
                      }
                      return const Text(
                        '전화번호 미등록',
                        style: TextStyle(
                          fontSize: 20,
                          color: Colors.white70,
                        ),
                      );
                    },
                    loading: () => const CircularProgressIndicator(
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                    error: (_, __) => const Text(
                      '정보 로드 실패',
                      style: TextStyle(color: Colors.white70),
                    ),
                  ),
                  const SizedBox(height: 8),
                  attributionState.when(
                    data: (attribution) {
                      if (attribution != null) {
                        return Text(
                          attribution.officeName,
                          style: const TextStyle(
                            fontSize: 16,
                            color: Colors.white70,
                          ),
                        );
                      }
                      return const Text(
                        '사무실 미연결',
                        style: TextStyle(color: Colors.white70),
                      );
                    },
                    loading: () => const SizedBox.shrink(),
                    error: (_, __) => const SizedBox.shrink(),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // 사무실 정보
            attributionState.when(
              data: (attribution) {
                if (attribution == null) {
                  return const SizedBox.shrink();
                }
                return Card(
                  margin: const EdgeInsets.symmetric(horizontal: 16),
                  child: Column(
                    children: [
                      ListTile(
                        leading: const Icon(Icons.business),
                        title: const Text('사무실 정보'),
                        subtitle: Text(attribution.officeName),
                        trailing: const Icon(Icons.chevron_right),
                      ),
                      const Divider(height: 1),
                      ListTile(
                        leading: const Icon(Icons.phone),
                        title: const Text('사무실 전화'),
                        subtitle: Text(attribution.officePhone),
                        trailing: IconButton(
                          icon: const Icon(Icons.call),
                          onPressed: () => _makePhoneCall(attribution.officePhone),
                        ),
                      ),
                      if (attribution.referralDriverName != null) ...[
                        const Divider(height: 1),
                        ListTile(
                          leading: const Icon(Icons.person_pin),
                          title: const Text('추천 기사님'),
                          subtitle: Text(attribution.referralDriverName!),
                        ),
                      ],
                    ],
                  ),
                );
              },
              loading: () => const SizedBox.shrink(),
              error: (_, __) => const SizedBox.shrink(),
            ),

            const SizedBox(height: 16),

            // 앱 설정
            Card(
              margin: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.notifications),
                    title: const Text('알림 설정'),
                    subtitle: const Text('푸시 알림, 소리, 진동'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () {
                      // Phase 5에서 구현
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Phase 5에서 구현 예정')),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.location_on),
                    title: const Text('집 주소 관리'),
                    subtitle: const Text('자주 사용하는 주소 저장'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () {
                      // Phase 5에서 구현
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Phase 5에서 구현 예정')),
                      );
                    },
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // 앱 정보
            Card(
              margin: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.info),
                    title: const Text('앱 버전'),
                    subtitle: Text(_appVersion.isEmpty ? '로딩 중...' : _appVersion),
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.description),
                    title: const Text('이용약관'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Phase 5에서 구현 예정')),
                      );
                    },
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.privacy_tip),
                    title: const Text('개인정보 처리방침'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Phase 5에서 구현 예정')),
                      );
                    },
                  ),
                ],
              ),
            ),

            const SizedBox(height: 24),

            // 로그아웃 버튼
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: OutlinedButton.icon(
                onPressed: () => _showLogoutDialog(),
                icon: const Icon(Icons.logout, color: Colors.red),
                label: const Text(
                  '로그아웃',
                  style: TextStyle(color: Colors.red),
                ),
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  minimumSize: const Size(double.infinity, 0),
                  side: const BorderSide(color: Colors.red),
                ),
              ),
            ),

            const SizedBox(height: 80),
          ],
        ),
      ),
    );
  }

  Future<void> _makePhoneCall(String phoneNumber) async {
    final uri = Uri(scheme: 'tel', path: phoneNumber);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri);
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('전화를 걸 수 없습니다: $phoneNumber')),
        );
      }
    }
  }

  void _showLogoutDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('로그아웃'),
        content: const Text('로그아웃 하시겠습니까?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('취소'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(context);
              await ref.read(authNotifierProvider.notifier).signOut();
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('로그아웃 되었습니다')),
                );
              }
            },
            child: const Text('로그아웃', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }
}
