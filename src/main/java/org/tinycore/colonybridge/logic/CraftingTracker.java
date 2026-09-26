package org.tinycore.colonybridge.logic;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.Config;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Agenda crafts no AE2 sem requester: o resultado entra na rede ME,
 * e o ciclo seguinte do {@link BridgeLogic} entrega-o ao armazém.
 * Mais simples que um ICraftingRequester (não há links para persistir).
 */
final class CraftingTracker {

    private final Map<AEItemKey, Future<ICraftingPlan>> calculating = new HashMap<>();
    private final Map<AEItemKey, Long> failedUntil = new HashMap<>();

    /** true se já existe um craft em cálculo ou a correr para este item. */
    boolean isBusy(ICraftingService crafting, AEItemKey key) {
        return calculating.containsKey(key) || crafting.isRequesting(key);
    }

    boolean tryStart(ServerLevel level, IGrid grid, IActionSource source, AEItemKey key, long amount) {
        long now = level.getGameTime();
        Long until = failedUntil.get(key);
        if (until != null && until > now) {
            return false;
        }
        if (isBlacklisted(key)) {
            return false;
        }
        ICraftingService crafting = grid.getCraftingService();
        if (isBusy(crafting, key) || !crafting.isCraftable(key)) {
            return false;
        }
        long capped = Math.min(amount, Config.MAX_CRAFT_PER_REQUEST.get());
        calculating.put(key, crafting.beginCraftingCalculation(level, () -> source, key, capped,
                CalculationStrategy.REPORT_MISSING_ITEMS));
        return true;
    }

    /** Submete os cálculos que já terminaram. Chamado a cada ciclo. */
    void poll(ServerLevel level, IGrid grid, IActionSource source) {
        long now = level.getGameTime();
        Iterator<Map.Entry<AEItemKey, Future<ICraftingPlan>>> it = calculating.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!entry.getValue().isDone()) {
                continue;
            }
            it.remove();
            AEItemKey key = entry.getKey();
            try {
                ICraftingPlan plan = entry.getValue().get();
                if (plan.simulation()) {
                    // faltam materiais: não submete, espera antes de tentar de novo
                    fail(key, now, "faltam materiais");
                    continue;
                }
                var result = grid.getCraftingService().submitJob(plan, null, null, false, source);
                if (!result.successful()) {
                    fail(key, now, String.valueOf(result.errorCode()));
                }
            } catch (Exception e) {
                fail(key, now, e.getMessage());
            }
        }
    }

    void clear() {
        calculating.values().forEach(f -> f.cancel(true));
        calculating.clear();
    }

    private void fail(AEItemKey key, long now, String reason) {
        failedUntil.put(key, now + Config.CRAFT_FAIL_COOLDOWN_TICKS.get());
        ColonyBridgeMod.LOG.debug("Craft de {} falhou: {}", key, reason);
    }

    private static boolean isBlacklisted(AEItemKey key) {
        String id = BuiltInRegistries.ITEM.getKey(key.getItem()).toString();
        return Config.CRAFT_BLACKLIST.get().contains(id);
    }
}
