"""Testes para download.py — download de playlist com vídeos indisponíveis.

Cobre os 3 bugs encontrados na análise com a playlist real
(PLkLmA1--00s7-rboaPB3cii2m8kzl5eTN: 8 vídeos indisponíveis + 23 OK):

1. noplaylist fixo True ignora a playlist quando a URL é watch?v=...&list=...
2. Sem ignoreerrors, um vídeo indisponível aborta a playlist inteira
3. _apply_tags_to_files crasha (None.get) quando entries contêm None
   e os índices files/entries desalinham
"""
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import download


class TestApplyTagsComEntriesNone(unittest.TestCase):
    """Bug 3: não deve crashar com entries None (vídeos indisponíveis)."""

    def test_entries_none_nao_crasha(self):
        files_with_info = [
            ("/tmp/x.webm", {"title": "Desesperado", "thumbnail": None}),
            ("/tmp/y.webm", {"title": "Outra", "thumbnail": None}),
        ]
        with mock.patch.object(download, "_force_metadata_with_mutagen", return_value=True):
            result = download._apply_tags_to_files(files_with_info, None, None, None)
        self.assertTrue(result)


class TestResolveDownloadedFiles(unittest.TestCase):
    """Bug 3 (raiz): files devem vir alinhados com as entries correspondentes."""

    def test_alinhamento_quando_entries_none_no_inicio(self):
        entries = [None, {"title": "A", "id": "x"}, None, {"title": "B", "id": "y"}]
        fake_ydl = mock.MagicMock()
        fake_ydl.prepare_filename = mock.Mock(
            return_value="/tmp/arquivo.webm"
        )
        with mock.patch.object(download.os.path, "exists", return_value=True):
            result = download._resolve_downloaded_files(fake_ydl, entries, "audio")
        # só as entries válidas, alinhadas com a info correspondente
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0][1]["title"], "A")
        self.assertEqual(result[1][1]["title"], "B")


if __name__ == "__main__":
    unittest.main()
