package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Parte do {@link MonitorData} que depende do tipo de bloco ligado: {@link BridgeContent} (pedidos e
 * estatísticas da Ponte) ou {@link SupplyContent} (linhas de estoque do Abastecedor).
 * <p>
 * {@code sealed ... permits} fecha a lista de implementações (≈ uma hierarquia fechada em C#): quem usa
 * pode tratar cada tipo com {@code instanceof} sabendo que não existe outro.
 */
public sealed interface MonitorContent permits BridgeContent, SupplyContent {

    /** Nome gravado no NBT para saber qual tipo ler de volta. */
    String kind();

    void save(CompoundTag tag, HolderLookup.Provider registries);

    /** Lê o conteúdo do tipo gravado; tipo desconhecido vira o conteúdo vazio da Ponte. */
    static MonitorContent load(CompoundTag tag, HolderLookup.Provider registries) {
        return SupplyContent.KIND.equals(tag.getString("kind"))
                ? SupplyContent.load(tag)
                : BridgeContent.load(tag, registries);
    }
}
