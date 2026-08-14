# Fuzzing dos parsers (Atheris)

Os parsers de `metadata.py` e `helpers.py` comem string e bytes que vêm de
fora: nome de arquivo do disco, título do YouTube, campos do MusicBrainz e
resposta HTTP de capa. Teste de unidade cobre os casos que alguém pensou;
estes harnesses cobrem os que ninguém pensou.

## Setup

Atheris não tem wheel para Python 3.14 — use 3.12:

```bash
python3.12 -m venv /tmp/fuzzenv
/tmp/fuzzenv/bin/pip install atheris requests mutagen
```

## Rodar

```bash
cd android/app/src/main/python/fuzz

# parsers de texto (nome de arquivo, título, número de faixa)
/tmp/fuzzenv/bin/python fuzz_text_parsers.py -max_total_time=180 -max_len=8192

# sniffing de imagem e mime da capa
/tmp/fuzzenv/bin/python fuzz_image_sniff.py -max_total_time=120 -max_len=2048
```

Passe um diretório de corpus como último argumento para acumular entradas
entre campanhas. `-artifact_prefix=crashes/` grava o input que quebrou.

## Por que os harnesses afirmam invariantes

Boa parte desse código engoli exceção e devolve fallback, então procurar só
por crash acha pouco. Os harnesses afirmam os contratos que o Kotlin depende:
retorno é JSON válido com `title`/`artist`, `_parse_mp4_number` devolve
`None` ou int representável, `_looks_like_image` só aprova blob com assinatura
de imagem conhecida, e `_guess_image_mime` só devolve mime suportado.

## Achados

- `_parse_mp4_number` levantava `ValueError` com número de faixa acima de
  65535 (`trkn`/`disk` do MP4 são pares de uint16) e acima de 4300 dígitos
  (limite de conversão do Python). Nos dois casos a gravação **inteira** de
  tags falhava e o arquivo ficava sem tag nenhuma e sem capa. O campo `number`
  do MusicBrainz é texto livre. Corrigido; regressão travada em
  `tests/test_metadata.py::test_parse_mp4_number_respeita_o_limite_do_campo`.
