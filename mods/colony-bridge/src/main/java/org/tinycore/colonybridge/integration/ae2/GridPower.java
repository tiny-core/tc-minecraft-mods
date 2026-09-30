package org.tinycore.colonybridge.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;

/**
 * Energia da rede ME em FE (a unidade das baterias de outros mods), usada pelo carregador do tablet na Ponte.
 * A conversão AE ↔ FE é a do próprio AE2 ({@link PowerUnit}), e {@link PowerMultiplier#CONFIG} aplica o
 * multiplicador de energia da config do AE2, como as máquinas dele.
 */
public final class GridPower {

    private GridPower() {}

    /**
     * Tira da rede o equivalente a {@code fe} FE.
     *
     * @return FE de fato tirados (menos que o pedido se a rede não tinha o bastante)
     */
    public static int extractFe(IGrid grid, int fe) {
        if (fe <= 0) {
            return 0;
        }
        double ae = PowerUnit.FE.convertTo(PowerUnit.AE, fe);
        double extracted = grid.getEnergyService().extractAEPower(ae, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return (int) Math.min(fe, Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, extracted)));
    }
}
