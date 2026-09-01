package com.example.ytdown.core.business

import com.example.ytdown.core.artwork.AcaoDeReparo
import com.example.ytdown.core.artwork.ReparoDeCapaPolicy as ReparoDeCapaPolicyDelegado
import com.example.ytdown.utils.MetadataUtils

object ReparoDeTagsPolicy {

    /**
     * Decide se um item da biblioteca precisa passar pelo enriquecimento.
     *
     * A regra antiga olhava so as strings: sem sentinela, sem cara de nome de
     * arquivo e com capa, o item era dado como pronto. Mas "Heavy Righteous
     * Metal" e uma string limpa — e uma coletanea. Nao da para descobrir isso
     * sem perguntar ao MusicBrainz qual e o album de origem e comparar.
     */
    fun decidir(
        title: String?,
        artist: String?,
        album: String?,
        temCapa: Boolean,
        albumDoMusicBrainz: String?,
    ): AcaoDeReparo {
        if (MetadataUtils.needsMetadataRepair(title, artist, album, temCapa)) {
            return AcaoDeReparo.REESCREVER
        }
        return ReparoDeCapaPolicyDelegado.decidir(album, albumDoMusicBrainz, temCapa)
    }
}
