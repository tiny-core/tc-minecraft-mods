package org.tinycore.colonybridge.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;

/**
 * Energia da rede ME: em FE (a unidade das baterias de outros mods), para o carregador do tablet na Ponte, e em AE,
 * para o custo por item do Terminal do Armazém.
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

    /**
     * Cobra {@code ae} AE da rede (com o multiplicador da config do AE2). Se a rede não tiver o bastante, tira o
     * que houver.
     */
    public static void chargeAe(IGrid grid, double ae) {
        if (ae > 0) {
            grid.getEnergyService().extractAEPower(ae, Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
    }
}
