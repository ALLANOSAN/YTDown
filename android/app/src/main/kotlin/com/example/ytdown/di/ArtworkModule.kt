package com.example.ytdown.di

import com.example.ytdown.core.business.BibliotecaDeAudio
import com.example.ytdown.core.business.BibliotecaDeAudioRoom
import com.example.ytdown.core.business.CoverSource
import com.example.ytdown.core.business.CoverSourcePadrao
import com.example.ytdown.core.business.RecordingLookup
import com.example.ytdown.core.business.RecordingLookupMusicBrainz
import com.example.ytdown.core.business.TagWriter
import com.example.ytdown.core.business.TagWriterMutagen
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Liga as fronteiras do enriquecimento de capas às implementações de produção. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ArtworkModule {

    @Binds
    @Singleton
    abstract fun recordingLookup(impl: RecordingLookupMusicBrainz): RecordingLookup

    @Binds
    @Singleton
    abstract fun coverSource(impl: CoverSourcePadrao): CoverSource

    @Binds
    @Singleton
    abstract fun tagWriter(impl: TagWriterMutagen): TagWriter

    @Binds
    @Singleton
    abstract fun bibliotecaDeAudio(impl: BibliotecaDeAudioRoom): BibliotecaDeAudio

    @Binds
    @Singleton
    abstract fun enriquecedorDeItem(
        impl: com.example.ytdown.core.business.EnriquecedorViaImportProcessor
    ): com.example.ytdown.core.business.EnriquecedorDeItem

    @Binds
    @Singleton
    abstract fun tagRewriter(
        impl: com.example.ytdown.core.business.TagRewriterDownloadManager
    ): com.example.ytdown.core.business.TagRewriter
}
