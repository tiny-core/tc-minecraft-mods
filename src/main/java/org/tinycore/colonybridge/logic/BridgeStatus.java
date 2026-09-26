package org.tinycore.colonybridge.logic;

/** Estado da ponte mostrado ao jogador (clique direito). Cada valor tem uma chave de tradução. */
public enum BridgeStatus {
    STARTING,
    OFFLINE,
    /** Sem cabo ME comum (não denso) embaixo da ponte. */
    INVALID_CABLE,
    NO_COLONY,
    /** Quem colocou a ponte não tem (ou perdeu) permissão na colônia. */
    NO_PERMISSION,
    NO_WAREHOUSE,
    IDLE,
    WORKING;

    public String translationKey() {
        return "status.tccolonybridge." + name().toLowerCase();
    }
}
