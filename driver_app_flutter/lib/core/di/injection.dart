import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:get_it/get_it.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:connectivity_plus/connectivity_plus.dart';

import '../network/network_info.dart';

// Auth
import '../../features/auth/data/datasources/auth_local_datasource.dart';
import '../../features/auth/data/datasources/auth_remote_datasource.dart';
import '../../features/auth/data/repositories/auth_repository_impl.dart';
import '../../features/auth/domain/repositories/auth_repository.dart';
import '../../features/auth/domain/usecases/login_usecase.dart';
import '../../features/auth/domain/usecases/logout_usecase.dart';
import '../../features/auth/domain/usecases/auto_login_usecase.dart';
import '../../features/auth/domain/usecases/get_current_session_usecase.dart';

// Call
import '../../features/call/data/datasources/call_remote_datasource.dart';
import '../../features/call/data/repositories/call_repository_impl.dart';
import '../../features/call/domain/repositories/call_repository.dart';
import '../../features/call/domain/usecases/get_assigned_call_usecase.dart';
import '../../features/call/domain/usecases/accept_call_usecase.dart';
import '../../features/call/domain/usecases/reject_call_usecase.dart';
import '../../features/call/domain/usecases/complete_call_usecase.dart';

// Trip
import '../../features/trip/data/datasources/trip_remote_datasource.dart';
import '../../features/trip/data/repositories/trip_repository_impl.dart';
import '../../features/trip/domain/repositories/trip_repository.dart';
import '../../features/trip/domain/usecases/start_trip_usecase.dart';
import '../../features/trip/domain/usecases/complete_trip_usecase.dart';
import '../../features/trip/domain/usecases/get_trip_history_usecase.dart';

// Driver
import '../../features/driver/data/datasources/driver_remote_datasource.dart';
import '../../features/driver/data/repositories/driver_repository_impl.dart';
import '../../features/driver/domain/repositories/driver_repository.dart';
import '../../features/driver/domain/usecases/update_driver_status_usecase.dart';

// Customer
import '../../features/customer/data/datasources/customer_local_datasource.dart';
import '../../features/customer/data/datasources/customer_remote_datasource.dart';
import '../../features/customer/data/repositories/customer_repository_impl.dart';
import '../../features/customer/domain/repositories/customer_repository.dart';
import '../../features/customer/domain/usecases/get_customer_points_usecase.dart';

// Location
import '../../features/location/data/datasources/location_local_datasource.dart';
import '../../features/location/data/repositories/location_repository_impl.dart';
import '../../features/location/domain/repositories/location_repository.dart';
import '../../features/location/domain/usecases/get_current_location_usecase.dart';

final sl = GetIt.instance;

Future<void> initializeDependencies() async {
  // =========================================================================
  // External Dependencies (Firebase, Storage, etc.)
  // =========================================================================
  final sharedPreferences = await SharedPreferences.getInstance();
  sl.registerLazySingleton(() => sharedPreferences);

  sl.registerLazySingleton(() => const FlutterSecureStorage());
  sl.registerLazySingleton(() => FirebaseAuth.instance);
  sl.registerLazySingleton(() => FirebaseFirestore.instance);
  sl.registerLazySingleton(() => FirebaseMessaging.instance);
  sl.registerLazySingleton(() => Connectivity());

  // =========================================================================
  // Core
  // =========================================================================
  sl.registerLazySingleton<NetworkInfo>(
    () => NetworkInfoImpl(sl()),
  );

  // =========================================================================
  // Auth Feature
  // =========================================================================

  // Data sources
  sl.registerLazySingleton<AuthLocalDataSource>(
    () => AuthLocalDataSourceImpl(
      sharedPreferences: sl(),
      secureStorage: sl(),
    ),
  );

  sl.registerLazySingleton<AuthRemoteDataSource>(
    () => AuthRemoteDataSourceImpl(
      firebaseAuth: sl(),
      firestore: sl(),
      firebaseMessaging: sl(),
    ),
  );

  // Repository
  sl.registerLazySingleton<AuthRepository>(
    () => AuthRepositoryImpl(
      remoteDataSource: sl(),
      localDataSource: sl(),
      networkInfo: sl(),
    ),
  );

  // Use cases
  sl.registerLazySingleton(() => LoginUseCase(sl()));
  sl.registerLazySingleton(() => LogoutUseCase(sl()));
  sl.registerLazySingleton(() => AutoLoginUseCase(sl()));
  sl.registerLazySingleton(() => GetCurrentSessionUseCase(sl()));

  // =========================================================================
  // Call Feature
  // =========================================================================

  // Data sources
  sl.registerLazySingleton<CallRemoteDataSource>(
    () => CallRemoteDataSourceImpl(firestore: sl()),
  );

  // Repository
  sl.registerLazySingleton<CallRepository>(
    () => CallRepositoryImpl(
      remoteDataSource: sl(),
      authLocalDataSource: sl(),
      networkInfo: sl(),
    ),
  );

  // Use cases
  sl.registerLazySingleton(() => GetAssignedCallUseCase(sl()));
  sl.registerLazySingleton(() => AcceptCallUseCase(sl()));
  sl.registerLazySingleton(() => RejectCallUseCase(sl()));
  sl.registerLazySingleton(() => CompleteCallUseCase(sl()));

  // =========================================================================
  // Trip Feature
  // =========================================================================

  // Data sources
  sl.registerLazySingleton<TripRemoteDataSource>(
    () => TripRemoteDataSourceImpl(firestore: sl()),
  );

  // Repository
  sl.registerLazySingleton<TripRepository>(
    () => TripRepositoryImpl(
      remoteDataSource: sl(),
      authLocalDataSource: sl(),
      networkInfo: sl(),
    ),
  );

  // Use cases
  sl.registerLazySingleton(() => StartTripUseCase(sl()));
  sl.registerLazySingleton(() => CompleteTripUseCase(sl()));
  sl.registerLazySingleton(() => GetTripHistoryUseCase(sl()));

  // =========================================================================
  // Driver Feature
  // =========================================================================

  // Data sources
  sl.registerLazySingleton<DriverRemoteDataSource>(
    () => DriverRemoteDataSourceImpl(firestore: sl()),
  );

  // Repository
  sl.registerLazySingleton<DriverRepository>(
    () => DriverRepositoryImpl(
      remoteDataSource: sl(),
      authLocalDataSource: sl(),
      networkInfo: sl(),
    ),
  );

  // Use cases
  sl.registerLazySingleton(() => UpdateDriverStatusUseCase(sl()));

  // =========================================================================
  // Customer Feature
  // =========================================================================

  // Data sources
  sl.registerLazySingleton<CustomerLocalDataSource>(
    () => CustomerLocalDataSourceImpl(sharedPreferences: sl()),
  );

  sl.registerLazySingleton<CustomerRemoteDataSource>(
    () => CustomerRemoteDataSourceImpl(firestore: sl()),
  );

  // Repository
  sl.registerLazySingleton<CustomerRepository>(
    () => CustomerRepositoryImpl(
      remoteDataSource: sl(),
      localDataSource: sl(),
      networkInfo: sl(),
    ),
  );

  // Use cases
  sl.registerLazySingleton(() => GetCustomerPointsUseCase(sl()));

  // =========================================================================
  // Location Feature
  // =========================================================================

  // Data sources
  sl.registerLazySingleton<LocationLocalDataSource>(
    () => LocationLocalDataSourceImpl(),
  );

  // Repository
  sl.registerLazySingleton<LocationRepository>(
    () => LocationRepositoryImpl(localDataSource: sl()),
  );

  // Use cases
  sl.registerLazySingleton(() => GetCurrentLocationUseCase(sl()));
}
