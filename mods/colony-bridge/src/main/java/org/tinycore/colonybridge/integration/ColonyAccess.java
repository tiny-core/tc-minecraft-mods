package org.tinycore.colonybridge.integration;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
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
import java.util.UUID;

/**
 * Único ponto do mod que fala com o MineColonies.
 * Se uma atualização do MineColonies quebrar algo, é aqui que se corrige.
 */
public final class ColonyAccess {

    private ColonyAccess() {}

    /** Colônia cujas fronteiras contêm a posição, ou null. */
    public static @Nullable IColony findColony(Level level, BlockPos pos) {
        return IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
    }

    /**
     * Chave estável da colônia entre reinícios. O id só é único dentro de uma dimensão,
     * por isso a dimensão entra na chave.
     */
    public static String colonyKey(IColony colony) {
        return colony.getDimension().location() + "#" + colony.getID();
    }

    /**
     * true se a ponte pode ser colocada nesta posição: fora de qualquer colônia (fica inativa até
     * uma colônia alcançá-la) ou dentro de uma colônia onde o jogador tem permissão.
     */
    public static boolean canPlaceBridge(Level level, BlockPos pos, @Nullable UUID player) {
        IColony colony = findColony(level, pos);
        return colony == null || canUseBridge(colony, player);
    }

    /**
     * true se o jogador pode abrir e configurar a ponte nesta posição: dentro de colônia, precisa da
     * mesma permissão de {@link #canUseBridge}; fora, só o dono (ou qualquer um se não há dono salvo).
     */
    public static boolean canConfigureBridge(Level level, BlockPos pos, UUID player, @Nullable UUID owner) {
        IColony colony = findColony(level, pos);
        if (colony != null) {
            return canUseBridge(colony, player);
        }
        return owner == null || owner.equals(player);
    }

    /**
     * true se o jogador pode ligar uma ponte a esta colônia.
     * Exige {@link Action#ACCESS_HUTS} (por padrão: dono, oficiais e amigos), a mesma permissão
     * de abrir as cabanas — a ponte mexe no armazém, então faz sentido exigir o mesmo.
     */
    public static boolean canUseBridge(IColony colony, @Nullable UUID player) {
        if (player == null) {
            return false;
        }
        IPermissions permissions = colony.getPermissions();
        Rank rank = permissions.getRank(player);
        return rank != null && permissions.hasPermission(rank, Action.ACCESS_HUTS);
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
            result.add(new OpenRequest(token, deliverable, exact, iconOf(request, exact),
                    request.getShortDisplayString()));
        }
        return result;
    }

    /** Item para a tela: o pedido exato, senão o primeiro exemplo que o MineColonies mostra. */
    private static ItemStack iconOf(IRequest<?> request, ItemStack exact) {
        if (!exact.isEmpty()) {
            return exact;
        }
        List<ItemStack> examples = request.getDisplayStacks();
        return examples.isEmpty() ? ItemStack.EMPTY : examples.get(0).copyWithCount(1);
    }

    /** Nome da colônia (limitado a 64 caracteres para o pacote enviado à tela). */
    public static String colonyName(IColony colony) {
        String name = colony.getName();
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    /** Inventários de todos os racks dos armazéns da colônia (apenas chunks carregados). */
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
     * Depois de colocarmos os itens no armazém, o resolver do armazém deve ficar com ele
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
