package org.tinycore.colonybridge.integration;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Único ponto do mod que fala com o MineColonies.
 * Se uma atualização do MineColonies partir algo, é aqui que se corrige.
 */
public final class ColonyAccess {

    private ColonyAccess() {}

    /** Colónia cujas fronteiras contêm a posição, ou null. */
    public static @Nullable IColony findColony(Level level, BlockPos pos) {
        return IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
    }

    /**
     * Pedidos que nenhum resolver interno conseguiu tratar: os que estão
     * no resolver do jogador (lista "em aberto") e os que estão em nova tentativa.
     */
    public static List<OpenRequest> openRequests(IColony colony) {
        IRequestManager manager = colony.getRequestManager();
        Set<IToken<?>> tokens = new LinkedHashSet<>();
        tokens.addAll(manager.getPlayerResolver().getAllAssignedRequests());
        tokens.addAll(manager.getRetryingRequestResolver().getAllAssignedRequests());

        List<OpenRequest> result = new ArrayList<>();
        for (IToken<?> token : tokens) {
            IRequest<?> request;
            try {
                request = manager.getRequestForToken(token);
            } catch (IllegalArgumentException e) {
                continue; // pedido já foi removido entretanto
            }
            if (request == null || !(request.getRequest() instanceof IDeliverable deliverable)) {
                continue; // não é um pedido de itens
            }
            ItemStack exact = request.getRequest() instanceof Stack stack ? stack.getStack().copy() : ItemStack.EMPTY;
            result.add(new OpenRequest(token, deliverable, exact));
        }
        return result;
    }

    /** Inventários de todos os racks dos armazéns da colónia (apenas chunks carregados). */
    public static List<IItemHandler> warehouseRacks(IColony colony) {
        Level level = colony.getWorld();
        List<IItemHandler> handlers = new ArrayList<>();
        if (level == null) {
            return handlers;
        }
        for (IWareHouse warehouse : colony.getServerBuildingManager().getWareHouses()) {
            for (BlockPos pos : warehouse.getContainers()) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                if (level.getBlockEntity(pos) instanceof AbstractTileEntityRack rack) {
                    IItemHandler handler = rack.getItemHandlerCap((Direction) null);
                    if (handler != null) {
                        handlers.add(handler);
                    }
                }
            }
        }
        return handlers;
    }

    /**
     * Pede ao MineColonies para voltar a procurar um resolver para o pedido.
     * Depois de pormos os itens no armazém, o resolver do armazém deve ficar com ele
     * e um courier faz a entrega.
     */
    public static void reassign(IColony colony, IToken<?> token) {
        try {
            colony.getRequestManager().reassignRequest(token, List.of());
        } catch (IllegalArgumentException e) {
            ColonyBridgeMod.LOG.debug("Pedido {} desapareceu antes da reatribuição", token);
        }
    }
}
