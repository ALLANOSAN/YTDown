package com.example.ytdown.data.local.metal.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.ytdown.data.local.metal.database.Converters

/**
 * Entidade de Artista para Cache Offline - Sistema Metal
 * 
 * Armazena artistas descobertos com todos os metadados necessários
 * para funcionamento offline e sincronização.
 */
@Entity(
    tableName = "metal_artists",
    indices = [
        androidx.room.Index(value = ["mbid"], unique = true),
        androidx.room.Index(value = ["compatibilityScore"]),
        androidx.room.Index(value = ["lastUpdated"]),
        androidx.room.Index(value = ["name"])
    ]
)
@TypeConverters(Converters::class)
data class MetalArtistEntity(
    @PrimaryKey
    val mbid: String,
    
    val name: String,
    val sortName: String = "",
    
    val country: String? = null,
    val countryCode: String? = null,
    
    // JSON arrays para tags e gêneros
    val genresJson: String = "[]",
    val tagsJson: String = "[]",
    val aliasesJson: String = "[]",
    
    // Score de compatibilidade calculado dinamicamente (0.0 - 100.0)
    val compatibilityScore: Double = 0.0,
    
    // Tags que correspondem com a biblioteca do usuário
    val matchedTagsJson: String = "[]",
    
    // URLs de imagem
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
    
    // Informações de atividade
    val isActive: Boolean = true,
    val beginYear: String? = null,
    val endYear: String? = null,
    
    // Disambiguation (nome completo da banda)
    val disambiguation: String? = null,
    
    // Timestamps para cache e sincronização
    val cachedAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastSyncAttempt: Long = 0,
    val syncAttempts: Int = 0,
    
    // Status de sincronização
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    
    // Estatísticas de uso
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val isFavorite: Boolean = false
) {
    companion object {
        // Cache timeout padrão: 24 horas
        const val DEFAULT_CACHE_TIMEOUT = 1000L * 60 * 60 * 24
        
        // Cache timeout curto para dados frequentemente atualizados
        const val SHORT_CACHE_TIMEOUT = 1000L * 60 * 60 * 1
        
        // Cache timeout longo para dados estáveis
        const val LONG_CACHE_TIMEOUT = 1000L * 60 * 60 * 48
        
        fun fromJsonArrays(
            mbid: String,
            name: String,
            genres: List<String>,
            tags: List<String>,
            matchedTags: List<String>,
            score: Double,
            country: String? = null,
            imageUrl: String? = null,
            isActive: Boolean = true,
            beginYear: String? = null,
            endYear: String? = null
        ): MetalArtistEntity {
            return MetalArtistEntity(
                mbid = mbid,
                name = name,
                country = country,
                genresJson = genres.toJsonString(),
                tagsJson = tags.toJsonString(),
                matchedTagsJson = matchedTags.toJsonString(),
                compatibilityScore = score,
                imageUrl = imageUrl,
                isActive = isActive,
                beginYear = beginYear,
                endYear = endYear
            )
        }
        
        private fun List<String>.toJsonString(): String {
            return this.let { list ->
                if (list.isEmpty()) "[]"
                else "[${list.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}}]"
            }
        }
    }
    
    fun getGenresList(): List<String> {
        return try {
            genresJson
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getTagsList(): List<String> {
        return try {
            tagsJson
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getMatchedTagsList(): List<String> {
        return try {
            matchedTagsJson
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getActiveYears(): String? {
        return when {
            beginYear != null && endYear != null -> "$beginYear - $endYear"
            beginYear != null && isActive -> "$beginYear - presente"
            else -> null
        }
    }
    
    fun isExpired(cacheTimeoutMs: Long = DEFAULT_CACHE_TIMEOUT): Boolean {
        return System.currentTimeMillis() - lastUpdated > cacheTimeoutMs
    }
}

enum class SyncStatus {
    PENDING,        // Precisa sincronizar
    SYNCING,         // Sincronizando atualmente
    SYNCED,          // Sincronizado com sucesso
    FAILED,          // Falha na sincronização
    STALE            // Dados desatualizados
}