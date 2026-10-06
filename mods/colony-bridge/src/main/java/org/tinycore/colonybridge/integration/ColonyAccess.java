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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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

    /** Teto de exemplos guardados por pedido ({@link OpenRequest#examples}). */
    public static final int MAX_EXAMPLES = 8;

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
     * Dimensão de uma chave de colônia ({@link #colonyKey}: {@code dimensão#id}), ou null se a chave é inválida.
     * Usada pelo tablet, que guarda só a chave e precisa achar o mundo da colônia.
     */
    public static @Nullable ResourceLocation dimensionOf(String colonyKey) {
        int separator = colonyKey.lastIndexOf('#');
        return separator <= 0 ? null : ResourceLocation.tryParse(colonyKey.substring(0, separator));
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
                    request.getShortDisplayString(), examplesOf(request, exact)));
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

    /** Pedidos em aberto da colônia onde está {@code pos} (lista vazia fora de colônia). */
    public static List<OpenRequest> openRequestsAt(Level level, BlockPos pos) {
        IColony colony = findColony(level, pos);
        return colony == null ? List.of() : openRequests(colony);
    }

    /** Alguns itens aceitos pelo pedido (o exato, ou os exemplos do MineColonies), cópias de 1 unidade. */
    private static List<ItemStack> examplesOf(IRequest<?> request, ItemStack exact) {
        if (!exact.isEmpty()) {
            return List.of(exact.copyWithCount(1));
        }
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : request.getDisplayStacks()) {
            if (result.size() >= MAX_EXAMPLES) break;
            if (!stack.isEmpty()) result.add(stack.copyWithCount(1));
        }
        return result;
    }

    /** Nome da colônia (limitado a 64 caracteres para o pacote enviado à tela). */
    public static String colonyName(IColony colony) {
        String name = colony.getName();
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    /**
     * Inventários de todos os racks dos armazéns da colônia (apenas chunks carregados), um por bloco.
     * <p>
     * Usa o inventário próprio de cada rack ({@code getInventory}) e não a capability
     * ({@code getItemHandlerCap}): num rack duplo, a capability das <b>duas</b> metades devolve o
     * inventário combinado das duas, e como o armazém lista as duas posições, tudo seria contado
     * (e movido) em dobro.
     */
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
                    handlers.add(rack.getInventory());
                }
            }
        }
        return handlers;
    }

    /**
     * Racks do armazém da colônia nesta posição, para o Terminal do Armazém: só se a posição está numa
     * colônia e o jogador tem a mesma permissão exigida da ponte ({@link #canUseBridge}).
     *
     * @return null se não há colônia ou o jogador não tem permissão (lista vazia = colônia sem armazém)
     */
    public static @Nullable List<IItemHandler> accessibleRacks(Level level, BlockPos pos, UUID player) {
        IColony colony = findColony(level, pos);
        if (colony == null || !canUseBridge(colony, player)) {
            return null;
        }
        return warehouseRacks(colony);
    }

    /** Chave da colônia nesta posição ({@link #colonyKey}), ou null fora de colônia. */
    public static @Nullable String colonyKeyAt(Level level, BlockPos pos) {
        IColony colony = findColony(level, pos);
        return colony == null ? null : colonyKey(colony);
    }

    /** true se a posição está numa colônia e o jogador tem a permissão da ponte ({@link #canUseBridge}) nela. */
    public static boolean canUseAt(Level level, BlockPos pos, @Nullable UUID player) {
        IColony colony = findColony(level, pos);
        return colony != null && canUseBridge(colony, player);
    }

    /** Nome da colônia nesta posição (limitado como em {@link #colonyName}), ou vazio se não há colônia. */
    public static String colonyNameAt(Level level, BlockPos pos) {
        IColony colony = findColony(level, pos);
        return colony == null ? "" : colonyName(colony);
    }

    /**
     * Chunks reivindicados pela colônia nesta posição (formato {@code ChunkPos.asLong}), para o Chunk Loader.
     * Lê o mapa de reivindicações do MineColonies da dimensão (inclui chunks não carregados, sem carregá-los) e
     * fica com os que têm esta colônia como dona. Vazio fora de colônia.
     */
    public static List<Long> claimedChunksAt(Level level, BlockPos pos) {
        IColony colony = findColony(level, pos);
        if (colony == null) {
            return List.of();
        }
        List<Long> chunks = new ArrayList<>();
        IColonyManager.getInstance().getClaimData(colony.getDimension()).forEach((chunk, claim) -> {
            if (claim != null && claim.getOwningColony() == colony.getID()) {
                chunks.add(chunk.toLong());
            }
        });
        return chunks;
    }

    /** Centro da colônia nesta posição (bloco da prefeitura), ou null fora de colônia. */
    public static @Nullable BlockPos colonyCenterAt(Level level, BlockPos pos) {
        IColony colony = findColony(level, pos);
        return colony == null ? null : colony.getCenter();
    }

    /**
     * true se algum membro da colônia desta posição (dono, oficiais, amigos... quem o MineColonies considera
     * membro) está online no servidor. Percorre só a lista de jogadores online.
     */
    public static boolean memberOnlineAt(Level level, BlockPos pos) {
        IColony colony = findColony(level, pos);
        if (colony == null || level.getServer() == null) {
            return false;
        }
        for (Player player : level.getServer().getPlayerList().getPlayers()) {
            if (colony.getPermissions().isColonyMember(player)) {
                return true;
            }
        }
        return false;
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
