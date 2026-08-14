import json
import unittest
import os
import shutil
from metadata import _force_metadata_with_mutagen, read_file_metadata, rewrite_file_metadata
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

    def test_read_file_metadata_detecta_capa_embutida_em_mp3(self):
        """O frame APIC do mutagen tem chave "APIC:Cover", nao "APIC" — checar
        `"APIC" in tags` reporta sempre False e o app acha que o arquivo esta
        sem capa mesmo tendo acabado de gravar uma."""
        cover = os.path.abspath("cover_test.jpg")
        with open(cover, "wb") as f:
            f.write(b"\xff\xd8\xff\xe0" + b"\x00" * 64)
        try:
            written = json.loads(
                rewrite_file_metadata(self.test_file, "T", "A", "L", cover)
            )
            self.assertTrue(written.get("success"), f"write falhou: {written}")

            tags = ID3(self.test_file)
            self.assertEqual(len(tags.getall("APIC")), 1, "capa nao foi embutida")

            data = json.loads(read_file_metadata(self.test_file))
            self.assertTrue(data["has_artwork"], f"has_artwork errado: {data}")
        finally:
            if os.path.exists(cover):
                os.remove(cover)

    def test_read_file_metadata_sem_capa(self):
        _force_metadata_with_mutagen(self.test_file, "T", "A", "L")
        data = json.loads(read_file_metadata(self.test_file))
        self.assertFalse(data["has_artwork"])

    def _write_cover_file(self, name, marker):
        path = os.path.abspath(name)
        with open(path, "wb") as f:
            f.write(b"\xff\xd8\xff\xe0" + marker + b"\x00" * 64)
        return path

    def test_rewrite_preserva_capa_quando_artwork_url_e_none(self):
        """artwork_url=None significa "não mexi na capa", nunca "apague a capa".

        MetadataRepairer (botão Reparar Tags) passa null explícito, e
        LibraryRepository.updateArtistInBatch/updateAlbumInBatch passam null
        sempre que o usuário renomeia sem escolher foto da galeria — todos
        estavam apagando a capa embutida do arquivo."""
        cover = self._write_cover_file("cover_keep.jpg", b"KEEP")
        try:
            rewrite_file_metadata(self.test_file, "T", "A", "L", cover)
            self.assertEqual(len(ID3(self.test_file).getall("APIC")), 1)

            rewrite_file_metadata(self.test_file, "Novo Titulo", "A", "L", None)

            apic = ID3(self.test_file).getall("APIC")
            self.assertEqual(len(apic), 1, "capa foi apagada no rewrite sem artwork")
            self.assertIn(b"KEEP", apic[0].data)
            self.assertEqual(str(ID3(self.test_file)["TIT2"]), "Novo Titulo")
        finally:
            if os.path.exists(cover):
                os.remove(cover)

    def test_rewrite_substitui_capa_sem_duplicar(self):
        old = self._write_cover_file("cover_old.jpg", b"OLD")
        new = self._write_cover_file("cover_new.jpg", b"NEW")
        try:
            rewrite_file_metadata(self.test_file, "T", "A", "L", old)
            rewrite_file_metadata(self.test_file, "T", "A", "L", new)

            apic = ID3(self.test_file).getall("APIC")
            self.assertEqual(len(apic), 1, "capa duplicada")
            self.assertIn(b"NEW", apic[0].data)
        finally:
            for p in (old, new):
                if os.path.exists(p):
                    os.remove(p)

    @unittest.skipUnless(shutil.which("ffmpeg"), "precisa de ffmpeg para criar m4a")
    def test_faixa_nao_numerica_nao_derruba_o_write_em_m4a(self):
        """MP4 trkn é tupla de int, mas o MusicBrainz devolve "B1" (lado do
        vinil) e "5/12". int() estourava e o payload voltava
        success=false — o arquivo ficava SEM NENHUMA tag e sem capa."""
        from mutagen.mp4 import MP4

        m4a = self._make_m4a("track_num.m4a")
        try:
            for track, expected in (("B1", 1), ("5", 5), ("5/12", 5), ("A", None), ("", None)):
                res = json.loads(
                    rewrite_file_metadata(m4a, "T", "A", "L", None, None, "1974", track, None)
                )
                self.assertTrue(res.get("success"), f"track={track!r} falhou: {res}")

                audio = MP4(m4a)
                self.assertEqual(audio["©nam"][0], "T", f"tags perdidas com track={track!r}")
                trkn = audio.get("trkn")
                if expected is None:
                    self.assertIsNone(trkn, f"track={track!r} nao deveria gravar trkn")
                else:
                    self.assertEqual(trkn[0][0], expected, f"track={track!r}")
        finally:
            if os.path.exists(m4a):
                os.remove(m4a)

    def test_parse_mp4_number_respeita_o_limite_do_campo(self):
        """Achado por fuzzing (atheris).

        trkn/disk do MP4 são pares de uint16: acima de 65535 o mutagen recusa
        com "invalid numeric pair" e a gravação INTEIRA de tags falha. E acima
        de 4300 dígitos o próprio int() do Python levanta ValueError antes
        disso. Os dois vêm do campo livre "number" do MusicBrainz.
        """
        from metadata import _parse_mp4_number

        self.assertEqual(_parse_mp4_number("65535"), 65535)
        self.assertIsNone(_parse_mp4_number("65536"))
        self.assertIsNone(_parse_mp4_number("999999999999"))
        self.assertIsNone(_parse_mp4_number("9" * 5000))
        # não regride o que já funcionava
        self.assertEqual(_parse_mp4_number("B1"), 1)
        self.assertEqual(_parse_mp4_number("5/12"), 5)
        self.assertIsNone(_parse_mp4_number("A"))

    @unittest.skipUnless(shutil.which("ffmpeg"), "precisa de ffmpeg para criar m4a")
    def test_faixa_absurda_nao_derruba_o_write_em_m4a(self):
        from mutagen.mp4 import MP4

        m4a = self._make_m4a("track_huge.m4a")
        try:
            for track in ("65535", "65536", "999999999999", "9" * 5000):
                res = json.loads(
                    rewrite_file_metadata(m4a, "T", "A", "L", None, None, None, track, None)
                )
                self.assertTrue(res.get("success"), f"track={track[:12]}... falhou: {res}")
                self.assertEqual(MP4(m4a)["©nam"][0], "T", f"tags perdidas com track={track[:12]}")
        finally:
            if os.path.exists(m4a):
                os.remove(m4a)

    @unittest.skipUnless(shutil.which("ffmpeg"), "precisa de ffmpeg para criar m4a")
    def test_disco_nao_numerico_nao_derruba_o_write_em_m4a(self):
        from mutagen.mp4 import MP4

        m4a = self._make_m4a("disc_num.m4a")
        try:
            res = json.loads(
                rewrite_file_metadata(m4a, "T", "A", "L", None, None, None, None, "lado B")
            )
            self.assertTrue(res.get("success"), f"falhou: {res}")
            self.assertEqual(MP4(m4a)["©nam"][0], "T")
        finally:
            if os.path.exists(m4a):
                os.remove(m4a)

    def _make_m4a(self, name):
        import subprocess

        path = os.path.abspath(name)
        subprocess.run(
            ["ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
             "-t", "1", "-c:a", "aac", path],
            check=True, capture_output=True,
        )
        return path

    @unittest.skipUnless(shutil.which("ffmpeg"), "precisa de ffmpeg para criar m4a")
    def test_rewrite_preserva_capa_em_m4a(self):
        import subprocess
        from mutagen.mp4 import MP4

        m4a = os.path.abspath("test_song.m4a")
        subprocess.run(
            ["ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
             "-t", "1", "-c:a", "aac", m4a],
            check=True, capture_output=True,
        )
        cover = self._write_cover_file("cover_m4a.jpg", b"KEEP")
        try:
            rewrite_file_metadata(m4a, "T", "A", "L", cover)
            self.assertEqual(len(MP4(m4a).get("covr", [])), 1)

            rewrite_file_metadata(m4a, "Novo Titulo", "A", "L", None)

            covr = MP4(m4a).get("covr", [])
            self.assertEqual(len(covr), 1, "capa foi apagada no rewrite sem artwork")
            self.assertIn(b"KEEP", bytes(covr[0]))
        finally:
            for p in (m4a, cover):
                if os.path.exists(p):
                    os.remove(p)


if __name__ == "__main__":
    unittest.main()
