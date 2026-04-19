import json

from runtime import _get_yt_dlp_module
from helpers import (
    _extract_info_with_retry,
    _failure_payload,
    _is_retryable_network_error,
    _map_fetch_video_info_error_message,
)


def fetch_video_info(url, app_files_dir=None):
    ydl_opts = {
        "format": "best",
        "quiet": True,
        "no_warnings": True,
        "extract_flat": False,
        "socket_timeout": 30,
    }

    try:
        yt_dlp_module = _get_yt_dlp_module(app_files_dir)
        with yt_dlp_module.YoutubeDL(ydl_opts) as ydl:
            info = _extract_info_with_retry(
                ydl,
                url,
                download=False,
                attempts=2,
                backoff_seconds=0.8,
            )

            if info is None:
                return json.dumps(
                    {
                        "success": False,
                        "error": "Não foi possível obter informações do vídeo",
                    }
                )

            is_playlist = info.get("_type", "video") == "playlist"
            entries = None
            if is_playlist:
                entries = info.get("entries", [])

            return json.dumps(
                {
                    "success": True,
                    "data": {
                        "id": info.get("id", ""),
                        "title": info.get("title", "Sem título"),
                        "thumbnail": info.get("thumbnail", ""),
                        "duration": info.get("duration", 0),
                        "url": url,
                        "artist": info.get("artist") or info.get("uploader", ""),
                        "album": info.get("album", "YTDown"),
                        "is_playlist": is_playlist,
                        "entries": entries,
                    },
                }
            )
    except Exception as e:
        error_msg = _map_fetch_video_info_error_message(str(e))
        return json.dumps(
            _failure_payload(
                error_msg,
                stage="fetch_video_info",
                retryable=_is_retryable_network_error(e),
            )
        )
