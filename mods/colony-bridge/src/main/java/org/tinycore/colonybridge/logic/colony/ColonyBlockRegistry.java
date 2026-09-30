package org.tinycore.colonybridge.logic.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;

import java.util.HashMap;
import java.util.Map;

/**
 * Registro do mundo com a posição do bloco de cada tipo ({@link ColonyBlockType}) em cada colônia.
 * É o que garante "um por colônia" e, no futuro, o que o Tablet usa para achar os blocos da colônia.
 * <p>
 * Como o {@code DeliveryLedger}, é um {@code SavedData} do overworld (arquivo
 * {@code data/tccolonybridge_colony_blocks.dat}). A chave da colônia já inclui a dimensão
 * ({@code ColonyAccess.colonyKey}), e os blocos de uma colônia ficam sempre na dimensão dela, então a
 * posição sozinha basta.
 * <p>
 * Aqui só se guarda e se lê. Quem decide se um bloco pode ficar com a vaga é o {@code ColonySlots}
 * (camada de blocos), usando o {@link ColonySlotRule}.
 */
public final class ColonyBlockRegistry extends SavedData {

    private static final String FILE_NAME = ColonyBridgeMod.MOD_ID + "_colony_blocks";
    private static final SavedData.Factory<ColonyBlockRegistry> FACTORY =
            new SavedData.Factory<>(ColonyBlockRegistry::new, ColonyBlockRegistry::load, null);

    /** Chave "colônia|TIPO" → posição ({@code BlockPos.asLong()}). */
    private final Map<String, Long> holders = new HashMap<>();

    /** Registro único do servidor, guardado no overworld. */
    public static ColonyBlockRegistry get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    /** Posição registrada para o tipo na colônia, ou null se a vaga está livre. */
    public @Nullable BlockPos holder(String colony, ColonyBlockType type) {
        Long pos = holders.get(key(colony, type));
        return pos == null ? null : BlockPos.of(pos);
    }

    /** Ocupa a vaga (substitui quem estiver nela: a regra já foi conferida por quem chama). */
    public void set(String colony, ColonyBlockType type, BlockPos pos) {
        Long previous = holders.put(key(colony, type), pos.asLong());
        if (previous == null || previous != pos.asLong()) {
            setDirty();
        }
    }

    /** Libera a vaga, mas só se ela é deste bloco (nunca solta a vaga de outro). */
    public void release(String colony, ColonyBlockType type, BlockPos pos) {
        if (holders.remove(key(colony, type), pos.asLong())) {
            setDirty();
        }
    }

    /**
     * Posições de todos os blocos de um tipo (chave da colônia → posição). Usado pelo Chunk Loader: validar tickets
     * ao iniciar o servidor e acordar o loader quando um jogador entra.
     */
    public Map<String, BlockPos> holders(ColonyBlockType type) {
        String suffix = "|" + type.name();
        Map<String, BlockPos> found = new HashMap<>();
        holders.forEach((key, pos) -> {
            if (key.endsWith(suffix)) {
                found.put(key.substring(0, key.length() - suffix.length()), BlockPos.of(pos));
            }
        });
        return found;
    }

    private static String key(String colony, ColonyBlockType type) {
        return colony + "|" + type.name();
    }

    // ---------------------------------------------------------------- NBT

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        holders.forEach((key, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            entry.putLong("pos", pos);
            list.add(entry);
        });
        tag.put("holders", list);
        return tag;
    }

    private static ColonyBlockRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        ColonyBlockRegistry registry = new ColonyBlockRegistry();
        for (Tag element : tag.getList("holders", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            registry.holders.put(entry.getString("key"), entry.getLong("pos"));
        }
        return registry;
    }
}
