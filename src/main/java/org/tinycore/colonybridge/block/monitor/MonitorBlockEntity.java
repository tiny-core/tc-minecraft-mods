package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.tinycore.colonybridge.multiblock.MonitorFormation;
import org.tinycore.colonybridge.registry.ModBlockEntities;

/**
 * Dados de um bloco de monitor: onde ele está dentro da tela e o tamanho da tela.
 * <p>
 * O <b>mestre</b> é o bloco na posição (0, 0), canto inferior esquerdo visto de frente; só ele é
 * desenhado pelo renderer (cobrindo a tela toda). Os demais só sabem a distância até o mestre.
 * <p>
 * Sincronização com o cliente: {@code getUpdateTag}/{@code getUpdatePacket} são o mecanismo nativo do
 * Minecraft para mandar dados de block entity aos jogadores que têm o chunk carregado. Só enviamos
 * quando a estrutura muda ({@link #setStructure}).
 */
public class MonitorBlockEntity extends BlockEntity {

    private int offsetX;
    private int offsetY;
    private int width = 1;
    private int height = 1;
    private boolean valid = true;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLONY_MONITOR.get(), pos, state);
    }

    /**
     * Define a posição deste bloco na tela. Chamado pela {@link MonitorFormation} no servidor;
     * se nada mudou, não salva nem envia nada.
     */
    public void setStructure(int offsetX, int offsetY, int width, int height, boolean valid) {
        if (this.offsetX == offsetX && this.offsetY == offsetY && this.width == width
                && this.height == height && this.valid == valid) {
            return;
        }
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
        this.valid = valid;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public boolean isMaster() {
        return offsetX == 0 && offsetY == 0;
    }

    public boolean isValidStructure() {
        return valid;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Direction getFacing() {
        return getBlockState().getValue(MonitorBlock.FACING);
    }

    /** Posição do bloco mestre desta tela. */
    public BlockPos getMasterPos() {
        return worldPosition.relative(MonitorFormation.right(getFacing()), -offsetX).below(offsetY);
    }

    // ---------------------------------------------------------------- NBT e sincronização

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("offsetX", offsetX);
        tag.putInt("offsetY", offsetY);
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putBoolean("valid", valid);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        offsetX = tag.getInt("offsetX");
        offsetY = tag.getInt("offsetY");
        width = Math.max(1, tag.getInt("width"));
        height = Math.max(1, tag.getInt("height"));
        valid = !tag.contains("valid") || tag.getBoolean("valid");
    }

    /** Dados enviados quando o chunk carrega no cliente. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    /** Pacote enviado a cada {@code sendBlockUpdated}; usa o {@link #getUpdateTag}. */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
