import json
import os
import requests
from mutagen.id3 import ID3, APIC, TIT2, TPE1, TALB, TPE2, COMM
from mutagen.mp4 import MP4, MP4Cover
from mutagen.flac import FLAC, Picture
import re

def embed_artwork(audio_path, cover_path):
    """
    Embutir capa do álbum diretamente no arquivo usando Mutagen.
    """
    try:
        if not os.path.exists(audio_path) or not os.path.exists(cover_path):
            return json.dumps({"success": False, "error": "File not found"})

        with open(cover_path, "rb") as f:
            image_data = f.read()

        if audio_path.lower().endswith(".mp3"):
            try:
                audio = ID3(audio_path)
            except Exception:
                audio = ID3()
            
            audio.delall("APIC")
            audio.add(APIC(
                encoding=3,
                mime="image/jpeg",
                type=3,
                desc="Cover",
                data=image_data
            ))
            audio.save(audio_path, v2_version=3)
            
        elif audio_path.lower().endswith((".m4a", ".mp4")):
            audio = MP4(audio_path)
            fmt = MP4Cover.FORMAT_JPEG if cover_path.lower().endswith((".jpg", ".jpeg")) else MP4Cover.FORMAT_PNG
            audio["covr"] = [MP4Cover(image_data, imageformat=fmt)]
            audio.save()
            
        elif audio_path.lower().endswith(".flac"):
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

def write_metadata(path, title, artist, album, year=None, album_art_path=None):
    """
    Escrever metadados completos (incluindo ano) e embutir capa.
    """
    try:
        if not os.path.exists(path):
            return json.dumps({"success": False, "error": "Audio file not found"})

        if path.lower().endswith(".mp3"):
            from mutagen.id3 import TDRC
            try:
                audio = ID3(path)
            except Exception:
                audio = ID3()
            
            # Limpeza
            for tag in ["TIT2", "TPE1", "TALB", "TPE2", "TDRC"]:
                audio.delall(tag)
            
            audio.add(TIT2(encoding=3, text=title))
            audio.add(TPE1(encoding=3, text=artist))
            audio.add(TALB(encoding=3, text=album))
            audio.add(TPE2(encoding=3, text=artist))
            audio.add(COMM(encoding=3, lang="por", desc="source", text="YTDown"))
            
            if year:
                audio.add(TDRC(encoding=3, text=str(year)))
            
            if album_art_path and os.path.exists(album_art_path):
                with open(album_art_path, "rb") as f:
                    audio.delall("APIC")
                    audio.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="Cover", data=f.read()))
            
            audio.save(path, v2_version=3)

        elif path.lower().endswith((".m4a", ".mp4")):
            from mutagen.mp4 import MP4
            audio = MP4(path)
            audio["\xa9nam"] = title
            audio["\xa9ART"] = artist
            audio["\xa9alb"] = album
            audio["aART"] = artist
            
            if year:
                audio["\xa9day"] = str(year)
            
            if album_art_path and os.path.exists(album_art_path):
                with open(album_art_path, "rb") as f:
                    fmt = MP4Cover.FORMAT_JPEG if album_art_path.lower().endswith((".jpg", ".jpeg")) else MP4Cover.FORMAT_PNG
                    audio["covr"] = [MP4Cover(f.read(), imageformat=fmt)]
            
            audio.save()

        elif path.lower().endswith(".flac"):
            audio = FLAC(path)
            audio["title"] = title
            audio["artist"] = artist
            audio["album"] = album
            if year:
                audio["date"] = str(year)
            
            if album_art_path and os.path.exists(album_art_path):
                with open(album_art_path, "rb") as f:
                    audio.clear_pictures()
                    pic = Picture()
                    pic.type = 3
                    pic.mime = "image/jpeg"
                    pic.data = f.read()
                    audio.add_picture(pic)
            audio.save()

        return json.dumps({"success": True})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})

def extract_metadata_from_filename(filename):
    """
    Extrai artista e título do nome do arquivo.
    """
    name = os.path.splitext(filename)[0]
    # Limpeza básica de números de faixa no início
    name = re.sub(r"^\d+[\s.-]+", "", name)
    
    # Padrao: Artista - Musica ou Artista - Album - Musica
    parts = [p.strip() for p in name.split("-")]
    
    if len(parts) >= 2:
        return json.dumps({
            "artist": parts[0],
            "title": parts[-1],
            "album": parts[1] if len(parts) > 2 else "Unknown"
        })
    
    return json.dumps({"artist": "Unknown", "title": name, "album": "Unknown"})
