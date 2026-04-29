import importlib
import json
import os

from helpers import (
    _failure_payload,
    _is_retryable_network_error,
    _resolve_metadata,
    _resolve_explicit_title_for_rewrite,
    _resolve_explicit_metadata_for_rewrite,
    _resolve_rewrite_metadata_value,
    _download_thumbnail_bytes,
    _guess_image_mime,
    _write_vorbis_like_tags_for_file,
)


def _import_mutagen_submodule(name):
    return importlib.import_module(name)


def _write_mp3_id3_tags(filepath, title, artist, album, thumbnail_url=None):
    mutagen_id3 = _import_mutagen_submodule("mutagen.id3")
    APIC = mutagen_id3.APIC
    COMM = mutagen_id3.COMM
    ID3 = mutagen_id3.ID3
    ID3NoHeaderError = mutagen_id3.ID3NoHeaderError
    TALB = mutagen_id3.TALB
    TIT2 = mutagen_id3.TIT2
    TPE1 = mutagen_id3.TPE1
    TPE2 = mutagen_id3.TPE2

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

        tags.save(filepath, v2_version=3)

        verify_tags = ID3(filepath)
        verified_tpe1 = ""
        verified_tpe1_value = verify_tags.get("TPE1")
        if verified_tpe1_value:
            verified_tpe1 = str(verified_tpe1_value)

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
    mutagen_mp4 = _import_mutagen_submodule("mutagen.mp4")
    MP4 = mutagen_mp4.MP4
    MP4Cover = mutagen_mp4.MP4Cover
    MP4FreeForm = mutagen_mp4.MP4FreeForm

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
            if mime == "image/jpeg":
                audio["covr"] = [MP4Cover(image_data, imageformat=MP4Cover.FORMAT_JPEG)]
            if mime not in {"image/png", "image/jpeg"}:
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

        if ext in ("m4a", "mp4"):
            ok = _write_mp4_m4a_tags(filepath, title, artist, album, thumbnail_url)
            if ok:
                print("✅ Tags M4A/MP4 (arquivo físico) injetadas")
            return ok

        if ext == "flac":
            mutagen_flac = _import_mutagen_submodule("mutagen.flac")
            FLAC = mutagen_flac.FLAC

            return _write_vorbis_like_tags_for_file(
                filepath,
                title,
                artist,
                album,
                "FLAC",
                FLAC,
            )

        if ext == "webm":
            mutagen_oggvorbis = _import_mutagen_submodule("mutagen.oggvorbis")
            OggVorbis = mutagen_oggvorbis.OggVorbis

            try:
                return _write_vorbis_like_tags_for_file(
                    filepath,
                    title,
                    artist,
                    album,
                    "WebM",
                    OggVorbis,
                )
            except Exception:
                print("⚠️ WebM não suporta tags completas")
                return False

        if ext == "ogg":
            mutagen_oggvorbis = _import_mutagen_submodule("mutagen.oggvorbis")
            OggVorbis = mutagen_oggvorbis.OggVorbis

            return _write_vorbis_like_tags_for_file(
                filepath,
                title,
                artist,
                album,
                "OGG",
                OggVorbis,
            )

        if ext == "opus":
            mutagen_oggopus = _import_mutagen_submodule("mutagen.oggopus")
            OggOpus = mutagen_oggopus.OggOpus

            return _write_vorbis_like_tags_for_file(
                filepath,
                title,
                artist,
                album,
                "OPUS",
                OggOpus,
            )

        if ext == "wav":
            mutagen_wave = _import_mutagen_submodule("mutagen.wave")
            mutagen_id3 = _import_mutagen_submodule("mutagen.id3")
            WAVE = mutagen_wave.WAVE
            ID3 = mutagen_id3.ID3
            TIT2 = mutagen_id3.TIT2
            TPE1 = mutagen_id3.TPE1
            TALB = mutagen_id3.TALB
            TPE2 = mutagen_id3.TPE2

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

        if ext == "aac":
            ok = _write_mp4_m4a_tags(filepath, title, artist, album, thumbnail_url)
            if ok:
                print("✅ Tags AAC (container MP4) injetadas")
            return ok

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
    try:
        if not filepath or not os.path.exists(filepath):
            return json.dumps(
                _failure_payload(
                    "Arquivo não encontrado para regravar metadados",
                    stage="rewrite_file_metadata",
                    retryable=False,
                )
            )

        fallback_title = os.path.splitext(os.path.basename(filepath))[0]
        fallback_seed_title = title or fallback_title
        (
            fallback_resolved_title,
            fallback_resolved_artist,
            fallback_resolved_album,
        ) = _resolve_metadata(
            fallback_seed_title,
            None,
            None,
            {},
        )

        resolved_title = _resolve_rewrite_metadata_value(
            title,
            fallback_resolved_title,
            lambda value: _resolve_explicit_title_for_rewrite(value, fallback_title),
        )
        resolved_artist = _resolve_rewrite_metadata_value(
            artist,
            fallback_resolved_artist,
            _resolve_explicit_metadata_for_rewrite,
        )
        resolved_album = _resolve_rewrite_metadata_value(
            album,
            fallback_resolved_album,
            _resolve_explicit_metadata_for_rewrite,
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

        error_message = None
        if not tags_injected:
            error_message = "Falha ao injetar tags no arquivo"

        payload = {
            "success": tags_injected,
            "tags_injected": tags_injected,
            "title": resolved_title,
            "artist": resolved_artist,
            "album": resolved_album,
            "filePath": filepath,
            "error": error_message,
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
