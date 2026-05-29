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
        "format": "bestaudio/best",
        "quiet": True,
        "no_warnings": True,
        "extract_flat": True,  # ✅ IMPORTANTE: Não extrai detalhes de cada item da playlist (evita timeout)
        "noplaylist": True,    # ✅ Se for um vídeo com Mix, ignora o restante da rádio
        "socket_timeout": 20,
        "retries": 2,
        "fragment_retries": 3,
        "http_headers": {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        },
        # Simular navegador Chrome para evitar bloqueios do YouTube
        "extractor_args": {
            "youtube": {
                "player_client": ["android", "web"],
            }
        },
    }

    try:
        yt_dlp_module = _get_yt_dlp_module(app_files_dir)
        with yt_dlp_module.YoutubeDL(ydl_opts) as ydl:
            info = _extract_info_with_retry(
                ydl,
                url,
                download=False,
                attempts=2,
                backoff_seconds=1.0,
            )

            if info is None:
                return json.dumps(
                    {
                        "success": False,
                        "error": "Não foi possível obter informações do vídeo",
                    }
                )

            # Tenta pegar o título do vídeo de várias formas
            video_title = info.get("title") or info.get("display_id") or "Sem título"

            is_playlist = info.get("_type", "video") == "playlist"
            entries = []
            if is_playlist:
                # Se for playlist, o título principal é o da playlist
                for entry in info.get("entries", []) or []:
                    if not entry:
                        continue
                    entries.append(
                        {
                            "id": entry.get("id", ""),
                            "title": entry.get("title") or entry.get("display_id") or "Sem título",
                            "thumbnail": entry.get("thumbnail", ""),
                            "duration": entry.get("duration", 0),
                            "url": entry.get("webpage_url") or entry.get("url") or f"https://www.youtube.com/watch?v={entry.get('id')}",
                            "artist": entry.get("artist") or entry.get("uploader", ""),
                            "album": entry.get("album", "YTDown"),
                        }
                    )
            else:
                formats = []
                for f in info.get("formats", []):
                    formats.append({
                        "format_id": f.get("format_id"),
                        "ext": f.get("ext"),
                        "height": f.get("height"),
                        "note": f.get("format_note"),
                        "vcodec": f.get("vcodec"),
                        "acodec": f.get("acodec"),
                        "filesize": f.get("filesize"),
                        "tbr": f.get("tbr")
                    })

                entries.append(
                    {
                        "id": info.get("id", ""),
                        "title": video_title,
                        "thumbnail": info.get("thumbnail", ""),
                        "duration": info.get("duration", 0),
                        "url": url,
                        "artist": info.get("artist") or info.get("uploader", ""),
                        "album": info.get("album", "YTDown"),
                    }
                )

            return json.dumps(
                {
                    "success": True,
                    "data": {
                        "id": info.get("id", ""),
                        "title": video_title,
                        "thumbnail": info.get("thumbnail", ""),
                        "duration": info.get("duration", 0),
                        "url": url,
                        "artist": info.get("artist") or info.get("uploader", ""),
                        "album": info.get("album", "YTDown"),
                        "is_playlist": is_playlist,
                        "entries": entries,
                        "formats": formats if not is_playlist else []
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
