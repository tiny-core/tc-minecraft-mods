package org.tinycore.cloud.item;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.item.policy.ItemPolicy;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Decide se um item pode entrar ou sair da nuvem (plano §6). Roda sempre no servidor.
 *
 * <p>Entrada, em ordem (a primeira que recusa decide): codificação/tamanho → política do dono → tag
 * {@code #tccloud:never_transfer} → conteúdo interno ({@link ContentProbe}) → referência ao mundo
 * ({@link WorldReferenceDetector}, já calculada no {@link ItemCatalog}).
 *
 * <p>Saída: só a política (pode ter mudado depois que o item entrou). A compatibilidade com este servidor é
 * do {@link ItemCatalog}.
 */
public final class TransferGuard {

    private final Supplier<ItemPolicy> policy;
    private final List<ContentProbe> probes;

    /**
     * @param policy a política atual da nuvem (muda quando o TCMine avisa; por isso um fornecedor)
     * @param probes fontes de "tem itens dentro?"
     */
    public TransferGuard(@NotNull Supplier<ItemPolicy> policy, @NotNull List<ContentProbe> probes) {
        this.policy = policy;
        this.probes = List.copyOf(probes);
    }

    /** {@code null} = pode entrar. */
    public @Nullable TransferRejection checkInsert(@NotNull ItemStack stack, @NotNull ItemCatalog.Described described) {
        if (described.rejection() != null) return described.rejection();
        if (!policyAllows(stack, described.item().itemId())) return TransferRejection.POLICY;
        if (stack.is(CloudItemTags.NEVER_TRANSFER)) return TransferRejection.NEVER_TRANSFER;
        for (ContentProbe probe : probes) {
            if (probe.hasContents(stack)) return TransferRejection.NOT_EMPTY;
        }
        if (described.worldReference() != null) return TransferRejection.WORLD_REFERENCE;
        return null;
    }

    /** O item (já decodificado neste servidor) pode sair pela política atual? */
    public boolean allowsExtract(@NotNull ItemStack prototype, @NotNull String itemId) {
        return policyAllows(prototype, itemId);
    }

    private boolean policyAllows(ItemStack stack, String itemId) {
        Set<String> tags = stack.getTags().map(tag -> tag.location().toString()).collect(Collectors.toSet());
        return policy.get().evaluate(itemId, tags).allowed();
    }
}
