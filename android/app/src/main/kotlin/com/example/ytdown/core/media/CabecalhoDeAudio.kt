package com.example.ytdown.core.media

/**
 * Formato de um arquivo de audio lido do CONTEUDO, nao do nome.
 *
 * O metadata.py escolhe o gravador pela extensao, e a copia de um `content://`
 * do MediaStore nao tem extensao confiavel: o chute "mp3" colou tag ID3 na
 * frente de m4a e quebrou 19 arquivos no aparelho. O MediaStore, por sua vez,
 * passa a dizer `audio/mpeg` para esses arquivos — o tipo dele tambem mente.
 */
object CabecalhoDeAudio {

    /** Bytes a ler do inicio do arquivo para decidir — cobre tag ID3 com capa. */
    const val BYTES_PARA_LER = 512 * 1024

    /**
     * Onde comeca o MP4 escondido atras de uma tag ID3, ou nulo se o arquivo
     * nao tem esse estrago. O corte em si e de quem chama.
     */
    fun inicioDoMp4(cabecalho: ByteArray): Int? {
        val fimDaTag = fimDaTagId3(cabecalho) ?: return null
        return fimDaTag.takeIf { ehMp4(cabecalho, it) }
    }

    /** "m4a", "mp3" ou nulo — so os formatos que o metadata.py grava. */
    fun extensao(cabecalho: ByteArray): String? {
        if (ehMp4(cabecalho, 0)) return "m4a"
        val fimDaTag = fimDaTagId3(cabecalho)
        if (fimDaTag != null) return if (ehMp4(cabecalho, fimDaTag)) "m4a" else "mp3"
        return if (ehFrameMp3(cabecalho, 0)) "mp3" else null
    }

    private fun fimDaTagId3(b: ByteArray): Int? {
        if (b.size < 10 || b[0] != 'I'.code.toByte() || b[1] != 'D'.code.toByte() || b[2] != '3'.code.toByte()) {
            return null
        }
        val tamanho = ((b[6].toInt() and 0x7f) shl 21) or ((b[7].toInt() and 0x7f) shl 14) or
            ((b[8].toInt() and 0x7f) shl 7) or (b[9].toInt() and 0x7f)
        val rodape = if (b[5].toInt() and 0x10 != 0) 10 else 0
        return 10 + tamanho + rodape
    }

    private fun ehMp4(b: ByteArray, inicio: Int): Boolean =
        inicio + 8 <= b.size && String(b, inicio + 4, 4, Charsets.ISO_8859_1) == "ftyp"

    private fun ehFrameMp3(b: ByteArray, inicio: Int): Boolean =
        inicio + 2 <= b.size && b[inicio] == 0xFF.toByte() && (b[inicio + 1].toInt() and 0xE0) == 0xE0
}
