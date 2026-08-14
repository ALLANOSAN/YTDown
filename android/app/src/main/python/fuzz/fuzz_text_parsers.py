"""Fuzz dos parsers de texto do YTDown.

Todos comem string que vem de fora: nome de arquivo do disco, título do YouTube
e campos do MusicBrainz. Vários engolem exceção e devolvem fallback, então
procurar só por crash é fraco — o harness afirma os CONTRATOS que o Kotlin
depende (retorno JSON válido, tipos, campos obrigatórios).
"""
import json
import os
import sys

import atheris

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, SRC)

with atheris.instrument_imports():
    import helpers
    import metadata


def check_extract_metadata_from_filename(s):
    out = metadata.extract_metadata_from_filename(s)
    # Contrato com Kotlin: toda função pública devolve json.dumps(...) e o
    # PythonMetadataBridge faz JSONObject(result).optString("artist"/"title").
    assert isinstance(out, str), f"nao e str: {type(out)}"
    data = json.loads(out)  # levanta se nao for JSON valido
    assert "title" in data, f"faltou title: {data!r}"
    assert "artist" in data, f"faltou artist: {data!r}"
    assert isinstance(data["title"], str), f"title nao e str: {data!r}"
    assert isinstance(data["artist"], str), f"artist nao e str: {data!r}"


def check_strip_filename_junk(s):
    out = metadata._strip_filename_junk(s)
    assert isinstance(out, str), f"nao e str: {type(out)}"


def check_parse_mp4_number(s):
    # MP4 trkn/disk sao tupla de int. Esta funcao NUNCA pode levantar: se
    # levantar, derruba a gravacao inteira de tags do arquivo do usuario.
    out = metadata._parse_mp4_number(s)
    assert out is None or isinstance(out, int), f"tipo errado: {out!r}"
    if isinstance(out, int):
        assert out >= 0, f"negativo: {out!r}"


def check_strip_generated_suffix(s):
    out = helpers._strip_generated_suffix(s)
    assert isinstance(out, str), f"nao e str: {type(out)}"


def check_guess_artist_from_title(s):
    out = helpers._guess_artist_from_title(s)
    assert isinstance(out, str), f"nao e str: {type(out)}"


def check_resolve_metadata(s):
    title, artist, album = helpers._resolve_metadata(s, None, None, {})
    # Os tres tem fallback garantido ("Sem título"/"YTDown"); vazio aqui
    # significa tag vazia gravada no arquivo do usuario.
    for nome, v in (("title", title), ("artist", artist), ("album", album)):
        assert isinstance(v, str), f"{nome} nao e str: {v!r}"
        assert v != "", f"{nome} vazio para entrada {s!r}"


def check_normalize_tag_value(s):
    out = helpers._normalize_tag_value(s)
    assert isinstance(out, str), f"nao e str: {type(out)}"


TARGETS = [
    check_extract_metadata_from_filename,
    check_strip_filename_junk,
    check_parse_mp4_number,
    check_strip_generated_suffix,
    check_guess_artist_from_title,
    check_resolve_metadata,
    check_normalize_tag_value,
]


def test_one_input(data: bytes):
    # Byte 0 escolhe o alvo; o resto vira string 1:1 em bytes. Usar
    # FuzzedDataProvider.ConsumeUnicode aqui consumia bytes de forma
    # imprevisível e o fuzzer nunca alcançava entradas longas o bastante
    # para exercitar os limites de conversão numérica.
    if not data:
        return
    idx = data[0] % len(TARGETS)
    s = data[1:].decode("utf-8", errors="replace")
    TARGETS[idx](s)


def main():
    atheris.Setup(sys.argv, test_one_input)
    atheris.Fuzz()


if __name__ == "__main__":
    main()
