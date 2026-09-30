package org.tinycore.colonybridge.logic;

/** Estado de um bloco TC ligado à rede (Ponte, Abastecedor, Terminal), mostrado ao jogador. Cada valor tem uma chave de tradução. */
public enum BridgeStatus {
    STARTING,
    OFFLINE,
    /** Sem cabo ME comum (não denso) embaixo da ponte. */
    INVALID_CABLE,
    /** Pausada pelo modo de redstone configurado na tela. */
    PAUSED,
    NO_COLONY,
    /** Quem colocou a ponte não tem (ou perdeu) permissão na colônia. */
    NO_PERMISSION,
    NO_WAREHOUSE,
    /** Ponte: há mais de uma Ponte na mesma rede ME (só uma é permitida). */
    DUPLICATE_BRIDGE,
    /** Terminal: nenhuma Ponte ativa desta colônia na rede ME. */
    NO_BRIDGE,
    IDLE,
    WORKING,
    /**
     * Já existe outro bloco deste tipo na colônia (só um é permitido). No fim da lista porque o número
     * do estado é salvo nos monitores.
     */
    DUPLICATE_IN_COLONY;

    /** Mensagem completa, mostrada na barra de ação. */
    public String translationKey() {
        return "status.tccolonybridge." + name().toLowerCase();
    }

    /** Texto curto para a tela da ponte. */
    public String guiKey() {
        return "gui.tccolonybridge.status." + name().toLowerCase();
    }
}
