package com.example.ytdown.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobre a limpeza de titulo vindo de nome de arquivo.
 *
 * Bug de origem: arquivos externos escaneados da pasta de musica entram na
 * biblioteca (e sao gravados como tag pelo Mutagen) com o nome cru do arquivo,
 * ex: "02 Get Back To The Bible(m4a 128k)".
 */
class MetadataUtilsTest {

    @Test
    fun `remove numero de faixa e sufixo de formato do nome de arquivo`() {
        assertEquals(
            "Get Back To The Bible",
            MetadataUtils.cleanFilenameTitle("02 Get Back To The Bible(m4a 128k)")
        )
    }

    @Test
    fun `remove numero de faixa com ponto`() {
        assertEquals("Song Name", MetadataUtils.cleanFilenameTitle("01. Song Name"))
    }

    @Test
    fun `remove numero de faixa com hifen`() {
        assertEquals("Artist - Song", MetadataUtils.cleanFilenameTitle("3 - Artist - Song"))
    }

    @Test
    fun `preserva numero que faz parte do titulo`() {
        assertEquals(
            "50 Ways to Leave Your Lover",
            MetadataUtils.cleanFilenameTitle("50 Ways to Leave Your Lover")
        )
        assertEquals("99 Problems", MetadataUtils.cleanFilenameTitle("99 Problems"))
    }

    @Test
    fun `preserva parenteses que nao sao lixo de formato`() {
        assertEquals(
            "Bohemian Rhapsody (Remastered 2011)",
            MetadataUtils.cleanFilenameTitle("Bohemian Rhapsody (Remastered 2011)")
        )
        assertEquals("Song (Live)", MetadataUtils.cleanFilenameTitle("Song (Live)"))
    }

    @Test
    fun `remove bitrate em colchetes`() {
        assertEquals("Song Name", MetadataUtils.cleanFilenameTitle("Song Name [320kbps]"))
        assertEquals("Song Name", MetadataUtils.cleanFilenameTitle("Song Name [128 kbps]"))
    }

    @Test
    fun `remove formato sozinho`() {
        assertEquals("Track", MetadataUtils.cleanFilenameTitle("Track (mp3)"))
        assertEquals("Track", MetadataUtils.cleanFilenameTitle("Track (320K)"))
    }

    @Test
    fun `mantem comportamento de normalizeMetadataText`() {
        assertEquals("Some Song", MetadataUtils.cleanFilenameTitle("Some_Song"))
        assertEquals("Some Song", MetadataUtils.cleanFilenameTitle("Some Song-a1b2c3d4"))
    }

    @Test
    fun `nao apaga o titulo inteiro`() {
        assertEquals("128k", MetadataUtils.cleanFilenameTitle("128k"))
        assertEquals("02", MetadataUtils.cleanFilenameTitle("02"))
    }

    /**
     * "Unknown" e similares sao sentinelas do parser de filename, nao artista.
     * Se vazarem, viram `artist:"Unknown"` na query do MusicBrainz (0 resultados)
     * e ainda fazem o guard de fallback rejeitar o match correto.
     */
    @Test
    fun `sanitizeArtist zera sentinelas de artista desconhecido`() {
        assertEquals("", MetadataUtils.sanitizeArtist("Unknown"))
        assertEquals("", MetadataUtils.sanitizeArtist("unknown artist"))
        assertEquals("", MetadataUtils.sanitizeArtist("Desconhecido"))
        assertEquals("", MetadataUtils.sanitizeArtist("YTDown"))
        assertEquals("", MetadataUtils.sanitizeArtist("N/A"))
        assertEquals("", MetadataUtils.sanitizeArtist("   "))
        assertEquals("", MetadataUtils.sanitizeArtist(null))
    }

    /**
     * O gêmeo Python (_strip_generated_suffix) remove o sufixo ANTES de trocar
     * "_" por espaço. O Kotlin trocava primeiro, então o sufixo gerado com
     * underscore — o caso comum de arquivo temporário — nunca era removido.
     */
    @Test
    fun `remove sufixo gerado com underscore`() {
        assertEquals("Some Song", MetadataUtils.normalizeMetadataText("Some Song_a1b2c3d4"))
        assertEquals("Some Song", MetadataUtils.normalizeMetadataText("Some Song-a1b2c3d4"))
        assertEquals(
            "Some Song",
            MetadataUtils.normalizeMetadataText("Some Song_550e8400-e29b-41d4-a716-446655440000")
        )
    }

    @Test
    fun `underscore comum continua virando espaco`() {
        assertEquals("Some Song", MetadataUtils.normalizeMetadataText("Some_Song"))
        assertEquals("A B C", MetadataUtils.normalizeMetadataText("A__B___C"))
    }

    /**
     * O botao "Reparar Tags" pulava item cujo titulo apenas EXISTE. Um titulo
     * como "02 Get Back To The Bible(m4a 128k)" nao e sentinela, entao passava
     * como "ja tem tag" e nunca era corrigido — justamente o caso que o botao
     * deveria resolver.
     */
    @Test
    fun `titulo com lixo de nome de arquivo precisa de reparo`() {
        assertTrue(
            MetadataUtils.needsMetadataRepair(
                "02 Get Back To The Bible(m4a 128k)", "Petra", "Petra", hasArtwork = true
            )
        )
        assertTrue(MetadataUtils.needsMetadataRepair("01. Song Name", "A", "B", hasArtwork = true))
        assertTrue(MetadataUtils.needsMetadataRepair("Song [320kbps]", "A", "B", hasArtwork = true))
        assertTrue(MetadataUtils.needsMetadataRepair("Song_a1b2c3d4", "A", "B", hasArtwork = true))
    }

    @Test
    fun `metadado limpo e completo nao precisa de reparo`() {
        assertFalse(
            MetadataUtils.needsMetadataRepair(
                "Get Back to the Bible", "Petra", "Petra", hasArtwork = true
            )
        )
    }

    /** Se marcar titulo legitimo como sujo, todo scan reprocessa tudo e martela o MusicBrainz. */
    @Test
    fun `titulo legitimo nao e confundido com lixo`() {
        for (titulo in listOf(
            "50 Ways to Leave Your Lover",
            "99 Problems",
            "Song (Live)",
            "Bohemian Rhapsody (Remastered 2011)",
            "AC/DC Live"
        )) {
            assertFalse(
                "marcou como sujo: $titulo",
                MetadataUtils.needsMetadataRepair(titulo, "Artista", "Album", hasArtwork = true)
            )
        }
    }

    @Test
    fun `sentinela em qualquer campo precisa de reparo`() {
        assertTrue(MetadataUtils.needsMetadataRepair("", "A", "B", hasArtwork = true))
        assertTrue(MetadataUtils.needsMetadataRepair("Song", "Desconhecido", "B", hasArtwork = true))
        assertTrue(MetadataUtils.needsMetadataRepair("Song", "A", "YTDown", hasArtwork = true))
        assertTrue(MetadataUtils.needsMetadataRepair("Song", null, "B", hasArtwork = true))
    }

    @Test
    fun `falta de capa precisa de reparo`() {
        assertTrue(MetadataUtils.needsMetadataRepair("Song", "A", "B", hasArtwork = false))
    }

    @Test
    fun `sanitizeArtist preserva artista real`() {
        assertEquals("Petra", MetadataUtils.sanitizeArtist("Petra"))
        assertEquals("Petra", MetadataUtils.sanitizeArtist("  Petra  "))
        assertEquals("Unknown Mortal Orchestra", MetadataUtils.sanitizeArtist("Unknown Mortal Orchestra"))
    }

    /**
     * O titulo vinha do YouTube com o artista em caixa alta ("WHITECROSS - ...").
     * O codigo antigo testava o prefixo com `startsWith(ignoreCase = true)` mas
     * removia com `removePrefix`, que e case-sensitive e devolve a string
     * intacta quando nao casa exato. A query ia para o MusicBrainz como
     * `recording:"WHITECROSS - Love On The Line"` e zerava o resultado.
     */
    @Test
    fun `remove prefixo do artista mesmo com caixa diferente`() {
        assertEquals(
            "Love On The Line",
            MetadataUtils.stripArtistPrefix("WHITECROSS - Love On The Line", "Whitecross")
        )
    }

    /**
     * 4 dos 171 recordings do Whitecross no MusicBrainz usam o apostrofo
     * tipografico U+2019 ("Angel\u2019s Disguise", "I Keep Prayin\u2019"), enquanto
     * outros usam ASCII ("It's Already Done"). O titulo vindo do YouTube traz
     * ASCII, entao toda comparacao exata falhava justamente nessas faixas.
     * O MusicBrainz tambem alterna caixa ("Enough Is Enough" / "Enough is Enough").
     */
    @Test
    fun `apostrofo tipografico e ASCII normalizam para a mesma forma`() {
        val tipografico = MetadataUtils.normalizeForMatch("Angel\u2019s Disguise")

        assertEquals("angel's disguise", tipografico)
        assertEquals(MetadataUtils.normalizeForMatch("Angel's Disguise"), tipografico)
    }

    /**
     * "Track 07 My Love" chega assim do arquivo baixado. O prefixo nao e
     * sentinela nem numero solto, entao passava por needsMetadataRepair como
     * titulo valido e ia inteiro para a query do MusicBrainz —
     * `recording:"Track 07 My Love"` nao acha nada, e o arquivo termina sem
     * album, sem ano e com a capa errada.
     */
    @Test
    fun `remove prefixo Track NN do titulo`() {
        assertEquals("My Love", MetadataUtils.cleanFilenameTitle("Track 07 My Love"))
    }

    /**
     * A quebra que este teste pega: um padrao sem `\b` engoliria "Tracks",
     * "Tracking", "Trackless" — e o titulo legitimo perderia a primeira palavra.
     */
    @Test
    fun `palavra que apenas comeca com Track e preservada`() {
        assertEquals(
            "Tracks of My Tears",
            MetadataUtils.cleanFilenameTitle("Tracks of My Tears")
        )
        assertEquals("Track Star", MetadataUtils.cleanFilenameTitle("Track Star"))
    }

    /**
     * A quebra que este teste pega: limpar ate sobrar nada. "Track 07" sem
     * titulo depois e tudo que se sabe da faixa; devolver vazio apagaria a
     * unica identificacao que o item tem na biblioteca.
     */
    @Test
    fun `titulo que e so o rotulo da faixa nao vira vazio`() {
        assertEquals("Track 07", MetadataUtils.cleanFilenameTitle("Track 07"))
    }

    /**
     * A quebra que este teste pega: se needsMetadataRepair nao enxergar o
     * rotulo como sujeira, o botao Reparar Tags continua pulando essas faixas e
     * a biblioteca nunca se conserta sozinha — mesmo com a limpeza corrigida.
     */
    @Test
    fun `titulo com rotulo de faixa e marcado para reparo`() {
        assertTrue(
            MetadataUtils.needsMetadataRepair(
                "Track 07 My Love", "Whitecross", "Triumphant Return", hasArtwork = true
            )
        )
    }
}
