package org.tinycore.colonybridge.block.encoder;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.tinycore.colonybridge.integration.ae2.PatternEncoding;

import java.util.HashSet;
import java.util.Set;

/**
 * Slots do TC Pattern Encoder: um de entrada para Blank Patterns e {@link #OUTPUT_SLOTS} de saída para os padrões
 * prontos. Não são expostos a funis nem cabos (só à tela, que só abre para quem tem permissão na colônia).
 * <p>
 * Contra duplicação: quem codifica confere {@link #fits} antes de gastar o Blank Pattern (do slot ou da rede ME) e
 * só então chama {@link #put}. Ao quebrar o bloco, tudo cai no chão ({@link #drop}).
 */
final class EncoderInventory {

    static final int OUTPUT_SLOTS = 9;

    private final ItemStackHandler blanks;
    /** Saída: o jogador só tira; o bloco põe por {@link #store}. */
    private final ItemStackHandler outputs;

    EncoderInventory(Runnable onChanged) {
        this.blanks = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return PatternEncoding.isBlankPattern(stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
        this.outputs = new ItemStackHandler(OUTPUT_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    ItemStackHandler blanks() {
        return blanks;
    }

    ItemStackHandler outputs() {
        return outputs;
    }

    boolean hasBlank() {
        return !blanks.extractItem(0, 1, true).isEmpty();
    }

    /** true se o padrão cabe inteiro na saída (simulação, sem efeito). */
    boolean fits(ItemStack pattern) {
        return ItemHandlerHelper.insertItemStacked(outputs, pattern.copy(), true).isEmpty();
    }

    /** Gasta 1 Blank Pattern do slot; false se não havia. */
    boolean takeBlank() {
        return !blanks.extractItem(0, 1, false).isEmpty();
    }

    /** Põe o padrão na saída (chamar só depois de {@link #fits}). */
    void put(ItemStack pattern) {
        ItemHandlerHelper.insertItemStacked(outputs, pattern, false);
    }

    /** Itens cujos padrões estão na saída (para a tela marcar "na saída"). */
    Set<AEItemKey> encodedOutputs(Level level) {
        Set<AEItemKey> result = new HashSet<>();
        for (int i = 0; i < outputs.getSlots(); i++) {
            AEItemKey key = PatternEncoding.patternOutput(outputs.getStackInSlot(i), level);
            if (key != null) result.add(key);
        }
        return result;
    }

    void drop(Level level, BlockPos pos) {
        dropAll(level, pos, blanks);
        dropAll(level, pos, outputs);
    }

    private static void dropAll(Level level, BlockPos pos, ItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
                handler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("blanks", blanks.serializeNBT(registries));
        tag.put("outputs", outputs.serializeNBT(registries));
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("blanks")) blanks.deserializeNBT(registries, tag.getCompound("blanks"));
        if (tag.contains("outputs")) outputs.deserializeNBT(registries, tag.getCompound("outputs"));
    }
}
