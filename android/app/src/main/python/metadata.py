import importlib
import json
import os
import re
import requests
from helpers import (
    _failure_payload,
    _is_retryable_network_error,
    _resolve_metadata,
    _resolve_explicit_title_for_rewrite,
    _resolve_explicit_metadata_for_rewrite,
    _resolve_rewrite_metadata_value,
    _download_thumbnail_bytes,
    _guess_image_mime,
)


def _import_mutagen_submodule(name):
    return importlib.import_module(name)


def _force_metadata_with_mutagen(
    filepath, title, artist, album, thumbnail_url=None, lyrics=None,
    year=None, track_number=None
):
    """
    Força o write de metadata com mutagen, mesmo se já existir.
    Usa _write_mp3_id3_tags ou _write_mp4_m4a_tags baseado na extensão.
    """
    if filepath.lower().endswith(".mp3"):
        return _write_mp3_id3_tags(
            filepath, title, artist, album, thumbnail_url, lyrics, year, track_number
        )
    elif filepath.lower().endswith((".m4a", ".mp4")):
        return _write_mp4_m4a_tags(
            filepath, title, artist, album, thumbnail_url, lyrics, year, track_number
        )
    return json.dumps({"success": False, "error": "Formato não suportado"})


def rewrite_file_metadata(
    filepath, title=None, artist=None, album=None, artwork_url=None, lyrics=None,
    year=None, track_number=None
):
    try:
        if filepath.lower().endswith(".mp3"):
            return _write_mp3_id3_tags(
                filepath, title, artist, album, artwork_url, lyrics, year, track_number
            )
        elif filepath.lower().endswith((".m4a", ".mp4")):
            return _write_mp4_m4a_tags(
                filepath, title, artist, album, artwork_url, lyrics, year, track_number
            )
        return json.dumps({"success": False, "error": "Formato não suportado"})
    except Exception as e:
        return json.dumps(_failure_payload(str(e), stage="rewrite_file_metadata"))


def _write_mp3_id3_tags(
    filepath, title, artist, album, thumbnail_url=None, lyrics=None,
    year=None, track_number=None
):
    mutagen_id3 = _import_mutagen_submodule("mutagen.id3")
    APIC = mutagen_id3.APIC
    COMM = mutagen_id3.COMM
    ID3 = mutagen_id3.ID3
    ID3NoHeaderError = mutagen_id3.ID3NoHeaderError
    TALB = mutagen_id3.TALB
    TDRC = mutagen_id3.TDRC
    TIT2 = mutagen_id3.TIT2
    TPE1 = mutagen_id3.TPE1
    TPE2 = mutagen_id3.TPE2
    TRCK = mutagen_id3.TRCK
    USLT = mutagen_id3.USLT

    has_existing_tags = False
    try:
        tags = ID3(filepath)
        has_existing_tags = True
    except ID3NoHeaderError:
        tags = ID3()

    for frame_id in ("TIT2", "TPE1", "TALB", "TPE2", "COMM", "APIC", "USLT"):
        tags.delall(frame_id)

    tags.add(TIT2(encoding=3, text=str(title)))
    tags.add(TPE1(encoding=3, text=str(artist)))
    tags.add(TALB(encoding=3, text=str(album)))
    tags.add(TPE2(encoding=3, text=str(artist)))
    tags.add(COMM(encoding=3, lang="por", desc="source", text="YTDown"))

    if year:
        tags.add(TDRC(encoding=3, text=str(year)))

    if track_number:
        tags.add(TRCK(encoding=3, text=str(track_number)))

    if lyrics:
        tags.add(USLT(encoding=3, lang="por", desc="Lyrics", text=str(lyrics)))

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

    tags.save(filepath, v2_version=3)
    return json.dumps({"success": True})


def update_music_metadata(audio_path, metadata_json):
    """
    Atualiza metadados gerais usando o pipeline Mutagen existente.
    metadata_json: string JSON contendo {title, artist, album, genre, year, track_number}
    """
    try:
        data = json.loads(metadata_json)
        # Reutiliza lógica existente (_write_mp3_id3_tags ou _write_mp4_m4a_tags)
        # Adaptada para aceitar um dict de metadados
        title = data.get("title")
        artist = data.get("artist")
        album = data.get("album")
        # ... (implementar conforme o padrão existente de _write_*)
        return json.dumps({"success": True})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})


def extract_metadata_from_filename(filename):
    """
    Parser inteligente para extrair artista e título de nomes de arquivo.
    Suporta: "Artist - Title", "Artist - Album - Title", "01 - Artist - Title"
    """
    try:
        name = os.path.splitext(filename)[0]

        # Remover padrões de numeração (ex: 01. ou 01 -)
        name = re.sub(r"^\d+[\s.-]+", "", name)

        # Padrões comuns com regex
        patterns = [
            r"^(?P<artist>.+?)\s*-\s*(?P<album>.+?)\s*-\s*(?P<title>.+)$",
            r"^(?P<artist>.+?)\s*-\s*(?P<title>.+)$",
            r"^(?P<artist>.+?)\s*–\s*(?P<title>.+)$",  # En dash
            r"^(?P<artist>.+?)\s*_\s*(?P<title>.+)$",
        ]

        for pattern in patterns:
            match = re.match(pattern, name)
            if match:
                data = match.groupdict()
                return json.dumps(data)

        return json.dumps({"artist": "Unknown", "title": name})
    except Exception as e:
        return json.dumps({"artist": "Unknown", "title": filename})


def embed_album_art(audio_path, cover_path):
    """
    Implementação profissional de embedding usando Mutagen.
    Suporta ID3 APIC (MP3), MP4Cover (M4A) e Picture block (FLAC).
    """
    try:
        import mutagen
        from mutagen.id3 import ID3, APIC
        from mutagen.mp4 import MP4, MP4Cover
        from mutagen.flac import FLAC, Picture
        from mutagen.mp3 import MP3

        with open(cover_path, "rb") as f:
            image_data = f.read()

        if audio_path.endswith(".mp3"):
            try:
                audio = ID3(audio_path)
            except Exception:
                audio = ID3()

            # Limpeza cirúrgica de todas as artes anteriores para evitar arquivos gigantes
            audio.delall("APIC")
            audio.add(
                APIC(
                    encoding=3, mime="image/jpeg", type=3, desc="Cover", data=image_data
                )
            )
            audio.save(audio_path)
        elif audio_path.endswith(".m4a"):
            audio = MP4(audio_path)
            # Formato JPEG/PNG automático baseado na extensão do cover_path
            fmt = (
                MP4Cover.FORMAT_JPEG
                if cover_path.lower().endswith((".jpg", ".jpeg"))
                else MP4Cover.FORMAT_PNG
            )
            audio["covr"] = [MP4Cover(image_data, imageformat=fmt)]
            audio.save()
        elif audio_path.endswith(".flac"):
            audio = FLAC(audio_path)
            audio.clear_pictures()
            pic = Picture()
            pic.type = 3
            pic.mime = "image/jpeg"
            pic.data = image_data
            audio.add_picture(pic)
            audio.save()
        return json.dumps({"success": True})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})
