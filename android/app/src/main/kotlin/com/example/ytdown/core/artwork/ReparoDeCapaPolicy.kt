package com.example.ytdown.core.artwork

import com.example.ytdown.utils.MetadataUtils

/** O que a varredura de reparo precisa fazer com a capa de um item. */
enum class AcaoDeReparo {
    /** Capa confere com o album de origem: nao mexer. */
    NADA,

    /** Sem capa: buscar. */
    BUSCAR,

    /** Capa e de outro album (tipicamente coletanea): trocar. */
    REESCREVER,

    /** MusicBrainz nao respondeu: sem base para decidir. */
    SEM_FONTE,
}

object ReparoDeCapaPolicy {

    /**
     * Decide o que fazer com a capa de um item durante a varredura de reparo.
     *
     * Ter capa nao significa ter a capa certa: o codigo antigo escolhia
     * `releases[0]` e gravava coletanea, entao a biblioteca carrega capas de
     * album errado sob nome de album errado. Quando o MusicBrainz aponta um
     * album diferente do gravado, a capa atual e de outro lancamento e precisa
     * ser trocada — nao da para decidir isso sem a rede.
     */
    fun decidir(
        albumAtual: String?,
        albumDoMusicBrainz: String?,
        temCapa: Boolean,
    ): AcaoDeReparo {
        // Silencio da API nao e informacao: sob rate limit parte da varredura
        // volta vazia, e decidir a partir disso mexeria em item desconhecido.
        if (albumDoMusicBrainz.isNullOrBlank()) return AcaoDeReparo.SEM_FONTE
        if (MetadataUtils.normalizeForMatch(albumDoMusicBrainz) !=
            MetadataUtils.normalizeForMatch(albumAtual.orEmpty())
        ) {
            return AcaoDeReparo.REESCREVER
        }
        if (temCapa) return AcaoDeReparo.NADA
        return AcaoDeReparo.BUSCAR
    }
}
