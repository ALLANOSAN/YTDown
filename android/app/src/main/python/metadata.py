import importlib
import json
import os
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

def rewrite_file_metadata(filepath, title=None, artist=None, album=None, artwork_url=None, lyrics=None):
    try:
        if filepath.lower().endswith(".mp3"):
            return _write_mp3_id3_tags(filepath, title, artist, album, artwork_url, lyrics)
        elif filepath.lower().endswith((".m4a", ".mp4")):
            return _write_mp4_m4a_tags(filepath, title, artist, album, artwork_url, lyrics)
        return json.dumps({"success": False, "error": "Formato não suportado"})
    except Exception as e:
        return json.dumps(_failure_payload(str(e), stage="rewrite_file_metadata"))

def _write_mp3_id3_tags(filepath, title, artist, album, thumbnail_url=None, lyrics=None):
    mutagen_id3 = _import_mutagen_submodule("mutagen.id3")
    APIC = mutagen_id3.APIC
    COMM = mutagen_id3.COMM
    ID3 = mutagen_id3.ID3
    ID3NoHeaderError = mutagen_id3.ID3NoHeaderError
    TALB = mutagen_id3.TALB
    TIT2 = mutagen_id3.TIT2
    TPE1 = mutagen_id3.TPE1
    TPE2 = mutagen_id3.TPE2
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

def _write_mp4_m4a_tags(filepath, title, artist, album, thumbnail_url=None, lyrics=None):
    mutagen_mp4 = _import_mutagen_submodule("mutagen.mp4")
    MP4 = mutagen_mp4.MP4
    MP4Cover = mutagen_mp4.MP4Cover

    audio = MP4(filepath)
    audio["\xa9nam"] = str(title)
    audio["\xa9ART"] = str(artist)
    audio["\xa9alb"] = str(album)
    audio["\xa9cmt"] = "YTDown"
    
    if lyrics:
        audio["\xa9lyr"] = str(lyrics)
    
    image_data = _download_thumbnail_bytes(thumbnail_url)
    if image_data:
        mime = _guess_image_mime(thumbnail_url, image_data)
        fmt = MP4Cover.FORMAT_PNG if "png" in mime else MP4Cover.FORMAT_JPEG
        audio["covr"] = [MP4Cover(image_data, imageformat=fmt)]
    
    audio.save()
    return json.dumps({"success": True})
