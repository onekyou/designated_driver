import 'package:connectivity_plus/connectivity_plus.dart';

/// 네트워크 연결 상태 확인
abstract class NetworkInfo {
  Future<bool> get isConnected;
  Stream<bool> get onConnectivityChanged;
}

class NetworkInfoImpl implements NetworkInfo {
  final Connectivity connectivity;

  NetworkInfoImpl(this.connectivity);

  @override
  Future<bool> get isConnected async {
    final result = await connectivity.checkConnectivity();
    return result.contains(ConnectivityResult.mobile) ||
           result.contains(ConnectivityResult.wifi) ||
           result.contains(ConnectivityResult.ethernet);
  }

  @override
  Stream<bool> get onConnectivityChanged {
    return connectivity.onConnectivityChanged.map((results) {
      return results.contains(ConnectivityResult.mobile) ||
             results.contains(ConnectivityResult.wifi) ||
             results.contains(ConnectivityResult.ethernet);
    });
  }
}
