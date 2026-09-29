package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.tinycore.colonybridge.logic.BridgeStatus;

/**
 * O que a tela do monitor mostra: situação da ligação, estado e colônia do bloco ligado, e o conteúdo
 * próprio de cada tipo de bloco ({@link MonitorContent}: Ponte ou Abastecedor). Montado no servidor pelo
 * mestre a cada segundo e enviado ao cliente só quando muda (records comparam por valor, então
 * {@code equals} decide).
 * <p>
 * Viaja no NBT de sincronização do block entity ({@link #save}/{@link #load}), mas não é salvo no
 * disco: é sempre recalculado a partir do bloco ligado ({@link MonitorSource}).
 */
public record MonitorData(LinkState link, BridgeStatus status, String colonyName, MonitorContent content) {

    /** Situação da ligação monitor → bloco. */
    public enum LinkState {
        /** Nenhum bloco ligado (use o cartão). */
        UNLINKED,
        /** Ligado, mas o bloco não existe mais ou está num chunk descarregado. */
        BRIDGE_MISSING,
        /** Ligado e com dados. */
        OK
    }

    public static final MonitorData UNLINKED =
            new MonitorData(LinkState.UNLINKED, BridgeStatus.STARTING, "", BridgeContent.EMPTY);
    public static final MonitorData MISSING =
            new MonitorData(LinkState.BRIDGE_MISSING, BridgeStatus.STARTING, "", BridgeContent.EMPTY);

    private static final LinkState[] LINKS = LinkState.values();
    private static final BridgeStatus[] STATUSES = BridgeStatus.values();

    /** Dados de um bloco ligado e funcionando. */
    public static MonitorData ok(BridgeStatus status, String colonyName, MonitorContent content) {
        return new MonitorData(LinkState.OK, status, colonyName, content);
    }

    /** {@code registries}: necessário para gravar os textos (descrição dos pedidos). */
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("link", link.ordinal());
        tag.putInt("status", status.ordinal());
        tag.putString("colony", colonyName);
        tag.putString("kind", content.kind());
        content.save(tag, registries);
        return tag;
    }

    /** Lê com limites (índices de enum, tamanho de textos e listas): o dado vem da rede. */
    public static MonitorData load(CompoundTag tag, HolderLookup.Provider registries) {
        String colony = tag.getString("colony");
        return new MonitorData(
                LINKS[Math.floorMod(tag.getInt("link"), LINKS.length)],
                STATUSES[Math.floorMod(tag.getInt("status"), STATUSES.length)],
                colony.length() > 64 ? colony.substring(0, 64) : colony,
                MonitorContent.load(tag, registries));
    }
}
