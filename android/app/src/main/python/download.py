import json
import os
import traceback
from logger import ChaquopyLogger, log_failure
from runtime import _get_yt_dlp_module
from helpers import (
    _apply_cookies_file,
    _is_pure_playlist_url,
    _failure_payload,
    _is_retryable_network_error,
    _resolve_metadata,
)
from metadata import _force_metadata_with_mutagen


def _prepare_ffmpeg_runtime(native_lib_dir, app_files_dir):
    ChaquopyLogger.debug(f"Preparando runtime FFmpeg. Libs: {native_lib_dir}", category="FLOW")
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
            ChaquopyLogger.debug(f"🚀 PATH atualizado (libs): {native_lib_dir}")

    if app_files_dir and os.path.isdir(app_files_dir):
        _clean_path_append("LD_LIBRARY_PATH", app_files_dir)
        if _clean_path_append("PATH", app_files_dir):
            ChaquopyLogger.debug(f"🚀 PATH atualizado (files): {app_files_dir}")


def _resolve_ffmpeg_binary(native_lib_dir, app_files_dir, binary_name="ffmpeg"):
    """
    No Android 10+, binários só podem ser executados se estiverem no diretório de libs nativas
    e possuírem o prefixo 'lib' e extensão '.so'.
    """
    ChaquopyLogger.debug(f"Buscando binário: {binary_name}", category="STORAGE")
    # 1. Tenta buscar no diretório de bibliotecas nativas (Obrigatório para Android 10+)
    if native_lib_dir:
        names = [f"lib{binary_name}_exe.so", f"lib{binary_name}.so"]
        for name in names:
            path = os.path.join(native_lib_dir, name)
            if os.path.exists(path):
                ChaquopyLogger.debug(f"🎯 Binário nativo encontrado: {path}", category="STORAGE")
                return path

    # 2. Fallback para pasta de arquivos
    if app_files_dir:
        path = os.path.join(app_files_dir, binary_name)
        if os.path.exists(path) and os.access(path, os.X_OK):
            ChaquopyLogger.debug(f"⚠️ Binário encontrado em app_files (fallback): {path}", category="STORAGE")
            return path

    ChaquopyLogger.error(f"❌ Binário {binary_name} não encontrado!", category="STORAGE")
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

    ChaquopyLogger.debug(f"Configurando Áudio: Codec={desired_codec}, Qualidade={preferred_quality}", category="DOWNLOAD")

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
        ChaquopyLogger.error(f"⚠️ FFmpeg ausente! Forçando fallback de {old_ext} para m4a", category="STORAGE")
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

    ChaquopyLogger.debug(f"Configurando Vídeo: Altura={height}, Container={desired_container}", category="DOWNLOAD")

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
            # par (filepath, info): mantém alinhado com a entry válida,
            # mesmo quando entries contém None (vídeos indisponíveis)
            files.append((filepath, entry))
    return files


def _apply_tags_to_files(files_with_info, artist, album, artwork_url):
    if not files_with_info:
        return False

    success = True
    for filepath, info in files_with_info:
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
            ChaquopyLogger.error(f"⚠️ Falha ao injetar tags em {filepath}", category="FLOW")
            success = False
        else:
            ChaquopyLogger.debug(f"✅ Tags injetadas em {filepath}", category="FLOW")
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
    title=None,
    artwork_url=None,
    selected_format=None,
    progress_callback=None,
):
    ChaquopyLogger.info(f"Iniciando download_video: {url}", category="FLOW")

    # /playlist?list=X ignora noplaylist=True e expande as N faixas, que
    # cairiam todas no mesmo output_path. Recusa antes de baixar qualquer
    # byte: a fila deve enviar uma faixa por vez (fetch_video_info expande).
    if _is_pure_playlist_url(url):
        return json.dumps(log_failure(
            "URL de playlist não pode virar um único arquivo — "
            "enfileire uma faixa por vez",
            stage="playlist_url_rejeitada",
        ))

    progress_data = {"percent": 0}
    downloaded_info = None
    final_filename = None
    tags_injected = False

    def progress_hook(d):
        if d["status"] == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate", 0)
            downloaded = d.get("downloaded_bytes", 0)
            
            # Se não temos o total, tentamos pegar a porcentagem pronta do yt-dlp
            if total > 0:
                progress_data["percent"] = int((downloaded / total) * 100)
            elif d.get("_percent_str"):
                try:
                    p_str = d["_percent_str"].replace("%", "").strip()
                    progress_data["percent"] = int(float(p_str))
                except:
                    pass
            
            # Log de progresso para o adb logcat
            speed = d.get("_speed_str", "N/A")
            eta = d.get("_eta_str", "N/A")
            ChaquopyLogger.download(f"⬇️ {progress_data['percent']}% | Velocidade: {speed} | ETA: {eta}")

        if d["status"] == "finished":
            progress_data["percent"] = 100
            ChaquopyLogger.info("✅ Download de arquivo concluído pelo yt-dlp", category="FLOW")

        if progress_callback is not None:
            try:
                progress_callback.onProgress(progress_data["percent"])
            except Exception as e:
                pass

    ffmpeg_bin = _resolve_ffmpeg_binary(native_lib_dir, app_files_dir, "ffmpeg")
    has_ffmpeg = bool(ffmpeg_bin and os.path.exists(ffmpeg_bin))

    if has_ffmpeg:
        _prepare_ffmpeg_runtime(native_lib_dir, app_files_dir)

    ydl_opts = {
        "outtmpl": output_path,
        "progress_hooks": [progress_hook],
        "quiet": True,
        "no_warnings": False,
        "socket_timeout": 60,
        "retries": 5,
        "fragment_retries": 10,
        "logger": ChaquopyLogger,
        # Cada chamada baixa UM vídeo para UM output_path fixo
        # ("Artista - Album - Titulo.%(ext)s"), que não tem placeholder por
        # faixa. Reexpandir a playlist aqui fazia as N faixas caírem no MESMO
        # arquivo, cada uma sobrescrevendo a anterior. A expansão acontece no
        # fetch_video_info, que gera um item de fila por faixa.
        "noplaylist": True,
        # ponytail: vídeos indisponíveis (semi-privados/bot-check) não podem
        # abortar a playlist — pula a entry, baixa o resto
        "ignoreerrors": True,
    }

    # Conta logada via cookies.txt (se importado) desbloqueia videos que o
    # YouTube esconde de acesso anonimo
    _apply_cookies_file(ydl_opts, app_files_dir)

    # SSL CA BUNDLE Diagnostic
    if app_files_dir:
        cacert_candidates = [
            os.path.join(app_files_dir, "cacert.pem"),
            os.path.join(app_files_dir, "python", "cacert.pem"),
        ]
        for cacert_path in cacert_candidates:
            if os.path.isfile(cacert_path):
                ydl_opts["ca_cert"] = cacert_path
                ChaquopyLogger.network(f"🔐 CA bundle configurado: {cacert_path}")
                break

    if has_ffmpeg:
        ydl_opts["ffmpeg_location"] = ffmpeg_bin
        ffprobe_bin = _resolve_ffmpeg_binary(native_lib_dir, app_files_dir, "ffprobe")
        if ffprobe_bin and os.path.exists(ffprobe_bin):
            if os.path.getsize(ffprobe_bin) == os.path.getsize(ffmpeg_bin):
                ChaquopyLogger.error("⚠️ FFprobe placeholder detectado. Ignorando.", category="STORAGE")
                ffprobe_bin = None
        ydl_opts["ffprobe_location"] = ffprobe_bin
        ChaquopyLogger.debug(f"🚀 FFmpeg ativo: {ffmpeg_bin}", category="STORAGE")

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
        ChaquopyLogger.info(f"Preparando módulo yt-dlp...", category="FLOW")
        yt_dlp_module = _get_yt_dlp_module(app_files_dir)
        with yt_dlp_module.YoutubeDL(ydl_opts) as ydl:
            ChaquopyLogger.info(f"Extraindo informações e iniciando download...", category="FLOW")
            downloaded_info = ydl.extract_info(url, download=True)
            # ponytail: com ignoreerrors=True, video unico que falha retorna
            # None em vez de lancar — tratar antes de chamar .get()
            if not downloaded_info:
                return json.dumps(log_failure(
                    "Falha ao obter informações do vídeo (possivelmente indisponível sem login)",
                    stage="extract_info",
                ))
            is_playlist = downloaded_info.get("_type") == "playlist"

            if is_playlist:
                ChaquopyLogger.info("Detectado modo Playlist.", category="FLOW")
                entries = downloaded_info.get("entries", []) or []
                downloaded_files = _resolve_downloaded_files(ydl, entries, format_type)
                if not downloaded_files:
                    return json.dumps(log_failure("Playlist vazia ou falha no download dos itens", stage="playlist_validation"))
                
                final_filename = downloaded_files[0][0]
                tags_injected = _apply_tags_to_files(
                    downloaded_files,
                    artist,
                    album,
                    artwork_url or downloaded_info.get("thumbnail"),
                )
                
                return json.dumps({
                        "success": True,
                        "message": "Playlist download completed",
                        "progress": 100,
                        "filename": final_filename,
                        "filenames": [f for f, _ in downloaded_files],
                        "tags_injected": tags_injected,
                        "detected_title": downloaded_info.get("title", "Playlist"),
                })

            orig_filename = ydl.prepare_filename(downloaded_info)
            final_filename = _find_downloaded_file(orig_filename, format_type)

            if not final_filename or not os.path.exists(final_filename):
                return json.dumps(log_failure(f"Arquivo não encontrado: {final_filename}", stage="final_file_validation"))

            ChaquopyLogger.info(f"📁 Arquivo final localizado: {final_filename}", category="STORAGE")

            # Metadados e Tags - ESTÁGIO FINAL
            try:
                ChaquopyLogger.info("🎨 Iniciando injeção de metadados e capa...", category="FLOW")
                # Se o usuário editou o título no app, prioriza ele sobre o do YouTube.
                # Se o usuário deixou vazio, usa o do YouTube como fallback.
                explicit_title = (title or "").strip() or None
                resolved_title, resolved_artist, resolved_album = _resolve_metadata(
                    explicit_title or downloaded_info.get("title", "Sem título"),
                    artist,
                    album,
                    downloaded_info,
                )
                resolved_artwork_url = artwork_url or downloaded_info.get("thumbnail")
                
                ChaquopyLogger.debug(f"📝 Aplicando: {resolved_title} | Artista: {resolved_artist}", category="FLOW")
                tags_injected = _force_metadata_with_mutagen(
                    final_filename,
                    resolved_title,
                    resolved_artist,
                    resolved_album,
                    resolved_artwork_url,
                )
                if tags_injected:
                    ChaquopyLogger.info("✅ Metadados injetados com sucesso.", category="FLOW")
                else:
                    ChaquopyLogger.error("⚠️ Mutagen falhou ao injetar metadados.", category="FLOW")
            except Exception as e:
                ChaquopyLogger.error(f"⚠️ Erro ao processar metadados: {str(e)}", category="FLOW")

            return json.dumps({
                "success": True,
                "message": "Download completed",
                "progress": 100,
                "filename": final_filename,
                "tags_injected": tags_injected,
                "detected_title": resolved_title,
                "detected_artist": resolved_artist,
                "detected_album": resolved_album,
            })

    except Exception as e:
        return json.dumps(log_failure(e, stage="yt_dlp_execution", retryable=_is_retryable_network_error(e)))


def _find_downloaded_file(orig_filename, format_type):
    if os.path.exists(orig_filename):
        return orig_filename

    base_name = os.path.splitext(orig_filename)[0]
    dir_name = os.path.dirname(orig_filename)

    extensions = {
        "audio": [".m4a", ".webm", ".mp3", ".flac", ".wav", ".aac", ".ogg", ".opus"],
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
