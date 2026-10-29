"""Filtro de indisponiveis antes do enfileiramento (estilo Snaptube).

Contexto: a fila recebia videos indisponiveis (privados/excluidos/bloqueados)
e eles so falhavam no worker, 1 a 1, na etapa extract_info — com mensagem
generica. O probe abaixo verifica disponibilidade com extract_info rapido
(process=False, sem download, ~1s) ANTES de agendar.

Fontes independentes de verdade usadas aqui (nao recomputadas do codigo):
- URLs reais observadas no banco do aparelho em 29/09/2026:
  indisponiveis -> m-oPpqsUBQU, 9eT8H1tRM24 (DownloadError "This video is
  not available", ~0.8s); disponivel -> HP_o5b7uENk (ok em ~1.0s).
- Testes com rede seguem o precedente de tests/test_playlist_network.py:
  pulam sozinhos sem YTDOWN_NETWORK_TESTS=1.
"""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import ytdown

# Observado ao vivo via yt-dlp 2026.08.19 (logcat + roda local, 29/09/2026).
URL_INDISPONIVEL_1 = "https://www.youtube.com/watch?v=m-oPpqsUBQU"
URL_INDISPONIVEL_2 = "https://www.youtube.com/watch?v=9eT8H1tRM24"
URL_DISPONIVEL = "https://www.youtube.com/watch?v=HP_o5b7uENk"

MENSAGEM_YTDLP_INDISPONIVEL = (
    "ERROR: [youtube] m-oPpqsUBQU: This video is not available"
)


class FakeYdl:
    """Duble minimo do YoutubeDL: so o que o probe usa."""

    def __init__(self, comportamento):
        # comportamento: dict url -> ("ok", title) | ("erro", mensagem)
        self._comportamento = comportamento
        self.chamadas = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def extract_info(self, url, download=False, process=False):
        self.chamadas.append((url, download, process))
        modo, valor = self._comportamento.get(url, ("ok", "Titulo Qualquer"))
        if modo == "erro":
            raise FakeDownloadError(valor)
        return {"id": "abc123", "title": valor}


class FakeDownloadError(Exception):
    pass


def fabrica_fake(comportamento):
    fake = FakeYdl(comportamento)

    def _fabrica(app_files_dir=None):
        return fake

    return _fabrica, fake


class TestContratoCheckAvailability(unittest.TestCase):
    def test_fachada_expoe_check_availability(self):
        self.assertIn("check_availability", ytdown.__all__)
        self.assertTrue(callable(ytdown.check_availability))


class TestProbeOffline(unittest.TestCase):
    def test_video_indisponivel_nao_e_agendavel(self):
        from availability import check_availability

        fabrica, fake = fabrica_fake(
            {URL_INDISPONIVEL_1: ("erro", MENSAGEM_YTDLP_INDISPONIVEL)}
        )
        saida = json.loads(
            check_availability(json.dumps([URL_INDISPONIVEL_1]), _ydl_factory=fabrica)
        )
        self.assertTrue(saida["success"])
        (resultado,) = saida["data"]["results"]
        self.assertEqual(resultado["url"], URL_INDISPONIVEL_1)
        self.assertFalse(resultado["available"])
        self.assertIn("indispon", resultado["reason"].lower())
        # Probe nao baixa nada: process=False, download=False.
        self.assertEqual(fake.chamadas, [(URL_INDISPONIVEL_1, False, False)])

    def test_video_disponivel_passar(self):
        from availability import check_availability

        fabrica, _ = fabrica_fake({URL_DISPONIVEL: ("ok", "Algum Titulo")})
        saida = json.loads(
            check_availability(json.dumps([URL_DISPONIVEL]), _ydl_factory=fabrica)
        )
        (resultado,) = saida["data"]["results"]
        self.assertTrue(resultado["available"])
        self.assertIsNone(resultado["reason"])

    def test_erro_transiente_nao_descarta_o_video(self):
        # Regra de seguranca: so pula o que e DEFINITIVAMENTE indisponivel.
        # Timeout/rede falhando nao pode sumir com video bom da fila.
        from availability import check_availability

        fabrica, _ = fabrica_fake(
            {URL_DISPONIVEL: ("erro", "URLError: <urlopen error timed out>")}
        )
        saida = json.loads(
            check_availability(json.dumps([URL_DISPONIVEL]), _ydl_factory=fabrica)
        )
        (resultado,) = saida["data"]["results"]
        self.assertTrue(resultado["available"])

    def test_lista_vazia_ok_vazia(self):
        from availability import check_availability

        fabrica, fake = fabrica_fake({})
        saida = json.loads(check_availability(json.dumps([]), _ydl_factory=fabrica))
        self.assertTrue(saida["success"])
        self.assertEqual(saida["data"]["results"], [])
        self.assertEqual(fake.chamadas, [])

    def test_falha_na_fabrica_retorna_payload_de_falha(self):
        from availability import check_availability

        def fabrica_quebrada(app_files_dir=None):
            raise RuntimeError("sem python?")

        saida = json.loads(
            check_availability(json.dumps([URL_DISPONIVEL]), _ydl_factory=fabrica_quebrada)
        )
        self.assertFalse(saida["success"])
        self.assertIn("stage", saida)


@unittest.skipUnless(
    os.environ.get("YTDOWN_NETWORK_TESTS") == "1",
    "teste de rede real (precedente: test_playlist_network.py)",
)
class TestProbeRedeReal(unittest.TestCase):
    def test_indisponiveis_reais_sao_rejeitados(self):
        from availability import check_availability

        saida = json.loads(
            check_availability(json.dumps([URL_INDISPONIVEL_1, URL_INDISPONIVEL_2]))
        )
        self.assertTrue(saida["success"])
        for resultado in saida["data"]["results"]:
            with self.subTest(url=resultado["url"]):
                self.assertFalse(resultado["available"])
                self.assertIn("indispon", resultado["reason"].lower())

    def test_disponivel_real_passa(self):
        from availability import check_availability

        saida = json.loads(check_availability(json.dumps([URL_DISPONIVEL])))
        (resultado,) = saida["data"]["results"]
        self.assertTrue(resultado["available"])


if __name__ == "__main__":
    unittest.main()
