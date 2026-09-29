package org.tinycore.colonybridge.stats;

/** Contadores de série temporal guardados pela ponte (um anel de blocos de tempo para cada). */
enum StatMetric {
    /** Pedidos atendidos (itens colocados no armazém). */
    REQUESTS_DELIVERED,
    /** Soma das quantidades entregues. */
    ITEMS_DELIVERED,
    /** Jobs de craft enviados com sucesso ao AE2. */
    CRAFTS_STARTED,
    /** Tentativas de craft que falharam (falta de material, sem CPU...). */
    CRAFTS_FAILED,
    /** Jobs de craft terminados pelo AE2 (resultado entregue ao armazém pela ponte). Novo: fica no fim. */
    CRAFTS_DONE
}
