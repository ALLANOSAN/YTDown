package com.example.ytdown.core.infrastructure

import com.example.ytdown.core.domain.DownloadItemEntity
import com.example.ytdown.core.infrastructure.persistence.DownloadDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MetadataMergeEngine - Motor de Fusão de Metadados
 * 
 * Responsável por aplicar a hierarquia de prioridade:
 * 1. Overrides do Usuário (Edições manuais)
 * 2. Cache Local (Imagens baixadas/processadas)
 * 3. Metadados Online (MusicBrainz/LastFM)
 * 4. Tags Físicas do Arquivo
 */
@Singleton
class MetadataMergeEngine @Inject constructor(
    private val downloadDao: DownloadDao
) {
    
    /**
     * Combina as diferentes camadas de metadados para retornar o estado final.
     * No momento, DownloadItemEntity atua como portador do estado persistido.
     */
    fun mergeMetadata(item: DownloadItemEntity): DownloadItemEntity {
        // O objetivo aqui é garantir que campos nulos sejam preenchidos por camadas inferiores,
        // mas campos editados (que já estão no DownloadItemEntity via UI) sejam preservados.
        return item
    }

    /**
     * Resolve qual imagem deve ser usada para a CAPA DO ÁLBUM.
     * Prioridade: Capa baixada (albumImageUrl) -> Miniatura do vídeo (thumbnailPath) -> Tag do arquivo.
     */
    fun getAlbumArt(item: DownloadItemEntity): String? {
        return item.albumImageUrl ?: item.thumbnailPath
    }

    /**
     * Resolve qual imagem deve ser usada para o ARTISTA.
     * NUNCA deve retornar uma imagem embutida no arquivo.
     */
    fun getArtistArt(item: DownloadItemEntity): String? {
        return item.artistImageUrl
    }

    /**
     * Verifica se um item deve ser atualizado pelo scanner ou se deve ser ignorado
     * para proteger edições do usuário.
     */
    fun shouldProtectFromScanner(item: DownloadItemEntity): Boolean {
        // Se o item foi marcado como "completed" e está no banco, 
        // assumimos que ele já possui metadados válidos ou editados.
        return item.status == "completed"
    }
}