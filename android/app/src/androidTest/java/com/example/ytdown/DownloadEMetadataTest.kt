package com.example.ytdown

import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Testa no aparelho o que os testes de host nao alcancam: yt-dlp baixando de verdade,
 * ffmpeg convertendo, e o mutagen gravando tag e capa dentro do arquivo.
 *
 * Exige rede. Nao e teste de unidade — e a prova de que o bump de bibliotecas
 * (Room 2.8, Media3 1.11, Compose 1.12, AGP 9.3, Kotlin 2.3.21) nao quebrou o fluxo real.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DownloadEMetadataTest {

    companion object {
        private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

        // Video curto e estavel do proprio YouTube (Blender Foundation, licenca livre).
        private const val URL_CURTA = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"

        private lateinit var saidaDir: File

        @JvmStatic
        @BeforeClass
        fun prepararPython() {
            PythonBridge.initializePython(ctx)
            saidaDir = File(ctx.cacheDir, "teste_download").apply {
                deleteRecursively()
                mkdirs()
            }
        }

        private fun py() = Python.getInstance().getModule("ytdown")

        private fun nativeLibDir() = ctx.applicationInfo.nativeLibraryDir
        private fun appFilesDir() = ctx.filesDir.absolutePath
    }

    // ---------- Parte 1: gravacao de tag e capa, sem rede ----------

    @Test
    fun t01_mutagen_grava_titulo_artista_e_album_em_mp3() {
        val mp3 = File(saidaDir, "amostra.mp3")
        mp3.writeBytes(mp3SilencioMinimo())

        val bruto = Python.getInstance().getModule("metadata").callAttr(
            "rewrite_file_metadata",
            mp3.absolutePath, "Titulo Teste", "Artista Teste", "Album Teste", null,
        ).toString()

        val json = JSONObject(bruto)
        assertTrue("rewrite_file_metadata falhou: $bruto", json.optBoolean("success"))

        val lido = JSONObject(
            Python.getInstance().getModule("metadata")
                .callAttr("read_file_metadata", mp3.absolutePath).toString()
        )
        assertEquals("Titulo Teste", lido.optString("title"))
        assertEquals("Artista Teste", lido.optString("artist"))
        assertEquals("Album Teste", lido.optString("album"))
    }

    @Test
    fun t02_mutagen_embute_capa_e_ela_pode_ser_extraida_de_volta() {
        val mp3 = File(saidaDir, "com_capa.mp3")
        mp3.writeBytes(mp3SilencioMinimo())

        val capa = File(saidaDir, "capa.jpg")
        capa.writeBytes(jpegMinimo())

        val bruto = Python.getInstance().getModule("metadata").callAttr(
            "embed_album_art", mp3.absolutePath, capa.absolutePath
        ).toString()
        assertTrue("embed_album_art falhou: $bruto", JSONObject(bruto).optBoolean("success"))

        val tamanhoAntes = mp3.length()
        assertTrue("arquivo nao cresceu — capa nao entrou", tamanhoAntes > jpegMinimo().size)

        val extraida = File(saidaDir, "extraida.jpg")
        val r = Python.getInstance().getModule("metadata").callAttr(
            "extract_embedded_artwork", mp3.absolutePath, extraida.absolutePath
        ).toString()
        assertTrue("extract_embedded_artwork falhou: $r", JSONObject(r).optBoolean("success"))
        assertTrue("capa extraida nao existe", extraida.exists() && extraida.length() > 0)
    }

    // ---------- Parte 2: download real ----------

    @Test
    fun t03_ffmpeg_esta_disponivel_no_diretorio_de_libs_nativas() {
        val ffmpeg = File(nativeLibDir(), "libffmpeg_exe.so")
        assertTrue("libffmpeg_exe.so ausente em ${nativeLibDir()}", ffmpeg.exists())
        assertTrue("libffmpeg_exe.so nao e executavel", ffmpeg.canExecute())
    }

    @Test
    fun t04_fetch_video_info_traz_titulo_e_duracao() {
        val bruto = py().callAttr("fetch_video_info", URL_CURTA, appFilesDir()).toString()
        val json = JSONObject(bruto)
        assertTrue("fetch_video_info falhou: ${bruto.take(400)}", json.optBoolean("success"))

        // O payload real aninha tudo em "data" — o Kotlin le por ai (VideoInfoHandler).
        val data = json.optJSONObject("data")
        assertNotNull("retorno sem objeto 'data': ${bruto.take(400)}", data)
        assertTrue("sem titulo: ${bruto.take(400)}", data!!.optString("title").isNotBlank())
        assertTrue("duracao invalida: ${data.optInt("duration")}", data.optInt("duration") > 0)
        assertTrue("is_playlist deveria ser falso", !data.optBoolean("is_playlist"))
    }

    @Test
    fun t05_download_de_audio_gera_arquivo_com_tags_gravadas() {
        val progresso = mutableListOf<Int>()
        val callback = object : PythonBridge.PythonProgressCallback {
            override fun onProgress(percent: Int) { progresso.add(percent) }
        }

        val saida = File(saidaDir, "%(ext)s".let { "Artista X - Album Y - Musica Z.$it" })

        val bruto = py().callAttr(
            "download_video",
            URL_CURTA,
            saida.absolutePath,
            "audio",
            "128",
            nativeLibDir(),
            appFilesDir(),
            "Artista X",
            "Album Y",
            "Musica Z",
            null,
            "m4a",
            callback,
        ).toString()

        val json = JSONObject(bruto)
        assertTrue("download falhou: ${bruto.take(600)}", json.optBoolean("success"))

        val caminho = json.optString("filename")
        assertTrue("sem filename no retorno: $bruto", caminho.isNotBlank())

        val arquivo = File(caminho)
        assertTrue("arquivo nao existe: $caminho", arquivo.exists())
        assertTrue("arquivo vazio: $caminho", arquivo.length() > 10_000)

        assertTrue("nenhum progresso reportado", progresso.isNotEmpty())

        val tags = JSONObject(
            Python.getInstance().getModule("metadata")
                .callAttr("read_file_metadata", caminho).toString()
        )
        assertEquals("Musica Z", tags.optString("title"))
        assertEquals("Artista X", tags.optString("artist"))
        assertEquals("Album Y", tags.optString("album"))
    }

    // ---------- fixtures ----------

    /** MP3 valido minimo: um frame MPEG-1 Layer III silencioso, 128 kbps 44.1 kHz. */
    private fun mp3SilencioMinimo(): ByteArray {
        val frame = ByteArray(418)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()   // MPEG-1 Layer III, sem CRC
        frame[2] = 0x90.toByte()   // 128 kbps, 44.1 kHz
        frame[3] = 0x00
        // 10 frames para o mutagen conseguir estimar duracao
        return ByteArray(frame.size * 10) { frame[it % frame.size] }
    }

    /** JPEG minimo valido: SOI + APP0 JFIF + EOI. */
    private fun jpegMinimo(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00,
        0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0xFF.toByte(), 0xD9.toByte(),
    )
}
