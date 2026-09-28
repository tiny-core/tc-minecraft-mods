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
 * Agenda crafts no AE2 sem requester: o resultado entra na rede ME e o ciclo seguinte da ponte
 * ({@code BridgeLogic}) o entrega ao armazém. A quantidade pedida é só a que falta (a ponte já
 * desconta armazém e rede). Mais simples que um ICraftingRequester (não há links para persistir).
 * <p>
 * Também lembra <b>qual item está sendo craftado para cada pedido</b> ({@link #remember}): num pedido por
 * tag a ponte escolhe o item, e nos ciclos seguintes precisa saber qual acompanhar. Essa memória não é
 * salva: depois de um reinício o craft continua no AE2 e a reserva no {@code DeliveryLedger} expira sozinha.
 */
public final class CraftingTracker {

    private final Map<AEItemKey, Future<ICraftingPlan>> calculating = new HashMap<>();
    private final Map<AEItemKey, Long> failedUntil = new HashMap<>();
    /** Id do pedido → item que esta ponte está craftando para ele. */
    private final Map<String, AEItemKey> byRequest = new HashMap<>();

    /** true se já existe um craft em cálculo ou a correr para este item. */
    public boolean isBusy(ICraftingService crafting, AEItemKey key) {
        return calculating.containsKey(key) || crafting.isRequesting(key);
    }

    /**
     * true se vale a pena tentar craftar o item agora: não está na blacklist, não está em espera após
     * uma falha e não há craft dele em andamento. Usado para filtrar candidatos de pedidos por tag.
     */
    public boolean canTry(ServerLevel level, ICraftingService crafting, AEItemKey key) {
        Long until = failedUntil.get(key);
        return (until == null || until <= level.getGameTime()) && !isBlacklisted(key) && !isBusy(crafting, key);
    }

    /** Anota que o craft de {@code key} foi iniciado para o pedido {@code requestId}. */
    public void remember(String requestId, AEItemKey key) {
        byRequest.put(requestId, key);
    }

    /** Item anotado para o pedido, sem conferir se o craft ainda roda (use {@link #activeFor} para isso). */
    public @Nullable AEItemKey remembered(String requestId) {
        return byRequest.get(requestId);
    }

    /**
     * Item que esta ponte ainda está craftando para o pedido, ou null. Quando o craft já terminou (ou
     * falhou), a anotação é descartada.
     */
    public @Nullable AEItemKey activeFor(String requestId, ICraftingService crafting) {
        AEItemKey key = byRequest.get(requestId);
        if (key != null && !isBusy(crafting, key)) {
            byRequest.remove(requestId);
            return null;
        }
        return key;
    }

    public boolean tryStart(ServerLevel level, IGrid grid, IActionSource source, AEItemKey key, long amount) {
        ICraftingService crafting = grid.getCraftingService();
        if (!canTry(level, crafting, key) || !crafting.isCraftable(key)) {
            return false;
        }
        long capped = Math.min(amount, Config.MAX_CRAFT_PER_REQUEST.get());
        calculating.put(key, crafting.beginCraftingCalculation(level, () -> source, key, capped,
                CalculationStrategy.REPORT_MISSING_ITEMS));
        return true;
    }

    /** Submete os cálculos que já terminaram e registra sucesso/falha nas estatísticas. Chamado a cada ciclo. */
    public void poll(ServerLevel level, IGrid grid, IActionSource source, BridgeStats stats) {
        long now = level.getGameTime();
        // Esperas vencidas não servem mais: sem isto o mapa só cresceria enquanto a ponte existir.
        failedUntil.values().removeIf(until -> until <= now);
        // Pedidos que sumiram antes de o craft terminar deixariam anotações para sempre.
        ICraftingService craftingService = grid.getCraftingService();
        byRequest.values().removeIf(key -> !isBusy(craftingService, key));
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
                    fail(key, now, "faltam materiais", stats);
                    continue;
                }
                var result = grid.getCraftingService().submitJob(plan, null, null, false, source);
                if (result.successful()) {
                    stats.recordCraftStarted(now);
                } else {
                    fail(key, now, String.valueOf(result.errorCode()), stats);
                }
            } catch (Exception e) {
                fail(key, now, e.getMessage(), stats);
            }
        }
    }

    public void clear() {
        calculating.values().forEach(f -> f.cancel(true));
        calculating.clear();
        byRequest.clear();
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
