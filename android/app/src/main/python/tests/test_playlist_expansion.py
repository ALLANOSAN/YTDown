"""Testes para o link compartilhado de playlist (watch?v=X&list=Y).

Bug real reproduzido com o album "Beyond Belief" do Petra
(list=OLAK5uy_nHikntf2j7uiKZ0kl5XyH1WmTpfG6Q7j0, 10 faixas):

1. fetch_video_info devolvia 1 item "Sem titulo" em vez das 10 faixas —
   com extract_flat=True + noplaylist=True o yt-dlp NAO resolve o link
   compartilhado, devolve {_type: "url", title: None} sem entries nem formats.
2. Como o item enfileirado guardava a URL inteira (com &list=), o
   download_video reexpandia a playlist e escrevia as 10 faixas no MESMO
   outtmpl — cada video sobrescrevendo o anterior.

O FakeYDL abaixo reproduz o comportamento medido do yt-dlp 2026.08.19
para cada combinacao de opcoes.
"""
import json
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import fetch

ALBUM_URL = (
    "https://www.youtube.com/watch?v=k1Mp96wxFjU"
    "&list=OLAK5uy_nHikntf2j7uiKZ0kl5XyH1WmTpfG6Q7j0"
)
MIX_URL = "https://www.youtube.com/watch?v=k1Mp96wxFjU&list=RDk1Mp96wxFjU"
SOLO_URL = "https://www.youtube.com/watch?v=k1Mp96wxFjU"
PLAYLIST_PURA_URL = (
    "https://www.youtube.com/playlist?list=OLAK5uy_nHikntf2j7uiKZ0kl5XyH1WmTpfG6Q7j0"
)

_ALBUM_ENTRIES = [
    {"id": "qNw447VfGtA", "title": "Petra - Armed and Dangerous",
     "url": "https://www.youtube.com/watch?v=qNw447VfGtA", "duration": 231},
    {"id": "gisvfN4wXUU", "title": "Petra - I Am on the Rock",
     "url": "https://www.youtube.com/watch?v=gisvfN4wXUU", "duration": 254},
    {"id": "jy4JA5uStB0", "title": "Petra - Creed",
     "url": "https://www.youtube.com/watch?v=jy4JA5uStB0", "duration": 279},
]


def _album_info():
    return {
        "_type": "playlist",
        "id": "OLAK5uy_nHikntf2j7uiKZ0kl5XyH1WmTpfG6Q7j0",
        "title": "Beyond Belief",
        "uploader": "Petra",
        "entries": [dict(e) for e in _ALBUM_ENTRIES],
    }


def _video_info():
    return {
        "_type": "video",
        "id": "k1Mp96wxFjU",
        "title": "Armed and Dangerous",
        "uploader": "Petra",
        "thumbnail": "https://i.ytimg.com/vi/k1Mp96wxFjU/hq.jpg",
        "duration": 231,
        "formats": [{"format_id": "140", "ext": "m4a"}],
    }


class FakeYDL:
    """Reproduz o comportamento real medido do yt-dlp para cada combinacao."""

    captured = {}

    def __init__(self, opts):
        FakeYDL.captured["opts"] = opts

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def extract_info(self, url, download=False, **kw):
        opts = FakeYDL.captured["opts"]
        tem_lista = "list=" in url
        if tem_lista and not opts.get("noplaylist"):
            # yt-dlp expande a playlist normalmente
            return _album_info()
        if tem_lista and opts.get("extract_flat") is True:
            # MEDIDO: noplaylist=True + extract_flat=True NAO resolve o link,
            # devolve um UrlResult cru sem titulo, sem entries e sem formats
            return {"_type": "url", "url": url, "id": "k1Mp96wxFjU", "title": None}
        return _video_info()


class TestExpansaoDeLinkCompartilhado(unittest.TestCase):
    def _fetch(self, url):
        with mock.patch.object(fetch, "_get_yt_dlp_module") as m_mod:
            m_mod.return_value.YoutubeDL = FakeYDL
            return json.loads(fetch.fetch_video_info(url, app_files_dir=None))

    def test_link_de_album_expande_para_todas_as_faixas(self):
        """watch?v=X&list=OLAK5uy_ deve virar as N faixas do album."""
        payload = self._fetch(ALBUM_URL)

        self.assertTrue(payload.get("success"), payload.get("error"))
        data = payload["data"]
        self.assertTrue(
            data["is_playlist"],
            "link compartilhado de album precisa ser tratado como playlist",
        )
        self.assertEqual(len(data["entries"]), len(_ALBUM_ENTRIES))
        self.assertNotEqual(
            data["title"], "Sem título",
            "titulo do album nao pode cair no fallback 'Sem título'",
        )

    def test_mix_do_youtube_baixa_so_o_video_clicado(self):
        """list=RD... é rádio infinita: expandir encheria a fila."""
        payload = self._fetch(MIX_URL)

        data = payload["data"]
        self.assertFalse(data["is_playlist"], "Mix/rádio não pode virar playlist")
        self.assertEqual(len(data["entries"]), 1)
        self.assertEqual(data["title"], "Armed and Dangerous")

    def test_video_sem_lista_continua_com_formats(self):
        """Regressão: o vídeo solo precisa manter a lista de formatos."""
        payload = self._fetch(SOLO_URL)

        data = payload["data"]
        self.assertFalse(data["is_playlist"])
        self.assertEqual(len(data["entries"]), 1)
        self.assertTrue(data["formats"], "vídeo solo precisa expor os formatos")


class FakeYDLGravador:
    """FakeYDL que escreve de verdade no caminho que o outtmpl gerar.

    É assim que o bug da sobreposição aparece: o outtmpl do app é um caminho
    fixo ("Artista - Album - Titulo.%(ext)s"), então TODAS as entries da
    playlist resolvem para o MESMO arquivo (medido: 10 entries -> 1 caminho).
    """

    capturado = {}
    escritas = []

    def __init__(self, opts):
        self.opts = opts
        FakeYDLGravador.capturado["opts"] = opts

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def prepare_filename(self, info):
        return self.opts["outtmpl"].replace("%(ext)s", "m4a")

    def _escrever(self, caminho):
        FakeYDLGravador.escritas.append(caminho)
        with open(caminho, "w", encoding="utf-8") as f:
            f.write("audio")

    def extract_info(self, url, download=False, **kw):
        # MEDIDO: noplaylist=True NÃO protege URL de playlist pura
        # (/playlist?list=...) — o yt-dlp expande as 10 entries assim mesmo.
        url_de_playlist_pura = "list=" in url and "v=" not in url
        if url_de_playlist_pura or ("list=" in url and not self.opts.get("noplaylist")):
            info = _album_info()
            for entry in info["entries"]:
                self._escrever(self.prepare_filename(entry))
            return info
        video = _video_info()
        self._escrever(self.prepare_filename(video))
        return video


class TestDownloadDeUmItemDaFila(unittest.TestCase):
    """Cada item da fila baixa UM vídeo para UM outtmpl fixo."""

    def setUp(self):
        import tempfile
        self.tmp = tempfile.mkdtemp()
        FakeYDLGravador.escritas = []
        FakeYDLGravador.capturado = {}

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _download(self, url):
        import download
        saida = os.path.join(self.tmp, "Petra - Beyond Belief - Armed and Dangerous.%(ext)s")
        with mock.patch.object(download, "_get_yt_dlp_module") as m_mod, \
             mock.patch.object(download, "_force_metadata_with_mutagen", return_value=True):
            m_mod.return_value.YoutubeDL = FakeYDLGravador
            return json.loads(download.download_video(
                url, saida, format_type="audio", quality="192", app_files_dir=self.tmp,
            ))

    def test_url_com_list_nao_pode_sobrescrever_o_proprio_arquivo(self):
        """O bug: as 10 faixas caíam no mesmo arquivo, uma apagando a outra."""
        payload = self._download(ALBUM_URL)

        self.assertTrue(payload.get("success"), payload.get("error"))
        self.assertEqual(
            len(FakeYDLGravador.escritas), 1,
            "um item da fila deve gravar UM arquivo — %d gravações no mesmo "
            "caminho significam vídeos se sobrepondo" % len(FakeYDLGravador.escritas),
        )
        self.assertEqual(
            len(set(FakeYDLGravador.escritas)), len(FakeYDLGravador.escritas),
            "nenhum arquivo pode ser gravado duas vezes",
        )

    def test_url_de_playlist_pura_falha_em_vez_de_sobrescrever(self):
        """noplaylist=True não segura /playlist?list=... — é preciso recusar
        antes de baixar, senão as N faixas caem no mesmo arquivo em silêncio."""
        payload = self._download(PLAYLIST_PURA_URL)

        self.assertFalse(
            payload.get("success"),
            "URL de playlist não cabe em um único output_path",
        )
        self.assertEqual(
            FakeYDLGravador.escritas, [],
            "deve recusar ANTES de baixar qualquer byte",
        )
        self.assertIn("playlist", payload.get("error", "").lower())


if __name__ == "__main__":
    unittest.main()
