import 'package:flutter/material.dart';

class AppTheme {
  // Primary Colors (기존 Android 앱과 동일 - Orange/Amber)
  static const Color primaryColor = Color(0xFFFFAB00);
  static const Color primaryDark = Color(0xFFFF8F00);
  static const Color primaryLight = Color(0xFFFFCF66);

  // Secondary Colors
  static const Color secondaryColor = Color(0xFF6650A4);
  static const Color secondaryDark = Color(0xFF4A3B7C);
  static const Color secondaryLight = Color(0xFF8B7CC6);

  // Dark Theme Colors
  static const Color background = Color(0xFF121212);
  static const Color surface = Color(0xFF1E1E1E);
  static const Color surfaceVariant = Color(0xFF2C2C2C);

  // Text Colors (Dark Theme)
  static const Color textPrimary = Color(0xFFE0E0E0);
  static const Color textSecondary = Color(0xFFB0B0B0);
  static const Color textHint = Color(0xFF808080);
  static const Color textOnPrimary = Color(0xFF000000);

  // Status Colors
  static const Color colorOnline = Color(0xFF4CAF50);
  static const Color colorOffline = Color(0xFF9E9E9E);
  static const Color colorBusy = Color(0xFFF44336);
  static const Color colorWaiting = Color(0xFFFF9800);
  static const Color colorAssigned = Color(0xFF2196F3);
  static const Color colorAccepted = Color(0xFF00BCD4);
  static const Color colorOnTrip = Color(0xFFFF5722);

  // Card & Surface
  static const Color cardBackground = Color(0xFF1E1E1E);
  static const Color errorColor = Color(0xFFCF6679);
  static const Color disabledColor = Color(0xFF404040);

  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: const ColorScheme.dark(
        primary: primaryColor,
        secondary: secondaryColor,
        surface: surface,
        background: background,
        error: errorColor,
        onPrimary: textOnPrimary,
        onSecondary: textPrimary,
        onSurface: textPrimary,
        onBackground: textPrimary,
        onError: textOnPrimary,
        surfaceVariant: surfaceVariant,
      ),
      scaffoldBackgroundColor: background,
      appBarTheme: const AppBarTheme(
        backgroundColor: surface,
        foregroundColor: textPrimary,
        elevation: 0,
        centerTitle: true,
      ),
      cardTheme: CardThemeData(
        color: cardBackground,
        elevation: 4,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primaryColor,
          foregroundColor: textOnPrimary,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceVariant,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: textHint),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: textHint),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: primaryColor, width: 2),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      ),
      textTheme: const TextTheme(
        headlineLarge: TextStyle(
          fontSize: 32,
          fontWeight: FontWeight.bold,
          color: textPrimary,
        ),
        headlineMedium: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.bold,
          color: textPrimary,
        ),
        titleLarge: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          color: textPrimary,
        ),
        titleMedium: TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w500,
          color: textPrimary,
        ),
        bodyLarge: TextStyle(
          fontSize: 16,
          color: textPrimary,
        ),
        bodyMedium: TextStyle(
          fontSize: 14,
          color: textSecondary,
        ),
        bodySmall: TextStyle(
          fontSize: 12,
          color: textHint,
        ),
      ),
    );
  }

  // Status Color Helper (기존 Android 앱과 동일)
  static Color getStatusColor(String status) {
    switch (status.toUpperCase()) {
      case 'ONLINE':
        return colorOnline;
      case 'OFFLINE':
        return colorOffline;
      case 'WAITING':
        return colorWaiting;
      case 'ASSIGNED':
        return colorAssigned;
      case 'ACCEPTED':
        return colorAccepted;
      case 'ON_TRIP':
        return colorOnTrip;
      case 'BUSY':
        return colorBusy;
      default:
        return colorOffline;
    }
  }

  // Status Text Helper (기존 Android 앱과 동일)
  static String getStatusText(String status) {
    switch (status.toUpperCase()) {
      case 'ONLINE':
        return '온라인';
      case 'OFFLINE':
        return '오프라인';
      case 'WAITING':
        return '대기중';
      case 'ASSIGNED':
        return '배정됨';
      case 'ACCEPTED':
        return '수락함';
      case 'ON_TRIP':
        return '운행중';
      case 'BUSY':
        return '운행중';
      case 'UNKNOWN':
        return '알수없음';
      default:
        return '알 수 없음';
    }
  }
}
