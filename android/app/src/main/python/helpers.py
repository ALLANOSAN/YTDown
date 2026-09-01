import os
import re
import time
from datetime import datetime
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlparse
from urllib.request import Request, urlopen

_MAX_THUMBNAIL_BYTES = 6 * 1024 * 1024

def _failure_payload(error, stage, retryable=False, **extra):
    """
    Cria um payload de erro padronizado para retorno JSON.
    IMPORTANTE: retorna dict — quem chama deve usar json.dumps() no resultado final.
    Não serializar aqui evita double-encoding (JSON dentro de JSON string).
    """
    payload = {
        "success": False,
        "error": str(error),
        "stage": stage,
        "retryable": retryable,
    }
    payload.update(extra)
    return payload


import traceback
import logging
import os

# Configuração de logs persistentes
LOG_FILE = "/data/user/0/com.example.ytdown/files/python_errors.log"

def report_error_to_firebase(error, stage=None, **extra):
    """
    Registra erros em log local persistente e formata para o ObservabilityService capturar via PythonBridge.
    """
    timestamp = datetime.now().isoformat()
    error_msg = f"{timestamp} | Stage: {stage} | Error: {str(error)} | Extra: {str(extra)}"
    
    # Log local
    try:
        with open(LOG_FILE, "a") as f:
            f.write(error_msg + "\n" + traceback.format_exc() + "\n---\n")
    except:
        pass

    # Print para o logcat ser capturado pela ponte Kotlin
    print(f"[FIREBASE_REPORT] {error_msg}")
    
    import json
    return json.dumps(_failure_payload(error, stage, **extra))


def _is_retryable_network_error(error):
    if isinstance(error, HTTPError):
        return error.code in {408, 429} or error.code >= 500

    if isinstance(error, (URLError, TimeoutError)):
        return True

    text = str(error).lower()
    retry_tokens = (
        "timeout",
        "timed out",
        "temporarily unavailable",
        "temporary failure",
        "connection reset",
        "connection aborted",
        "connection refused",
        "remote end closed",
        "network is unreachable",
        "name or service not known",
        "too many requests",
        "429",
        "503",
    )
    return any(token in text for token in retry_tokens)


def _retry_backoff_seconds(attempt_index, base_seconds):
    return max(0.0, float(base_seconds)) * (2 ** max(0, attempt_index - 1))


def _download_url_bytes(
    url,
    *,
    timeout,
    attempts=3,
    backoff_seconds=0.6,
    max_bytes=None,
    label="request",
):
    last_error = None
    total_attempts = max(1, int(attempts))

    for attempt in range(1, total_attempts + 1):
        try:
            request = Request(url, headers={"User-Agent": "YTDown/1.0"})
            with urlopen(request, timeout=timeout) as response:
                chunks = []
                total = 0
                while True:
                    chunk = response.read(8192)
                    if not chunk:
                        break
                    total += len(chunk)
                    if max_bytes and total > int(max_bytes):
                        raise Exception(
                            f"Resposta excedeu limite permitido ({max_bytes} bytes)"
                        )
                    chunks.append(chunk)
                return b"".join(chunks)
        except Exception as e:
            last_error = e
            retryable = _is_retryable_network_error(e)
            if attempt >= total_attempts or not retryable:
                break

            delay = _retry_backoff_seconds(attempt, backoff_seconds)
            print(
                f"⚠️ Falha de rede ({label}), tentativa {attempt}/{total_attempts}: {str(e)}"
            )
            if delay > 0:
                time.sleep(delay)

    if last_error is not None:
        raise last_error
    raise Exception(f"Falha de rede em {label}")


def _extract_info_with_retry(
    ydl,
    url,
    *,
    download,
    attempts=2,
    backoff_seconds=0.6,
):
    last_error = None
    total_attempts = max(1, int(attempts))

    for attempt in range(1, total_attempts + 1):
        try:
            return ydl.extract_info(url, download=download)
        except Exception as e:
            last_error = e
            retryable = _is_retryable_network_error(e)
            if attempt >= total_attempts or not retryable:
                break

            delay = _retry_backoff_seconds(attempt, backoff_seconds)
            print(
                f"⚠️ Falha transitória ao extrair info, tentativa {attempt}/{total_attempts}: {str(e)}"
            )
            if delay > 0:
                time.sleep(delay)

    if last_error is not None:
        raise last_error
    raise Exception("Falha ao extrair info do yt-dlp")


def _parse_iso_datetime(value):
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value))
    except Exception:
        return None


def _normalize_text(value):
    if value is None:
        return ""
    text = str(value).strip()
    text = re.sub(r"\s+", " ", text)
    return text


def _strip_generated_suffix(value):
    text = _normalize_text(value)
    if not text:
        return ""

    # UUID antes do hex genérico: o genérico casa os 12 hex finais do UUID e
    # deixa o resto do UUID no título, sem o padrão de UUID nunca mais casar.
    text = re.sub(
        r"[_-][0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        "",
        text,
        flags=re.IGNORECASE,
    )
    text = re.sub(r"[_-][0-9a-f]{6,}$", "", text, flags=re.IGNORECASE)

    text = text.replace("_", " ")
    return _normalize_text(text)


def _is_unknown_label(value):
    normalized = _normalize_text(value).lower()
    return normalized in {
        "",
        "unknown",
        "unknown artist",
        "desconhecido",
        "artista desconhecido",
        "sem artista",
        "sem título",
        "sem titulo",
        "videoplayback",
        "n/a",
    }


def _guess_artist_from_title(title):
    normalized = _strip_generated_suffix(title)
    if not normalized:
        return ""

    for separator in [" - ", " – ", " — ", " | ", " by ", " / "]:
        if separator in normalized:
            candidate = _normalize_text(normalized.split(separator, 1)[0])
            if candidate and len(candidate) >= 2 and not _is_unknown_label(candidate):
                return candidate

    return ""


def _first_resolved_metadata_value(candidates):
    for candidate in candidates:
        normalized = _strip_generated_suffix(candidate)
        if normalized and not _is_unknown_label(normalized):
            return normalized
    return ""


def _resolve_metadata(title, artist, album, info):
    resolved_title = _strip_generated_suffix(title or info.get("title") or "Sem título")
    if not resolved_title or _is_unknown_label(resolved_title):
        resolved_title = "Sem título"

    artist_candidates = [
        artist,
        info.get("artist"),
        info.get("uploader"),
        info.get("channel"),
        info.get("creator"),
        info.get("playlist_uploader"),
        info.get("uploader_id"),
        _guess_artist_from_title(resolved_title),
    ]
    resolved_artist = _first_resolved_metadata_value(artist_candidates)

    if not resolved_artist:
        title_artist_fallback = _guess_artist_from_title(resolved_title)
        if title_artist_fallback and not _is_unknown_label(title_artist_fallback):
            resolved_artist = title_artist_fallback
    if not resolved_artist and resolved_title and not _is_unknown_label(resolved_title):
        resolved_artist = resolved_title
    if not resolved_artist:
        resolved_artist = "YTDown"

    album_candidates = [
        album,
        info.get("album"),
        info.get("playlist_title"),
    ]
    resolved_album = _first_resolved_metadata_value(album_candidates)
    if not resolved_album:
        resolved_album = "YTDown"

    return resolved_title, resolved_artist, resolved_album


# Mix/rádio gerados pelo YouTube (list=RD...) são infinitos: o link
# compartilhado de um Mix deve baixar só o vídeo clicado, nunca a "playlist".
_PREFIXOS_DE_RADIO = ("RD",)


def _extrair_id_da_playlist(url):
    """Devolve o valor do parâmetro list= da URL, ou None se não houver."""
    if not url:
        return None
    try:
        query = parse_qs(urlparse(url).query)
    except ValueError:
        return None
    valores = query.get("list") or []
    for valor in valores:
        if valor:
            return valor
    return None


def _is_pure_playlist_url(url):
    """
    True para URL que aponta só para a playlist (/playlist?list=X), sem vídeo.

    Esse formato ignora noplaylist=True: o yt-dlp expande as N faixas de
    qualquer jeito. Como o download grava num output_path único, isso faria
    as faixas se sobrescreverem em silêncio.
    """
    if not _extrair_id_da_playlist(url):
        return False
    try:
        query = parse_qs(urlparse(url).query)
    except ValueError:
        return False
    return not any(query.get("v") or [])


def _should_expand_playlist(url):
    """
    Decide se a URL deve baixar a playlist inteira ou só o vídeo.

    Link compartilhado de álbum/playlist (watch?v=X&list=OLAK5uy_... ou &list=PL...)
    expande para todas as faixas. Mix/rádio (list=RD...) não — o YouTube gera
    entradas indefinidamente a partir do vídeo clicado.
    """
    playlist_id = _extrair_id_da_playlist(url)
    if not playlist_id:
        return False
    return not playlist_id.upper().startswith(_PREFIXOS_DE_RADIO)


def _map_fetch_video_info_error_message(error_msg):
    normalized = str(error_msg)
    if "Sign in to confirm" in normalized:
        return "YouTube bloqueou a requisição (Bot check)"
    if "Video unavailable" in normalized:
        return "Vídeo indisponível"
    if "confirm your age" in normalized:
        return "Vídeo com restrição de idade"
    return normalized


def _resolve_explicit_title_for_rewrite(value, fallback_title):
    normalized = _strip_generated_suffix(value)
    if normalized:
        return normalized
    return fallback_title


def _resolve_explicit_metadata_for_rewrite(value):
    normalized = _strip_generated_suffix(value)
    if normalized:
        return normalized
    return str(value).strip()


def _resolve_rewrite_metadata_value(explicit_value, fallback_value, resolver):
    if explicit_value is None:
        return fallback_value
    return resolver(explicit_value)


def _guess_image_mime(url, data):
    lowered = (url or "").lower()
    if lowered.endswith(".png"):
        return "image/png"
    if lowered.endswith(".webp"):
        return "image/webp"
    if lowered.endswith(".jpg") or lowered.endswith(".jpeg"):
        return "image/jpeg"

    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        return "image/webp"
    return "image/jpeg"


def _looks_like_image(data):
    """
    Confere a assinatura binária do conteúdo.

    A capa chega como uma string só, que tanto pode ser um caminho do cache do
    app quanto uma URL vinda de metadado remoto — a função não distingue as
    duas. A garantia então é sobre o CONTEÚDO: sem isso, qualquer caminho local
    (cookies.txt, banco) virava "capa" embutida no arquivo do usuário, e uma
    página de erro HTTP entrava como image/jpeg.
    """
    if not data or len(data) < 12:
        return False
    if data.startswith(b"\xff\xd8\xff"):  # JPEG
        return True
    if data.startswith(b"\x89PNG\r\n\x1a\n"):  # PNG
        return True
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":  # WebP
        return True
    if data.startswith(b"GIF87a") or data.startswith(b"GIF89a"):  # GIF
        return True
    if data.startswith(b"BM"):  # BMP
        return True
    return False


def _download_thumbnail_bytes(thumbnail_url):
    if not thumbnail_url:
        return None

    source = str(thumbnail_url).strip()

    def _read_local_bytes(local_path):
        if not local_path:
            return None
        if not os.path.isfile(local_path):
            print(f"⚠️ Arquivo de capa não encontrado: {local_path}")
            return None

        try:
            file_size = os.path.getsize(local_path)
            if file_size > _MAX_THUMBNAIL_BYTES:
                print(
                    f"⚠️ Capa local excede limite ({file_size} bytes > {_MAX_THUMBNAIL_BYTES})"
                )
                return None

            with open(local_path, "rb") as f:
                data = f.read(_MAX_THUMBNAIL_BYTES + 1)
            if len(data) > _MAX_THUMBNAIL_BYTES:
                print("⚠️ Capa local excede limite ao ler bytes")
                return None
            if not data:
                return None
            return data
        except Exception as e:
            print(f"⚠️ Falha ao ler capa local: {str(e)}")
            return None

    try:
        parsed = urlparse(source)
        scheme = (parsed.scheme or "").lower()
    except Exception as e:
        print(f"⚠️ Falha ao interpretar origem da capa: {str(e)}")
        return None

    # Caminho absoluto sem esquema = arquivo do cache de capas do app.
    if not scheme and os.path.isabs(source):
        local_data = _read_local_bytes(source)
        if not _looks_like_image(local_data):
            print("⚠️ Arquivo de capa local não é imagem — ignorado")
            return None
        return local_data

    # Só http(s) vai para a rede. urllib resolve file://, data: e ftp://
    # nativamente, o que transformaria esta função em leitor de arquivo
    # arbitrário caso a origem viesse de metadado remoto.
    if scheme not in ("http", "https"):
        print(f"⚠️ Origem de capa com esquema não suportado: {scheme or '(nenhum)'}")
        return None

    try:
        data = _download_url_bytes(
            source,
            timeout=8,
            attempts=2,
            backoff_seconds=0.5,
            max_bytes=_MAX_THUMBNAIL_BYTES,
            label="thumbnail_download",
        )
    except Exception as e:
        print(f"⚠️ Não foi possível baixar thumbnail para APIC: {str(e)}")
        return None

    if not _looks_like_image(data):
        print("⚠️ Resposta da capa não é imagem — ignorada")
        return None
    return data


def _set_vorbis_like_tags(audio, title, artist, album):
    audio["title"] = [str(title)]
    audio["artist"] = [str(artist)]
    audio["album"] = [str(album)]


def _verify_vorbis_like_tags(audio):
    if audio is None:
        return False
    title = audio.get("title")
    artist = audio.get("artist")
    return bool(title) and bool(artist)


def _write_vorbis_like_tags_for_file(
    filepath,
    title,
    artist,
    album,
    format_label,
    loader,
):
    audio = loader(filepath)
    _set_vorbis_like_tags(audio, title, artist, album)
    audio.save()

    verify = loader(filepath)
    ok = _verify_vorbis_like_tags(verify)
    if ok:
        print(f"✅ Tags {format_label} injetadas")
    return ok


def _normalize_tag_value(value):
    if value is None:
        return ""
    if isinstance(value, (list, tuple)):
        if not value:
            return ""
        return _normalize_tag_value(value[0])
    if isinstance(value, bytes):
        try:
            value = value.decode("utf-8", errors="ignore")
        except Exception:
            value = str(value)
    text = str(value).strip()
    return text.casefold()


def _apply_cookies_file(ydl_opts, app_files_dir):
    """Aponta o yt-dlp para cookies.txt da conta logada, se existir.

    O yt-dlp suporta `cookiefile` nativamente; com cookies do usuário o
    YouTube deixa de esconder videos que nega a acesso anonimo
    ("Video unavailable" em playlists).

    So aplica se o arquivo for formato Netscape valido: arquivo corrompido/
    truncado (import quebrado) faz o yt-dlp FALHAR TODA a playlist com
    "does not look like a Netscape format cookies file".
    """
    if not app_files_dir:
        return
    cookies_path = os.path.join(app_files_dir, "cookies.txt")
    if not os.path.isfile(cookies_path):
        return
    try:
        with open(cookies_path, "rb") as f:
            head = f.read(1024)
    except OSError:
        return
    # Formato Netscape: primeira linha nao-vazia deve ser o header oficial.
    # yt-dlp exige exatamente "# Netscape HTTP Cookie File" como 1a linha.
    if not head.startswith(b"# Netscape HTTP Cookie File"):
        return
    ydl_opts["cookiefile"] = cookies_path
