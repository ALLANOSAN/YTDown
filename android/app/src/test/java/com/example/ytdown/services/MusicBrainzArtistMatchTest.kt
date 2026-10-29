package com.example.ytdown.services

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A busca `artist:"Queen"` do MusicBrainz e Lucene: a frase casa com qualquer
 * artista que CONTENHA o token — "Queen Latifah", "Queen Tribute Band". O
 * picker recebia o artista e nao o usava: a gravacao mais antiga de outro
 * artista vencia, e o MusicBrainz sobrescrevia titulo E artista do arquivo.
 * Era assim que faixas apareciam com o nome da banda errada.
 *
 * Fixtures montados a mao no formato da API (recording + artist-credit +
 * releases); os nomes ilustram a ambiguidade, nao sao respostas gravadas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MusicBrainzArtistMatchTest {

    private fun recording(id: String, title: String, artist: String, album: String, date: String) = """
        { "id": "$id", "title": "$title",
          "artist-credit": [{"name": "$artist", "artist": {"id": "a-$id", "name": "$artist"}}],
          "releases": [{ "id": "r-$id", "title": "$album", "date": "$date",
            "release-group": {"id": "rg-$id", "primary-type": "Album"} }] }
    """

    @Test
    fun `gravacao mais antiga de outro artista nao vence a do artista buscado`() {
        val recordings = JSONArray(
            "[" + recording("tributo", "Love of My Life", "The Queen Tribute Band", "Tributo", "1970") + "," +
                recording("queen", "Love of My Life", "Queen", "A Night at the Opera", "1975") + "]"
        )

        val escolha = MusicBrainzService.pickOriginalRecording(recordings, "Queen", "Love of My Life")

        assertEquals("A Night at the Opera", escolha?.release?.optString("title"))
    }

    /**
     * So vieram gravacoes de outros artistas: nao ha match. Devolver uma delas
     * trocava o artista do arquivo por "Queen Latifah" — sem resultado, o
     * pipeline mantem o que ja sabia.
     */
    @Test
    fun `sem gravacao do artista buscado nao devolve a de outro artista`() {
        val recordings = JSONArray(
            "[" + recording("latifah", "Love of My Life", "Queen Latifah", "Outro Disco", "1993") + "]"
        )

        assertNull(MusicBrainzService.pickOriginalRecording(recordings, "Queen", "Love of My Life"))
    }

    /**
     * Nenhum titulo bate: o "vale tudo" antigo devolvia outra musica da mesma
     * banda, e o titulo do arquivo virava "Bohemian Rhapsody".
     */
    @Test
    fun `outra musica do mesmo artista nao substitui o titulo buscado`() {
        val recordings = JSONArray(
            "[" + recording("bohemian", "Bohemian Rhapsody", "Queen", "A Night at the Opera", "1975") + "]"
        )

        assertNull(MusicBrainzService.pickOriginalRecording(recordings, "Queen", "Love of My Life"))
    }

    /** O motivo do fallback continua valendo: sufixo de versao nao e outra musica. */
    @Test
    fun `titulo com sufixo de versao ainda e a mesma musica`() {
        val recordings = JSONArray(
            "[" + recording("stereo", "Love of My Life (stereo)", "Queen", "A Night at the Opera", "1975") + "]"
        )

        val escolha = MusicBrainzService.pickOriginalRecording(recordings, "Queen", "Love of My Life")

        assertEquals("A Night at the Opera", escolha?.release?.optString("title"))
    }

    @Test
    fun `artista com The na frente ainda e o mesmo artista`() {
        val recordings = JSONArray(
            "[" + recording("scorpions", "Blackout", "Scorpions", "Blackout", "1982") + "]"
        )

        val escolha = MusicBrainzService.pickOriginalRecording(recordings, "The Scorpions", "Blackout")

        assertEquals("Blackout", escolha?.release?.optString("title"))
    }
}
