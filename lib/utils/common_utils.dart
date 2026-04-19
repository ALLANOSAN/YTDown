/// Utilitários centralizados para URLs do YouTube
class YouTubeUtils {
  YouTubeUtils._();

  static final RegExp youtubeUrlPattern = RegExp(
    r'(https?:\/\/)?(www\.)?(youtube\.com\/\S+|youtu\.be\/\S+)',
    caseSensitive: false,
  );

  /// Extrai uma URL do YouTube de um texto livre
  static String? extractUrl(String raw) {
    final trimmed = raw.trim();
    final match = youtubeUrlPattern.firstMatch(trimmed);
    if (match == null) return null;

    var url = match.group(0)!.replaceFirst(RegExp(r'[),.!?]+$'), '');
    if (!url.startsWith('http')) {
      url = 'https://$url';
    }
    return url;
  }

  /// Verifica se o texto contém uma URL do YouTube
  static bool isYouTubeUrl(String text) => youtubeUrlPattern.hasMatch(text);
}

/// Utilitários de uso geral para normalização de strings e validações.
class CommonUtils {
  CommonUtils._();

  /// Normaliza texto removendo espaços em branco nas extremidades.
  static String normalizeText(String? raw) => raw?.trim() ?? '';

  /// Retorna `true` se o texto não estiver vazio após normalização.
  static bool hasText(String? raw) => normalizeText(raw).isNotEmpty;

  /// Converte texto vazio em `null` após normalização.
  static String? normalizeNullableText(String? raw) {
    final normalized = normalizeText(raw);
    return normalized.isEmpty ? null : normalized;
  }

  /// Verifica se uma URI remota HTTP/HTTPS é válida.
  static bool isRemoteHttpUri(Uri? uri) {
    return uri != null &&
        (uri.scheme.toLowerCase() == 'http' ||
            uri.scheme.toLowerCase() == 'https');
  }
}

/// Utilitários de formatação de datas
class AppDateUtils {
  AppDateUtils._();

  /// Converte DateTime em chave de dia (yyyy-MM-dd) para agrupamento
  static String toDayKey(DateTime date) {
    final year = date.year.toString().padLeft(4, '0');
    final month = date.month.toString().padLeft(2, '0');
    final day = date.day.toString().padLeft(2, '0');
    return '$year-$month-$day';
  }
}

/// Utilitários centralizados para classificação de erros de download
class DownloadErrorUtils {
  DownloadErrorUtils._();

  static const int _maxFriendlyErrorLength = 100;

  /// Simplifica mensagem de erro para exibição ao usuário
  static String simplify(String error) {
    final normalizedError = error.trim();

    if (normalizedError.contains('Sign in to confirm')) {
      return 'YouTube bloqueou a requisição (Bot check)';
    }
    if (normalizedError.contains('Video unavailable')) {
      return 'Vídeo indisponível';
    }
    if (normalizedError.contains('confirm your age')) {
      return 'Vídeo com restrição de idade';
    }

    if (normalizedError.length <= _maxFriendlyErrorLength) {
      return normalizedError;
    }

    return '${normalizedError.substring(0, _maxFriendlyErrorLength)}...';
  }

  /// Normaliza o erro em uma chave de motivo para telemetria
  static String normalizeReason(String errorMessage) {
    final lower = errorMessage.toLowerCase();
    if (lower.contains('bot check') || lower.contains('sign in to confirm')) {
      return 'youtube_bot_check';
    }
    if (lower.contains('indispon') || lower.contains('unavailable')) {
      return 'video_unavailable';
    }
    if (lower.contains('idade') || lower.contains('age')) {
      return 'age_restricted';
    }
    if (lower.contains('network') || lower.contains('timeout')) {
      return 'network_error';
    }
    return 'unknown';
  }
}
