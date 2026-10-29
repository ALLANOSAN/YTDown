"""Probe de disponibilidade pre-enfileiramento.

Motivo: a fila recebia videos indisponiveis (privados/excluidos/bloqueados)
e eles so falhavam no worker, um a um, na etapa extract_info — com mensagem
generica e tempo de fila desperdicado. Igual ao Snaptube, que sabe ANTES de
baixar: este probe roda extract_info rapido (process=False, sem download,
~1s por URL, observado ao vivo em 29/09/2026) e devolve quem pode agendar.

Regra de seguranca: so marca indisponivel o que e DEFINITIVO (video
indisponivel/privado/excluido). Erro transiente (timeout/rede) ou
desconhecido mantem available=True — o worker decide nesses casos.
"""
import json

from helpers import (
    _apply_cookies_file,
    _failure_payload,
    _is_retryable_network_error,
)
from logger import ChaquopyLogger
from runtime import _get_yt_dlp_module


def _motivo_indisponivel(mensagem):
    """Devolve o motivo em portugues, ou None se nao for caso definitivo."""
    texto = str(mensagem).lower()
    if "sign in to confirm" in texto:
        return "YouTube exigiu login (bot-check) — importe cookies.txt"
    if "private video" in texto:
        return "Vídeo privado"
    if "deleted video" in texto:
        return "Vídeo excluído"
    if "not available" in texto or "has been removed" in texto:
        return "Vídeo indisponível"
    if "age" in texto and ("confirm" in texto or "restrict" in texto):
        return "Vídeo com restrição de idade"
    return None


def _sondar(ydl, url):
    try:
        ydl.extract_info(url, download=False, process=False)
        return {"url": url, "available": True, "reason": None}
    except Exception as e:
        if _is_retryable_network_error(e):
            ChaquopyLogger.debug(
                f"Probe transiente para {url}: {e} — mantendo na fila",
                category="FLOW",
            )
            return {"url": url, "available": True, "reason": None}
        motivo = _motivo_indisponivel(str(e))
        if motivo is None:
            ChaquopyLogger.debug(
                f"Probe inconclusivo para {url}: {e} — mantendo na fila",
                category="FLOW",
            )
            return {"url": url, "available": True, "reason": None}
        return {"url": url, "available": False, "reason": motivo}


def _fabrica_padrao(app_files_dir):
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        # Probe e por URL individual: nunca expandir playlist aqui.
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 1,
        "http_headers": {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        },
        "extractor_args": {
            "youtube": {
                "player_client": ["android", "web"],
            }
        },
    }
    _apply_cookies_file(ydl_opts, app_files_dir)
    return _get_yt_dlp_module(app_files_dir).YoutubeDL(ydl_opts)


def check_availability(urls_json, app_files_dir=None, _ydl_factory=None):
    """Sonda disponibilidade de cada URL. Retorna JSON (padrao da fachada)."""
    try:
        urls = json.loads(urls_json) if isinstance(urls_json, str) else list(urls_json)
    except Exception:
        return json.dumps(
            _failure_payload("lista de URLs inválida", stage="check_availability_parse")
        )
    try:
        fabrica = _ydl_factory or _fabrica_padrao
        with fabrica(app_files_dir) as ydl:
            resultados = [_sondar(ydl, url) for url in urls]
        return json.dumps({"success": True, "data": {"results": resultados}})
    except Exception as e:
        return json.dumps(
            _failure_payload(str(e), stage="check_availability", retryable=True)
        )
