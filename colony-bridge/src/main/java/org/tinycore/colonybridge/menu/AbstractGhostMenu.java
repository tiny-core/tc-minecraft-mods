package org.tinycore.colonybridge.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Base dos menus com "ghost slots": posições que mostram um item como modelo de comparação, mas que
 * nunca guardam nem entregam itens de verdade (filtro da ponte, listas do bloco de abastecimento).
 * <p>
 * É aqui que fica a proteção contra duplicação, num lugar só: clique copia uma unidade do item do
 * cursor sem tirá-lo da mão, e retirar, arrastar ou juntar itens nesses slots é bloqueado.
 * Os ghost slots são sempre os primeiros do menu (índices 0 a {@code ghostCount}-1).
 */
public abstract class AbstractGhostMenu extends AbstractContainerMenu {

    private final GhostContainer ghosts;
    private final int ghostCount;

    /**
     * @param items lista viva dos ghost slots (no servidor, a do bloco; várias listas podem ser juntadas
     *              com {@link JoinedList}); só {@code get}/{@code set} são usados, o tamanho nunca muda
     */
    protected AbstractGhostMenu(MenuType<?> type, int containerId, List<ItemStack> items, Runnable onChanged) {
        super(type, containerId);
        this.ghostCount = items.size();
        this.ghosts = new GhostContainer(items, onChanged);
    }

    /** Adiciona um ghost slot; a ordem das chamadas define o índice dele no menu. */
    protected void addGhostSlot(int index, int x, int y, BooleanSupplier visible) {
        addSlot(new GhostSlot(ghosts, index, x, y, visible));
    }

    /**
     * Inventário do jogador no espaçamento padrão do Minecraft (18 px por slot): três linhas de nove
     * e a barra rápida embaixo. Igual nas telas do mod, por isso fica aqui.
     */
    protected void addPlayerInventory(Inventory inventory, int x, int inventoryY, int hotbarY,
                                      BooleanSupplier visible) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new TabSlot(inventory, 9 + row * 9 + col, x + col * 18, inventoryY + row * 18, visible));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new TabSlot(inventory, col, x + col * 18, hotbarY, visible));
        }
    }

    protected int ghostCount() {
        return ghostCount;
    }

    protected ItemStack ghost(int slot) {
        return ghosts.getItem(slot);
    }

    /** true se este jogador pode mexer nos ghost slots agora (permissão na colônia). */
    protected abstract boolean canEditGhosts(Player player);

    /** Gancho após uma linha mudar (ex.: definir a quantidade alvo padrão). */
    protected void onGhostSet(int slot, ItemStack stack) {
    }

    /**
     * Intercepta cliques nos ghost slots antes da lógica padrão do Minecraft (que moveria itens):
     * clique normal copia 1 unidade do item do cursor (ou limpa, com a mão vazia); shift-clique limpa.
     * Qualquer outro tipo de clique (número, soltar, clonar) é ignorado. O item do cursor nunca muda.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= ghostCount) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (clickType == ClickType.PICKUP) {
            setGhost(slotId, getCarried(), player);
        } else if (clickType == ClickType.QUICK_MOVE) {
            setGhost(slotId, ItemStack.EMPTY, player);
        }
    }

    /**
     * Faixa de ghost slots {@code [início, fim)} que recebe o shift-clique do inventário. Por padrão, todos;
     * menus com mais de um grupo (ex.: filtro e preferidos) devolvem só o grupo da aba aberta.
     */
    protected int quickMoveStart() {
        return 0;
    }

    protected int quickMoveEnd() {
        return ghostCount;
    }

    /** Shift-clique no inventário: copia o item para a primeira linha livre. Nunca move itens. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < ghostCount) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int i = quickMoveStart(); i < quickMoveEnd(); i++) {
            if (ghosts.getItem(i).isEmpty()) {
                setGhost(i, stack, player);
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Grava uma cópia (1 unidade) na linha. No servidor, confere de novo a permissão (ela pode ter
     * sido retirada com a tela aberta). Chamado também pelos pacotes já validados (arrastar do JEI).
     */
    public void setGhost(int slot, ItemStack stack, Player player) {
        if (slot < 0 || slot >= ghostCount || !canEditGhosts(player)) {
            return;
        }
        ghosts.setItem(slot, stack);
        onGhostSet(slot, stack);
    }

    /** Duplo clique juntando itens nunca puxa dos ghost slots. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /** Arrastar o cursor espalhando itens nunca coloca nos ghost slots. */
    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot) && super.canDragTo(slot);
    }
}
