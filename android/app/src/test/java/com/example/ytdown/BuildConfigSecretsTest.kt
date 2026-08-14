package com.example.ytdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As chaves de API vêm do .secrets.json (git-ignored) via buildConfigField.
 * Se alguém adicionar um serviço novo e esquecer de ligar a chave no
 * build.gradle, o campo sai como string vazia e a falha só aparece em runtime
 * como "sem capa". Este teste tranca a fiação.
 */
class BuildConfigSecretsTest {

    @Test
    fun `chave do Lastfm vem do secrets e nao esta vazia`() {
        assertTrue(
            "BuildConfig.LASTFM_API_KEY vazia — confira .secrets.json e build.gradle",
            BuildConfig.LASTFM_API_KEY.isNotBlank()
        )
    }

    @Test
    fun `chave do FanArt vem do secrets e nao esta vazia`() {
        assertTrue(
            "BuildConfig.FANARTTV_API_KEY vazia — confira .secrets.json e build.gradle",
            BuildConfig.FANARTTV_API_KEY.isNotBlank()
        )
    }

    @Test
    fun `chaves nao sao placeholders`() {
        for (chave in listOf(BuildConfig.LASTFM_API_KEY, BuildConfig.FANARTTV_API_KEY)) {
            assertFalse(chave.equals("null", ignoreCase = true))
            assertFalse(chave.startsWith("your"))
            assertFalse(chave.startsWith("<"))
        }
    }
}
