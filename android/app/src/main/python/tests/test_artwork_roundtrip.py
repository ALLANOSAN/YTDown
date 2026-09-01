import base64
import json
import os
import unittest

from metadata import embed_album_art, read_file_metadata, rewrite_file_metadata


# M4A real de 0,15s gerado com ffmpeg (AAC 32k mono). O app baixa .m4a, entao e
# este o container que precisa receber a capa — nao o MP3.
M4A_SILENCIOSO = base64.b64decode(
    "AAAAHGZ0eXBNNEEgAAACAE00QSBpc29taXNvMgAAAAhmcmVlAAAAN21kYXTcAExhdmM2My4x"
    "LjEwMQACMEAOARggBwEYIAcBGCAHARggBwEYIAcBGCAHARggBwAAAxptb292AAAAbG12aGQA"
    "AAAAAAAAAAAAAAAAAKxEAAAZ1wABAAABAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAEA"
    "AAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAACRXRyYWsA"
    "AABcdGtoZAAAAAMAAAAAAAAAAAAAAAEAAAAAAAAZ1wAAAAAAAAAAAAAAAQEAAAAAAQAAAAAA"
    "AAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAACRlZHRzAAAAHGVsc3QA"
    "AAAAAAAAAQAAGdcAAAQAAAEAAAAAAb1tZGlhAAAAIG1kaGQAAAAAAAAAAAAAAAAAAKxEAAAd"
    "11XEAAAAAAAtaGRscgAAAAAAAAAAc291bgAAAAAAAAAAAAAAAFNvdW5kSGFuZGxlcgAAAAFo"
    "bWluZgAAABBzbWhkAAAAAAAAAAAAAAAkZGluZgAAABxkcmVmAAAAAAAAAAEAAAAMdXJsIAAA"
    "AAEAAAEsc3RibAAAAGpzdHNkAAAAAAAAAAEAAABabXA0YQAAAAAAAAABAAAAAAAAAAAAAQAQ"
    "AAAAAKxEAAAAAAA2ZXNkcwAAAAADgICAJQABAASAgIAXQBUAAAAAAH0AAAAIegWAgIAFEghW"
    "5QAGgICAAQIAAAAgc3R0cwAAAAAAAAACAAAABwAABAAAAAABAAAB1wAAABxzdHNjAAAAAAAA"
    "AAEAAAABAAAACAAAAAEAAAA0c3RzegAAAAAAAAAAAAAACAAAABMAAAAEAAAABAAAAAQAAAAE"
    "AAAABAAAAAQAAAAEAAAAFHN0Y28AAAAAAAAAAQAAACwAAAAac2dwZAEAAAByb2xsAAAAAgAA"
    "AAH//wAAABxzYmdwAAAAAHJvbGwAAAABAAAACAAAAAEAAABhdWR0YQAAAFltZXRhAAAAAAAA"
    "ACFoZGxyAAAAAAAAAABtZGlyYXBwbAAAAAAAAAAAAAAAACxpbHN0AAAAJKl0b28AAAAcZGF0"
    "YQAAAAEAAAAATGF2ZjYzLjEuMTAx"
)

# JPEG 1x1 valido.
JPEG_1X1 = bytes.fromhex(
    "ffd8ffe000104a46494600010100000100010000ffdb004300"
    "080606070605080707070909080a0c140d0c0b0b0c1912130f"
    "141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c3032"
    "35351f27393d38333c2e343533ffc0000b0800010001010111"
    "00ffc4001f0000010501010101010100000000000000000102"
    "030405060708090a0bffc400b5100002010303020403050504"
    "040000017d01020300041105122131410613516107227114328191a108234"
    "2b1c11552d1f02433627282090a161718191a25262728292a34353637383"
    "93a434445464748494a535455565758595a636465666768696a737475767"
    "778797a838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab"
    "2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e"
    "4e5e6e7e8e9eaf1f2f3f4f5f6f7f8f9faffda0008010100003f00fbfeffd9"
)


class TestArtworkRoundtripM4A(unittest.TestCase):
    """
    O pipeline confia em `embed_album_art` para gravar a capa no arquivo quando
    ela existe so no cache. Se essa gravacao falhasse em silencio, o SongEntity
    continuaria apontando para o cache (e o player do app mostraria a capa)
    enquanto qualquer outro player nao acharia nada no arquivo.

    A quebra que estes testes pegam: `embed_album_art` parar de escrever o atomo
    `covr`, parar de salvar, ou passar a reportar sucesso sem ter gravado.
    """

    def setUp(self):
        self.audio = "roundtrip_song.m4a"
        self.cover = "roundtrip_cover.jpg"
        with open(self.audio, "wb") as f:
            f.write(M4A_SILENCIOSO)
        with open(self.cover, "wb") as f:
            f.write(JPEG_1X1)

    def tearDown(self):
        for p in (self.audio, self.cover):
            if os.path.exists(p):
                os.remove(p)

    def test_capa_embutida_no_m4a_e_vista_como_artwork_do_arquivo(self):
        antes = json.loads(read_file_metadata(self.audio))
        self.assertFalse(antes["has_artwork"], "arquivo novo nao deveria ter capa")

        embed_album_art(self.audio, self.cover)

        depois = json.loads(read_file_metadata(self.audio))
        self.assertTrue(
            depois["has_artwork"],
            "capa embutida precisa aparecer como artwork do arquivo, nao so no cache",
        )

    def test_bytes_gravados_no_covr_sao_os_da_capa_de_origem(self):
        embed_album_art(self.audio, self.cover)

        from mutagen.mp4 import MP4

        capas = MP4(self.audio)["covr"]
        self.assertEqual(1, len(capas), "deveria haver exatamente uma capa")
        self.assertEqual(JPEG_1X1, bytes(capas[0]))


    def test_enriquecimento_completo_grava_capa_local_no_m4a(self):
        """
        Caminho do PASSO 0 para tras: o pipeline chama `rewrite_file_metadata`
        passando o caminho LOCAL da capa em cache no parametro `artwork_url`.
        Se esse parametro so aceitasse http, todo arquivo enriquecido sairia com
        tags corretas e sem capa.
        """
        # Caminho ABSOLUTO de proposito: `_download_thumbnail_bytes` so aceita
        # arquivo local quando o caminho e absoluto, e e assim que o
        # MediaImportProcessor chama (cachedFile.absolutePath).
        rewrite_file_metadata(
            self.audio, "Titulo", "Artista", "Album", os.path.abspath(self.cover),
        )

        depois = json.loads(read_file_metadata(self.audio))
        self.assertTrue(
            depois["has_artwork"],
            "capa em caminho local precisa ser embutida pelo enriquecimento",
        )
        self.assertEqual("Album", depois["album"])


    def test_formato_sem_suporte_nao_reporta_sucesso(self):
        """
        `embed_album_art` so tem ramo para mp3/m4a/flac, mas o varredor de pasta
        aceita opus, aac e wav. Reportar sucesso sem ter gravado nada faz o
        chamador acreditar que o arquivo ficou com capa quando ela seguiu apenas
        no cache — a mesma falha silenciosa, sem nem um aviso no log.
        """
        opus = "roundtrip_song.opus"
        with open(opus, "wb") as f:
            f.write(b"OggS" + b"\x00" * 64)
        try:
            resultado = json.loads(embed_album_art(opus, os.path.abspath(self.cover)))
            self.assertFalse(
                resultado["success"],
                "formato sem ramo de embed nao pode reportar sucesso",
            )
        finally:
            os.remove(opus)


if __name__ == "__main__":
    unittest.main()
