"""Testes para runtime.py — verificação/atualização do yt-dlp sem repackaging.

Cobre: comparação de versão, check com/sem cache, update quando disponível,
update quando já atualizado, e integridade do meta json.
"""
import json
import os
import shutil
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import runtime


class TestVersionLogic(unittest.TestCase):
    def test_version_tuple_extrai_numeros(self):
        self.assertEqual(runtime._version_tuple("2026.08.11"), (2026, 8, 11))

    def test_version_tuple_sem_numeros(self):
        self.assertEqual(runtime._version_tuple("unknown"), (0,))

    def test_is_newer_detecta_versao_maior(self):
        self.assertTrue(runtime._is_newer("2026.08.11", "2026.01.01"))

    def test_is_newer_versao_menor(self):
        self.assertFalse(runtime._is_newer("2026.01.01", "2026.08.11"))

    def test_is_newer_versao_igual(self):
        self.assertFalse(runtime._is_newer("2026.08.11", "2026.08.11"))


class TestCheckUpdate(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp(prefix="ytdlp_test_")

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def _meta_path(self):
        return os.path.join(
            self.tmpdir, "runtime_packages", "yt_dlp_update_meta.json"
        )

    @mock.patch.object(runtime, "_get_current_yt_dlp_version", return_value="2026.01.01")
    @mock.patch.object(runtime, "_fetch_latest_yt_dlp_info", return_value={
        "latest_version": "2026.08.11",
        "package_url": "https://example.com/yt_dlp.whl",
        "package_sha256": "abc123",
    })
    def test_check_sem_cache_detecta_update(self, mock_fetch, mock_ver):
        result = json.loads(runtime.check_yt_dlp_update(self.tmpdir))
        self.assertTrue(result["success"])
        self.assertEqual(result["current_version"], "2026.01.01")
        self.assertEqual(result["latest_version"], "2026.08.11")
        self.assertTrue(result["update_available"])
        self.assertFalse(result["cached"])
        # meta gravado
        meta = json.load(open(self._meta_path()))
        self.assertEqual(meta["latest_version"], "2026.08.11")
        self.assertEqual(meta["update_available"], True)

    @mock.patch.object(runtime, "_fetch_latest_yt_dlp_info")
    def test_check_com_cache_nao_consulta_rede(self, mock_fetch):
        from datetime import datetime, timedelta, timezone
        os.makedirs(os.path.join(self.tmpdir, "runtime_packages"), exist_ok=True)
        recent = (datetime.now(timezone.utc) - timedelta(minutes=5)).isoformat()
        with open(self._meta_path(), "w") as f:
            json.dump({
                "last_check_at": recent,
                "current_version": "2026.01.01",
                "latest_version": "2026.01.01",
                "update_available": False,
            }, f)
        result = json.loads(runtime.check_yt_dlp_update(self.tmpdir))
        self.assertTrue(result["success"])
        self.assertTrue(result["cached"])
        mock_fetch.assert_not_called()

    @mock.patch.object(runtime, "_get_current_yt_dlp_version", return_value="2026.01.01")
    @mock.patch.object(runtime, "_fetch_latest_yt_dlp_info", return_value={
        "latest_version": "2026.01.01",
        "package_url": "https://example.com/yt_dlp.whl",
        "package_sha256": "abc123",
    })
    def test_check_ja_atualizado(self, mock_fetch, mock_ver):
        result = json.loads(runtime.check_yt_dlp_update(self.tmpdir))
        self.assertTrue(result["success"])
        self.assertFalse(result["update_available"])


class TestUpdateIfNeeded(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp(prefix="ytdlp_test_")

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    @mock.patch.object(runtime, "_get_current_yt_dlp_version", return_value="2026.08.11")
    @mock.patch.object(runtime, "_install_yt_dlp_package")
    @mock.patch.object(runtime, "_fetch_latest_yt_dlp_info", return_value={
        "latest_version": "2026.08.11",
        "package_url": "https://example.com/yt_dlp.whl",
        "package_sha256": "abc123",
    })
    def test_update_ja_atualizado_nao_instala(self, mock_fetch, mock_install, mock_ver):
        result = json.loads(runtime.update_yt_dlp_if_needed(self.tmpdir))
        self.assertTrue(result["success"])
        self.assertFalse(result["updated"])
        mock_install.assert_not_called()

    @mock.patch.object(runtime, "_get_current_yt_dlp_version",
                       side_effect=["2026.01.01", "2026.08.11"])
    @mock.patch.object(runtime, "_install_yt_dlp_package")
    @mock.patch.object(runtime, "_fetch_latest_yt_dlp_info", return_value={
        "latest_version": "2026.08.11",
        "package_url": "https://example.com/yt_dlp.whl",
        "package_sha256": "abc123",
    })
    def test_update_disponivel_instala_e_atualiza_meta(self, mock_fetch, mock_install, mock_ver):
        result = json.loads(runtime.update_yt_dlp_if_needed(self.tmpdir))
        self.assertTrue(result["success"])
        self.assertTrue(result["updated"])
        mock_install.assert_called_once()
        meta = json.load(open(os.path.join(
            self.tmpdir, "runtime_packages", "yt_dlp_update_meta.json")))
        self.assertEqual(meta["current_version"], "2026.08.11")
        self.assertFalse(meta["update_available"])

    @mock.patch.object(runtime, "_install_yt_dlp_package",
                       side_effect=Exception("falha de rede simulada"))
    @mock.patch.object(runtime, "_fetch_latest_yt_dlp_info", return_value={
        "latest_version": "2026.08.11",
        "package_url": "https://example.com/yt_dlp.whl",
        "package_sha256": "abc123",
    })
    def test_update_falha_retorna_payload_erro(self, mock_fetch, mock_install):
        # versão atual fixa antiga: primeiro check retorna antiga, mas a instalação falha
        with mock.patch.object(runtime, "_get_current_yt_dlp_version",
                               return_value="2026.01.01"):
            result = json.loads(runtime.update_yt_dlp_if_needed(self.tmpdir))
        self.assertFalse(result["success"])
        self.assertIn("falha de rede simulada", result["error"])
        self.assertEqual(result["stage"], "update_yt_dlp_if_needed")


if __name__ == "__main__":
    unittest.main()
