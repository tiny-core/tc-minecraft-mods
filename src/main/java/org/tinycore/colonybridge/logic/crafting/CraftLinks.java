package org.tinycore.colonybridge.logic.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A ponte como <b>dona</b> ("requester") dos crafts que ela pede ao AE2. Quando um craft fica pronto, o
 * AE2 chama {@link #insertCraftedItems} e o resultado vai direto para o armazém (via {@link Sink}), sem
 * passar pela rede ME, onde outra máquina poderia consumi-lo antes.
 * <p>
 * Guarda os vínculos ({@code ICraftingLink}) de cada craft com o pedido da colônia a que ele pertence, e
 * os salva no NBT da ponte: depois de um reinício o AE2 reencontra a ponte pelos vínculos e o craft
 * continua chegando no armazém. O AE2 acha este objeto porque ele é registrado como serviço do nó ME
 * da ponte ({@code addService(ICraftingRequester.class, ...)} antes de o nó ser criado).
 * <p>
 * Regra de segurança: o {@link Sink} deve sempre aceitar tudo que puder (o que não couber nos racks vai
 * para a rede). Se o requester recusar itens, o AE2 <b>não</b> os joga na rede: o job fica parado.
 */
public final class CraftLinks implements ICraftingRequester {

    /** Para onde vão os itens craftados e quem reage ao fim de um job (implementado pela ponte). */
    public interface Sink {
        /**
         * Recebe itens craftados. Deve aceitar o máximo possível (racks e, no que sobrar, a rede ME).
         *
         * @return quanto foi (ou seria, em SIMULATE) aceito
         */
        long accept(AEItemKey key, long amount, Actionable mode);

        /** O job terminou ({@code completed}) ou foi cancelado no AE2. */
        void jobEnded(Job job, boolean completed);
    }

    /**
     * Um craft em andamento desta ponte.
     *
     * @param colonyKey colônia do pedido (chave do {@code DeliveryLedger})
     * @param requestId pedido da colônia atendido por este craft
     * @param item      item craftado (para a tela e para saber se já há craft desse item)
     */
    public record Job(String colonyKey, String requestId, AEItemKey item) {}

    private final Supplier<IGridNode> node;
    private final Sink sink;
    private final Runnable onChanged;
    /** Ordem de inserção mantida (LinkedHashMap ≈ um Dictionary que lembra a ordem) para o NBT ser estável. */
    private final Map<ICraftingLink, Job> jobs = new LinkedHashMap<>();

    /**
     * @param node      nó ME da ponte (o AE2 usa para saber a qual rede o requester pertence)
     * @param onChanged chamado quando os vínculos mudam (a ponte marca o bloco para salvar)
     */
    public CraftLinks(Supplier<IGridNode> node, Sink sink, Runnable onChanged) {
        this.node = node;
        this.sink = sink;
        this.onChanged = onChanged;
    }

    /** Registra o vínculo de um job recém-enviado ao AE2. */
    public void add(ICraftingLink link, Job job) {
        jobs.put(link, job);
        onChanged.run();
    }

    /** Craft ainda em andamento para o pedido, ou null. Descarta vínculos já terminados. */
    public @Nullable Job activeFor(String requestId) {
        pruneFinished();
        for (Job job : jobs.values()) {
            if (job.requestId().equals(requestId)) {
                return job;
            }
        }
        return null;
    }

    /** true se esta ponte já tem um craft desse item em andamento. */
    public boolean isCrafting(AEItemKey key) {
        for (Job job : jobs.values()) {
            if (job.item().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** Vínculos terminados sem aviso (ex.: rede reconstruída) não podem ficar para sempre. */
    private void pruneFinished() {
        Iterator<ICraftingLink> it = jobs.keySet().iterator();
        while (it.hasNext()) {
            ICraftingLink link = it.next();
            if (link.isDone() || link.isCanceled()) {
                it.remove();
                onChanged.run();
            }
        }
    }

    // ---------------------------------------------------------------- ICraftingRequester (chamado pelo AE2)

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.copyOf(jobs.keySet());
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        if (!(what instanceof AEItemKey key) || !jobs.containsKey(link) || amount <= 0) {
            return 0;
        }
        return sink.accept(key, amount, mode);
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        Job job = jobs.remove(link);
        if (job == null) {
            return;
        }
        onChanged.run();
        sink.jobEnded(job, !link.isCanceled());
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return node.get();
    }

    // ---------------------------------------------------------------- NBT

    public ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        jobs.forEach((link, job) -> {
            CompoundTag linkTag = new CompoundTag();
            link.writeToNBT(linkTag);
            CompoundTag entry = new CompoundTag();
            entry.put("link", linkTag);
            entry.putString("colony", job.colonyKey());
            entry.putString("request", job.requestId());
            entry.put("item", job.item().toTagGeneric(registries));
            list.add(entry);
        });
        return list;
    }

    /**
     * Restaura os vínculos. {@code StorageHelper.loadCraftingLink} recria o vínculo apontando para este
     * requester; o AE2 o reconecta ao job quando o nó da ponte entra na rede.
     */
    public void load(ListTag list, HolderLookup.Provider registries) {
        jobs.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                AEKey item = AEKey.fromTagGeneric(registries, entry.getCompound("item"));
                if (!(item instanceof AEItemKey key)) {
                    continue;
                }
                ICraftingLink link = StorageHelper.loadCraftingLink(entry.getCompound("link"), this);
                jobs.put(link, new Job(entry.getString("colony"), entry.getString("request"), key));
            } catch (RuntimeException e) {
                // Item de um mod removido, NBT corrompido...: descarta este vínculo e segue com os outros.
                ColonyBridgeMod.LOG.warn("Vínculo de craft inválido ignorado: {}", e.toString());
            }
        }
    }

    /** Lê a lista gravada por {@link #save} de dentro de uma tag maior. */
    public static ListTag listFrom(CompoundTag tag, String name) {
        return tag.getList(name, Tag.TAG_COMPOUND);
    }
}
