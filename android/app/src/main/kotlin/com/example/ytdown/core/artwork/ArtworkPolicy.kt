package com.example.ytdown.core.artwork

/** O que o pipeline precisa fazer com a capa de um arquivo. */
enum class AcaoDeCapa {
    /** Nada a fazer: o arquivo ja carrega a capa. */
    NADA,

    /** A capa esta no cache mas nao no arquivo — embutir sem tocar na rede. */
    EMBUTIR_DO_CACHE,

    /** Sem capa conhecida: rodar o pipeline completo. */
    ENRIQUECER,
}

object ArtworkPolicy {

    /**
     * Decide o que fazer com a capa antes de rodar o pipeline caro.
     *
     * A regra antiga pulava o enriquecimento quando a capa estava no cache, sem
     * olhar se ela estava no arquivo. O SongEntity apontava para o cache e o
     * player do app mostrava a capa, mas o arquivo ficava sem APIC — em qualquer
     * outro player a faixa aparecia sem capa nenhuma.
     *
     * Cache continua servindo para velocidade: quando ele ja tem a capa, o
     * arquivo e corrigido a partir dele, sem rede.
     */
    fun decidir(
        tagsLimpas: Boolean,
        capaNoArquivo: Boolean,
        capaNoCache: Boolean,
    ): AcaoDeCapa {
        if (!tagsLimpas) return AcaoDeCapa.ENRIQUECER
        if (capaNoArquivo) return AcaoDeCapa.NADA
        if (capaNoCache) return AcaoDeCapa.EMBUTIR_DO_CACHE
        return AcaoDeCapa.ENRIQUECER
    }
}
