package org.tinycore.colonybridge.logic.encoder;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.OpenRequest;
import org.tinycore.colonybridge.integration.ae2.PatternEncoding;
import org.tinycore.colonybridge.menu.encoder.EncoderLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Varredura do TC Pattern Encoder: dos pedidos em aberto da colônia, separa os que o AE2 <b>não sabe craftar</b> e
 * procura a receita de bancada de cada um ({@link PatternEncoding}).
 * <ul>
 *   <li>Pedido exato: o próprio item. Pedido por tag/ferramenta/comida: os exemplos que o MineColonies mostra, e o
 *       primeiro que tiver receita vira o candidato (como o craft por tag da Ponte, um candidato por pedido).</li>
 *   <li>Pedido que já tem algum item craftável no AE2 sai da lista (a Ponte já consegue craftá-lo).</li>
 *   <li>Mesmo item em vários pedidos vira uma linha só, com as quantidades somadas.</li>
 *   <li>Padrão desse item já nos slots de saída: linha "na saída" (não codifica de novo).</li>
 * </ul>
 * Roda só com a tela aberta e no máximo a cada {@code encoderScanTicks}; o resultado fica no block entity.
 */
public final class EncoderScanner {

    /** Uma linha e, se tem receita, o que é preciso para codificá-la (só no servidor). */
    public record Entry(EncoderLine line, @Nullable PatternEncoding.Encodable encodable) {}

    /** Linhas (até {@code maxLines}, as prontas primeiro) e quantas ficaram de fora. */
    public record Scan(List<Entry> entries, int hidden) {}

    private EncoderScanner() {}

    /**
     * @param inOutput itens cujos padrões já estão na saída do bloco
     */
    public static Scan scan(ServerLevel level, BlockPos pos, IGrid grid, Set<AEItemKey> inOutput, int maxLines) {
        ICraftingService crafting = grid.getCraftingService();
        KeyCounter stock = grid.getStorageService().getCachedInventory();
        List<Entry> entries = new ArrayList<>();
        Set<AEItemKey> seen = new HashSet<>();
        for (OpenRequest request : ColonyAccess.openRequestsAt(level, pos)) {
            List<ItemStack> candidates = candidates(request);
            if (candidates.isEmpty() || anyCraftable(candidates, crafting)) {
                continue;
            }
            Entry entry = entryFor(level, candidates, request.amount(), stock, crafting, inOutput);
            AEItemKey key = AEItemKey.of(entry.line().result());
            if (key == null) {
                continue;
            }
            if (!seen.add(key)) {
                merge(entries, key, request.amount());
                continue;
            }
            entries.add(entry);
        }
        entries.sort(Comparator.comparingInt(e -> e.line().state().ordinal())); // sort estável: mantém a ordem dos pedidos
        int shown = Math.min(maxLines, entries.size());
        return new Scan(List.copyOf(entries.subList(0, shown)), entries.size() - shown);
    }

    /** Itens que o pedido aceita e que valem como candidatos (exemplos que o pedido realmente aceita). */
    private static List<ItemStack> candidates(OpenRequest request) {
        if (request.isExact()) {
            return List.of(request.exactStack().copyWithCount(1));
        }
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack example : request.examples()) {
            if (request.accepts(example)) result.add(example);
        }
        return result;
    }

    private static boolean anyCraftable(List<ItemStack> candidates, ICraftingService crafting) {
        for (ItemStack candidate : candidates) {
            AEItemKey key = AEItemKey.of(candidate);
            if (key != null && crafting.isCraftable(key)) return true;
        }
        return false;
    }

    /** Primeiro candidato com receita; sem nenhum, o primeiro candidato com o motivo. */
    private static Entry entryFor(ServerLevel level, List<ItemStack> candidates, int amount, KeyCounter stock,
                                  ICraftingService crafting, Set<AEItemKey> inOutput) {
        PatternEncoding.Result first = null;
        for (ItemStack candidate : candidates) {
            PatternEncoding.Result result = PatternEncoding.find(level, candidate, stock, crafting);
            if (result.encodable() != null) {
                AEItemKey key = AEItemKey.of(candidate);
                EncoderState state = key != null && inOutput.contains(key) ? EncoderState.IN_OUTPUT : EncoderState.READY;
                return new Entry(new EncoderLine(candidate, amount, state, result.encodable().summedInputs()),
                        state == EncoderState.READY ? result.encodable() : null);
            }
            if (first == null) first = result;
        }
        EncoderState state = first != null && first.lookup() == PatternEncoding.Lookup.EXCLUDED
                ? EncoderState.EXCLUDED : EncoderState.NO_RECIPE;
        return new Entry(new EncoderLine(candidates.get(0), amount, state, List.of()), null);
    }

    private static void merge(List<Entry> entries, AEItemKey key, int amount) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (key.matches(e.line().result())) {
                EncoderLine l = e.line();
                entries.set(i, new Entry(new EncoderLine(l.result(), l.amount() + amount, l.state(), l.inputs()), e.encodable()));
                return;
            }
        }
    }
}
