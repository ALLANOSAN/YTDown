"""
YTDown - Python module for Chaquo SDK
Handles video info fetching and downloads using yt-dlp
Tags ID3 injetadas via Mutagen (sem dependência do FFmpeg)
"""

import json
import os
import re
import shutil
import sys
import tarfile
import tempfile
import zipfile
import hashlib
import time
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urlparse
from urllib.request import Request, urlopen


_RUNTIME_ROOT_DIR = "runtime_packages"
_UPDATE_META_FILENAME = "yt_dlp_update_meta.json"
_YT_DLP_MODULE = None
_DEFAULT_CHECK_CACHE_HOURS = 6
_DEFAULT_NETWORK_ATTEMPTS = 3
_DEFAULT_NETWORK_BACKOFF_SECONDS = 0.6
_MAX_THUMBNAIL_BYTES = 6 * 1024 * 1024


def _runtime_root(app_files_dir):
    return os.path.join(app_files_dir, _RUNTIME_ROOT_DIR)


def _runtime_yt_dlp_dir(app_files_dir):
    return os.path.join(_runtime_root(app_files_dir), "yt_dlp")


def _update_meta_path(app_files_dir):
    return os.path.join(_runtime_root(app_files_dir), _UPDATE_META_FILENAME)


def _ensure_runtime_root(app_files_dir):
    os.makedirs(_runtime_root(app_files_dir), exist_ok=True)


def _read_update_meta(app_files_dir):
    path = _update_meta_path(app_files_dir)
    if not os.path.exists(path):
        return {}
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def _write_update_meta(app_files_dir, data):
    _ensure_runtime_root(app_files_dir)
    path = _update_meta_path(app_files_dir)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)


def _activate_runtime_path(app_files_dir):
    if not app_files_dir:
        return

    runtime_root = _runtime_root(app_files_dir)
    runtime_pkg = _runtime_yt_dlp_dir(app_files_dir)
    if os.path.isdir(runtime_pkg) and runtime_root not in sys.path:
        sys.path.insert(0, runtime_root)


def _get_yt_dlp_module(app_files_dir=None):
    global _YT_DLP_MODULE

    if app_files_dir:
        _activate_runtime_path(app_files_dir)

    if _YT_DLP_MODULE is not None:
        return _YT_DLP_MODULE

    import yt_dlp

    _YT_DLP_MODULE = yt_dlp
    return _YT_DLP_MODULE


def _get_current_yt_dlp_version(app_files_dir=None):
    yt_dlp_module = _get_yt_dlp_module(app_files_dir)
    version = getattr(getattr(yt_dlp_module, "version", None), "__version__", None)
    if version:
        return str(version)
    return str(getattr(yt_dlp_module, "__version__", "unknown"))


def _version_tuple(version):
    numbers = re.findall(r"\d+", str(version))
    if not numbers:
        return (0,)
    return tuple(int(part) for part in numbers[:4])


def _is_newer(latest, current):
    return _version_tuple(latest) > _version_tuple(current)


def _failure_payload(error, stage=None, retryable=None, **extra):
    payload = {
        "success": False,
        "error": str(error),
    }
    if stage:
        payload["stage"] = stage
    if retryable is not None:
        payload["retryable"] = bool(retryable)
    if extra:
        payload.update(extra)
    return payload


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
    attempts=_DEFAULT_NETWORK_ATTEMPTS,
    backoff_seconds=_DEFAULT_NETWORK_BACKOFF_SECONDS,
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
    backoff_seconds=_DEFAULT_NETWORK_BACKOFF_SECONDS,
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


def _fetch_latest_yt_dlp_info():
    payload_bytes = _download_url_bytes(
        "https://pypi.org/pypi/yt-dlp/json",
        timeout=8,
        attempts=3,
        backoff_seconds=0.7,
        label="pypi_metadata",
    )
    payload = json.loads(payload_bytes.decode("utf-8"))

    latest_version = payload.get("info", {}).get("version")
    if not latest_version:
        raise Exception("Não foi possível obter a versão mais recente do yt-dlp")

    release_files = payload.get("releases", {}).get(latest_version, [])

    wheel_url = None
    wheel_sha256 = None
    for file_info in release_files:
        filename = file_info.get("filename", "")
        if file_info.get("packagetype") == "bdist_wheel" and "py3-none-any" in filename:
            wheel_url = file_info.get("url")
            wheel_sha256 = file_info.get("digests", {}).get("sha256")
            break

    if not wheel_url:
        for file_info in release_files:
            if file_info.get("packagetype") == "sdist":
                wheel_url = file_info.get("url")
                wheel_sha256 = file_info.get("digests", {}).get("sha256")
                break

    if not wheel_url:
        raise Exception("Arquivo de atualização do yt-dlp não encontrado no PyPI")

    if not wheel_sha256:
        raise Exception("Hash SHA256 do pacote yt-dlp indisponível no PyPI")

    return {
        "latest_version": latest_version,
        "package_url": wheel_url,
        "package_sha256": wheel_sha256,
    }


def _parse_iso_datetime(value):
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value))
    except Exception:
        return None


def _is_cache_valid(meta, cache_hours):
    last_check = _parse_iso_datetime(meta.get("last_check_at"))
    if not last_check:
        return False

    elapsed = datetime.now(timezone.utc) - last_check.astimezone(timezone.utc)
    return elapsed.total_seconds() < cache_hours * 3600


def _build_check_response_from_meta(meta):
    return {
        "success": True,
        "current_version": meta.get("current_version", "unknown"),
        "latest_version": meta.get("latest_version", "unknown"),
        "update_available": bool(meta.get("update_available", False)),
        "cached": True,
    }


def check_yt_dlp_update(
    app_files_dir,
    force_remote=False,
    cache_hours=_DEFAULT_CHECK_CACHE_HOURS,
):
    """
    Verifica se há nova versão do yt-dlp no PyPI.
    """
    try:
        meta = _read_update_meta(app_files_dir)

        if not force_remote and _is_cache_valid(meta, cache_hours):
            return json.dumps(_build_check_response_from_meta(meta))

        current_version = _get_current_yt_dlp_version(app_files_dir)
        latest_info = _fetch_latest_yt_dlp_info()
        latest_version = latest_info["latest_version"]
        update_available = _is_newer(latest_version, current_version)

        meta.update(
            {
                "last_check_at": datetime.now(timezone.utc).isoformat(),
                "current_version": current_version,
                "latest_version": latest_version,
                "package_url": latest_info["package_url"],
                "package_sha256": latest_info["package_sha256"],
                "update_available": update_available,
            }
        )
        _write_update_meta(app_files_dir, meta)

        return json.dumps(
            {
                "success": True,
                "current_version": current_version,
                "latest_version": latest_version,
                "update_available": update_available,
                "cached": False,
            }
        )
    except Exception as e:
        return json.dumps(
            _failure_payload(
                str(e),
                stage="check_yt_dlp_update",
                retryable=_is_retryable_network_error(e),
            )
        )


def _install_yt_dlp_package(app_files_dir, package_url, expected_sha256=None):
    _ensure_runtime_root(app_files_dir)
    runtime_pkg = _runtime_yt_dlp_dir(app_files_dir)

    fd, temp_package_path = tempfile.mkstemp(suffix=".pkg")
    os.close(fd)

    extract_dir = tempfile.mkdtemp(prefix="yt_dlp_extract_")

    def _is_safe_path(base, target):
        resolved = os.path.abspath(os.path.join(base, target))
        return resolved.startswith(os.path.abspath(base))

    try:
        package_bytes = _download_url_bytes(
            package_url,
            timeout=30,
            attempts=3,
            backoff_seconds=1.0,
            label="yt_dlp_package",
        )
        checksum = hashlib.sha256(package_bytes)

        with open(temp_package_path, "wb") as out:
            out.write(package_bytes)

        if expected_sha256:
            actual_sha256 = checksum.hexdigest()
            if actual_sha256.lower() != str(expected_sha256).lower():
                raise Exception(
                    "Falha na verificação de integridade do pacote yt-dlp "
                    f"(esperado={expected_sha256}, atual={actual_sha256})"
                )

        if package_url.endswith(".whl") or package_url.endswith(".zip"):
            with zipfile.ZipFile(temp_package_path, "r") as zf:
                for member in zf.namelist():
                    if not _is_safe_path(extract_dir, member):
                        raise Exception(
                            f"Caminho inseguro detectado na extração (Zip Slip): {member}"
                        )
                zf.extractall(extract_dir)
        elif package_url.endswith(".tar.gz") or package_url.endswith(".tgz"):
            with tarfile.open(temp_package_path, "r:gz") as tf:
                for member in tf.getmembers():
                    if not _is_safe_path(extract_dir, member.name):
                        raise Exception(
                            f"Caminho inseguro detectado na extração (Zip Slip): {member.name}"
                        )
                tf.extractall(extract_dir)
        else:
            raise Exception("Formato de pacote não suportado para atualização runtime")

        source_pkg_dir = None
        for root, dirs, files in os.walk(extract_dir):
            if os.path.basename(root) == "yt_dlp" and "__init__.py" in files:
                source_pkg_dir = root
                break

        if not source_pkg_dir:
            raise Exception(
                "Pacote de atualização inválido: diretório yt_dlp não encontrado"
            )

        if os.path.isdir(runtime_pkg):
            shutil.rmtree(runtime_pkg)
        shutil.copytree(source_pkg_dir, runtime_pkg)

    finally:
        if os.path.exists(temp_package_path):
            os.remove(temp_package_path)
        if os.path.isdir(extract_dir):
            shutil.rmtree(extract_dir)


def update_yt_dlp_if_needed(app_files_dir, force=False):
    """
    Atualiza o yt-dlp em runtime (sem rebuild) quando houver versão nova.
    """
    try:
        check_result = json.loads(
            check_yt_dlp_update(app_files_dir, force_remote=force)
        )
        if not check_result.get("success"):
            return json.dumps(check_result)

        current_version = check_result.get("current_version", "unknown")
        latest_version = check_result.get("latest_version", current_version)
        update_available = bool(check_result.get("update_available", False))

        if not force and not update_available:
            return json.dumps(
                {
                    "success": True,
                    "updated": False,
                    "current_version": current_version,
                    "latest_version": latest_version,
                    "update_available": False,
                    "message": "yt-dlp já está atualizado",
                }
            )

        meta = _read_update_meta(app_files_dir)
        package_url = meta.get("package_url")
        package_sha256 = meta.get("package_sha256")
        if not package_url or not package_sha256:
            latest_info = _fetch_latest_yt_dlp_info()
            package_url = latest_info["package_url"]
            package_sha256 = latest_info["package_sha256"]

        _install_yt_dlp_package(app_files_dir, package_url, package_sha256)

        global _YT_DLP_MODULE
        if "yt_dlp" in sys.modules:
            del sys.modules["yt_dlp"]
        _YT_DLP_MODULE = None

        installed_version = _get_current_yt_dlp_version(app_files_dir)

        meta.update(
            {
                "installed_runtime_version": installed_version,
                "updated_at": datetime.now(timezone.utc).isoformat(),
                "package_sha256": package_sha256,
                "update_available": _is_newer(latest_version, installed_version),
            }
        )
        _write_update_meta(app_files_dir, meta)

        return json.dumps(
            {
                "success": True,
                "updated": installed_version != current_version,
                "current_version": installed_version,
                "latest_version": latest_version,
                "update_available": _is_newer(latest_version, installed_version),
                "message": "yt-dlp atualizado com sucesso",
            }
        )
    except Exception as e:
        return json.dumps(
            _failure_payload(
                str(e),
                stage="update_yt_dlp_if_needed",
                retryable=_is_retryable_network_error(e),
            )
        )


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

    # Remove sufixos comuns do app/yt-dlp, ex: _319e5d ou _uuid
    text = re.sub(r"[_-][0-9a-f]{6,}$", "", text, flags=re.IGNORECASE)
    text = re.sub(
        r"[_-][0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        "",
        text,
        flags=re.IGNORECASE,
    )

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


def _resolve_metadata(title, artist, album, info):
    resolved_title = _strip_generated_suffix(title or info.get("title") or "Sem título")
    if not resolved_title or _is_unknown_label(resolved_title):
        resolved_title = "Sem título"

    resolved_artist = ""
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
    for candidate in artist_candidates:
        normalized = _strip_generated_suffix(candidate)
        if normalized and not _is_unknown_label(normalized):
            resolved_artist = normalized
            break

    if not resolved_artist:
        title_artist_fallback = _guess_artist_from_title(resolved_title)
        if title_artist_fallback and not _is_unknown_label(title_artist_fallback):
            resolved_artist = title_artist_fallback
        elif resolved_title and not _is_unknown_label(resolved_title):
            resolved_artist = resolved_title
        else:
            resolved_artist = "YTDown"

    resolved_album = ""
    album_candidates = [
        album,
        info.get("album"),
        info.get("playlist_title"),
    ]
    for candidate in album_candidates:
        normalized = _strip_generated_suffix(candidate)
        if normalized and not _is_unknown_label(normalized):
            resolved_album = normalized
            break

    if not resolved_album:
        resolved_album = "YTDown"

    return resolved_title, resolved_artist, resolved_album


def fetch_video_info(url, app_files_dir=None):
    """
    Fetch video information using yt-dlp
    """
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
                        "entries": info.get("entries", []) if is_playlist else None,
                    },
                }
            )
    except Exception as e:
        error_msg = str(e)
        if "Sign in to confirm" in error_msg:
            error_msg = "YouTube bloqueou a requisição (Bot check)"
        elif "Video unavailable" in error_msg:
            error_msg = "Vídeo indisponível"
        elif "confirm your age" in error_msg:
            error_msg = "Vídeo com restrição de idade"

        return json.dumps(
            _failure_payload(
                error_msg,
                stage="fetch_video_info",
                retryable=_is_retryable_network_error(e),
            )
        )


def download_video(
    url,
    output_path,
    format_type="best",
    quality="best",
    native_lib_dir=None,
    app_files_dir=None,
    artist=None,
    album=None,
    artwork_url=None,
    selected_format=None,
):
    """
    Download video/audio using yt-dlp com tags ID3 via Mutagen
    """
    progress_data = {"percent": 0}
    downloaded_info = None
    final_filename = None
    tags_injected = False

    def progress_hook(d):
        if d["status"] == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate", 0)
            downloaded = d.get("downloaded_bytes", 0)
            if total > 0:
                progress_data["percent"] = int((downloaded / total) * 100)
        elif d["status"] == "finished":
            progress_data["percent"] = 100

    # Configuração de FFmpeg
    ffmpeg_bin = None
    if native_lib_dir:
        # No Android, binários nativos estão em jniLibs -> nativeLibDir
        # Normalmente ffmpeg está em libffmpeg.so, mas chaquo-ffmpeg empacota como binários
        # Tentamos localizar o binário executável
        potential_ffmpeg = os.path.join(
            native_lib_dir, "libffmpeg.so"
        )  # Alguns casos renomeiam
        if os.path.exists(potential_ffmpeg):
            ffmpeg_bin = potential_ffmpeg
        else:
            # Caso padrão chaquo-ffmpeg
            ffmpeg_bin = os.path.join(native_lib_dir, "ffmpeg")

    ydl_opts = {
        "outtmpl": output_path,
        "progress_hooks": [progress_hook],
        "quiet": True,
        "no_warnings": True,
        "socket_timeout": 300,
        "retries": 3,
        "nocheckcertificate": True,
    }

    if ffmpeg_bin and os.path.exists(ffmpeg_bin):
        ydl_opts["ffmpeg_location"] = ffmpeg_bin
        print(f"🚀 Usando FFmpeg: {ffmpeg_bin}")
    else:
        print("⚠️ FFmpeg não encontrado, usando modo nativo limitado")

    selected_format = _normalize_text(selected_format).lower()

    if format_type == "audio":
        allowed_audio_formats = {"mp3", "m4a", "aac", "flac", "wav", "opus", "ogg"}
        desired_codec = (
            selected_format
            if selected_format in allowed_audio_formats
            else ("mp3" if str(quality) == "320" else "m4a")
        )

        preferred_quality = str(quality) if str(quality).isdigit() else "192"
        if desired_codec in {"wav", "flac"}:
            preferred_quality = "0"

        if ffmpeg_bin and os.path.exists(ffmpeg_bin):
            # Conversão profissional com FFmpeg
            ydl_opts.update(
                {
                    "format": "bestaudio/best",
                    "postprocessors": [
                        {
                            "key": "FFmpegExtractAudio",
                            "preferredcodec": desired_codec,
                            "preferredquality": preferred_quality,
                        }
                    ],
                }
            )
        else:
            # Fallback nativo
            fallback_ext = "m4a" if desired_codec == "aac" else desired_codec

            # Impede MP3/WAV falso sem FFmpeg renomeando para o container real
            if fallback_ext in {"mp3", "wav", "flac", "ogg"}:
                old_ext = fallback_ext
                print(
                    f"⚠️ Forçando fallback de {old_ext} para m4a devido a falta de FFmpeg"
                )
                fallback_ext = "m4a"
                if output_path.lower().endswith(f".{old_ext}"):
                    output_path = output_path[: -(len(old_ext) + 1)] + ".m4a"
                    ydl_opts["outtmpl"] = output_path

            ydl_opts.update(
                {
                    "format": f"bestaudio[ext={fallback_ext}]/bestaudio/best",
                }
            )
    else:
        # Video download
        desired_container = (
            selected_format if selected_format in {"mp4", "mkv", "webm"} else "mp4"
        )
        height = (
            quality.replace("p", "")
            if quality != "best" and quality.replace("p", "").isdigit()
            else "1080"
        )
        if ffmpeg_bin and os.path.exists(ffmpeg_bin):
            ydl_opts.update(
                {
                    "format": f"bestvideo[height<={height}]+bestaudio/best[height<={height}]",
                    "merge_output_format": desired_container,
                }
            )
        else:
            ydl_opts.update(
                {
                    "format": f"best[height<={height}][ext={desired_container}]/best[height<={height}]",
                }
            )

    try:
        yt_dlp_module = _get_yt_dlp_module(app_files_dir)
        with yt_dlp_module.YoutubeDL(ydl_opts) as ydl:
            downloaded_info = ydl.extract_info(url, download=True)

            # Encontrar o arquivo baixado
            orig_filename = ydl.prepare_filename(downloaded_info)
            final_filename = _find_downloaded_file(orig_filename, format_type)

            print(f"📁 Arquivo baixado: {final_filename}")

            if not final_filename or not os.path.exists(final_filename):
                return json.dumps(
                    _failure_payload(
                        "Arquivo final não encontrado após concluir o download",
                        stage="final_file_validation",
                        retryable=False,
                        progress=progress_data["percent"],
                        filename=final_filename,
                    )
                )

            # INJETAR METADADOS COM MUTAGEN
            resolved_title = ""
            resolved_artist = ""
            resolved_album = ""

            if downloaded_info and final_filename and os.path.exists(final_filename):
                try:
                    resolved_title, resolved_artist, resolved_album = _resolve_metadata(
                        downloaded_info.get("title", "Sem título"),
                        artist,
                        album,
                        downloaded_info,
                    )

                    resolved_artwork_url = artwork_url or downloaded_info.get(
                        "thumbnail", None
                    )

                    tags_injected = _force_metadata_with_mutagen(
                        final_filename,
                        resolved_title,
                        resolved_artist,
                        resolved_album,
                        resolved_artwork_url,
                    )

                    if tags_injected:
                        print("✅ Tags ID3 injetadas:")
                        print(f"   🎵 Título: {resolved_title}")
                        print(f"   🎤 Artista: {resolved_artist}")
                        print(f"   💿 Álbum: {resolved_album}")
                        print(f"   📁 Arquivo: {final_filename}")
                    else:
                        print("⚠️ Não foi possível injetar tags")
                except Exception as e:
                    print(f"❌ Erro ao injetar tags: {str(e)}")
                    import traceback

                    traceback.print_exc()

            return json.dumps(
                {
                    "success": True,
                    "message": "Download completed",
                    "progress": progress_data["percent"],
                    "filename": final_filename,
                    "tags_injected": tags_injected,
                    "detected_title": resolved_title,
                    "detected_artist": resolved_artist,
                    "detected_album": resolved_album,
                }
            )
    except Exception as e:
        error_msg = str(e)
        print(f"❌ Erro no download: {error_msg}")
        import traceback

        traceback.print_exc()
        return json.dumps(
            _failure_payload(
                error_msg,
                stage="download_video",
                retryable=_is_retryable_network_error(e),
                progress=progress_data["percent"],
                filename=final_filename,
            )
        )


def _find_downloaded_file(orig_filename, format_type):
    """Encontra o arquivo real baixado pelo yt-dlp"""
    if os.path.exists(orig_filename):
        return orig_filename

    base_name = os.path.splitext(orig_filename)[0]
    dir_name = os.path.dirname(orig_filename)

    # Extensões possíveis para áudio
    if format_type == "audio":
        extensions = [".m4a", ".webm", ".mp3", ".flac", ".wav", ".aac", ".ogg", ".opus"]
    else:
        extensions = [".mp4", ".mkv", ".webm", ".avi"]

    for ext in extensions:
        candidate = base_name + ext
        if os.path.exists(candidate):
            return candidate

    # Buscar no diretório
    if os.path.exists(dir_name):
        for f in os.listdir(dir_name):
            if f.startswith(os.path.basename(base_name)):
                return os.path.join(dir_name, f)

    return orig_filename


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
            return data if data else None
        except Exception as e:
            print(f"⚠️ Falha ao ler capa local: {str(e)}")
            return None

    try:
        parsed = urlparse(source)
        scheme = (parsed.scheme or "").lower()

        if scheme == "file":
            local_path = unquote(parsed.path or "")
            if os.name == "nt" and re.match(r"^/[A-Za-z]:", local_path):
                local_path = local_path[1:]
            local_data = _read_local_bytes(local_path)
            if local_data:
                return local_data

        if not scheme and os.path.isabs(source):
            local_data = _read_local_bytes(source)
            if local_data:
                return local_data
    except Exception as e:
        print(f"⚠️ Falha ao interpretar origem da capa: {str(e)}")

    try:
        data = _download_url_bytes(
            source,
            timeout=8,
            attempts=2,
            backoff_seconds=0.5,
            max_bytes=_MAX_THUMBNAIL_BYTES,
            label="thumbnail_download",
        )
        return data if data else None
    except Exception as e:
        print(f"⚠️ Não foi possível baixar thumbnail para APIC: {str(e)}")
        return None


def _set_vorbis_like_tags(audio, title, artist, album):
    audio["title"] = [str(title)]
    audio["artist"] = [str(artist)]
    audio["album"] = [str(album)]


def _verify_vorbis_like_tags(audio):
    title = audio.get("title") if audio is not None else None
    artist = audio.get("artist") if audio is not None else None
    return bool(title) and bool(artist)


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


def _write_mp3_id3_tags(filepath, title, artist, album, thumbnail_url=None):
    from mutagen.id3 import APIC, COMM, ID3, ID3NoHeaderError, TALB, TIT2, TPE1, TPE2

    try:
        has_existing_tags = False

        try:
            tags = ID3(filepath)
            has_existing_tags = True
        except ID3NoHeaderError:
            tags = ID3()

        for frame_id in ("TIT2", "TPE1", "TALB", "TPE2", "COMM", "APIC"):
            tags.delall(frame_id)

        tags.add(TIT2(encoding=3, text=str(title)))
        tags.add(TPE1(encoding=3, text=str(artist)))
        tags.add(TALB(encoding=3, text=str(album)))
        tags.add(TPE2(encoding=3, text=str(artist)))
        tags.add(COMM(encoding=3, lang="por", desc="source", text="YTDown"))

        image_data = _download_thumbnail_bytes(thumbnail_url)
        if image_data:
            tags.add(
                APIC(
                    encoding=3,
                    mime=_guess_image_mime(thumbnail_url, image_data),
                    type=3,
                    desc="Cover",
                    data=image_data,
                )
            )

        # ID3v2.3 maximiza compatibilidade com players e editores antigos.
        tags.save(filepath, v2_version=3)

        # VERIFICAÇÃO PÓS-GRAVAÇÃO
        verify_tags = ID3(filepath)
        verified_tpe1 = str(verify_tags.get("TPE1")) if verify_tags.get("TPE1") else ""

        print(
            f"[METADATA_SUCCESS] MP3 tags gravados: file={filepath}, "
            f"title={title}, artist={artist}, album={album}, "
            f"had_existing_tags={has_existing_tags}, verified_artist={verified_tpe1}"
        )

        return True

    except Exception as e:
        print(
            f"[METADATA_ERROR] Falha ao gravar ID3 no MP3: file={filepath}, "
            f"title={title}, artist={artist}, error={str(e)}"
        )
        import traceback

        traceback.print_exc()
        return False


def _write_mp4_m4a_tags(filepath, title, artist, album, thumbnail_url=None):
    from mutagen.mp4 import MP4, MP4Cover, MP4FreeForm

    try:
        audio = MP4(filepath)

        for tag_key in (
            "\xa9nam",
            "\xa9ART",
            "aART",
            "\xa9alb",
            "\xa9wrt",
            "----:com.apple.iTunes:ARTIST",
            "----:com.apple.iTunes:ALBUM",
            "----:com.apple.iTunes:TITLE",
        ):
            if tag_key in audio:
                del audio[tag_key]

        audio["\xa9nam"] = [str(title)]
        audio["\xa9ART"] = [str(artist)]
        audio["aART"] = [str(artist)]
        audio["\xa9alb"] = [str(album)]
        audio["\xa9wrt"] = ["YTDown"]

        # Compatibilidade extra com players que priorizam atoms livres do iTunes.
        audio["----:com.apple.iTunes:ARTIST"] = [
            MP4FreeForm(str(artist).encode("utf-8"))
        ]
        audio["----:com.apple.iTunes:ALBUM"] = [MP4FreeForm(str(album).encode("utf-8"))]
        audio["----:com.apple.iTunes:TITLE"] = [MP4FreeForm(str(title).encode("utf-8"))]

        image_data = _download_thumbnail_bytes(thumbnail_url)
        if image_data:
            mime = _guess_image_mime(thumbnail_url, image_data)
            if mime == "image/png":
                audio["covr"] = [MP4Cover(image_data, imageformat=MP4Cover.FORMAT_PNG)]
            elif mime == "image/jpeg":
                audio["covr"] = [MP4Cover(image_data, imageformat=MP4Cover.FORMAT_JPEG)]
            else:
                print("⚠️ Capa WebP ignorada para MP4/M4A (formato não suportado)")

        audio.save()
        return True

    except Exception as e:
        print(f"❌ Falha ao gravar metadados MP4/M4A direto no arquivo: {str(e)}")
        import traceback

        traceback.print_exc()
        return False


def _force_metadata_with_mutagen(
    filepath, title, artist, album="YTDown", thumbnail_url=None
):
    """
    Injeta tags ID3/MP4 usando Mutagen
    """
    if not filepath or not os.path.exists(filepath):
        print(f"⚠️ Arquivo não existe: {filepath}")
        return False

    ext = filepath.lower().split(".")[-1]
    print(f"🏷️  Injetando tags em {ext}: {filepath}")

    try:
        if ext == "mp3":
            ok = _write_mp3_id3_tags(filepath, title, artist, album, thumbnail_url)
            if ok:
                print("✅ Tags MP3 (ID3 direto no arquivo) injetadas")
            return ok

        elif ext in ("m4a", "mp4"):
            ok = _write_mp4_m4a_tags(filepath, title, artist, album, thumbnail_url)
            if ok:
                print("✅ Tags M4A/MP4 (arquivo físico) injetadas")
            return ok

        elif ext == "flac":
            from mutagen.flac import FLAC

            audio = FLAC(filepath)
            _set_vorbis_like_tags(audio, title, artist, album)
            audio.save()

            verify = FLAC(filepath)
            ok = _verify_vorbis_like_tags(verify)
            if ok:
                print("✅ Tags FLAC injetadas")
            return ok

        elif ext == "webm":
            # WebM usa tags Ogg Vorbis
            from mutagen.oggvorbis import OggVorbis

            try:
                audio = OggVorbis(filepath)
            except Exception:
                print("⚠️ WebM não suporta tags completas")
                return False

            _set_vorbis_like_tags(audio, title, artist, album)
            audio.save()

            verify = OggVorbis(filepath)
            ok = _verify_vorbis_like_tags(verify)
            if ok:
                print("✅ Tags WebM injetadas")
            return ok

        elif ext == "ogg":
            from mutagen.oggvorbis import OggVorbis

            audio = OggVorbis(filepath)
            _set_vorbis_like_tags(audio, title, artist, album)
            audio.save()

            verify = OggVorbis(filepath)
            ok = _verify_vorbis_like_tags(verify)
            if ok:
                print("✅ Tags OGG injetadas")
            return ok

        elif ext == "opus":
            from mutagen.oggopus import OggOpus

            audio = OggOpus(filepath)
            _set_vorbis_like_tags(audio, title, artist, album)
            audio.save()

            verify = OggOpus(filepath)
            ok = _verify_vorbis_like_tags(verify)
            if ok:
                print("✅ Tags OPUS injetadas")
            return ok

        elif ext == "wav":
            from mutagen.wave import WAVE
            from mutagen.id3 import ID3, TIT2, TPE1, TALB, TPE2

            audio = WAVE(filepath)
            if audio.tags is None:
                audio.tags = ID3()

            audio.tags.delall("TIT2")
            audio.tags.delall("TPE1")
            audio.tags.delall("TALB")
            audio.tags.delall("TPE2")

            audio.tags.add(TIT2(encoding=3, text=title))
            audio.tags.add(TPE1(encoding=3, text=artist))
            audio.tags.add(TALB(encoding=3, text=album))
            audio.tags.add(TPE2(encoding=3, text=artist))
            audio.save()

            verify = WAVE(filepath)
            verify_tags = verify.tags
            ok = bool(
                verify_tags and verify_tags.get("TIT2") and verify_tags.get("TPE1")
            )
            if ok:
                print("✅ Tags WAV (ID3) injetadas")
            return ok

        elif ext == "aac":
            # AAC em container MP4/M4A costuma aceitar os mesmos átomos.
            ok = _write_mp4_m4a_tags(filepath, title, artist, album, thumbnail_url)
            if ok:
                print("✅ Tags AAC (container MP4) injetadas")
            return ok

        else:
            print(f"⚠️ Formato não suportado: {ext}")
            return False

    except ImportError as e:
        print(f"❌ Mutagen não importado: {str(e)}")
        return False
    except Exception as e:
        print(f"❌ Erro ao injetar tags: {str(e)}")
        import traceback

        traceback.print_exc()
        return False


def rewrite_file_metadata(
    filepath,
    title=None,
    artist=None,
    album=None,
    artwork_url=None,
):
    """
    Regrava metadados de um arquivo já baixado, sem refazer download.

    Quando os parâmetros são explicitamente fornecidos (edição manual),
    eles são usados diretamente. Quando omitidos, usa fallbacks inteligentes.
    """
    try:
        if not filepath or not os.path.exists(filepath):
            return json.dumps(
                _failure_payload(
                    "Arquivo não encontrado para regravar metadados",
                    stage="rewrite_file_metadata",
                    retryable=False,
                )
            )

        # Para edição manual: usa parâmetros fornecidos diretamente
        # Para reparo automático: usa fallbacks inteligentes
        fallback_title = os.path.splitext(os.path.basename(filepath))[0]

        if title is not None:
            # Título foi explicitamente fornecido - usar diretamente
            resolved_title = _strip_generated_suffix(title)
            if not resolved_title:
                resolved_title = fallback_title
        else:
            # Sem título fornecido - tentar resolver com info vazio
            resolved_title, _, _ = _resolve_metadata(
                fallback_title,
                None,
                None,
                {},
            )

        if artist is not None:
            # Artista foi explicitamente fornecido - usar diretamente
            resolved_artist = _strip_generated_suffix(artist)
            # Se ficou vazio após normalização, usar o valor original
            if not resolved_artist:
                resolved_artist = artist.strip()
            # Se ainda está vazio, o usuário quis deixar em branco
            if not resolved_artist:
                resolved_artist = ""
        else:
            # Sem artista fornecido - tentar resolver com info vazio
            _, resolved_artist, _ = _resolve_metadata(
                title or fallback_title,
                None,
                None,
                {},
            )

        if album is not None:
            # Album foi explicitamente fornecido - usar diretamente
            resolved_album = _strip_generated_suffix(album)
            if not resolved_album:
                resolved_album = album.strip()
        else:
            # Sem album fornecido - tentar resolver com info vazio
            _, _, resolved_album = _resolve_metadata(
                title or fallback_title,
                None,
                None,
                {},
            )

        print(
            f"[METADATA_REWRITE] Iniciando gravação: file={filepath}, "
            f"input_title={title}, input_artist={artist}, input_album={album}, "
            f"resolved_title={resolved_title}, resolved_artist={resolved_artist}, "
            f"resolved_album={resolved_album}"
        )

        tags_injected = _force_metadata_with_mutagen(
            filepath,
            resolved_title,
            resolved_artist,
            resolved_album,
            artwork_url,
        )

        payload = {
            "success": tags_injected,
            "tags_injected": tags_injected,
            "title": resolved_title,
            "artist": resolved_artist,
            "album": resolved_album,
            "filePath": filepath,
            "error": None if tags_injected else "Falha ao injetar tags no arquivo",
        }
        if not tags_injected:
            payload["stage"] = "metadata_write_validation"
            payload["retryable"] = False

        return json.dumps(payload)
    except Exception as e:
        return json.dumps(
            _failure_payload(
                str(e),
                stage="rewrite_file_metadata",
                retryable=_is_retryable_network_error(e),
            )
        )


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="YTDown CLI Wrapper")

    # Argumentos de Download
    parser.add_argument("--url", help="URL do vídeo")
    parser.add_argument("--output", help="Diretório de saída")
    parser.add_argument("--format-type", choices=["best", "audio", "video"], default="best")
    parser.add_argument("--quality", default="best")
    parser.add_argument("--selected-format", help="Formato específico (mp3, m4a, etc)")

    # Argumentos de Metadados
    parser.add_argument("--rewrite-metadata", action="store_true", help="Regravar metadados")
    parser.add_argument("--file-path", help="Caminho do arquivo para regravação")
    parser.add_argument("--title", help="Título")
    parser.add_argument("--artist", help="Artista")
    parser.add_argument("--album", help="Álbum")
    parser.add_argument("--artwork-url", help="URL da capa")

    # Argumentos de Atualização
    parser.add_argument("--check-update", action="store_true", help="Verificar atualização")
    parser.add_argument("--perform-update", action="store_true", help="Executar atualização")

    # Argumentos de Ambiente
    parser.add_argument("--app-files-dir", help="Diretório de arquivos do app")
    parser.add_argument("--native-lib-dir", help="Diretório de libs nativas (FFmpeg)")

    args = parser.parse_args()

    if args.check_update:
        print(check_yt_dlp_update(args.app_files_dir))
    elif args.perform_update:
        print(update_yt_dlp_if_needed(args.app_files_dir, force=True))
    elif args.rewrite_metadata:
        result = rewrite_file_metadata(
            filepath=args.file_path,
            title=args.title,
            artist=args.artist,
            album=args.album,
            artwork_url=args.artwork_url
        )
        print(result)
    elif args.url:
        result = download_video(
            url=args.url,
            output_path=os.path.join(args.output, "%(title)s.%(ext)s"),
            format_type=args.format_type,
            quality=args.quality,
            native_lib_dir=args.native_lib_dir,
            app_files_dir=args.app_files_dir,
            artist=args.artist,
            album=args.album,
            artwork_url=args.artwork_url,
            selected_format=args.selected_format
        )
        print(result)
