"""Golden master do contrato entre Python e Kotlin.

Congela o que o Kotlin depende: nomes exportados, formato de retorno e as chaves
dos payloads. Se alguem mudar uma assinatura ou trocar uma chave de JSON, quebra
aqui e nao em producao.
"""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import ytdown
from helpers import _failure_payload

# Os 8 pontos que o Kotlin chama hoje (YtDlpWrapper.kt e PythonMetadataBridge.kt).
# search_metadata saiu junto com enrich.py: o enriquecimento passou a ser feito
# pelo MusicBrainzService/MediaImportProcessor do lado Kotlin, que traz também
# faixa, disco, ano e os IDs da capa — coisas que o enrich.py não devolvia.
EXPORTS_ESPERADOS = {
    "download_video",
    "fetch_video_info",
    "rewrite_file_metadata",
    "check_yt_dlp_update",
    "update_yt_dlp_if_needed",
    "get_band_details",
    "get_similar_bands",
    "get_band_albums",
}

# Chamados direto no modulo `metadata`, sem passar pela fachada ytdown.
EXPORTS_METADATA = {
    "rewrite_file_metadata",
    "read_file_metadata",
    "embed_album_art",
    "extract_embedded_artwork",
    "extract_metadata_from_filename",
}


class TestFachadaPublica(unittest.TestCase):
    def test_ytdown_exporta_exatamente_o_que_o_kotlin_chama(self):
        self.assertEqual(EXPORTS_ESPERADOS, set(ytdown.__all__))

    def test_todo_export_existe_e_e_chamavel(self):
        for nome in ytdown.__all__:
            with self.subTest(funcao=nome):
                self.assertTrue(hasattr(ytdown, nome), f"{nome} nao existe em ytdown")
                self.assertTrue(callable(getattr(ytdown, nome)), f"{nome} nao e chamavel")

    def test_modulo_metadata_expoe_o_que_o_PythonMetadataBridge_chama(self):
        import metadata

        for nome in EXPORTS_METADATA:
            with self.subTest(funcao=nome):
                self.assertTrue(
                    callable(getattr(metadata, nome, None)),
                    f"metadata.{nome} sumiu — PythonMetadataBridge.kt quebra",
                )


class TestPayloadDeFalha(unittest.TestCase):
    """O Kotlin faz JSONObject(retorno).optBoolean("success").

    _failure_payload devolve dict de proposito (helpers.py:14): quem chama serializa.
    Serializar aqui causaria JSON dentro de string JSON. Este teste tranca as duas
    metades do contrato: o dict daqui e o fato de ele sobreviver ao json.dumps.
    """

    def test_failure_payload_devolve_dict_e_nao_string(self):
        payload = _failure_payload("deu ruim", stage="extract_info", retryable=True)
        self.assertIsInstance(payload, dict, "serializar aqui causaria double-encoding")

    def test_failure_payload_tem_as_chaves_que_o_kotlin_le(self):
        payload = _failure_payload("deu ruim", stage="extract_info", retryable=True)
        self.assertIs(False, payload["success"])
        self.assertEqual("deu ruim", payload["error"])
        self.assertEqual("extract_info", payload["stage"])
        self.assertIs(True, payload["retryable"])

    def test_failure_payload_sobrevive_ao_json_dumps_do_chamador(self):
        payload = _failure_payload("x", stage="download", retryable=False, filename="a.mp3")
        redondo = json.loads(json.dumps(payload))
        self.assertEqual("a.mp3", redondo["filename"])
        self.assertIs(False, redondo["success"])

    def test_erro_nao_string_vira_string(self):
        payload = _failure_payload(ValueError("objeto de excecao"), stage="x")
        self.assertEqual("objeto de excecao", payload["error"])

    def test_retryable_e_falso_por_padrao(self):
        self.assertIs(False, _failure_payload("x", stage="y")["retryable"])


class TestResolucaoDoFFmpeg(unittest.TestCase):
    """Android 10+ so executa binario que esteja em nativeLibraryDir como lib*.so.

    O empacotamento mudou no AGP 9.3 (libs passaram a ser comprimidas). Este teste
    trava a regra de nomenclatura que o app depende.
    """

    @staticmethod
    def _criar_binario(diretorio, nome):
        caminho = os.path.join(diretorio, nome)
        with open(caminho, "wb") as f:
            f.write(b"\x7fELF")
        os.chmod(caminho, 0o755)
        return caminho

    def test_prefere_lib_ffmpeg_exe_so_no_diretorio_de_libs_nativas(self):
        import tempfile

        from download import _resolve_ffmpeg_binary

        with tempfile.TemporaryDirectory() as libdir:
            alvo = self._criar_binario(libdir, "libffmpeg_exe.so")
            self.assertEqual(alvo, _resolve_ffmpeg_binary(libdir, None))

    def test_aceita_libffmpeg_so_quando_nao_ha_o_sufixo_exe(self):
        import tempfile

        from download import _resolve_ffmpeg_binary

        with tempfile.TemporaryDirectory() as libdir:
            alvo = self._criar_binario(libdir, "libffmpeg.so")
            self.assertEqual(alvo, _resolve_ffmpeg_binary(libdir, None))

    def test_lib_exe_so_ganha_de_libffmpeg_so(self):
        import tempfile

        from download import _resolve_ffmpeg_binary

        with tempfile.TemporaryDirectory() as libdir:
            preferido = self._criar_binario(libdir, "libffmpeg_exe.so")
            self._criar_binario(libdir, "libffmpeg.so")
            self.assertEqual(preferido, _resolve_ffmpeg_binary(libdir, None))

    def test_cai_para_app_files_quando_nao_ha_lib_nativa(self):
        import tempfile

        from download import _resolve_ffmpeg_binary

        with tempfile.TemporaryDirectory() as libdir, tempfile.TemporaryDirectory() as filesdir:
            alvo = self._criar_binario(filesdir, "ffmpeg")
            self.assertEqual(alvo, _resolve_ffmpeg_binary(libdir, filesdir))

    def test_ignora_fallback_sem_permissao_de_execucao(self):
        import tempfile

        from download import _resolve_ffmpeg_binary

        with tempfile.TemporaryDirectory() as libdir, tempfile.TemporaryDirectory() as filesdir:
            caminho = self._criar_binario(filesdir, "ffmpeg")
            os.chmod(caminho, 0o644)
            self.assertIsNone(_resolve_ffmpeg_binary(libdir, filesdir))

    def test_sem_binario_nenhum_devolve_none_sem_estourar(self):
        import tempfile

        from download import _resolve_ffmpeg_binary

        with tempfile.TemporaryDirectory() as libdir:
            self.assertIsNone(_resolve_ffmpeg_binary(libdir, None))


if __name__ == "__main__":
    unittest.main(verbosity=2)
