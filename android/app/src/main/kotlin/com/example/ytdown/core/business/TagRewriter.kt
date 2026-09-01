package com.example.ytdown.core.business

import com.example.ytdown.DownloadMetadataManager
import com.example.ytdown.core.domain.ExitCode
import com.example.ytdown.core.domain.FilePath
import com.example.ytdown.core.domain.MediaMetadata
import javax.inject.Inject

/**
 * Fronteira de reescrita de tag no arquivo.
 *
 * Existe para que o renomear-em-lote possa ser verificado: o que ele passa em
 * `artworkUrl` acaba no frame APIC, e essa era a diferenca entre preservar a
 * capa do disco e substitui-la pela foto do artista.
 */
interface TagRewriter {
    suspend fun reescrever(
        path: FilePath,
        metadata: MediaMetadata,
        exportedPath: String? = null,
        artworkUrl: String? = null,
    ): ExitCode
}

class TagRewriterDownloadManager @Inject constructor(
    private val manager: DownloadMetadataManager,
) : TagRewriter {
    override suspend fun reescrever(
        path: FilePath,
        metadata: MediaMetadata,
        exportedPath: String?,
        artworkUrl: String?,
    ): ExitCode = manager.rewriteMetadata(path, metadata, exportedPath, artworkUrl)
}
