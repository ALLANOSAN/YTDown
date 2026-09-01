"""Teste de integracao com rede real contra o YouTube.

Pega regressoes que o mock nao pega: mudanca de comportamento do yt-dlp
nas opcoes extract_flat/noplaylist. NAO roda por padrao (precisa de internet
e do video continuar no ar).

    cd android/app/src/main/python
    YTDOWN_NETWORK_TESTS=1 PYTHONPATH=. python3 tests/test_playlist_network.py
"""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import fetch

# Album "Beyond Belief" do Petra: 10 faixas, link compartilhado real.
ALBUM_URL = (
    "https://www.youtube.com/watch?v=k1Mp96wxFjU"
    "&list=OLAK5uy_nHikntf2j7uiKZ0kl5XyH1WmTpfG6Q7j0"
)
FAIXAS_ESPERADAS = 10

_HABILITADO = os.environ.get("YTDOWN_NETWORK_TESTS") == "1"


@unittest.skipUnless(_HABILITADO, "defina YTDOWN_NETWORK_TESTS=1 para rodar")
class TestPlaylistRedeReal(unittest.TestCase):
    def test_link_compartilhado_devolve_o_album_inteiro(self):
        payload = json.loads(fetch.fetch_video_info(ALBUM_URL))
        self.assertTrue(payload.get("success"), payload.get("error"))

        data = payload["data"]
        self.assertTrue(data["is_playlist"], "album precisa ser tratado como playlist")
        self.assertEqual(len(data["entries"]), FAIXAS_ESPERADAS)

    def test_faixas_vao_pra_fila_sem_o_parametro_list(self):
        """Cada item da fila precisa de uma URL de video puro.

        Se a URL enfileirada ainda tiver &list=, o download reexpande a
        playlist e as faixas se sobrescrevem no mesmo arquivo.
        """
        payload = json.loads(fetch.fetch_video_info(ALBUM_URL))
        urls = [e["url"] for e in payload["data"]["entries"]]

        self.assertEqual(len(urls), FAIXAS_ESPERADAS)
        self.assertEqual(len(set(urls)), FAIXAS_ESPERADAS, "URLs duplicadas na fila")
        for url in urls:
            self.assertNotIn("list=", url, f"URL da fila ainda carrega a playlist: {url}")


if __name__ == "__main__":
    unittest.main()
