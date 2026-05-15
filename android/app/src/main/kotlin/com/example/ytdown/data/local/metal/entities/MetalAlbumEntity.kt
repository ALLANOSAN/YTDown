package com.example.ytdown.data.local.metal.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.ytdown.data.local.metal.database.Converters

/**
 * Entidade de Álbum para Cache Offline - Sistema Metal
 * 
 * Armazena álbuns/discografia com capas e metadados completos.
 * Suporta múltiplas versões de um mesmo álbum (releases).
 */
@Entity(
    tableName = "metal_albums",
    indices = [
        androidx.room.Index(value = ["mbid"], unique = true),
        androidx.room.Index(value = ["artistMbid"]),
        androidx.room.Index(value = ["title"]),
        androidx.room.Index(value = ["releaseYear"]),
        androidx.room.Index(value = ["cachedAt"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = MetalArtistEntity::class,
            parentColumns = ["mbid"],
            childColumns = ["artistMbid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(Converters::class)
data class MetalAlbumEntity(
    @PrimaryKey
    val mbid: String,
    
    val artistMbid: String,
    val artistName: String,
    
    val title: String,
    val sortTitle: String = "",
    
    // Ano de lançamento (primeiro release)
    val releaseYear: String? = null,
    val releaseDate: String? = null,
    
    // Tipo de release (album, ep, single, etc)
    val primaryType: String = "",
    val secondaryTypesJson: String = "[]",
    
    // Capas - múltiplas qualidades
    val frontCoverUrl: String? = null,
    val backCoverUrl: String? = null,
    val coverThumbnail250: String? = null,
    val coverThumbnail500: String? = null,
    
    // Informações de mídia
    val format: String? = null,
    val country: String? = null,
    val barcode: String? = null,
    
    // Contagem de faixas
    val trackCount: Int = 0,
    val durationMs: Long? = null,
    
    // Status do conteúdo
    val hasLyrics: Boolean = false,
    val isComplete: Boolean = true,
    
    // Timestamps
    val cachedAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val releaseGroupMbid: String? = null,
    
    // Estatísticas de download
    val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val localPath: String? = null,
    val downloadedAt: Long? = null,
    val fileSize: Long? = null,
    
    // Preferências do usuário
    val isFavorite: Boolean = false,
    val userRating: Float? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null
) {
    companion object {
        const val DEFAULT_CACHE_TIMEOUT = 1000L * 60 * 60 * 24 * 3 // 3 dias
        
        fun fromMusicBrainz(
            mbid: String,
            artistMbid: String,
            artistName: String,
            title: String,
            year: String,
            type: String,
            coverUrl: String? = null
        ): MetalAlbumEntity {
            return MetalAlbumEntity(
                mbid = mbid,
                artistMbid = artistMbid,
                artistName = artistName,
                title = title,
                releaseYear = year.take(4),
                primaryType = type,
                frontCoverUrl = coverUrl
            )
        }
    }
    
    fun getSecondaryTypes(): List<String> {
        return try {
            secondaryTypesJson
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun isExpired(cacheTimeoutMs: Long = DEFAULT_CACHE_TIMEOUT): Boolean {
        return System.currentTimeMillis() - cachedAt > cacheTimeoutMs
    }
}

enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
    PAUSED
}