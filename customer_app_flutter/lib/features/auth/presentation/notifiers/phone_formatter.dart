/// 한국 전화번호 → Firebase Phone Auth E.164 포맷.
///
/// PhoneAuthViewModel.kt:127-135 이관.
/// - 숫자만 추출
/// - `010` 시작 시 `+82` + (010 제외한 나머지)
/// - 그 외 `+82` 단순 prepend (edge: 이미 82 시작해도 `+82` 덧붙임)
String formatKoreanPhoneNumber(String raw) {
  final cleaned = raw.replaceAll(RegExp(r'[^0-9]'), '');
  if (cleaned.startsWith('010')) {
    return '+82${cleaned.substring(1)}';
  }
  return '+82$cleaned';
}
