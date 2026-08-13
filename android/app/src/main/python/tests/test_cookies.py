"""Testes para suporte a cookies do YouTube (cookies.txt) no yt-dlp.

RED→GREEN: quando o usuário importa cookies.txt (conta logada), o yt-dlp
consegue baixar vídeos que o YouTube esconde de acesso anônimo
(ex: "Video unavailable" em playlists gospel).

O yt-dlp suporta a opcao nativa `cookiefile` — so precisamos apontar
para o arquivo quando ele existir.
"""
import os
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import helpers


class TestApplyCookiesFile(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp(prefix="cookies_test_")

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def _write_cookies(self):
        path = os.path.join(self.tmpdir, "cookies.txt")
        with open(path, "w") as f:
            f.write("# Netscape HTTP Cookie File\n")
        return path

    def test_cookiefile_apontado_quando_arquivo_existe(self):
        path = self._write_cookies()
        opts = {}
        helpers._apply_cookies_file(opts, self.tmpdir)
        self.assertEqual(opts["cookiefile"], path)

    def test_sem_arquivo_nao_mexe_nas_opts(self):
        opts = {"format": "bestaudio/best"}
        helpers._apply_cookies_file(opts, self.tmpdir)
        self.assertNotIn("cookiefile", opts)

    def test_diretorio_inexistente_nao_falha(self):
        opts = {}
        helpers._apply_cookies_file(opts, os.path.join(self.tmpdir, "nope"))
        self.assertNotIn("cookiefile", opts)

    def test_cookies_vazio_ou_invalido_nao_seta_cookiefile(self):
        """RED: cookies.txt corrompido/truncado fazia o yt-dlp falhar TODA
        a playlist ('does not look like a Netscape format cookies file').
        O helper deve validar o formato antes de apontar o cookiefile."""
        path = os.path.join(self.tmpdir, "cookies.txt")
        # arquivo vazio (caso real: import corrompido)
        open(path, "w").close()
        opts = {}
        helpers._apply_cookies_file(opts, self.tmpdir)
        self.assertNotIn("cookiefile", opts, "cookies vazio não pode ser usado")

        # lixo binário / truncado
        with open(path, "wb") as f:
            f.write(b"\x00\x01\x02\xff\xfe\xfd" * 10)
        opts = {}
        helpers._apply_cookies_file(opts, self.tmpdir)
        self.assertNotIn("cookiefile", opts, "cookies binário não pode ser usado")

    def test_cookies_netscape_valido_seta_cookiefile(self):
        path = os.path.join(self.tmpdir, "cookies.txt")
        with open(path, "w") as f:
            f.write("# Netscape HTTP Cookie File\n")
            f.write(".youtube.com\tTRUE\t/\tTRUE\t0\tSID\tabc123\n")
        opts = {}
        helpers._apply_cookies_file(opts, self.tmpdir)
        self.assertEqual(opts["cookiefile"], path)

    def test_cookies_sem_header_netscape_nao_seta(self):
        """Arquivo texto mas sem o header # Netscape não é aceitável."""
        path = os.path.join(self.tmpdir, "cookies.txt")
        with open(path, "w") as f:
            f.write("SID=abc123; HSID=xyz; (formato errado de outra extensao)\n")
        opts = {}
        helpers._apply_cookies_file(opts, self.tmpdir)
        self.assertNotIn("cookiefile", opts)

    def test_download_opts_recebe_cookiefile(self):
        """download_video deve propagar o cookiefile nas ydl_opts."""
        path = self._write_cookies()
        import download

        captured = {}

        class FakeYDL:
            def __init__(self, opts):
                captured["opts"] = opts

            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

            def extract_info(self, *a, **kw):
                return {"_type": "video", "title": "t", "id": "1", "formats": [], "thumbnail": None}

            def prepare_filename(self, info):
                return "/tmp/nao_existe.mp4"

        with mock.patch.object(download, "_get_yt_dlp_module") as m_mod, \
             mock.patch.object(download, "_find_downloaded_file", return_value=None), \
             mock.patch("download.os.path.exists", return_value=False):
            m_mod.return_value.YoutubeDL = FakeYDL
            result = download.download_video(
                "https://www.youtube.com/watch?v=abc123",
                "/tmp/out.%(ext)s",
                format_type="audio",
                quality="192",
                app_files_dir=self.tmpdir,
            )

        self.assertIn("cookiefile", captured["opts"])
        self.assertEqual(captured["opts"]["cookiefile"], path)
        import json
        payload = json.loads(result)
        # arquivo não existe -> falha esperada, mas o cookiefile foi aplicado
        self.assertFalse(payload.get("success"))

    def test_extract_info_none_retorna_falha_controlada(self):
        """Com ignoreerrors, video unico indisponivel retorna None do
        yt-dlp — deve virar falha controlada, nao AttributeError."""
        import download

        captured = {}

        class FakeYDL:
            def __init__(self, opts):
                captured["opts"] = opts

            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

            def extract_info(self, *a, **kw):
                return None

        with mock.patch.object(download, "_get_yt_dlp_module") as m_mod:
            m_mod.return_value.YoutubeDL = FakeYDL
            result = download.download_video(
                "https://www.youtube.com/watch?v=abc123",
                "/tmp/out.%(ext)s",
                format_type="audio",
                quality="192",
                app_files_dir=self.tmpdir,
            )

        import json
        payload = json.loads(result)
        self.assertFalse(payload.get("success"))
        self.assertIn("indisponível", payload.get("error", ""))
        self.assertTrue(captured["opts"].get("ignoreerrors"))


if __name__ == "__main__":
    unittest.main()
