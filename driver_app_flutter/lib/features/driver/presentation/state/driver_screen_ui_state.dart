import '../../domain/entities/driver.dart';
import '../../../call/domain/entities/call.dart';

/// 기사앱 통합 UI 상태 (Kotlin DriverScreenUiState 1:1 매핑)
class DriverScreenUiState {
  final DriverStatus driverStatus;
  final List<Call> assignedCalls;
  final Call? activeCall;
  final Call? callForSettlement;
  final Call? newCallPopup;
  final bool isLoading;
  final String? errorMessage;
  final bool navigateToHome;
  final bool navigateToHistorySettlement;

  const DriverScreenUiState({
    this.driverStatus = DriverStatus.offline,
    this.assignedCalls = const [],
    this.activeCall,
    this.callForSettlement,
    this.newCallPopup,
    this.isLoading = false,
    this.errorMessage,
    this.navigateToHome = false,
    this.navigateToHistorySettlement = false,
  });

  DriverScreenUiState copyWith({
    DriverStatus? driverStatus,
    List<Call>? assignedCalls,
    Call? Function()? activeCall,
    Call? Function()? callForSettlement,
    Call? Function()? newCallPopup,
    bool? isLoading,
    String? Function()? errorMessage,
    bool? navigateToHome,
    bool? navigateToHistorySettlement,
  }) {
    return DriverScreenUiState(
      driverStatus: driverStatus ?? this.driverStatus,
      assignedCalls: assignedCalls ?? this.assignedCalls,
      activeCall: activeCall != null ? activeCall() : this.activeCall,
      callForSettlement: callForSettlement != null ? callForSettlement() : this.callForSettlement,
      newCallPopup: newCallPopup != null ? newCallPopup() : this.newCallPopup,
      isLoading: isLoading ?? this.isLoading,
      errorMessage: errorMessage != null ? errorMessage() : this.errorMessage,
      navigateToHome: navigateToHome ?? this.navigateToHome,
      navigateToHistorySettlement: navigateToHistorySettlement ?? this.navigateToHistorySettlement,
    );
  }
}

/// 오늘의 정산 요약 (Kotlin TodaySettlement 1:1 매핑)
class TodaySettlement {
  final int totalFare;
  final int driverShare;
  final int cashReceived;
  final int realDeposit;
  final int tripCount;
  final int totalCredit;
  final int officeDeposit;
  final int pointsUsed;

  const TodaySettlement({
    this.totalFare = 0,
    this.driverShare = 0,
    this.cashReceived = 0,
    this.realDeposit = 0,
    this.tripCount = 0,
    this.totalCredit = 0,
    this.officeDeposit = 0,
    this.pointsUsed = 0,
  });

  TodaySettlement copyWith({
    int? totalFare,
    int? driverShare,
    int? cashReceived,
    int? realDeposit,
    int? tripCount,
    int? totalCredit,
    int? officeDeposit,
    int? pointsUsed,
  }) {
    return TodaySettlement(
      totalFare: totalFare ?? this.totalFare,
      driverShare: driverShare ?? this.driverShare,
      cashReceived: cashReceived ?? this.cashReceived,
      realDeposit: realDeposit ?? this.realDeposit,
      tripCount: tripCount ?? this.tripCount,
      totalCredit: totalCredit ?? this.totalCredit,
      officeDeposit: officeDeposit ?? this.officeDeposit,
      pointsUsed: pointsUsed ?? this.pointsUsed,
    );
  }
}

/// 운행내역 항목 (Kotlin TripHistoryItem 1:1 매핑)
class TripHistoryItem {
  final int tripNumber;
  final String customerName;
  final String departure;
  final String destination;
  final int fare;
  final String paymentMethod;
  final int? cashAmount;
  final int timestamp;

  const TripHistoryItem({
    required this.tripNumber,
    required this.customerName,
    required this.departure,
    required this.destination,
    required this.fare,
    required this.paymentMethod,
    this.cashAmount,
    required this.timestamp,
  });

  TripHistoryItem copyWith({int? tripNumber}) {
    return TripHistoryItem(
      tripNumber: tripNumber ?? this.tripNumber,
      customerName: customerName,
      departure: departure,
      destination: destination,
      fare: fare,
      paymentMethod: paymentMethod,
      cashAmount: cashAmount,
      timestamp: timestamp,
    );
  }
}

/// 기사 이월금 (Kotlin DriverCarryOver 매핑)
class DriverCarryOver {
  final int balance;
  final String status;
  final DateTime? lastUpdatedAt;

  const DriverCarryOver({
    this.balance = 0,
    this.status = '',
    this.lastUpdatedAt,
  });

  DriverCarryOver copyWith({int? balance}) {
    return DriverCarryOver(
      balance: balance ?? this.balance,
      status: status,
      lastUpdatedAt: lastUpdatedAt,
    );
  }

  factory DriverCarryOver.fromMap(Map<String, dynamic>? map) {
    if (map == null) return const DriverCarryOver();
    return DriverCarryOver(
      balance: (map['balance'] as num?)?.toInt() ?? 0,
      status: map['status'] as String? ?? '',
      lastUpdatedAt: map['lastUpdatedAt'] != null
          ? (map['lastUpdatedAt'] as dynamic).toDate()
          : null,
    );
  }
}

/// 일일 정산 상태
enum DailySettlementStatus {
  working('WORKING'),
  pendingConfirm('PENDING_CONFIRM'),
  confirmed('CONFIRMED'),
  rejected('REJECTED');

  final String value;
  const DailySettlementStatus(this.value);

  static DailySettlementStatus fromString(String? s) {
    if (s == null) return working;
    return DailySettlementStatus.values.firstWhere(
      (e) => e.value == s,
      orElse: () => working,
    );
  }
}
