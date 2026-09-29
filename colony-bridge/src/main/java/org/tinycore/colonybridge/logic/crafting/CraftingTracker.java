package org.tinycore.colonybridge.logic.crafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.stats.BridgeStats;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Agenda crafts no AE2 para os pedidos da colônia, em duas etapas:
 * <ol>
 *   <li>{@link #tryStart}: pede ao AE2 o <b>cálculo</b> do plano (assíncrono, roda fora da thread do jogo);</li>
 *   <li>{@link #poll} (a cada ciclo): quando o cálculo termina, <b>envia o job</b> com a ponte como dona
 *       ({@link CraftLinks}), para o resultado ir direto ao armazém.</li>
 * </ol>
 * A quantidade pedida é só a que falta (a ponte já desconta armazém e rede). Também guarda a espera após
 * falha (falta de material, sem CPU) e a blacklist da config.
 * <p>
 * "Qual craft está rodando para qual pedido" vem dos cálculos pendentes (em memória, duram segundos) e dos
 * vínculos do {@link CraftLinks} (salvos no NBT), então sobrevive a reinícios.
 */
public final class CraftingTracker {

    /** Cálculo em andamento: o plano ainda não voltou do AE2. */
    private record Pending(String colonyKey, String requestId, Future<ICraftingPlan> plan) {}

    private final CraftLinks links;
    private final Map<AEItemKey, Pending> calculating = new HashMap<>();
    private final Map<AEItemKey, Long> failedUntil = new HashMap<>();

    public CraftingTracker(CraftLinks links) {
        this.links = links;
    }

    /** true se já existe um craft em cálculo ou a correr para este item (desta ponte ou de qualquer um). */
    public boolean isBusy(ICraftingService crafting, AEItemKey key) {
        return calculating.containsKey(key) || links.isCrafting(key) || crafting.isRequesting(key);
    }

    /**
     * true se vale a pena tentar craftar o item agora: não está na blacklist, não está em espera após
     * uma falha e não há craft dele em andamento. Usado para filtrar candidatos de pedidos por tag.
     */
    public boolean canTry(ServerLevel level, ICraftingService crafting, AEItemKey key) {
        Long until = failedUntil.get(key);
        return (until == null || until <= level.getGameTime()) && !isBlacklisted(key) && !isBusy(crafting, key);
    }

    /** Item que esta ponte está calculando ou craftando para o pedido, ou null. */
    public @Nullable AEItemKey activeFor(String requestId) {
        for (Map.Entry<AEItemKey, Pending> entry : calculating.entrySet()) {
            if (entry.getValue().requestId().equals(requestId)) {
                return entry.getKey();
            }
        }
        CraftLinks.Job job = links.activeFor(requestId);
        return job == null ? null : job.item();
    }

    public boolean tryStart(ServerLevel level, IGrid grid, IActionSource source, AEItemKey key, long amount,
                            String colonyKey, String requestId) {
        ICraftingService crafting = grid.getCraftingService();
        if (!canTry(level, crafting, key) || !crafting.isCraftable(key)) {
            return false;
        }
        long capped = Math.min(amount, Config.MAX_CRAFT_PER_REQUEST.get());
        calculating.put(key, new Pending(colonyKey, requestId, crafting.beginCraftingCalculation(level,
                () -> source, key, capped, CalculationStrategy.REPORT_MISSING_ITEMS)));
        return true;
    }

    /** Envia os cálculos que já terminaram e registra sucesso/falha nas estatísticas. Chamado a cada ciclo. */
    public void poll(ServerLevel level, IGrid grid, IActionSource source, BridgeStats stats) {
        long now = level.getGameTime();
        // Esperas vencidas não servem mais: sem isto o mapa só cresceria enquanto a ponte existir.
        failedUntil.values().removeIf(until -> until <= now);
        Iterator<Map.Entry<AEItemKey, Pending>> it = calculating.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Pending pending = entry.getValue();
            if (!pending.plan().isDone()) {
                continue;
            }
            it.remove();
            AEItemKey key = entry.getKey();
            try {
                ICraftingPlan plan = pending.plan().get();
                if (plan.simulation()) {
                    // faltam materiais: não submete, espera antes de tentar de novo
                    fail(key, now, "faltam materiais", stats);
                    continue;
                }
                // A ponte como requester: o resultado volta por CraftLinks.insertCraftedItems.
                var result = grid.getCraftingService().submitJob(plan, links, null, false, source);
                if (!result.successful()) {
                    fail(key, now, String.valueOf(result.errorCode()), stats);
                    continue;
                }
                if (result.link() != null) {
                    links.add(result.link(), new CraftLinks.Job(pending.colonyKey(), pending.requestId(), key));
                }
                stats.recordCraftStarted(now);
            } catch (Exception e) {
                fail(key, now, e.getMessage(), stats);
            }
        }
    }

    /**
     * Rede caiu: descarta os cálculos em andamento. Os jobs já enviados continuam no AE2 e os vínculos
     * ficam no {@link CraftLinks}.
     */
    public void clear() {
        calculating.values().forEach(p -> p.plan().cancel(true));
        calculating.clear();
    }

    private void fail(AEItemKey key, long now, String reason, BridgeStats stats) {
        failedUntil.put(key, now + Config.CRAFT_FAIL_COOLDOWN_TICKS.get());
        stats.recordCraftFailed(now);
        ColonyBridgeMod.LOG.debug("Craft de {} falhou: {}", key, reason);
    }

    private static boolean isBlacklisted(AEItemKey key) {
        String id = BuiltInRegistries.ITEM.getKey(key.getItem()).toString();
        return Config.CRAFT_BLACKLIST.get().contains(id);
    }
}
