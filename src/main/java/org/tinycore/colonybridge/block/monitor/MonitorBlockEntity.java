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
import org.jetbrains.annotations.Nullable;
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
 * quando a estrutura muda ({@link #setStructure}) ou quando os dados da ponte mudam.
 * <p>
 * Ligação: o mestre guarda a posição do bloco mostrado — Ponte ou Abastecedor, qualquer
 * {@link MonitorSource} ({@link #setLink}, feito pelo cartão de ligação). A cada {@link #REFRESH_TICKS}
 * ele lê o bloco — só se o chunk dele estiver carregado — e, se os {@link MonitorData} mudaram, envia ao
 * cliente.
 */
public class MonitorBlockEntity extends BlockEntity {

    private int offsetX;
    private int offsetY;
    private int width = 1;
    private int height = 1;
    private boolean valid = true;

    /** Frequência de leitura da ponte: 1×/s. */
    private static final int REFRESH_TICKS = 20;
    /** Só no mestre: ponte ligada (salva no disco). */
    private @Nullable BlockPos link;
    /** Só no mestre: últimos dados lidos (não salvos no disco, só sincronizados). */
    private MonitorData data = MonitorData.UNLINKED;
    private int refreshCounter;

    /** Troca automática de página da lista (cliente): a cada 10 s. */
    private static final int AUTO_PAGE_TICKS = 200;
    /** Depois de um clique, a troca automática pausa por 30 s. */
    private static final int MANUAL_PAUSE_TICKS = 600;
    /** Só no cliente: contador de páginas (o renderer faz o módulo pelo número de páginas). */
    private int pageCounter;
    private int ticksSincePageChange;
    private int manualPauseTicks;

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

    /** Tick do servidor (todos os monitores; só o mestre de uma tela válida trabalha). */
    public void serverTick() {
        if (level == null || !isMaster() || !valid || ++refreshCounter < REFRESH_TICKS) {
            return;
        }
        refreshCounter = 0;
        MonitorData current = readBridge();
        if (!current.equals(data)) {
            data = current;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Tick do cliente (só o mestre): avança a página automaticamente. É estado só de exibição,
     * de cada jogador — não vai para o servidor.
     */
    public void clientTick() {
        if (!isMaster()) {
            return;
        }
        if (manualPauseTicks > 0) {
            manualPauseTicks--;
            return;
        }
        if (++ticksSincePageChange >= AUTO_PAGE_TICKS) {
            ticksSincePageChange = 0;
            pageCounter++;
        }
    }

    /** Clique do jogador (cliente): +1 avança, -1 volta; pausa a troca automática. */
    public void turnPage(int delta) {
        pageCounter += delta;
        ticksSincePageChange = 0;
        manualPauseTicks = MANUAL_PAUSE_TICKS;
    }

    /** Página atual para {@code pages} páginas (sempre entre 0 e pages-1). */
    public int currentPage(int pages) {
        return pages <= 0 ? 0 : Math.floorMod(pageCounter, pages);
    }

    /** Posição horizontal deste bloco dentro da tela (0 = coluna do mestre). */
    public int getOffsetX() {
        return offsetX;
    }

    /**
     * Lê o bloco ligado (Ponte ou Abastecedor, qualquer {@link MonitorSource}) sem carregar chunks:
     * chunk descarregado ou bloco sumido = "não encontrado".
     */
    private MonitorData readBridge() {
        if (link == null) {
            return MonitorData.UNLINKED;
        }
        if (!level.isLoaded(link) || !(level.getBlockEntity(link) instanceof MonitorSource source)) {
            return MonitorData.MISSING;
        }
        return source.monitorData();
    }

    public @Nullable BlockPos getLink() {
        return link;
    }

    /** Liga (ou desliga, com null) a ponte. Validação de distância/permissão fica no cartão. */
    public void setLink(@Nullable BlockPos bridgePos) {
        link = bridgePos == null ? null : bridgePos.immutable();
        refreshCounter = REFRESH_TICKS; // atualiza a tela já no próximo tick
        setChanged();
    }

    public MonitorData getData() {
        return data;
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
        if (link != null) {
            tag.putLong("link", link.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        offsetX = tag.getInt("offsetX");
        offsetY = tag.getInt("offsetY");
        width = Math.max(1, tag.getInt("width"));
        height = Math.max(1, tag.getInt("height"));
        valid = !tag.contains("valid") || tag.getBoolean("valid");
        link = tag.contains("link") ? BlockPos.of(tag.getLong("link")) : null;
        if (tag.contains("data")) { // só vem na sincronização com o cliente
            data = MonitorData.load(tag.getCompound("data"), registries);
        }
    }

    /** Dados enviados ao cliente (chunk carregando ou {@code sendBlockUpdated}): estrutura + dados da ponte. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        if (isMaster()) {
            tag.put("data", data.save(registries));
        }
        return tag;
    }

    /** Pacote enviado a cada {@code sendBlockUpdated}; usa o {@link #getUpdateTag}. */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
