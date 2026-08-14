"""Fuzz do sniffing de imagem e do mime da capa.

`_looks_like_image` é o portão que impede arquivo local arbitrário e página de
erro HTTP de virarem capa embutida no arquivo do usuário. Ele come bytes crus
vindos de rede e de disco.
"""
import os
import sys

import atheris

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, SRC)

with atheris.instrument_imports():
    import helpers

MIMES_VALIDOS = {"image/jpeg", "image/png", "image/webp"}


def test_one_input(data: bytes):
    fdp = atheris.FuzzedDataProvider(data)
    blob = fdp.ConsumeBytes(fdp.remaining_bytes() // 2)
    url = fdp.ConsumeUnicodeNoSurrogates(fdp.remaining_bytes())

    ok = helpers._looks_like_image(blob)
    assert isinstance(ok, bool), f"nao e bool: {ok!r}"

    # O portão nunca pode aprovar conteúdo que não comece com uma assinatura
    # de imagem conhecida — é isso que impede cookies.txt de virar capa.
    if ok:
        assert (
            blob.startswith(b"\xff\xd8\xff")
            or blob.startswith(b"\x89PNG\r\n\x1a\n")
            or (blob.startswith(b"RIFF") and blob[8:12] == b"WEBP")
            or blob.startswith(b"GIF87a")
            or blob.startswith(b"GIF89a")
            or blob.startswith(b"BM")
        ), f"aprovou blob sem assinatura de imagem: {blob[:16]!r}"

    # O mime vai direto pro frame APIC do ID3; tem que ser um dos suportados.
    mime = helpers._guess_image_mime(url, blob)
    assert mime in MIMES_VALIDOS, f"mime invalido {mime!r} para url={url!r}"


def main():
    atheris.Setup(sys.argv, test_one_input)
    atheris.Fuzz()


if __name__ == "__main__":
    main()
