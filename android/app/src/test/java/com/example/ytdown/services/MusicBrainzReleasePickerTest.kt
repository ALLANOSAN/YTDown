package com.example.ytdown.services

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O MusicBrainz devolve os releases de um recording em ordem arbitraria, e a
 * coletanea cai em primeiro com frequencia. Pegar `releases[0]` cego gravava
 * "Heavy Righteous Metal" (uma coletanea de varios artistas) como album de
 * "Love on the Line", do Whitecross.
 *
 * O fixture abaixo e a resposta real da API para
 * `recording:"Love on the Line" AND artist:"Whitecross"` — reduzida aos campos
 * que o picker le, sem alterar ordem nem valores.
 *
 * Robolectric e necessario porque `returnDefaultValues = true` faz o org.json
 * stub do android.jar devolver default silenciosamente em vez de parsear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MusicBrainzReleasePickerTest {

    private val whitecrossLoveOnTheLine = JSONArray(
        """
        [{ "id": "31e6cbe0-d0aa-4505-ab7a-262b692acadc", "title": "Love on the Line", "releases": [
          {
            "id": "d44c85bf-1ce7-41c4-9c70-ea817ccec6c6",
            "title": "Heavy Righteous Metal",
            "date": "1988",
            "release-group": {
              "id": "94d66b27-e8ee-34bc-a9be-c44dca26867b",
              "primary-type": "Album",
              "secondary-types": ["Compilation"]
            }
          },
          {
            "id": "1e35af1d-3a76-4d9f-ac08-51589031276f",
            "title": "Love on the Line",
            "date": "1988",
            "release-group": {
              "id": "6ee6832e-69d5-4e57-8f8c-96fbb67283bf",
              "primary-type": "Album"
            }
          },
          {
            "id": "b205355d-9dc2-4eeb-a937-f90fc71ed62e",
            "title": "Ready to Rock",
            "date": null,
            "release-group": {
              "id": "1a4f8c1a-8170-3751-9300-a0fe9cf8362e",
              "primary-type": "Album",
              "secondary-types": ["Compilation"]
            }
          }
        ]}]
        """.trimIndent()
    )

    @Test
    fun `escolhe o album de estudio em vez da coletanea que veio em primeiro`() {
        val escolha = MusicBrainzService.pickOriginalRecording(whitecrossLoveOnTheLine, "Whitecross")

        assertEquals("Love on the Line", escolha?.release?.optString("title"))
    }

    /**
     * Resposta real para `recording:"Enough Is Enough" AND artist:"Whitecross"`.
     * Aqui cada recording traz UM release so — a coletanea nao esta dentro de um
     * recording, ela e um recording inteiro. Escolher dentro de `releases` nunca
     * acerta este caso; a selecao tem que ser sobre os pares (recording, release).
     * A faixa estreou no album homonimo de 1987.
     */
    private val whitecrossEnoughIsEnough = JSONArray(
        """
        [
          { "id": "a43c57b6-c67e-4124-b45f-a37c4180efa0", "title": "Enough Is Enough", "score": 100,
            "releases": [ { "id": "acf5372e-3248-4777-b946-20088c653875", "title": "The Very Best Of Whitecross", "date": null,
              "release-group": { "primary-type": "Album", "secondary-types": ["Compilation"] } } ] },
          { "id": "d9e7e212-a6d9-4574-adc0-cdfb697b3dc2", "title": "Enough Is Enough", "score": 100,
            "releases": [ { "id": "1e35af1d-3a76-4d9f-ac08-51589031276f", "title": "Love on the Line", "date": "1988",
              "release-group": { "primary-type": "Album" } } ] },
          { "id": "12643328-6c89-4a1f-9757-465500b13373", "title": "Enough Is Enough", "score": 100,
            "releases": [ { "id": "8472e3a9-5371-420e-92d0-547d409a73c3", "title": "To the Limit: The Best of Whitecross", "date": "1993",
              "release-group": { "primary-type": "Album", "secondary-types": ["Compilation"] } } ] },
          { "id": "9d191f38-100e-4801-b957-ae682a591942", "title": "Enough is Enough", "score": 100,
            "releases": [ { "id": "9c200040-134c-4ff0-8c84-e788c9f42308", "title": "Whitecross", "date": "1987-09-05",
              "release-group": { "primary-type": "Album" } } ] },
          { "id": "7ee0ed04-93d5-4eec-ba8f-b1a0bdd3a49c", "title": "Enough Is Enough", "score": 100,
            "releases": [ { "id": "02c2569c-5b66-472d-90c5-20154937ec04", "title": "Rock On: Christian Loud", "date": "2002-03-12",
              "release-group": { "primary-type": "Album", "secondary-types": ["Compilation"] } } ] },
          { "id": "bcf89bbb-8363-4647-bcb8-fcc89850a2f5", "title": "Enough Is Enough", "score": 100,
            "releases": [ { "id": "4a92dffe-66d9-4598-991e-e98f3d2ff52d", "title": "Rock of 80's, Volume 1", "date": null,
              "release-group": { "primary-type": "Album", "secondary-types": ["Compilation"] } } ] },
          { "id": "03a71c52-b2c9-41ad-92b6-15ffd899f701", "title": "Enough Is Enough", "score": 100,
            "releases": [ { "id": "f2b25ceb-62ef-4b75-9845-97e12e1c7cae", "title": "Nineteen Eighty Seven", "date": "2005",
              "release-group": { "primary-type": "Album" } } ] }
        ]
        """.trimIndent()
    )

    @Test
    fun `escolhe o album de estreia entre varios recordings ignorando coletaneas`() {
        val escolha = MusicBrainzService.pickOriginalRecording(whitecrossEnoughIsEnough, "Whitecross")

        assertEquals("Whitecross", escolha?.release?.optString("title"))
    }
}
