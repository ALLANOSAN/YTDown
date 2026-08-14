package com.example.ytdown.core.infrastructure.persistence

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `fallbackToDestructiveMigration()` apagava a biblioteca inteira do usuário em
 * silêncio sempre que a versão do schema subisse sem migração escrita. Tirar o
 * fallback só resolve se algo passar a reclamar em tempo de build — senão o
 * próximo bump vira crash no aparelho em vez de wipe silencioso.
 *
 * Este teste é esse algo: falha se a cadeia de migrações não cobrir todo o
 * caminho até a versão atual, ou se o schema exportado não acompanhar.
 */
class AppDatabaseMigrationsTest {

    private fun schemaDir(): File {
        // Testes de unidade do AGP rodam com working dir no módulo (android/app),
        // mas subir a árvore torna o teste independente disso.
        var dir = File("").absoluteFile
        repeat(4) {
            val candidate = File(dir, "schemas/${AppDatabase::class.java.name}")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        throw AssertionError("pasta de schemas nao encontrada a partir de ${File("").absolutePath}")
    }

    private fun exportedVersions(): List<Int> =
        schemaDir().listFiles { f -> f.extension == "json" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()

    @Test
    fun `migracoes cobrem toda a cadeia ate a versao atual`() {
        val versoes = exportedVersions()
        assertTrue("nenhum schema exportado — exportSchema esta false?", versoes.isNotEmpty())

        val saltos = AppDatabase.ALL_MIGRATIONS.associate { it.startVersion to it.endVersion }
        var atual = versoes.first()
        while (atual < DB_VERSION) {
            val proximo = saltos[atual]
                ?: throw AssertionError(
                    "falta migracao a partir da versao $atual (alvo $DB_VERSION). " +
                        "Sem ela o banco do usuario seria apagado."
                )
            assertTrue("migracao $atual->$proximo anda para tras", proximo > atual)
            atual = proximo
        }
        assertEquals(DB_VERSION, atual)
    }

    @Test
    fun `schema da versao atual esta exportado`() {
        assertTrue(
            "falta schemas/${DB_VERSION}.json — sem ele nao da para escrever a proxima migracao",
            exportedVersions().contains(DB_VERSION)
        )
    }

    @Test
    fun `nenhuma migracao duplicada para a mesma versao de origem`() {
        val origens = AppDatabase.ALL_MIGRATIONS.map { it.startVersion }
        assertEquals(origens.size, origens.distinct().size)
    }
}
