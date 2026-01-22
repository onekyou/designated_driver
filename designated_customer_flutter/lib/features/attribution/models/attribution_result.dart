/// Attribution 매칭 결과
class AttributionResult {
  final String regionId;
  final String officeId;
  final String officeName;
  final String officePhone;
  final String? bankName;
  final String? accountNumber;
  final String? accountHolder;
  final String? referralDriverId;
  final String? referralDriverName;
  final String? token;
  final DateTime? tokenExpiry;

  AttributionResult({
    required this.regionId,
    required this.officeId,
    required this.officeName,
    required this.officePhone,
    this.bankName,
    this.accountNumber,
    this.accountHolder,
    this.referralDriverId,
    this.referralDriverName,
    this.token,
    this.tokenExpiry,
  });

  /// JSON에서 변환 (Cloud Function 응답)
  factory AttributionResult.fromJson(Map<String, dynamic> json) {
    return AttributionResult(
      regionId: json['regionId'] as String,
      officeId: json['officeId'] as String,
      officeName: json['officeName'] as String? ?? '알 수 없음',
      officePhone: json['officePhone'] as String? ?? '정보 없음',
      bankName: json['bankName'] as String?,
      accountNumber: json['accountNumber'] as String?,
      accountHolder: json['accountHolder'] as String?,
      referralDriverId: json['referralDriverId'] as String?,
      referralDriverName: json['referralDriverName'] as String?,
      token: json['token'] as String?,
      tokenExpiry: json['tokenExpiry'] != null
          ? DateTime.fromMillisecondsSinceEpoch(json['tokenExpiry'] as int)
          : null,
    );
  }

  /// JSON으로 변환
  Map<String, dynamic> toJson() {
    return {
      'regionId': regionId,
      'officeId': officeId,
      'officeName': officeName,
      'officePhone': officePhone,
      'bankName': bankName,
      'accountNumber': accountNumber,
      'accountHolder': accountHolder,
      'referralDriverId': referralDriverId,
      'referralDriverName': referralDriverName,
      'token': token,
      'tokenExpiry': tokenExpiry?.millisecondsSinceEpoch,
    };
  }

  /// copyWith
  AttributionResult copyWith({
    String? regionId,
    String? officeId,
    String? officeName,
    String? officePhone,
    String? bankName,
    String? accountNumber,
    String? accountHolder,
    String? referralDriverId,
    String? referralDriverName,
    String? token,
    DateTime? tokenExpiry,
  }) {
    return AttributionResult(
      regionId: regionId ?? this.regionId,
      officeId: officeId ?? this.officeId,
      officeName: officeName ?? this.officeName,
      officePhone: officePhone ?? this.officePhone,
      bankName: bankName ?? this.bankName,
      accountNumber: accountNumber ?? this.accountNumber,
      accountHolder: accountHolder ?? this.accountHolder,
      referralDriverId: referralDriverId ?? this.referralDriverId,
      referralDriverName: referralDriverName ?? this.referralDriverName,
      token: token ?? this.token,
      tokenExpiry: tokenExpiry ?? this.tokenExpiry,
    );
  }

  @override
  String toString() {
    return 'AttributionResult(regionId: $regionId, officeId: $officeId, '
        'officeName: $officeName, officePhone: $officePhone, '
        'referralDriverName: $referralDriverName)';
  }
}
