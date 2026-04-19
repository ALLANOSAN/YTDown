import 'package:flutter_test/flutter_test.dart';
import 'package:ytdown/utils/metadata_utils.dart';

void main() {
  group('MetadataUtils', () {
    test('isUnknownMetadata identifies base unknown values', () {
      expect(MetadataUtils.isUnknownMetadata(null), isTrue);
      expect(MetadataUtils.isUnknownMetadata(''), isTrue);
      expect(MetadataUtils.isUnknownMetadata('Unknown'), isTrue);
      expect(MetadataUtils.isUnknownMetadata('desconhecido'), isTrue);
      expect(MetadataUtils.isUnknownMetadata('videoplayback'), isTrue);
      expect(MetadataUtils.isUnknownMetadata('Radiohead'), isFalse);
    });

    test('isUnknownMetadata supports custom unknown values', () {
      expect(
        MetadataUtils.isUnknownMetadata(
          'ytdown',
          additionalUnknownValues: const <String>{'ytdown'},
        ),
        isTrue,
      );
    });

    test('isUnknownAppMetadata treats ytdown as unknown placeholder', () {
      expect(MetadataUtils.isUnknownAppMetadata('ytdown'), isTrue);
      expect(MetadataUtils.isUnknownAppMetadata('Radiohead'), isFalse);
    });

    test('normalizeMetadataText removes generated suffix and normalizes spaces',
        () {
      final value = MetadataUtils.normalizeMetadataText('  the_song-abc123  ');
      expect(value, 'the song');
    });

    test('guessArtistFromTitle extracts artist with separators', () {
      expect(
        MetadataUtils.guessArtistFromTitle('Daft Punk - Harder Better Faster'),
        'Daft Punk',
      );
      expect(
        MetadataUtils.guessArtistFromTitle(
            'Nirvana \u2013 Smells Like Teen Spirit'),
        'Nirvana',
      );
      expect(
        MetadataUtils.guessArtistFromTitle('Adele | Hello'),
        'Adele',
      );
    });

    test('guessArtistFromTitle returns null for invalid candidate', () {
      expect(MetadataUtils.guessArtistFromTitle('unknown - title'), isNull);
      expect(MetadataUtils.guessArtistFromTitle('single title only'), isNull);
    });

    test('guessAppArtistFromTitle ignores app placeholder artist', () {
      expect(MetadataUtils.guessAppArtistFromTitle('ytdown - title'), isNull);
      expect(
        MetadataUtils.guessAppArtistFromTitle('Daft Punk - One More Time'),
        'Daft Punk',
      );
    });

    test('guessTitleFromPath extracts normalized title from unix path', () {
      final title = MetadataUtils.guessTitleFromPath(
        '/storage/emulated/0/Music/the_song-abc123.m4a',
      );

      expect(title, 'the song');
    });

    test('guessTitleFromPath handles windows separators and uuid suffix', () {
      final title = MetadataUtils.guessTitleFromPath(
        r'C:\Music\Daft_Punk_-_Harder_8f6c1a2b-1111-2222-3333-aabbccddeeff.mp3',
      );

      expect(title, 'Daft Punk - Harder');
    });

    test('guessTitleFromPath returns null for unknown title', () {
      final title = MetadataUtils.guessTitleFromPath(
        '/tmp/videoplayback_123abc.mp3',
      );

      expect(title, isNull);
    });

    test('guessAppTitleFromPath ignores app placeholder title', () {
      final title = MetadataUtils.guessAppTitleFromPath(
        '/tmp/ytdown_123abc.mp3',
      );

      expect(title, isNull);
    });
  });
}
