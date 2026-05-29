import logging
import traceback
import json
from datetime import datetime

class ChaquopyLogger:
    """
    Logger centralizado para o aplicativo YTDown.
    Redireciona logs para o Logcat com as tags solicitadas.
    Compatível com o protocolo de logger do yt-dlp.
    """
    
    TAGS = {
        "DEBUG": "CHAQUOPY_DEBUG",
        "DOWNLOAD": "PYTHON_DOWNLOAD",
        "FLOW": "DOWNLOAD_FLOW",
        "STORAGE": "STORAGE_DEBUG",
        "NETWORK": "NETWORK_DEBUG",
        "ERROR": "PYTHON_ERROR"
    }

    @staticmethod
    def _log(level_tag, message, category="DEBUG"):
        tag = ChaquopyLogger.TAGS.get(category, "CHAQUOPY_DEBUG")
        # No Chaquopy, o que for impresso no stdout vai para o Logcat com tag 'python.stdout'
        # Mas para facilitar o filtro adb -s, usamos o prefixo que o usuário está filtrando.
        print(f"{tag}: [{level_tag}] {message}")

    @staticmethod
    def debug(message, category="DOWNLOAD"):
        # yt-dlp envia mensagens de progresso via debug
        ChaquopyLogger._log("D", message, category)

    @staticmethod
    def info(message, category="FLOW"):
        ChaquopyLogger._log("I", message, category)

    @staticmethod
    def warning(message, category="DOWNLOAD"):
        ChaquopyLogger._log("W", message, category)

    @staticmethod
    def error(message, category="ERROR", exc_info=False):
        ChaquopyLogger._log("E", message, category)
        if exc_info:
            traceback.print_exc()

    @staticmethod
    def network(message):
        ChaquopyLogger._log("D", message, "NETWORK")

    @staticmethod
    def storage(message):
        ChaquopyLogger._log("D", message, "STORAGE")

    @staticmethod
    def download(message):
        ChaquopyLogger._log("D", message, "DOWNLOAD")

def log_failure(error, stage, **extra):
    """Gera o payload de falha e loga o erro."""
    from helpers import _failure_payload
    err_msg = str(error)
    ChaquopyLogger.error(f"Falha na etapa {stage}: {err_msg}", category="FLOW", exc_info=True)
    return _failure_payload(error, stage, **extra)
