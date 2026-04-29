import 'dart:collection';

class LruCache<K, V> {
  final int capacity;
  final LinkedHashMap<K, V> _entries = LinkedHashMap<K, V>();

  LruCache(this.capacity) {
    if (capacity <= 0) {
      throw ArgumentError.value(
        capacity,
        'capacity',
        'LruCache capacity must be greater than zero',
      );
    }
  }

  V? get(K key) {
    if (!_entries.containsKey(key)) {
      return null;
    }

    final value = _entries.remove(key) as V;
    _entries[key] = value;
    return value;
  }

  void put(K key, V value) {
    if (_entries.containsKey(key)) {
      _entries.remove(key);
    }

    _entries[key] = value;
    if (_entries.length > capacity) {
      _entries.remove(_entries.keys.first);
    }
  }

  void remove(K key) {
    _entries.remove(key);
  }

  void clear() {
    _entries.clear();
  }

  bool containsKey(K key) => _entries.containsKey(key);

  int get length => _entries.length;
}
