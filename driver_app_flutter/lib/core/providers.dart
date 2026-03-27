import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../features/auth/data/datasources/auth_local_datasource.dart';
import '../features/auth/data/datasources/auth_remote_datasource.dart';
import '../features/auth/data/repositories/auth_repository_impl.dart';
import '../features/auth/domain/entities/user_session.dart';
import '../features/auth/domain/repositories/auth_repository.dart';
import '../features/auth/presentation/providers/auth_notifier.dart';
import '../features/auth/presentation/providers/auth_state.dart';
import '../features/driver/presentation/notifiers/driver_workflow_notifier.dart';
import '../features/driver/presentation/state/driver_screen_ui_state.dart';
import 'network/network_info.dart';

// ─── Firebase Singletons ─────────────────────────────────
final firestoreProvider = Provider((_) => FirebaseFirestore.instance);
final firebaseAuthProvider = Provider((_) => FirebaseAuth.instance);
final firebaseMessagingProvider = Provider((_) => FirebaseMessaging.instance);

// ─── External Dependencies ───────────────────────────────
final sharedPreferencesProvider = Provider<SharedPreferences>((_) {
  throw UnimplementedError('main.dart ProviderScope overrides에서 설정');
});
final secureStorageProvider = Provider((_) => const FlutterSecureStorage());
final connectivityProvider = Provider((_) => Connectivity());

// ─── Network ─────────────────────────────────────────────
final networkInfoProvider = Provider<NetworkInfo>((ref) {
  return NetworkInfoImpl(ref.read(connectivityProvider));
});

// ─── Auth DataSources ────────────────────────────────────
final authLocalDataSourceProvider = Provider<AuthLocalDataSource>((ref) {
  return AuthLocalDataSourceImpl(
    sharedPreferences: ref.read(sharedPreferencesProvider),
    secureStorage: ref.read(secureStorageProvider),
  );
});

final authRemoteDataSourceProvider = Provider<AuthRemoteDataSource>((ref) {
  return AuthRemoteDataSourceImpl(
    firebaseAuth: ref.read(firebaseAuthProvider),
    firestore: ref.read(firestoreProvider),
    firebaseMessaging: ref.read(firebaseMessagingProvider),
  );
});

// ─── Auth Repository ─────────────────────────────────────
final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepositoryImpl(
    remoteDataSource: ref.read(authRemoteDataSourceProvider),
    localDataSource: ref.read(authLocalDataSourceProvider),
    networkInfo: ref.read(networkInfoProvider),
  );
});

// ─── Auth Notifier ───────────────────────────────────────
final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(repository: ref.read(authRepositoryProvider));
});

final currentSessionProvider = Provider<UserSession?>((ref) {
  final authState = ref.watch(authNotifierProvider);
  return authState.maybeWhen(
    authenticated: (session) => session,
    orElse: () => null,
  );
});

final isAuthenticatedProvider = Provider<bool>((ref) {
  final authState = ref.watch(authNotifierProvider);
  return authState.maybeWhen(
    authenticated: (_) => true,
    orElse: () => false,
  );
});

// ─── Driver Workflow ─────────────────────────────────────
final driverWorkflowProvider =
    StateNotifierProvider<DriverWorkflowNotifier, DriverScreenUiState>((ref) {
  return DriverWorkflowNotifier(
    firestore: ref.read(firestoreProvider),
    auth: ref.read(firebaseAuthProvider),
    prefs: ref.read(sharedPreferencesProvider),
  );
});
