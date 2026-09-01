#!/usr/bin/env python3
"""Testes de unidade do common.py. Rodam sem aparelho — o adb e substituido por fake.

Motivo de existir: os scripts fixavam "com.example.ytdown", mas o build debug
instala "com.example.ytdown.native" (applicationIdSuffix ".native"). A suite
inteira apontava para um pacote que nao existe no aparelho.
"""
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import common


class TestResolvePackage(unittest.TestCase):
    def test_prefere_o_sufixo_native_quando_os_dois_estao_instalados(self):
        instalados = "package:com.example.ytdown\npackage:com.example.ytdown.native\n"
        with mock.patch.object(common, "run_adb", return_value=(True, instalados)):
            self.assertEqual("com.example.ytdown.native", common.resolve_package("com.example.ytdown"))

    def test_cai_para_o_pacote_base_quando_so_ele_existe(self):
        with mock.patch.object(common, "run_adb", return_value=(True, "package:com.example.ytdown\n")):
            self.assertEqual("com.example.ytdown", common.resolve_package("com.example.ytdown"))

    def test_devolve_none_quando_nada_esta_instalado(self):
        with mock.patch.object(common, "run_adb", return_value=(True, "")):
            self.assertIsNone(common.resolve_package("com.example.ytdown"))

    def test_nao_confunde_pacote_de_outro_app_com_prefixo_igual(self):
        instalados = "package:com.example.ytdownloader\n"
        with mock.patch.object(common, "run_adb", return_value=(True, instalados)):
            self.assertIsNone(common.resolve_package("com.example.ytdown"))

    def test_adb_falhando_devolve_none_em_vez_de_estourar(self):
        with mock.patch.object(common, "run_adb", return_value=(False, "no devices")):
            self.assertIsNone(common.resolve_package("com.example.ytdown"))


class TestCheckPackageInstalled(unittest.TestCase):
    def test_nao_casa_por_substring(self):
        """pm list | grep casa 'ytdownloader' quando se procura 'ytdown'."""
        with mock.patch.object(common, "run_adb", return_value=(True, "package:com.example.ytdownloader\n")):
            self.assertFalse(common.check_package_installed("com.example.ytdown"))

    def test_casa_o_pacote_exato(self):
        with mock.patch.object(common, "run_adb", return_value=(True, "package:com.example.ytdown\n")):
            self.assertTrue(common.check_package_installed("com.example.ytdown"))


class TestStartAppActivity(unittest.TestCase):
    """O applicationIdSuffix muda o pacote, nao o nome da classe da Activity.

    Com "com.example.ytdown.native", a forma relativa "/.MainActivity" resolve para
    com.example.ytdown.native.MainActivity — classe que nao existe. Precisa do
    nome absoluto da Activity.
    """

    def test_usa_o_nome_absoluto_da_activity_e_nao_a_forma_relativa(self):
        capturado = {}

        def fake(cmd, timeout=30):
            capturado["cmd"] = cmd
            return True, "Starting: Intent"

        with mock.patch.object(common, "run_adb", side_effect=fake):
            common.start_app_activity("com.example.ytdown.native",
                                      "com.example.ytdown.MainActivity")

        self.assertIn("com.example.ytdown.native/com.example.ytdown.MainActivity",
                      capturado["cmd"])
        self.assertNotIn("/.com.example", capturado["cmd"])

    def test_nome_relativo_continua_funcionando_para_quem_ja_usava(self):
        capturado = {}

        def fake(cmd, timeout=30):
            capturado["cmd"] = cmd
            return True, ""

        with mock.patch.object(common, "run_adb", side_effect=fake):
            common.start_app_activity("com.exemplo.app", "MainActivity")

        self.assertIn("com.exemplo.app/.MainActivity", capturado["cmd"])

    def test_am_start_que_imprime_Error_nao_conta_como_sucesso(self):
        saida = "Starting: Intent { ... }\nError type 3\nError: Activity class does not exist."
        with mock.patch.object(common, "run_adb", return_value=(True, saida)):
            self.assertFalse(common.start_app_activity("com.x", "MainActivity"))


class TestGetFocusedPackage(unittest.TestCase):
    """O check de "UI visivel" usava `dumpsys window windows | grep mCurrentFocus`.

    No Android 16 o subcomando `windows` nao imprime mais mCurrentFocus: a saida
    vem vazia e o teste reprovava um app que estava em foco. `dumpsys window`
    sozinho continua imprimindo.
    """

    SAIDA_REAL = (
        "  mCurrentFocus=Window{da273a0 u0 "
        "com.example.ytdown.native/com.example.ytdown.MainActivity}\n"
        "  mFocusedApp=ActivityRecord{76241417 u0 "
        "com.example.ytdown.native/com.example.ytdown.MainActivity t8144}\n"
    )

    def test_extrai_o_pacote_da_linha_de_foco(self):
        with mock.patch.object(common, "run_adb", return_value=(True, self.SAIDA_REAL)):
            self.assertEqual("com.example.ytdown.native", common.get_focused_package())

    def test_nao_usa_o_subcomando_windows_que_quebrou_no_android_16(self):
        capturado = {}

        def fake(cmd, timeout=30):
            capturado["cmd"] = cmd
            return True, self.SAIDA_REAL

        with mock.patch.object(common, "run_adb", side_effect=fake):
            common.get_focused_package()

        self.assertNotIn("dumpsys window windows", capturado["cmd"])

    def test_devolve_none_quando_nao_ha_linha_de_foco(self):
        with mock.patch.object(common, "run_adb", return_value=(True, "")):
            self.assertIsNone(common.get_focused_package())

    def test_tela_de_bloqueio_sem_activity_nao_vira_pacote(self):
        """Sem '/' nao ha componente: NotificationShade nao e nome de pacote."""
        saida = "  mCurrentFocus=Window{abc u0 NotificationShade}\n"
        with mock.patch.object(common, "run_adb", return_value=(True, saida)):
            self.assertIsNone(common.get_focused_package())


class TestGetAppLogcat(unittest.TestCase):
    """get_logcat filtrava por nome de pacote, que o logcat nunca imprime inteiro.

    O logcat trunca o nome do processo em 15 caracteres: "com.example.ytdown.native"
    aparece como "e.ytdown.native". Filtrar pelo nome completo devolvia vazio sempre,
    e a suite reportava "No recent logs found" com o app rodando normalmente.
    Filtrar por PID e exato e imune ao truncamento.
    """

    def test_filtra_por_pid_e_nao_pelo_nome_do_pacote(self):
        comandos = []

        def fake(cmd, timeout=30):
            comandos.append(cmd)
            if "pidof" in cmd:
                return True, "8534\n"
            return True, "09-01 16:00:00.000  8534  8534 D BassCore: init ok\n"

        with mock.patch.object(common, "run_adb", side_effect=fake):
            saida = common.get_app_logcat("com.example.ytdown.native", lines=50)

        logcat = [c for c in comandos if "logcat" in c][0]
        self.assertIn("--pid=8534", logcat)
        self.assertNotIn("grep", logcat)
        self.assertIn("BassCore", saida)

    def test_app_parado_devolve_vazio_em_vez_de_estourar(self):
        with mock.patch.object(common, "run_adb", return_value=(True, "")):
            self.assertEqual("", common.get_app_logcat("com.example.ytdown.native"))

    def test_pidof_com_varios_pids_usa_o_primeiro(self):
        """Servico em processo separado faz o pidof devolver dois PIDs."""
        def fake(cmd, timeout=30):
            if "pidof" in cmd:
                return True, "8534 8600\n"
            return True, "linha"

        with mock.patch.object(common, "run_adb", side_effect=fake):
            self.assertEqual("linha", common.get_app_logcat("com.x"))


class TestDumpUiHierarchy(unittest.TestCase):
    """O check de input afirmava sobre a saida do `adb pull`, nao sobre o XML.

    `adb pull` imprime "1 file pulled..." — procurar "ytdown" ali nunca acha nada
    util, e o resultado nao dizia se a UI tinha sido lida. Ler o arquivo no
    aparelho devolve o XML de verdade.
    """

    def test_devolve_o_xml_e_nao_a_saida_do_adb_pull(self):
        xml = '<?xml version="1.0"?><hierarchy><node text="Buscar"/></hierarchy>'

        def fake(cmd, timeout=30):
            if "uiautomator dump" in cmd:
                return True, "UI hierchary dumped to: /sdcard/ui_dump.xml"
            if "cat" in cmd:
                return True, xml
            return False, ""

        with mock.patch.object(common, "run_adb", side_effect=fake):
            self.assertEqual(xml, common.dump_ui_hierarchy())

    def test_dump_que_falha_devolve_none(self):
        with mock.patch.object(common, "run_adb", return_value=(False, "erro")):
            self.assertIsNone(common.dump_ui_hierarchy())


class TestResumirChecks(unittest.TestCase):
    """Um unico check falsy reprovava a suite inteira, mesmo sendo informativo.

    `lock_screen_show_media` e preferencia do usuario e sai como "null" quando
    nunca foi tocada; "app tem log recente" depende de o app ter feito algo nos
    ultimos segundos. Nenhum dos dois diz se o app esta quebrado, mas os dois
    derrubavam o exit code e a suite reportava falha com o app saudavel.
    """

    def test_informativo_falso_nao_reprova(self):
        self.assertTrue(
            common.resumir_checks(
                obrigatorios={"abriu": True},
                informativos={"lock_screen": False},
            )
        )

    def test_obrigatorio_falso_reprova(self):
        self.assertFalse(
            common.resumir_checks(
                obrigatorios={"abriu": False},
                informativos={"lock_screen": True},
            )
        )

    def test_sem_obrigatorio_algum_nao_passa_por_vacuidade(self):
        """Suite sem nenhum check obrigatorio nao provou nada — nao pode dar verde."""
        self.assertFalse(common.resumir_checks(obrigatorios={}, informativos={"x": True}))

    def test_informativo_nao_e_rotulado_como_WARN(self, ):
        import io
        from contextlib import redirect_stdout
        buffer = io.StringIO()
        with redirect_stdout(buffer):
            common.resumir_checks(obrigatorios={"abriu": True},
                                  informativos={"lock_screen": False})
        saida = buffer.getvalue()
        self.assertIn("INFO", saida)
        self.assertNotIn("WARN", saida)


if __name__ == "__main__":
    unittest.main(verbosity=2)
