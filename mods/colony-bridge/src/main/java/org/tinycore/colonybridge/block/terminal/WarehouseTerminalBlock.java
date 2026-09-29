package org.tinycore.colonybridge.block.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.menu.terminal.WarehouseTerminalMenu;
import org.tinycore.colonybridge.registry.ModBlockEntities;

/**
 * Terminal do Armazém: abre uma tela com todos os itens dos racks do armazém da colônia, para tirar,
 * guardar e craftar como num terminal do AE2.
 * <p>
 * Tudo o que é de bloco (frente virada para quem colocou, permissão, estado visual, cabo ME por baixo,
 * clique direito) vem do {@link AbstractBridgeBlock}, como na Ponte e no Abastecedor. As restrições de rede
 * (energia, Ponte da colônia) ficam no {@link WarehouseTerminalBlockEntity}.
 */
public class WarehouseTerminalBlock extends AbstractBridgeBlock<WarehouseTerminalBlockEntity> {

    public WarehouseTerminalBlock(Properties props) {
        super(props, WarehouseTerminalBlockEntity.class);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarehouseTerminalBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<WarehouseTerminalBlockEntity> blockEntityType() {
        return ModBlockEntities.WAREHOUSE_TERMINAL.get();
    }

    @Override
    protected String tooltipName() {
        return "warehouse_terminal";
    }

    @Override
    protected Component menuTitle() {
        return Component.translatable("gui.tccolonybridge.terminal.title");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory, WarehouseTerminalBlockEntity be) {
        return new WarehouseTerminalMenu(containerId, inventory, be);
    }

    /** A tela do terminal mostra o nome da colônia no cabeçalho, então ele vai junto na abertura. */
    @Override
    protected void writeMenuData(RegistryFriendlyByteBuf buf, WarehouseTerminalBlockEntity be) {
        WarehouseTerminalMenu.writeOpenData(buf, be.getBlockPos(),
                be.getLevel() == null ? "" : ColonyAccess.colonyNameAt(be.getLevel(), be.getBlockPos()));
    }
}
