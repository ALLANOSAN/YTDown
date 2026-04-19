import 'package:flutter/material.dart';

class AppTheme {
  AppTheme._();

  // Cores Principais - Dark moderno com acento frio
  static const Color primary = Color(0xFF8A2BE2); // BlueViolet suave
  static const Color primaryDark = Color(0xFF6B21A8);
  static const Color accent = Color(0xFF9333EA);
  static const Color audioAccent = Color(0xFF34D399);

  // Backgrounds (True Black / Amoled)
  static const Color bg = Color(0xFF000000);
  static const Color surface = Color(0xFF0A0A0A);
  static const Color surfaceElevated = Color(0xFF121212);
  static const Color card = Color(0xFF1A1A1A);

  // Textos
  static const Color textPrimary = Color(0xFFFFFFFF);
  static const Color textSecondary = Color(0xFFC8D0DD);
  static const Color textTertiary = Color(0xFF8793A8);

  // Status
  static const Color success = Color(0xFF22C55E);
  static const Color warning = Color(0xFFF59E0B);
  static const Color error =
      Color(0xFF9333EA); // Substituindo vermelho por Violeta

  // Bordas e Divisores
  static const Color border = Color(0xFF2E384B);
  static const Color divider = Color(0xFF212A3A);

  static ThemeData get dark {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: bg,
      colorScheme: _darkColorScheme,
      appBarTheme: _appBarTheme,
      bottomNavigationBarTheme: _bottomNavigationBarTheme,
      inputDecorationTheme: _buildInputDecorationTheme(),
      elevatedButtonTheme: _buildElevatedButtonTheme(),
      textButtonTheme: _buildTextButtonTheme(),
      floatingActionButtonTheme: _buildFloatingActionButtonTheme(),
      cardTheme: _buildCardTheme(),
      dividerTheme: _dividerTheme,
      progressIndicatorTheme: _progressIndicatorTheme,
      snackBarTheme: _buildSnackBarTheme(),
      bottomSheetTheme: _bottomSheetTheme,
      dialogTheme: _buildDialogTheme(),
      chipTheme: _buildChipTheme(),
    );
  }

  static const ColorScheme _darkColorScheme = ColorScheme.dark(
    primary: primary,
    secondary: accent,
    surface: surface,
    error: error,
    onPrimary: textPrimary,
    onSecondary: textPrimary,
    onSurface: textPrimary,
    onError: textPrimary,
  );

  static const AppBarTheme _appBarTheme = AppBarTheme(
    backgroundColor: bg,
    elevation: 0,
    centerTitle: true,
    titleTextStyle: TextStyle(
      color: textPrimary,
      fontSize: 20,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.5,
    ),
    iconTheme: IconThemeData(color: textPrimary, size: 24),
  );

  static const BottomNavigationBarThemeData _bottomNavigationBarTheme =
      BottomNavigationBarThemeData(
    backgroundColor: surface,
    selectedItemColor: primary,
    unselectedItemColor: textSecondary,
    type: BottomNavigationBarType.fixed,
    elevation: 8,
    selectedLabelStyle: TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
    unselectedLabelStyle: TextStyle(fontSize: 11),
  );

  static const DividerThemeData _dividerTheme = DividerThemeData(
    color: divider,
    thickness: 1,
    space: 1,
  );

  static const ProgressIndicatorThemeData _progressIndicatorTheme =
      ProgressIndicatorThemeData(
    color: primary,
    linearTrackColor: surfaceElevated,
  );

  static const BottomSheetThemeData _bottomSheetTheme = BottomSheetThemeData(
    backgroundColor: surface,
    modalBackgroundColor: surface,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
    ),
  );

  static InputDecorationTheme _buildInputDecorationTheme() {
    return InputDecorationTheme(
      filled: true,
      fillColor: card,
      border: _buildInputBorder(color: border),
      enabledBorder: _buildInputBorder(color: border),
      focusedBorder: _buildInputBorder(color: primary, width: 2),
      errorBorder: _buildInputBorder(color: error),
      hintStyle: const TextStyle(color: textTertiary, fontSize: 14),
      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      prefixIconColor: textSecondary,
      suffixIconColor: textSecondary,
    );
  }

  static OutlineInputBorder _buildInputBorder({
    required Color color,
    double width = 1,
  }) {
    return OutlineInputBorder(
      borderRadius: BorderRadius.circular(16),
      borderSide: BorderSide(color: color, width: width),
    );
  }

  static ElevatedButtonThemeData _buildElevatedButtonTheme() {
    return ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: primary,
        foregroundColor: textPrimary,
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
        textStyle: const TextStyle(
          fontWeight: FontWeight.w700,
          fontSize: 15,
          letterSpacing: 0.5,
        ),
      ),
    );
  }

  static TextButtonThemeData _buildTextButtonTheme() {
    return TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: primary,
        textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
      ),
    );
  }

  static FloatingActionButtonThemeData _buildFloatingActionButtonTheme() {
    return FloatingActionButtonThemeData(
      backgroundColor: primary,
      foregroundColor: textPrimary,
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    );
  }

  static CardThemeData _buildCardTheme() {
    return CardThemeData(
      color: card,
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      margin: const EdgeInsets.all(8),
    );
  }

  static SnackBarThemeData _buildSnackBarTheme() {
    return SnackBarThemeData(
      backgroundColor: surfaceElevated,
      contentTextStyle: const TextStyle(color: textPrimary),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      behavior: SnackBarBehavior.floating,
    );
  }

  static DialogThemeData _buildDialogTheme() {
    return DialogThemeData(
      backgroundColor: surfaceElevated,
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      titleTextStyle: const TextStyle(
        color: textPrimary,
        fontSize: 18,
        fontWeight: FontWeight.w700,
      ),
      contentTextStyle: const TextStyle(
        color: textSecondary,
        fontSize: 14,
      ),
    );
  }

  static ChipThemeData _buildChipTheme() {
    return ChipThemeData(
      backgroundColor: card,
      selectedColor: primary.withValues(alpha: 0.2),
      labelStyle: const TextStyle(color: textSecondary, fontSize: 13),
      secondaryLabelStyle: const TextStyle(color: textPrimary),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    );
  }
}
