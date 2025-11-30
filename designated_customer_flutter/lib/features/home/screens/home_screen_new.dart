import ''dart:async'';
import ''package:flutter/material.dart'';
import ''package:flutter_riverpod/flutter_riverpod.dart'';
import ''package:intl/intl.dart'';
import ''../../attribution/providers/attribution_provider.dart'';
import ''../../auth/providers/auth_provider.dart'';
import ''../../call/repositories/call_repository.dart'';
import ''../../call/screens/call_request_screen.dart'';
import ''../../points/providers/point_provider.dart'';
import ''../providers/point_earn_watcher_provider.dart'';
import ''../widgets/step_counter_card.dart'';
import ''package:url_launcher/url_launcher.dart'';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  Timer? _activeCallTimer;
  String? _lastActiveCallId;
  final CallRepository _callRepository = CallRepository();

  @override
  void initState() {
    super.initState();
    _startWatchingActiveCall();
  }

  @override
  void dispose() {
    _activeCallTimer?.cancel();
    super.dispose();
  }

  void _startWatchingActiveCall() {
    _activeCallTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      _checkActiveCall();
    });
  }

  Future<void> _checkActiveCall() async {
    try {
      final attribution = ref.read(attributionNotifierProvider).value;
      final user = ref.read(authNotifierProvider).value;

      if (attribution == null || user?.phoneNumber == null) {
        return;
      }

      final activeCall = await _callRepository.getActiveCall(
        regionId: attribution.regionId,
        officeId: attribution.officeId,
        phoneNumber: user\!.phoneNumber\!,
      );

      final currentCallId = activeCall?.id;

      if (_lastActiveCallId \!= null && currentCallId == null) {
        debugPrint(''[HomeScreen] Call disappeared'');
        ref.read(pointEarnWatcherProvider.notifier)
            .onActiveCallDisappeared(_lastActiveCallId\!);
      }

      _lastActiveCallId = currentCallId;
    } catch (e) {
      debugPrint(''[HomeScreen] Error: 262e'');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container();
  }
}