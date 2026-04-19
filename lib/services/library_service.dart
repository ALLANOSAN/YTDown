import '../models/download_item.dart';
import 'database_service.dart';

class LibraryService {
  LibraryService._();
  static final instance = LibraryService._();

  DatabaseService get _database => DatabaseService.instance;

  Future<List<DownloadItem>> getSongs() async {
    // Retorna apenas áudios completados (mais focado e performático na view Tracks)
    return _database.getLibraryAudios();
  }

  Future<List<Map<String, dynamic>>> getArtistsWithMetadata(
      {String query = ''}) async {
    return _database.getDistinctArtists(query: query);
  }

  Future<List<Map<String, dynamic>>> getAlbumsWithMetadata(
      {String query = ''}) async {
    return _database.getDistinctAlbums(query: query);
  }

  Future<List<DownloadItem>> search(String query) async {
    return _database.searchLibrary(query);
  }

  Future<List<DownloadItem>> getLibraryByArtist(String artist) async {
    return _database.getLibraryByArtist(artist);
  }

  Future<List<DownloadItem>> getLibraryByAlbum(String album) async {
    return _database.getLibraryByAlbum(album);
  }
}
