package org.tinycore.colonybridge.logic.supply;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.server.level.ServerLevel;
import org.tinycore.colonybridge.logic.crafting.CraftingTracker;

import java.util.List;

/**
 * Auto-craft do Abastecedor: quando uma linha "manter" continua abaixo da meta e a rede ME não tem mais nada dela,
 * pede ao AE2 o craft da falta ({@link SupplyRule#craft} decide se e quanto).
 * <p>
 * Reaproveita o {@link CraftingTracker} da Ponte (cálculo assíncrono, teto {@code maxCraftPerRequest} por job,
 * espera após falha, blacklist), mas <b>sem dono</b>: o resultado entra na rede ME e a própria linha o leva aos
 * racks no ciclo seguinte. Assim não há segundo caminho de entrega para manter. Um job por linha de cada vez: o
 * AE2 diz se o item já está sendo craftado ({@code isRequesting}), mesmo depois de um reinício.
 * <p>
 * Linha de tag: crafta o primeiro item da tag que a rede sabe craftar.
 */
final class SupplyCrafter {

    private static final String REQUEST_ID = "supply";

    private final CraftingTracker tracker = new CraftingTracker(null);

    /** Começo do ciclo: envia ao AE2 os cálculos que terminaram. */
    void beginCycle(ServerLevel level, IGrid grid, IActionSource source) {
        tracker.poll(level, grid, source, CraftingTracker.Events.NONE);
    }

    /** Rede caiu: descarta os cálculos (os jobs já enviados seguem no AE2). */
    void clear() {
        tracker.clear();
    }

    /**
     * Pede o craft da falta, se a regra mandar.
     *
     * @param sources itens da linha (o item, ou os da tag)
     * @param network quanto a rede tem da linha depois do movimento deste ciclo
     * @return true se há craft de algum item da linha rodando (de antes ou começado agora)
     */
    boolean craftFor(ServerLevel level, IGrid grid, IActionSource source, List<AEItemKey> sources, boolean enabled,
                     long current, long target, long network) {
        ICraftingService crafting = grid.getCraftingService();
        boolean busy = false;
        for (AEItemKey key : sources) {
            if (tracker.isBusy(crafting, key)) {
                busy = true;
                break;
            }
        }
        long amount = SupplyRule.craft(enabled, current, target, network, busy);
        if (amount <= 0) {
            return busy;
        }
        for (AEItemKey key : sources) {
            if (tracker.tryStart(level, grid, source, key, amount, "", REQUEST_ID)) {
                return true;
            }
        }
        return false;
    }
}
