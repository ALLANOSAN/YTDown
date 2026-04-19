class MetadataUtils {
  MetadataUtils._();

  static const List<String> _artistSeparators = <String>[
    ' - ',
    ' \u2013 ',
    ' \u2014 ',
    ' | ',
  ];

  static final RegExp _generatedSuffixPattern =
      RegExp(r'[_-][0-9a-f]{6,}$', caseSensitive: false);
  static final RegExp _uuidSuffixPattern = RegExp(
    r'[_-][0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
    caseSensitive: false,
  );

  static const Set<String> appUnknownValues = <String>{
    'ytdown',
  };

  static const Set<String> _baseUnknownValues = <String>{
    'unknown',
    'unknown artist',
    'desconhecido',
    'artista desconhecido',
    'videoplayback',
  };

  static bool isUnknownMetadata(
    String? value, {
    Set<String> additionalUnknownValues = const <String>{},
  }) {
    final normalized = _normalizeLowercase(value);
    if (normalized.isEmpty) return true;

    return _baseUnknownValues.contains(normalized) ||
        additionalUnknownValues.contains(normalized);
  }

  static bool isUnknownAppMetadata(String? value) {
    return isUnknownMetadata(
      value,
      additionalUnknownValues: appUnknownValues,
    );
  }

  static String normalizeMetadataText(
    String value, {
    bool stripGeneratedSuffix = true,
  }) {
    var normalized = value.trim();
    normalized = normalized.replaceAll(RegExp(r'[_]+'), ' ');

    if (stripGeneratedSuffix) {
      normalized = normalized.replaceAll(_generatedSuffixPattern, '');
    }

    normalized = normalized.replaceAll(RegExp(r'\s+'), ' ').trim();
    return normalized;
  }

  static String? guessArtistFromTitle(
    String title, {
    Set<String> additionalUnknownValues = const <String>{},
  }) {
    final normalizedTitle = normalizeMetadataText(title);
    for (final separator in _artistSeparators) {
      if (!normalizedTitle.contains(separator)) continue;

      final candidate = normalizedTitle.split(separator).first.trim();
      if (!isUnknownMetadata(
            candidate,
            additionalUnknownValues: additionalUnknownValues,
          ) &&
          candidate.length >= 2) {
        return candidate;
      }
    }

    return null;
  }

  static String? guessAppArtistFromTitle(String title) {
    return guessArtistFromTitle(
      title,
      additionalUnknownValues: appUnknownValues,
    );
  }

  static String? guessTitleFromPath(
    String path, {
    Set<String> additionalUnknownValues = const <String>{},
  }) {
    final sanitizedPath = path.trim();
    if (sanitizedPath.isEmpty) return null;

    final normalizedPath = sanitizedPath.replaceAll('\\', '/');
    final filename = normalizedPath.split('/').last;
    if (filename.isEmpty) return null;

    final dotIndex = filename.lastIndexOf('.');
    var baseName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;

    baseName = baseName.replaceAll(_uuidSuffixPattern, '');
    baseName = baseName.replaceAll(_generatedSuffixPattern, '');

    final normalizedTitle = normalizeMetadataText(baseName);
    if (normalizedTitle.isEmpty) return null;

    if (isUnknownMetadata(
      normalizedTitle,
      additionalUnknownValues: additionalUnknownValues,
    )) {
      return null;
    }

    return normalizedTitle;
  }

  static String? guessAppTitleFromPath(String path) {
    return guessTitleFromPath(
      path,
      additionalUnknownValues: appUnknownValues,
    );
  }

  static String _normalizeLowercase(String? value) {
    return (value ?? '').trim().toLowerCase();
  }
}
