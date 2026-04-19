import json
import os

from runtime import _get_yt_dlp_module
from helpers import (
    _failure_payload,
    _is_retryable_network_error,
    _resolve_metadata,
)
from metadata import _force_metadata_with_mutagen


def _resolve_ffmpeg_binary(native_lib_dir):
    if not native_lib_dir:
        return None

    potential_ffmpeg = os.path.join(native_lib_dir, "libffmpeg.so")
    if os.path.exists(potential_ffmpeg):
        return potential_ffmpeg

    return os.path.join(native_lib_dir, "ffmpeg")


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

    ffmpeg_bin = _resolve_ffmpeg_binary(native_lib_dir)
    has_ffmpeg = bool(ffmpeg_bin and os.path.exists(ffmpeg_bin))

    ydl_opts = {
        "outtmpl": output_path,
        "progress_hooks": [progress_hook],
        "quiet": True,
        "no_warnings": True,
        "socket_timeout": 300,
        "retries": 3,
        "nocheckcertificate": True,
    }

    if has_ffmpeg:
        ydl_opts["ffmpeg_location"] = ffmpeg_bin
        print(f"🚀 Usando FFmpeg: {ffmpeg_bin}")
    if not has_ffmpeg:
        print("⚠️ FFmpeg não encontrado, usando modo nativo limitado")

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
