package org.tinycore.colonybridge.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.block.supply.ColonySupplyBlockEntity;
import org.tinycore.colonybridge.block.supply.StockList;
import org.tinycore.colonybridge.client.ClientPayloadHandler;
import org.tinycore.colonybridge.menu.bridge.BridgeTab;
import org.tinycore.colonybridge.menu.bridge.ColonyBridgeMenu;
import org.tinycore.colonybridge.menu.supply.ColonySupplyMenu;
import org.tinycore.core.block.RedstoneMode;

import java.util.List;

/**
 * Registro dos pacotes do mod e tratamento dos que chegam ao servidor.
 * <p>
 * Os handlers rodam na thread principal do jogo (padrão do NeoForge 21.1), então podem mexer no
 * mundo com segurança. O handler do cliente só chama {@link ClientPayloadHandler} dentro de uma
 * lambda: a classe de cliente só é carregada quando a lambda executa, o que nunca acontece num
 * servidor dedicado.
 */
public final class ModNetwork {

    /** Versão do protocolo: mudar quando o formato de algum pacote mudar (cliente e servidor precisam casar). */
    private static final String PROTOCOL_VERSION = "7";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(BridgeSnapshotPayload.TYPE, BridgeSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.onSnapshot(payload));
        registrar.playToClient(SupplySnapshotPayload.TYPE, SupplySnapshotPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.onSupplySnapshot(payload));
        registrar.playToServer(BridgeSettingsPayload.TYPE, BridgeSettingsPayload.STREAM_CODEC,
                ModNetwork::onSettings);
        registrar.playToServer(FilterSlotPayload.TYPE, FilterSlotPayload.STREAM_CODEC,
                ModNetwork::onFilterSlot);
        registrar.playToServer(CraftSettingsPayload.TYPE, CraftSettingsPayload.STREAM_CODEC,
                ModNetwork::onCraftSettings);
        registrar.playToServer(BridgeTabPayload.TYPE, BridgeTabPayload.STREAM_CODEC,
                ModNetwork::onBridgeTab);
        registrar.playToServer(SupplyConfigPayload.TYPE, SupplyConfigPayload.STREAM_CODEC,
                ModNetwork::onSupplyConfig);
        registrar.playToClient(WarehouseContentsPayload.TYPE, WarehouseContentsPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.onWarehouseContents(payload));
        registrar.playToServer(WarehouseActionPayload.TYPE, WarehouseActionPayload.STREAM_CODEC,
                TerminalPackets::onAction);
        registrar.playToServer(TerminalRecipePayload.TYPE, TerminalRecipePayload.STREAM_CODEC,
                TerminalPackets::onRecipe);
    }

    private static void onSettings(BridgeSettingsPayload payload, IPayloadContext context) {
        ColonyBridgeMenu menu = validMenu(context, payload.containerId(), ColonyBridgeMenu.class);
        if (menu == null) {
            return;
        }
        menu.getBridge().applySettings(payload.settings());
        menu.requestSync();
    }

    /**
     * Item arrastado do JEI para um ghost slot da ponte (filtro ou preferidos). O menu confere o índice
     * e grava só uma cópia de 1 unidade.
     */
    private static void onFilterSlot(FilterSlotPayload payload, IPayloadContext context) {
        ColonyBridgeMenu menu = validMenu(context, payload.containerId(), ColonyBridgeMenu.class);
        if (menu == null) {
            return;
        }
        menu.setGhost(payload.slot(), payload.stack(), context.player());
    }

    /** Preferências de craft; o codec já limpou os ids de mod e limitou a lista. */
    private static void onCraftSettings(CraftSettingsPayload payload, IPayloadContext context) {
        ColonyBridgeMenu menu = validMenu(context, payload.containerId(), ColonyBridgeMenu.class);
        if (menu == null) {
            return;
        }
        menu.getBridge().applyCraftSettings(payload.settings());
        menu.requestSync();
    }

    /** Aba aberta na tela: só decide o destino do shift-clique; não altera o bloco. */
    private static void onBridgeTab(BridgeTabPayload payload, IPayloadContext context) {
        ColonyBridgeMenu menu = validMenu(context, payload.containerId(), ColonyBridgeMenu.class);
        if (menu != null) {
            menu.setTab(BridgeTab.byId(payload.tab()));
        }
    }

    /** Quantidades e modo de redstone do bloco de abastecimento; cada valor é limitado aqui. */
    private static void onSupplyConfig(SupplyConfigPayload payload, IPayloadContext context) {
        ColonySupplyMenu menu = validMenu(context, payload.containerId(), ColonySupplyMenu.class);
        if (menu == null) {
            return;
        }
        ColonySupplyBlockEntity supply = menu.getSupply();
        if (supply == null) {
            return;
        }
        supply.setRedstoneMode(RedstoneMode.byId(payload.redstoneMode()));
        List<Integer> amounts = payload.amounts();
        for (int slot = 0; slot < Math.min(amounts.size(), StockList.SIZE); slot++) {
            supply.setAmount(slot, amounts.get(slot)); // a StockList limita o valor
        }
        menu.requestSync();
    }

    /**
     * Pacote vindo do cliente = hostil até prova em contrário. Devolve o menu só se: é do tipo esperado,
     * está aberto com esse id, o jogador está a até 8 blocos (stillValid), o bloco ainda existe e o
     * jogador tem permissão para configurá-lo. Caso contrário, null (pacote ignorado).
     */
    private static <T extends AbstractContainerMenu> @Nullable T validMenu(IPayloadContext context, int containerId,
                                                                           Class<T> type) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return null;
        }
        AbstractContainerMenu open = player.containerMenu;
        if (!type.isInstance(open) || open.containerId != containerId || !open.stillValid(player)) {
            return null;
        }
        AbstractBridgeBlockEntity host = hostOf(open);
        if (host == null || host.isRemoved()) {
            return null;
        }
        if (!host.canConfigure(player)) {
            ColonyBridgeMod.LOG.debug("{} tentou configurar o bloco em {} sem permissão",
                    player.getGameProfile().getName(), host.getBlockPos());
            return null;
        }
        return type.cast(open);
    }

    /** Bloco por trás do menu, quando ele é do servidor (no cliente é sempre null). */
    private static @Nullable AbstractBridgeBlockEntity hostOf(AbstractContainerMenu menu) {
        if (menu instanceof ColonyBridgeMenu bridge) {
            return bridge.getBridge();
        }
        if (menu instanceof ColonySupplyMenu supply) {
            return supply.getSupply();
        }
        return null;
    }
}
