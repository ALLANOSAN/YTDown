"""Testes para runtime.py — leitura de versão do yt_dlp runtime package."""
import os
import shutil
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import runtime

FAKE_INIT = """\
# pacote minimo que NAO expoe version attr (como o yt_dlp real)
def _noop():
    pass
"""

FAKE_VERSION_PY = """\
__version__ = "2099.01.02"
"""


class TestRuntimeVersionRead(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp(prefix="ytdlp_ver_")
        self.pkg = os.path.join(
            self.tmpdir, "runtime_packages", "yt_dlp"
        )
        os.makedirs(self.pkg, exist_ok=True)
        with open(os.path.join(self.pkg, "__init__.py"), "w") as f:
            f.write(FAKE_INIT)
        with open(os.path.join(self.pkg, "version.py"), "w") as f:
            f.write(FAKE_VERSION_PY)

    def tearDown(self):
        runtime._YT_DLP_MODULE = None
        sys.modules.pop("yt_dlp", None)
        sys.modules.pop("yt_dlp.version", None)
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def test_le_versao_do_version_py_quando_attr_nao_existe_no_init(self):
        # RED: o yt_dlp real nao expoe module.version apos spec_from_file_location
        version = runtime._get_current_yt_dlp_version(self.tmpdir)
        self.assertEqual(version, "2099.01.02")


if __name__ == "__main__":
    unittest.main()
