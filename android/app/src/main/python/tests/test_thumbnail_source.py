import json
import os
import tempfile
import unittest

from helpers import _download_thumbnail_bytes
from metadata import read_file_metadata, rewrite_file_metadata

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 64
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64
WEBP = b"RIFF" + b"\x00\x00\x00\x00" + b"WEBP" + b"\x00" * 64


class TestThumbnailSource(unittest.TestCase):
    """A capa é lida de caminho local (cache do app) OU baixada de URL.

    O parâmetro é a mesma string nos dois casos, então a função não sabe se
    veio do cache confiável ou de metadado remoto. A garantia tem que ser sobre
    o CONTEÚDO: só entra no arquivo do usuário o que for imagem de verdade.
    """

    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def _write(self, name, data):
        path = os.path.join(self.tmp, name)
        with open(path, "wb") as f:
            f.write(data)
        return path

    def test_aceita_imagem_local_de_verdade(self):
        for nome, dados in (("a.jpg", JPEG), ("b.png", PNG), ("c.webp", WEBP)):
            self.assertEqual(_download_thumbnail_bytes(self._write(nome, dados)), dados)

    def test_recusa_arquivo_local_que_nao_e_imagem(self):
        """Sem isso, qualquer caminho local vira "capa": um cookies.txt seria
        embutido no MP3 como image/jpeg e sairia junto com o arquivo."""
        cookies = self._write(
            "cookies.txt",
            b"# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tSID\tsegredo\n",
        )
        self.assertIsNone(_download_thumbnail_bytes(cookies))

    def test_recusa_html_no_lugar_de_imagem(self):
        pagina = self._write("404.html", b"<!DOCTYPE html><html><body>Not Found</body></html>")
        self.assertIsNone(_download_thumbnail_bytes(pagina))

    def test_recusa_esquema_file(self):
        """urllib resolve file:// nativamente — sem allowlist de esquema a
        rejeição do caminho local seria contornável só trocando o prefixo."""
        alvo = self._write("segredo.txt", b"conteudo secreto qualquer")
        self.assertIsNone(_download_thumbnail_bytes("file://" + alvo))

    def test_recusa_esquemas_nao_http(self):
        for url in ("ftp://exemplo.com/x.jpg", "data:image/jpeg;base64,AAAA", "gopher://x/1"):
            self.assertIsNone(_download_thumbnail_bytes(url))

    def test_entrada_vazia(self):
        self.assertIsNone(_download_thumbnail_bytes(None))
        self.assertIsNone(_download_thumbnail_bytes(""))

    def test_capa_invalida_nao_derruba_a_gravacao_de_tags(self):
        mp3 = self._write("song.mp3", b"ID3" + b"\x00" * 100)
        lixo = self._write("naoimagem.jpg", b"isso aqui nao e imagem nenhuma")

        payload = json.loads(rewrite_file_metadata(mp3, "T", "A", "L", lixo))
        self.assertTrue(payload.get("success"), f"write falhou: {payload}")

        data = json.loads(read_file_metadata(mp3))
        self.assertFalse(data["has_artwork"], "lixo foi embutido como capa")
        self.assertEqual(data["title"], "T")


if __name__ == "__main__":
    unittest.main()
