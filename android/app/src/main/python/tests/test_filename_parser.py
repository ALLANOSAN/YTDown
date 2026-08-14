import json
import unittest

from helpers import _strip_generated_suffix
from metadata import extract_metadata_from_filename


def parse(name):
    return json.loads(extract_metadata_from_filename(name))


class TestFilenameParser(unittest.TestCase):
    def test_remove_numero_de_faixa_e_sufixo_de_formato(self):
        data = parse("02 Get Back To The Bible(m4a 128k).m4a")
        self.assertEqual(data["title"], "Get Back To The Bible")

    def test_remove_numero_de_faixa_com_ponto(self):
        data = parse("01. Song Name.mp3")
        self.assertEqual(data["title"], "Song Name")

    def test_preserva_numero_que_faz_parte_do_titulo(self):
        self.assertEqual(parse("50 Ways to Leave Your Lover.mp3")["title"],
                         "50 Ways to Leave Your Lover")
        self.assertEqual(parse("99 Problems.mp3")["title"], "99 Problems")

    def test_preserva_parenteses_legitimos(self):
        self.assertEqual(parse("Bohemian Rhapsody (Remastered 2011).mp3")["title"],
                         "Bohemian Rhapsody (Remastered 2011)")
        self.assertEqual(parse("Song (Live).mp3")["title"], "Song (Live)")

    def test_remove_bitrate_em_colchetes(self):
        self.assertEqual(parse("Song Name [320kbps].mp3")["title"], "Song Name")
        self.assertEqual(parse("Song Name [128 kbps].mp3")["title"], "Song Name")

    def test_artista_e_titulo_continuam_separados(self):
        data = parse("03 - Linkin Park - Numb (mp3 320k).mp3")
        self.assertEqual(data["artist"], "Linkin Park")
        self.assertEqual(data["title"], "Numb")

    def test_nao_apaga_o_titulo_inteiro(self):
        self.assertEqual(parse("02.mp3")["title"], "02")

    def test_artista_ausente_nao_vira_sentinela_Unknown(self):
        """Sem separador não há artista. Devolver "Unknown" faz o Kotlin montar
        `artist:"Unknown"` no MusicBrainz e perder todo o enrichment."""
        self.assertEqual(parse("Get Back To The Bible.m4a")["artist"], "")


class TestStripGeneratedSuffix(unittest.TestCase):
    """Paridade com MetadataUtils.normalizeMetadataText do Kotlin."""

    def test_remove_sufixo_hex(self):
        self.assertEqual(_strip_generated_suffix("Some Song-a1b2c3d4"), "Some Song")
        self.assertEqual(_strip_generated_suffix("Some Song_a1b2c3d4"), "Some Song")

    def test_remove_uuid_inteiro(self):
        """O padrão hex genérico casava os 12 últimos dígitos do UUID e deixava
        o resto do UUID grudado no título."""
        self.assertEqual(
            _strip_generated_suffix("Some Song_550e8400-e29b-41d4-a716-446655440000"),
            "Some Song",
        )

    def test_underscore_comum_vira_espaco(self):
        self.assertEqual(_strip_generated_suffix("Some_Song"), "Some Song")


if __name__ == "__main__":
    unittest.main()
