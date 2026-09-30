package org.tinycore.colonybridge.logic.target;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Uma linha de lista (Abastecedor ou filtro da Ponte): o alvo ({@link TargetSpec}), o item modelo e a
 * quantidade.
 * <p>
 * {@code item} só existe em linhas de item: quando o jogador solta um item no ícone, a linha guarda a pilha
 * inteira (com encantamentos, nome, etc. — os "componentes" do 1.21), para comparar exatamente com ela. Quando
 * o id é digitado, o modelo é o item "puro". Se o item não existe mais (mod removido), fica
 * {@link ItemStack#EMPTY} e a linha é mostrada como inválida, sem perder o texto. Tag e mod: sempre vazio.
 * <p>
 * Os itens aqui são só modelos (ghost): nunca são entregues a ninguém.
 * Atenção: {@code ItemStack} não tem igualdade por valor, então dois {@code TargetLine} iguais não são
 * {@code equals}; compare pelos campos quando precisar.
 *
 * @param amount meta/limite da linha; 0 = linha desligada (como nas grades antigas). Em listas sem
 *               quantidade (filtro) é sempre 0
 * @param all    "tudo" no lugar da quantidade (só no excedente: meta 0)
 */
public record TargetLine(TargetSpec spec, ItemStack item, int amount, boolean all) {

    /** Linha de item a partir da pilha solta no ícone (guarda uma cópia com 1 unidade). */
    public static TargetLine ofStack(ItemStack stack, int amount) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new TargetLine(TargetSpec.ofItem(id), stack.copyWithCount(1), amount, false);
    }

    /** Linha a partir do texto já lido; item digitado vira o modelo "puro" do registro. */
    public static TargetLine ofSpec(TargetSpec spec, int amount, boolean all) {
        return new TargetLine(spec, modelFor(spec), amount, all);
    }

    /**
     * A linha dentro das regras da lista ({@link TargetListKind}): quantidade limitada e "tudo" só onde vale.
     *
     * @return null se a lista não aceita este tipo de alvo (ex.: {@code @mod} em "manter")
     */
    public @Nullable TargetLine sanitized(TargetListKind list) {
        if (!list.allows(spec.kind())) {
            return null;
        }
        boolean cleanAll = all && list.allowsAll();
        int cleanAmount = cleanAll ? 0 : list.clampAmount(amount);
        return cleanAmount == amount && cleanAll == all ? this : new TargetLine(spec, item, cleanAmount, cleanAll);
    }

    /** true se o modelo tem dados além do item "puro" (a tela marca a linha com ✦ e mostra na dica). */
    public boolean hasComponents() {
        return !item.isEmpty() && !item.getComponentsPatch().isEmpty();
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("target", spec.text());
        tag.putInt("amount", amount);
        if (all) {
            tag.putBoolean("all", true);
        }
        if (!item.isEmpty()) {
            tag.put("item", item.save(registries));
        }
        return tag;
    }

    /**
     * Lê do NBT. {@code ItemStack.parseOptional} devolve vazio (sem exceção) se o item salvo não existe mais.
     *
     * @return null se o texto salvo não é um alvo válido
     */
    public static @Nullable TargetLine load(CompoundTag tag, HolderLookup.Provider registries) {
        TargetSpec spec = TargetSpec.parse(tag.getString("target"));
        if (spec == null) {
            return null;
        }
        ItemStack item = ItemStack.EMPTY;
        if (spec.kind() == TargetKind.ITEM && tag.contains("item")) {
            item = ItemStack.parseOptional(registries, tag.getCompound("item"));
        }
        if (spec.kind() == TargetKind.ITEM && item.isEmpty()) {
            item = modelFor(spec);
        }
        return new TargetLine(spec, item, tag.getInt("amount"), tag.getBoolean("all"));
    }

    /** Item "puro" do id (linhas de item), ou vazio se não existe / não é linha de item. */
    private static ItemStack modelFor(TargetSpec spec) {
        ResourceLocation id = spec.location();
        if (spec.kind() != TargetKind.ITEM || id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
}
