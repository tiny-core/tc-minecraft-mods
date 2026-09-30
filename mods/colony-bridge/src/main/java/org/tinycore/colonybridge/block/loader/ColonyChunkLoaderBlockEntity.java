package org.tinycore.colonybridge.block.loader;

import appeng.api.networking.IGrid;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.logic.loader.ChunkSelection;
import org.tinycore.colonybridge.logic.loader.ChunkTickets;
import org.tinycore.colonybridge.logic.loader.LoaderRule;
import org.tinycore.colonybridge.logic.loader.LoaderState;
import org.tinycore.colonybridge.menu.loader.ChunkLoaderSnapshot;
import org.tinycore.colonybridge.registry.ModBlockEntities;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.core.block.RedstoneMode;

/**
 * TC Colony Chunk Loader: mantém carregados os chunks reivindicados pela colônia, pagando energia AE por chunk.
 * <p>
 * Nó ME, dono, permissão, cabo, redstone e "um por colônia" vêm do {@link AbstractBridgeBlockEntity}. A cada
 * {@link #CHECK_TICKS} (sempre, mesmo sem rede ME: é preciso soltar chunks quando a energia acaba) o bloco:
 * <ol>
 *   <li>vê se algum membro da colônia está online e guarda a hora (tempo real) da última vez que viu um;</li>
 *   <li>decide a situação pela {@link LoaderRule} (carregando, contagem, sem energia, dormindo, desligado);</li>
 *   <li>calcula os chunks desejados — a área (os {@code chunkLoaderMaxChunks} mais perto do centro, relidos a cada
 *       {@code chunkLoaderRefreshTicks}) e/ou o chunk do próprio bloco — e força/solta só a diferença
 *       ({@link ChunkTickets});</li>
 *   <li>ajusta o consumo parado do nó ME para {@code chunkLoaderPowerPerChunk} × chunks forçados: quem cobra é o
 *       AE2, e sem energia o nó desliga e o próximo passo solta a área.</li>
 * </ol>
 * O chunk do próprio bloco fica carregado enquanto ele estiver ligado, para perceber quando um membro volta.
 * Quando isso acontece, a área é carregada com uma tolerância de {@code chunkLoaderWakeGraceSeconds} sem exigir
 * energia, porque a rede ME pode estar justamente na área que estava descarregada.
 */
public class ColonyChunkLoaderBlockEntity extends AbstractBridgeBlockEntity {

    private static final int CHECK_TICKS = 20;

    /** Chunks com ticket deste bloco agora (salvo: sobrevive a reinícios, junto com os tickets do NeoForge). */
    private final LongSet forced = new LongOpenHashSet();
    /** Área escolhida na última leitura das reivindicações. */
    private LongList area = new LongArrayList();
    private int claimedCount;
    private long nextRefreshTick;
    private int checkCounter;

    private boolean switchedOn = true;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    /** Última vez (relógio do servidor, ms) que um membro foi visto online; -1 = nunca. */
    private long lastMemberMillis = -1;
    private boolean memberOnline;
    private long graceUntilTick;
    private LoaderState state = LoaderState.OFF;
    private BridgeStatus status = BridgeStatus.STARTING;
    private String colonyName = "";

    public ColonyChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHUNK_LOADER.get(), pos, state, ModItems.CHUNK_LOADER.get(), () -> 0.0);
    }

    // ---------------------------------------------------------------- ciclo

    /** Além do ciclo da base (status, cabo), o passo do loader roda sempre, com ou sem rede ME. */
    @Override
    public void serverTick() {
        super.serverTick();
        if (level instanceof ServerLevel serverLevel && ++checkCounter >= CHECK_TICKS) {
            checkCounter = 0;
            step(serverLevel);
        }
    }

    /** O ciclo da base só roda com a rede ativa; aqui só confirma que a rede está ok. */
    @Override
    protected void runCycle(ServerLevel level, IGrid grid) {
        status = state.loadsArea() ? BridgeStatus.WORKING : BridgeStatus.IDLE;
    }

    /** Um jogador entrou ou saiu do servidor: rodar o passo já no próximo tick ({@code LoaderEvents}). */
    public void wake() {
        checkCounter = CHECK_TICKS;
    }

    private void step(ServerLevel serverLevel) {
        BlockPos pos = getBlockPos();
        boolean validColony = ColonyAccess.canUseAt(serverLevel, pos, getOwner())
                && getStatus() != BridgeStatus.DUPLICATE_IN_COLONY;
        colonyName = ColonyAccess.colonyNameAt(serverLevel, pos);
        boolean wasOnline = memberOnline;
        memberOnline = validColony && ColonyAccess.memberOnlineAt(serverLevel, pos);
        long now = System.currentTimeMillis();
        long tick = serverLevel.getGameTime();
        if (memberOnline) {
            if (!wasOnline && !state.loadsArea()) {
                startGrace(tick); // membro voltou: a rede ME pode estar na área descarregada
            }
            if (now - lastMemberMillis >= 60_000) {
                setChanged(); // salva no máximo 1×/min (a precisão de um minuto basta para contar horas)
            }
            lastMemberMillis = now;
        }
        boolean switched = switchedOn && redstoneMode().allows(serverLevel.hasNeighborSignal(pos));
        state = LoaderRule.state(Config.CHUNK_LOADER_ENABLED.get(), switched, validColony,
                managedNode().isActive(), tick < graceUntilTick, memberOnline, remainingMillis(now));
        if (state.loadsArea() && tick >= nextRefreshTick) {
            refreshArea(serverLevel);
            nextRefreshTick = tick + Config.CHUNK_LOADER_REFRESH_TICKS.get();
        }
        apply(serverLevel, desiredChunks());
    }

    private void startGrace(long tick) {
        graceUntilTick = tick + Config.CHUNK_LOADER_WAKE_GRACE_SECONDS.get() * 20L;
    }

    private long remainingMillis(long now) {
        return LoaderRule.remainingMillis(lastMemberMillis, now, Config.CHUNK_LOADER_OFFLINE_HOURS.get());
    }

    /** Relê as reivindicações da colônia e fica com as mais perto do centro. */
    private void refreshArea(ServerLevel serverLevel) {
        var claimed = ColonyAccess.claimedChunksAt(serverLevel, getBlockPos());
        BlockPos center = ColonyAccess.colonyCenterAt(serverLevel, getBlockPos());
        ChunkPos centerChunk = new ChunkPos(center != null ? center : getBlockPos());
        claimedCount = claimed.size();
        area = ChunkSelection.nearest(claimed, centerChunk.x, centerChunk.z, Config.CHUNK_LOADER_MAX_CHUNKS.get());
    }

    private LongSet desiredChunks() {
        LongSet desired = new LongOpenHashSet();
        if (state.loadsArea()) {
            desired.addAll(area);
        }
        if (state.keepsOwnChunk()) {
            desired.add(ChunkPos.asLong(getBlockPos()));
        }
        return desired;
    }

    /** Força/solta só o que mudou e ajusta o consumo de energia. */
    private void apply(ServerLevel serverLevel, LongSet desired) {
        boolean changed = false;
        for (long chunk : new LongArrayList(forced)) {
            if (!desired.contains(chunk)) {
                ChunkTickets.force(serverLevel, getBlockPos(), chunk, false);
                forced.remove(chunk);
                changed = true;
            }
        }
        for (long chunk : desired) {
            if (forced.add(chunk)) {
                ChunkTickets.force(serverLevel, getBlockPos(), chunk, true);
                changed = true;
            }
        }
        managedNode().setIdlePowerUsage(forced.size() * Config.CHUNK_LOADER_POWER_PER_CHUNK.get());
        if (changed) {
            setChanged();
        }
    }

    /** Solta tudo (bloco quebrado). */
    @Override
    public void onBroken(ServerLevel serverLevel) {
        for (long chunk : forced) {
            ChunkTickets.force(serverLevel, getBlockPos(), chunk, false);
        }
        forced.clear();
    }

    // ---------------------------------------------------------------- tela

    /** Botão liga/desliga (tela ou tablet): o próximo passo aplica. */
    public void toggle() {
        switchedOn = !switchedOn;
        if (switchedOn && level != null) {
            startGrace(level.getGameTime()); // ligado agora: dá tempo para a rede ME subir junto com a área
        }
        wake();
        setChanged();
    }

    public void cycleRedstone() {
        redstoneMode = redstoneMode.next();
        wake();
        setChanged();
    }

    public ChunkLoaderSnapshot snapshot() {
        long remaining = state == LoaderState.COUNTDOWN ? remainingMillis(System.currentTimeMillis()) : 0;
        return new ChunkLoaderSnapshot(getStatus(), colonyName, state, switchedOn, redstoneMode, forced.size(), claimedCount,
                Config.CHUNK_LOADER_MAX_CHUNKS.get(), forced.size() * Config.CHUNK_LOADER_POWER_PER_CHUNK.get(),
                remaining / 60_000);
    }

    public LoaderState getLoaderState() {
        return state;
    }

    public int loadedChunks() {
        return forced.size();
    }

    // ---------------------------------------------------------------- base

    @Override
    public ColonyBlockType colonyBlockType() {
        return ColonyBlockType.CHUNK_LOADER;
    }

    @Override
    protected RedstoneMode redstoneMode() {
        return redstoneMode;
    }

    @Override
    public BridgeStatus getStatus() {
        return status;
    }

    @Override
    protected void setStatus(BridgeStatus status) {
        this.status = status;
    }

    @Override
    protected void afterCycle(ServerLevel level) {
        syncVisualState(level, AbstractBridgeBlock.STATUS);
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("on", switchedOn);
        tag.putInt("redstone", redstoneMode.ordinal());
        tag.putLong("lastMember", lastMemberMillis);
        tag.putLongArray("forced", forced.toLongArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        switchedOn = !tag.contains("on") || tag.getBoolean("on");
        redstoneMode = RedstoneMode.byId(tag.getInt("redstone"));
        lastMemberMillis = tag.contains("lastMember") ? tag.getLong("lastMember") : -1;
        forced.clear();
        for (long chunk : tag.getLongArray("forced")) {
            forced.add(chunk);
        }
    }
}
