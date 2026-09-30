package org.tinycore.colonybridge.logic.target;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Uma lista de linhas ({@link TargetLine}) de um bloco: "manter" ou "excedente" do Abastecedor, ou o filtro
 * da Ponte ({@link TargetListKind}). Substitui as grades fixas de ghost slots ({@code StockList},
 * {@code ItemFilter}), que continuam só para ler blocos salvos no formato antigo ({@link #importSlots}).
 * <p>
 * Toda edição passa pelas regras da lista ({@link TargetLine#sanitized}) e pelos limites de
 * {@link ListEdits}, porque vem da tela. O número máximo de linhas vem da config (quem chama passa);
 * {@link #HARD_MAX_LINES} é o teto absoluto, também aplicado ao ler o NBT.
 */
public final class TargetList {

    /** Teto absoluto de linhas (a config não passa disso; protege NBT e pacotes). */
    public static final int HARD_MAX_LINES = 64;

    private final TargetListKind kind;
    private final List<TargetLine> lines = new ArrayList<>();

    public TargetList(TargetListKind kind) {
        this.kind = kind;
    }

    public TargetListKind kind() {
        return kind;
    }

    /** Linhas em ordem, só para leitura (editar só pelos métodos, que aplicam as regras). */
    public List<TargetLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public int size() {
        return lines.size();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** @return false se a lista está cheia ou não aceita o tipo de alvo */
    public boolean add(TargetLine line, int maxLines) {
        TargetLine clean = line.sanitized(kind);
        return clean != null && ListEdits.add(lines, clean, Math.min(maxLines, HARD_MAX_LINES));
    }

    /** @return false se o índice não existe ou a lista não aceita o tipo de alvo */
    public boolean set(int index, TargetLine line) {
        TargetLine clean = line.sanitized(kind);
        return clean != null && ListEdits.set(lines, index, clean);
    }

    public boolean remove(int index) {
        return ListEdits.remove(lines, index);
    }

    /** Corta as linhas que passam do limite atual da config. */
    public boolean trim(int maxLines) {
        return ListEdits.trim(lines, Math.min(maxLines, HARD_MAX_LINES));
    }

    public void clear() {
        lines.clear();
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (TargetLine line : lines) {
            list.add(line.save(registries));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("lines", list);
        return tag;
    }

    /** Lê do NBT, descartando linhas inválidas e passando pelas regras da lista (NBT pode ter sido editado). */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        lines.clear();
        ListTag list = tag.getList("lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && lines.size() < HARD_MAX_LINES; i++) {
            TargetLine line = TargetLine.load(list.getCompound(i), registries);
            TargetLine clean = line == null ? null : line.sanitized(kind);
            if (clean != null) {
                lines.add(clean);
            }
        }
    }

    /**
     * Migração do formato antigo (grade de ghost slots): cada slot com item vira uma linha de item, na mesma
     * ordem e com a mesma quantidade. Slots vazios são pulados.
     *
     * @param slots   itens da grade antiga
     * @param amounts quantidade de cada slot, ou null (filtro, sem quantidade)
     * @param from    primeiro slot desta lista (inclusive)
     * @param to      fim (exclusive)
     */
    public void importSlots(List<ItemStack> slots, int @Nullable [] amounts, int from, int to) {
        for (int i = from; i < to && i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty()) {
                int amount = amounts != null && i < amounts.length ? amounts[i] : 0;
                add(TargetLine.ofStack(stack, amount), HARD_MAX_LINES);
            }
        }
    }
}
