import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cloud_functions/cloud_functions.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

// Firebase Singletons
final firestoreProvider = Provider<FirebaseFirestore>((_) => FirebaseFirestore.instance);
final firebaseAuthProvider = Provider<FirebaseAuth>((_) => FirebaseAuth.instance);
final firebaseMessagingProvider = Provider<FirebaseMessaging>((_) => FirebaseMessaging.instance);

// PR 1 callable CF region
final cloudFunctionsProvider = Provider<FirebaseFunctions>((_) {
  return FirebaseFunctions.instanceFor(region: 'asia-northeast3');
});

// SharedPreferences — main.dart ProviderScope overrides에서 주입
final sharedPreferencesProvider = Provider<SharedPreferences>((_) {
  throw UnimplementedError('main.dart ProviderScope overrides에서 설정');
});

final secureStorageProvider = Provider<FlutterSecureStorage>((_) {
  return const FlutterSecureStorage(
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
    ),
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );
});
