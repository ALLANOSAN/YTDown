import unittest
import os
import shutil
from metadata import _force_metadata_with_mutagen
from mutagen.id3 import ID3

class TestMetadataInjection(unittest.TestCase):
    def setUp(self):
        # Cria um arquivo MP3 de teste (mock)
        # Nota: Em um ambiente real, você usaria um arquivo de áudio minúsculo como 'Gold Master'
        self.test_file = "test_song.mp3"
        with open(self.test_file, "wb") as f:
            f.write(b"ID3" + b"\x00" * 100) # Mock header básico

    def tearDown(self):
        if os.path.exists(self.test_file):
            os.remove(self.test_file)

    def test_id3_injection(self):
        title = "Test Title"
        artist = "Test Artist"
        album = "Test Album"
        
        # Executa a injeção
        success = _force_metadata_with_mutagen(self.test_file, title, artist, album)
        
        self.assertTrue(success, "A injeção de metadados deveria ter sucesso")
        
        # Verifica se as tags foram escritas
        tags = ID3(self.test_file)
        self.assertEqual(str(tags["TIT2"]), title)
        self.assertEqual(str(tags["TPE1"]), artist)
        self.assertEqual(str(tags["TALB"]), album)

    def test_id3_v2_0_corrompido_nao_crasha(self):
        """Header ID3v2.0 (nunca suportado pelo mutagen) num arquivo baixado
        corrompido não pode derrubar a injeção — deve criar tags do zero."""
        # Mock identico ao setup: b"ID3" + zeros = ID3v2.0
        broken = "broken_song.mp3"
        with open(broken, "wb") as f:
            f.write(b"ID3" + b"\x00" * 100)
        try:
            result = _force_metadata_with_mutagen(broken, "T", "A", "L")
            import json
            payload = json.loads(result)
            self.assertTrue(payload.get("success"), f"falhou: {payload}")
        finally:
            if os.path.exists(broken):
                os.remove(broken)

if __name__ == "__main__":
    unittest.main()
