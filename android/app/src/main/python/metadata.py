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
    year=None, track_number=None, disc_number=None
):
    """
    Força o write de metadata com mutagen, mesmo se já existir.
    Usa _write_mp3_id3_tags ou _write_mp4_m4a_tags baseado na extensão.
    """
    if filepath.lower().endswith(".mp3"):
        return _write_mp3_id3_tags(
            filepath, title, artist, album, thumbnail_url, lyrics, year, track_number, disc_number
        )
    elif filepath.lower().endswith((".m4a", ".mp4")):
        return _write_mp4_m4a_tags(
            filepath, title, artist, album, thumbnail_url, lyrics, year, track_number, disc_number
        )
    return json.dumps({"success": False, "error": "Formato não suportado"})


def rewrite_file_metadata(
    filepath, title=None, artist=None, album=None, artwork_url=None, lyrics=None,
    year=None, track_number=None, disc_number=None
):
    try:
        if filepath.lower().endswith(".mp3"):
            return _write_mp3_id3_tags(
                filepath, title, artist, album, artwork_url, lyrics, year, track_number, disc_number
            )
        elif filepath.lower().endswith((".m4a", ".mp4")):
            return _write_mp4_m4a_tags(
                filepath, title, artist, album, artwork_url, lyrics, year, track_number, disc_number
            )
        return json.dumps({"success": False, "error": "Formato não suportado"})
    except Exception as e:
        return json.dumps(_failure_payload(str(e), stage="rewrite_file_metadata"))


def _write_mp3_id3_tags(
    filepath, title, artist, album, thumbnail_url=None, lyrics=None,
    year=None, track_number=None, disc_number=None
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
    TPOS = mutagen_id3.TPOS
    USLT = mutagen_id3.USLT

    has_existing_tags = False
    try:
        tags = ID3(filepath)
        has_existing_tags = True
    except mutagen_id3.ID3UnsupportedVersionError:
        # ponytail: header ID3v2.0 (nunca suportado) num arquivo baixado
        # corrompido — remove o header (10 bytes + tamanho syncsafe)
        # preservando o áudio; o save() abaixo releria o header e falharia
        with open(filepath, "rb") as f:
            header = f.read(10)
            size = 0
            for b in header[6:10]:
                size = (size << 7) | b
            f.seek(10 + size)
            audio = f.read()
        with open(filepath, "wb") as f:
            f.write(audio)
        tags = ID3()
    except ID3NoHeaderError:
        tags = ID3()

    # APIC/USLT ficam de fora: valor None significa "não mexi nisso", não
    # "apague". Apagar aqui destruía a capa embutida em todo rewrite sem
    # artwork (botão Reparar Tags, renomear artista/álbum em lote).
    for frame_id in ("TIT2", "TPE1", "TALB", "TPE2", "COMM"):
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

    if disc_number:
        tags.add(TPOS(encoding=3, text=str(disc_number)))

    if lyrics:
        tags.delall("USLT")
        tags.add(USLT(encoding=3, lang="por", desc="Lyrics", text=str(lyrics)))

    image_data = _download_thumbnail_bytes(thumbnail_url)
    if image_data:
        tags.delall("APIC")
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


_LEADING_DIGITS_RE = re.compile(r"\d+")
# trkn/disk do MP4 são pares de uint16 — acima disso o mutagen recusa a tag
# com "invalid numeric pair" e leva a gravação inteira junto.
_MP4_NUMBER_MAX = 65535


def _parse_mp4_number(value):
    """
    trkn/disk do MP4 são tuplas de int. O MusicBrainz devolve "B1" (lado do
    vinil) e "5/12" — int() direto estourava e derrubava a gravação INTEIRA
    do arquivo, deixando ele sem nenhuma tag e sem capa.

    Devolve o primeiro número representável no campo, ou None. Fora da faixa
    ou com dígitos demais (int() do Python levanta acima de 4300) vira None:
    perder o número da faixa é aceitável, perder todas as tags não.
    """
    if value is None:
        return None
    match = _LEADING_DIGITS_RE.search(str(value))
    if not match:
        return None
    digits = match.group()
    if len(digits) > len(str(_MP4_NUMBER_MAX)):
        return None
    number = int(digits)
    return number if number <= _MP4_NUMBER_MAX else None


def _write_mp4_m4a_tags(
    filepath, title, artist, album, thumbnail_url=None, lyrics=None,
    year=None, track_number=None, disc_number=None
):
    """
    Escreve metadados em arquivos M4A/MP4 usando mutagen.mp4.
    Tags MP4: ©nam (title), ©ART (artist), ©alb (album), ©day (year),
              trkn (track number), disk (disc number), ©lyr (lyrics), covr (cover)
    """
    mutagen_mp4 = _import_mutagen_submodule("mutagen.mp4")
    MP4 = mutagen_mp4.MP4
    MP4Cover = mutagen_mp4.MP4Cover

    # Sem fallback para MP4(): sem filename o save() sempre estourava com
    # "Missing filename or fileobj argument", escondendo o erro real (arquivo
    # inexistente, container inválido) do payload e do Crashlytics.
    audio = MP4(filepath)

    # Limpa só o que é sempre reescrito. Campos opcionais (©day, trkn, disk,
    # ©lyr, covr) e ©gen ficam de fora: valor None significa "não mexi nisso",
    # não "apague" — apagar aqui destruía capa, ano, faixa e gênero em todo
    # rewrite parcial. A atribuição abaixo já sobrescreve quando há valor novo.
    for key in ("©nam", "©ART", "©alb", "aART"):
        try:
            del audio[key]
        except KeyError:
            pass

    audio["©nam"] = [str(title)]
    audio["©ART"] = [str(artist)]
    audio["©alb"] = [str(album)]
    audio["aART"] = [str(artist)]

    if year:
        audio["©day"] = [str(year)]

    # None = "não mexi"; valor informado mas irrepresentável (ex: "A") remove a
    # tag em vez de manter um número antigo errado — e nunca falha o write.
    for key, raw in (("trkn", track_number), ("disk", disc_number)):
        if raw is None:
            continue
        parsed = _parse_mp4_number(raw)
        if parsed is not None:
            audio[key] = [(parsed, 0)]  # tupla (número, total)
        else:
            try:
                del audio[key]
            except KeyError:
                pass

    if lyrics:
        audio["©lyr"] = [str(lyrics)]

    # Embed thumbnail/capa se fornecida
    if thumbnail_url:
        image_data = _download_thumbnail_bytes(thumbnail_url)
        if image_data:
            mime = _guess_image_mime(thumbnail_url, image_data)
            fmt = MP4Cover.FORMAT_JPEG if mime == "image/jpeg" else MP4Cover.FORMAT_PNG
            audio["covr"] = [MP4Cover(image_data, imageformat=fmt)]

    audio.save()
    return json.dumps({"success": True})


_BRACKET_GROUP_RE = re.compile(r"[(\[]([^)\]]*)[)\]]")
_JUNK_TOKEN_RE = re.compile(
    r"^(?:mp3|m4a|aac|ogg|opus|wav|flac|webm|mp4|\d{2,4}(?:k|kb|kbps|kbit)?|k|kb|kbps|kbit|bps)$",
    re.IGNORECASE,
)
# Pelo menos um token precisa ser "forte" (formato ou bitrate com unidade),
# senão "(2011)" seria confundido com lixo e o ano do título sumiria.
_STRONG_JUNK_TOKEN_RE = re.compile(
    r"^(?:mp3|m4a|aac|ogg|opus|wav|flac|webm|mp4|\d{2,4}(?:k|kb|kbps|kbit)|k|kb|kbps|kbit|bps)$",
    re.IGNORECASE,
)
# "01. ", "3 - ", "02) " — numeração com pontuação é sempre faixa
_PUNCTUATED_TRACK_RE = re.compile(r"^\d{1,3}\s*[.\-_)]+\s*")
# "02 " — número com zero à esquerda é faixa; "50 Ways" e "99 Problems" não são
_PADDED_TRACK_RE = re.compile(r"^0\d{0,2}\s+")


def _strip_filename_junk(name):
    """
    Remove numeração de faixa e sufixos de formato/bitrate deixados por rippers,
    ex: "02 Get Back To The Bible(m4a 128k)" -> "Get Back To The Bible".
    """

    def _drop_if_junk(match):
        tokens = [t for t in re.split(r"[\s,._-]+", match.group(1)) if t]
        is_junk = (
            bool(tokens)
            and all(_JUNK_TOKEN_RE.match(t) for t in tokens)
            and any(_STRONG_JUNK_TOKEN_RE.match(t) for t in tokens)
        )
        return "" if is_junk else match.group(0)

    cleaned = _BRACKET_GROUP_RE.sub(_drop_if_junk, name)
    cleaned = _PUNCTUATED_TRACK_RE.sub("", cleaned, count=1)
    cleaned = _PADDED_TRACK_RE.sub("", cleaned, count=1)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned or name.strip()


def extract_metadata_from_filename(filename):
    """
    Parser inteligente para extrair artista e título de nomes de arquivo.
    Suporta: "Artist - Title", "Artist - Album - Title", "01 - Artist - Title"
    """
    try:
        name = os.path.splitext(filename)[0]
        name = _strip_filename_junk(name)

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

        # Artista vazio = "não sei", nunca um sentinela textual: "Unknown" vira
        # `artist:"Unknown"` na query do MusicBrainz e zera o enrichment.
        return json.dumps({"artist": "", "title": name})
    except Exception as e:
        return json.dumps({"artist": "", "title": filename})


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
        else:
            # Sem ramo de embed o arquivo fica sem capa. Reportar sucesso aqui
            # fazia o chamador acreditar que a capa entrou no arquivo quando ela
            # seguiu so no cache — a falha silenciosa nao aparecia nem no log.
            return json.dumps({
                "success": False,
                "error": f"Formato sem suporte a capa: {audio_path.rsplit('.', 1)[-1]}",
            })
        return json.dumps({"success": True})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})


def extract_embedded_artwork(audio_path, output_path):
    """
    Extrai a capa embutida de um arquivo (MP3, M4A ou FLAC) e salva em output_path.
    Retorna JSON {success: True} se extraído com sucesso.
    """
    try:
        import mutagen
        if audio_path.lower().endswith(".mp3"):
            from mutagen.id3 import ID3
            tags = ID3(audio_path)
            apic = tags.getall("APIC")
            if not apic:
                return json.dumps({"success": False, "error": "No APIC frame"})
            data = apic[0].data
        elif audio_path.lower().endswith((".m4a", ".mp4")):
            from mutagen.mp4 import MP4
            audio = MP4(audio_path)
            covr = audio.get("covr")
            if not covr:
                return json.dumps({"success": False, "error": "No covr tag"})
            data = covr[0]
        elif audio_path.lower().endswith(".flac"):
            from mutagen.flac import FLAC
            audio = FLAC(audio_path)
            if not audio.pictures:
                return json.dumps({"success": False, "error": "No pictures"})
            data = audio.pictures[0].data
        else:
            return json.dumps({"success": False, "error": "Unsupported format"})

        with open(output_path, "wb") as f:
            f.write(data)
        return json.dumps({"success": True})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})


def read_file_metadata(filepath):
    """
    Lê metadados existentes de um arquivo de áudio.
    Retorna JSON com: title, artist, album, track_number, disc_number, year, has_artwork
    Se o arquivo não existir ou não tiver tags, retorna campos de texto como strings vazias.
    """
    try:
        if filepath.lower().endswith(".mp3"):
            mutagen_id3 = _import_mutagen_submodule("mutagen.id3")
            ID3 = mutagen_id3.ID3
            ID3NoHeaderError = mutagen_id3.ID3NoHeaderError
            try:
                tags = ID3(filepath)
            except (ID3NoHeaderError, mutagen_id3.ID3UnsupportedVersionError):
                return json.dumps({
                    "success": True, "title": None, "artist": None, "album": None,
                    "track_number": None, "disc_number": None, "year": None, "has_artwork": False
                })
            title = str(tags.get("TIT2", ""))
            artist = str(tags.get("TPE1", ""))
            album = str(tags.get("TALB", ""))
            track = str(tags.get("TRCK", ""))
            disc = str(tags.get("TPOS", ""))
            year = str(tags.get("TDRC", ""))
            # O frame vem com chave "APIC:Cover" (id + desc), então `"APIC" in tags`
            # é sempre False. getall() casa pelo id do frame.
            has_artwork = bool(tags.getall("APIC"))
            return json.dumps({
                "success": True,
                "title": title or "", "artist": artist or "", "album": album or "",
                "track_number": track or "", "disc_number": disc or "", "year": year or "",
                "has_artwork": has_artwork
            })
        elif filepath.lower().endswith((".m4a", ".mp4")):
            mutagen_mp4 = _import_mutagen_submodule("mutagen.mp4")
            MP4 = mutagen_mp4.MP4
            try:
                audio = MP4(filepath)
            except Exception:
                return json.dumps({
                    "success": True, "title": "", "artist": "", "album": "",
                    "track_number": "", "disc_number": "", "year": "", "has_artwork": False
                })
            title = audio.get("©nam", [""])[0]
            artist = audio.get("©ART", [""])[0]
            album = audio.get("©alb", [""])[0]
            track = audio.get("trkn", [("", "")])[0]
            disc = audio.get("disk", [("", "")])[0]
            year = audio.get("©day", [""])[0]
            has_artwork = "covr" in audio
            
            track_str = str(track[0]) if isinstance(track, tuple) and track[0] else ""
            disc_str = str(disc[0]) if isinstance(disc, tuple) and disc[0] else ""
            
            return json.dumps({
                "success": True,
                "title": str(title) if title else "",
                "artist": str(artist) if artist else "",
                "album": str(album) if album else "",
                "track_number": track_str,
                "disc_number": disc_str,
                "year": str(year) if year else "",
                "has_artwork": has_artwork
            })
        elif filepath.lower().endswith(".flac"):
            mutagen_flac = _import_mutagen_submodule("mutagen.flac")
            FLAC = mutagen_flac.FLAC
            try:
                audio = FLAC(filepath)
            except Exception:
                return json.dumps({
                    "success": True, "title": "", "artist": "", "album": "",
                    "track_number": "", "disc_number": "", "year": "", "has_artwork": False
                })
            title = audio.get("title", [""])[0]
            artist = audio.get("artist", [""])[0]
            album = audio.get("album", [""])[0]
            track = audio.get("tracknumber", [""])[0]
            disc = audio.get("discnumber", [""])[0]
            year = audio.get("date", [""])[0]
            has_artwork = len(audio.pictures) > 0
            return json.dumps({
                "success": True,
                "title": str(title) if title else "",
                "artist": str(artist) if artist else "",
                "album": str(album) if album else "",
                "track_number": str(track) if track else "",
                "disc_number": str(disc) if disc else "",
                "year": str(year) if year else "",
                "has_artwork": has_artwork
            })
        return json.dumps({
            "success": True, "title": "", "artist": "", "album": "",
            "track_number": "", "disc_number": "", "year": "", "has_artwork": False
        })
    except Exception as e:
        return json.dumps(_failure_payload(str(e), stage="read_file_metadata"))
