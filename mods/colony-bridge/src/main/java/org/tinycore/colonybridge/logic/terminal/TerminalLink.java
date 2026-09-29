package org.tinycore.colonybridge.logic.terminal;

import org.tinycore.colonybridge.logic.BridgeStatus;

/**
 * Regra pura das restrições da rede ME: o Terminal do Armazém só funciona se a rede dele tem <b>uma</b>
 * Ponte ativa, ligada à mesma colônia; e a Ponte só funciona se for a única da rede.
 * <p>
 * Números e flags em vez de blocos, para ser testada sem o jogo. Quem conta as Pontes da rede é o
 * {@code BridgeNetwork} (API do AE2).
 */
public final class TerminalLink {

    private TerminalLink() {}

    /** true se a Ponte pode trabalhar: é a única Ponte na rede. */
    public static boolean bridgeAllowed(int bridgesInNetwork) {
        return bridgesInNetwork <= 1;
    }

    /**
     * Estado do terminal quanto à Ponte.
     *
     * @param bridgesInNetwork quantas Pontes há na rede ME do terminal
     * @param bridgeActive     a (única) Ponte está funcionando (ociosa ou trabalhando)
     * @param sameColony       a Ponte está na mesma colônia do terminal
     * @return {@link BridgeStatus#IDLE} se tudo certo; senão o motivo
     */
    public static BridgeStatus terminalStatus(int bridgesInNetwork, boolean bridgeActive, boolean sameColony) {
        if (bridgesInNetwork > 1) {
            return BridgeStatus.DUPLICATE_BRIDGE;
        }
        if (bridgesInNetwork == 0 || !bridgeActive || !sameColony) {
            return BridgeStatus.NO_BRIDGE;
        }
        return BridgeStatus.IDLE;
    }

    /** Ociosa ou trabalhando = a Ponte está de fato ligada à colônia e à rede. */
    public static boolean isActive(BridgeStatus status) {
        return status == BridgeStatus.IDLE || status == BridgeStatus.WORKING;
    }
}
