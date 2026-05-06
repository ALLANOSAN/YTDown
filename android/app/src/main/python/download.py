import json
import os

from runtime import _get_yt_dlp_module
from helpers import (
    _failure_payload,
    _is_retryable_network_error,
    _resolve_metadata,
)
from metadata import _force_metadata_with_mutagen


def _prepare_ffmpeg_runtime(native_lib_dir, app_files_dir):
    # Evita concatenação infinita limpando caminhos duplicados
    def _clean_path_append(env_name, new_path):
        current = os.environ.get(env_name, "")
        parts = [p for p in current.split(":") if p]
        if new_path not in parts:
            parts.insert(0, new_path)
            os.environ[env_name] = ":".join(parts)
            return True
        return False

    if native_lib_dir and os.path.isdir(native_lib_dir):
        _clean_path_append("LD_LIBRARY_PATH", native_lib_dir)
        if _clean_path_append("PATH", native_lib_dir):
            print(f"🚀 PATH atualizado (libs): {native_lib_dir}")

    if app_files_dir and os.path.isdir(app_files_dir):
        _clean_path_append("LD_LIBRARY_PATH", app_files_dir)
        if _clean_path_append("PATH", app_files_dir):
            print(f"🚀 PATH atualizado (files): {app_files_dir}")


def _resolve_ffmpeg_binary(native_lib_dir, app_files_dir, binary_name="ffmpeg"):
    """
    No Android 10+, binários só podem ser executados se estiverem no diretório de libs nativas
    e possuírem o prefixo 'lib' e extensão '.so'.
    """
    # 1. Tenta buscar no diretório de bibliotecas nativas (Obrigatório para Android 10+)
    if native_lib_dir:
        # Se buscamos ffmpeg, tentamos libffmpeg_exe.so
        # Se buscamos ffprobe, tentamos libffprobe_exe.so
        names = [f"lib{binary_name}_exe.so", f"lib{binary_name}.so"]
        for name in names:
            path = os.path.join(native_lib_dir, name)
            if os.path.exists(path):
                print(f"🎯 Binário nativo encontrado: {path}")
                return path

    # 2. Fallback para pasta de arquivos (apenas para dispositivos antigos ou testes)
    if app_files_dir:
        path = os.path.join(app_files_dir, binary_name)
        if os.path.exists(path) and os.access(path, os.X_OK):
            return path

    return None


def _resolve_audio_codec(selected_format, quality):
    allowed_audio_formats = {"mp3", "m4a", "aac", "flac", "wav", "opus", "ogg"}
    if selected_format in allowed_audio_formats:
        return selected_format
    if str(quality) == "320":
        return "mp3"
    return "m4a"


def _resolve_preferred_audio_quality(quality, desired_codec):
    preferred_quality = "192"
    quality_str = str(quality)
    if quality_str.isdigit():
        preferred_quality = quality_str
    if desired_codec in {"wav", "flac"}:
        return "0"
    return preferred_quality


def _resolve_audio_fallback_extension(desired_codec):
    if desired_codec == "aac":
        return "m4a"
    return desired_codec


def _resolve_video_container(selected_format):
    supported_containers = {"mp4", "mkv", "webm"}
    if selected_format in supported_containers:
        return selected_format
    return "mp4"


def _resolve_video_height(quality):
    quality_text = str(quality)
    if quality_text == "best":
        return "1080"
    normalized_height = quality_text.replace("p", "")
    if normalized_height.isdigit():
        return normalized_height
    return "1080"


def _build_audio_download_options(
    ydl_opts, selected_format, quality, has_ffmpeg, output_path
):
    desired_codec = _resolve_audio_codec(selected_format, quality)
    preferred_quality = _resolve_preferred_audio_quality(quality, desired_codec)

    if has_ffmpeg:
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
        return ydl_opts, output_path

    fallback_ext = _resolve_audio_fallback_extension(desired_codec)
    if fallback_ext in {"mp3", "wav", "flac", "ogg"}:
        old_ext = fallback_ext
        print(f"⚠️ Forçando fallback de {old_ext} para m4a devido a falta de FFmpeg")
        fallback_ext = "m4a"
        if output_path.lower().endswith(f".{old_ext}"):
            output_path = output_path[: -(len(old_ext) + 1)] + ".m4a"

    ydl_opts.update(
        {
            "format": f"bestaudio[ext={fallback_ext}]/bestaudio/best",
            "outtmpl": output_path,
        }
    )
    return ydl_opts, output_path


def _build_video_download_options(ydl_opts, selected_format, quality, has_ffmpeg):
    desired_container = _resolve_video_container(selected_format)
    height = _resolve_video_height(quality)

    if has_ffmpeg:
        ydl_opts.update(
            {
                "format": f"bestvideo[height<={height}]+bestaudio/best[height<={height}]",
                "merge_output_format": desired_container,
            }
        )
        return ydl_opts, None

    ydl_opts.update(
        {
            "format": f"best[height<={height}][ext={desired_container}]/best[height<={height}]",
        }
    )
    return ydl_opts, None


def _build_download_options(
    ydl_opts, format_type, selected_format, quality, has_ffmpeg, output_path
):
    if format_type == "audio":
        return _build_audio_download_options(
            ydl_opts,
            selected_format,
            quality,
            has_ffmpeg,
            output_path,
        )
    return _build_video_download_options(
        ydl_opts,
        selected_format,
        quality,
        has_ffmpeg,
    )


def _resolve_downloaded_filename(ydl, info, format_type):
    if not info:
        return None

    orig_filename = ydl.prepare_filename(info)
    if os.path.exists(orig_filename):
        return orig_filename

    return _find_downloaded_file(orig_filename, format_type)


def _resolve_downloaded_files(ydl, entries, format_type):
    files = []
    if not entries:
        return files

    for entry in entries:
        if not entry:
            continue
        filepath = _resolve_downloaded_filename(ydl, entry, format_type)
        if filepath and os.path.exists(filepath):
            files.append(filepath)
    return files


def _apply_tags_to_files(files, info_sources, artist, album, artwork_url):
    if not files:
        return False

    success = True
    for index, filepath in enumerate(files):
        info = info_sources[index] if index < len(info_sources) else {}
        title = info.get("title", os.path.splitext(os.path.basename(filepath))[0])
        resolved_title, resolved_artist, resolved_album = _resolve_metadata(
            title,
            artist,
            album,
            info,
        )
        resolved_artwork_url = artwork_url or info.get("thumbnail")

        injected = _force_metadata_with_mutagen(
            filepath,
            resolved_title,
            resolved_artist,
            resolved_album,
            resolved_artwork_url,
        )
        if not injected:
            print(f"⚠️ Falha ao injetar tags em {filepath}")
            success = False
        else:
            print(f"✅ Tags injetadas em {filepath}")
    return success


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
    progress_callback=None,
):
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
        if d["status"] == "finished":
            progress_data["percent"] = 100

        if progress_callback is not None:
            try:
                progress_callback.onProgress(progress_data["percent"])
            except Exception:
                pass

    ffmpeg_bin = _resolve_ffmpeg_binary(native_lib_dir, app_files_dir, "ffmpeg")
    has_ffmpeg = bool(ffmpeg_bin and os.path.exists(ffmpeg_bin))

    if has_ffmpeg:
        _prepare_ffmpeg_runtime(native_lib_dir, app_files_dir)

    ydl_opts = {
        "outtmpl": output_path,
        "progress_hooks": [progress_hook],
        "quiet": True,
        "no_warnings": True,
        "socket_timeout": 300,
        "retries": 3,
        # nocheckcertificate REMOVIDO — desabilitar SSL é risco de segurança em produção
        # Se necessário por ambiente corporativo/proxy, o usuário pode configurar via settings
    }

    if has_ffmpeg:
        # Fornecemos o caminho completo para o executável (que pode ser libffmpeg_exe.so)
        # O yt-dlp executará este arquivo diretamente.
        ydl_opts["ffmpeg_location"] = ffmpeg_bin

        # Tenta resolver o ffprobe separadamente
        # Se não houver ffprobe real, definimos como None para que yt-dlp lide com isso.
        ffprobe_bin = _resolve_ffmpeg_binary(native_lib_dir, app_files_dir, "ffprobe")

        # Verificação de sanidade: ffprobe não pode ter o mesmo tamanho que ffmpeg (sinal de placeholder)
        if ffprobe_bin and os.path.exists(ffprobe_bin) and os.path.exists(ffmpeg_bin):
            if os.path.getsize(ffprobe_bin) == os.path.getsize(ffmpeg_bin):
                print(
                    "⚠️ Detectado FFprobe placeholder (cópia do FFmpeg). Ignorando para evitar erros."
                )
                ffprobe_bin = None

        ydl_opts["ffprobe_location"] = ffprobe_bin

        print(f"🚀 FFmpeg: {ffmpeg_bin} | FFprobe: {ydl_opts['ffprobe_location']}")

    selected_format = (selected_format or "").strip().lower()
    ydl_opts, output_path = _build_download_options(
        ydl_opts,
        format_type,
        selected_format,
        quality,
        has_ffmpeg,
        output_path,
    )

    try:
        yt_dlp_module = _get_yt_dlp_module(app_files_dir)
        with yt_dlp_module.YoutubeDL(ydl_opts) as ydl:
            downloaded_info = ydl.extract_info(url, download=True)
            is_playlist = downloaded_info.get("_type") == "playlist"

            if is_playlist:
                entries = downloaded_info.get("entries", []) or []
                downloaded_files = _resolve_downloaded_files(ydl, entries, format_type)
                if not downloaded_files:
                    return json.dumps(
                        _failure_payload(
                            "Nenhum arquivo de playlist encontrado após o download",
                            stage="final_file_validation",
                            retryable=False,
                            progress=progress_data["percent"],
                        )
                    )
                final_filename = downloaded_files[0]
                print(f"📁 Playlist baixada com {len(downloaded_files)} arquivos")

                tags_injected = _apply_tags_to_files(
                    downloaded_files,
                    entries,
                    artist,
                    album,
                    artwork_url or downloaded_info.get("thumbnail"),
                )
                detected_title = downloaded_info.get("title", "Playlist")
                detected_artist = artist or downloaded_info.get("uploader") or "YTDown"
                detected_album = (
                    album or downloaded_info.get("playlist_title") or "YTDown"
                )

                return json.dumps(
                    {
                        "success": True,
                        "message": "Playlist download completed",
                        "progress": progress_data["percent"],
                        "filename": final_filename,
                        "filenames": downloaded_files,
                        "tags_injected": tags_injected,
                        "detected_title": detected_title,
                        "detected_artist": detected_artist,
                        "detected_album": detected_album,
                    }
                )

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
                    if not tags_injected:
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
    if os.path.exists(orig_filename):
        return orig_filename

    base_name = os.path.splitext(orig_filename)[0]
    dir_name = os.path.dirname(orig_filename)

    extensions = {
        "audio": [
            ".m4a",
            ".webm",
            ".mp3",
            ".flac",
            ".wav",
            ".aac",
            ".ogg",
            ".opus",
        ],
    }.get(format_type, [".mp4", ".mkv", ".webm", ".avi"])

    for ext in extensions:
        candidate = base_name + ext
        if os.path.exists(candidate):
            return candidate

    if os.path.exists(dir_name):
        for f in os.listdir(dir_name):
            if f.startswith(os.path.basename(base_name)):
                return os.path.join(dir_name, f)

    return orig_filename
