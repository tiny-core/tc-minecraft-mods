package org.tinycore.colonybridge.stats;

/** Contadores de série temporal do Abastecedor (um anel de blocos de tempo para cada). */
enum SupplyMetric {
    /** Itens tirados da rede ME e colocados no armazém ("manter no armazém"). */
    ITEMS_RESTOCKED,
    /** Itens tirados do armazém e devolvidos à rede ME ("excedente"). */
    ITEMS_RETURNED
}
