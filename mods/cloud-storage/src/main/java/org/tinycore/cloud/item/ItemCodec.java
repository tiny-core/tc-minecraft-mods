package org.tinycore.cloud.item;

import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.TcCloud;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Predicate;

/**
 * Converte {@code ItemStack} ↔ bytes guardados na nuvem, usando os registros do servidor ({@code RegistryOps}:
 * encantamentos e outros dados são registros dinâmicos no 1.21 e precisam deles para codificar).
 *
 * <p>Regra da fase 0 (plano §11): o codec do Minecraft devolve erro <b>com resultado parcial</b> quando um
 * componente falha, ou seja, o item sem aquele dado. Aqui QUALQUER erro torna o item incompatível; o parcial
 * nunca é usado (entregaria um item "limpo": perda de dados ou exploit).
 */
public final class ItemCodec {

    /** Teto de memória ao ler o NBT de volta (proteção contra bytes corrompidos que declaram listas enormes). */
    private static final long READ_QUOTA_BYTES = 2L * 1024 * 1024;

    private ItemCodec() {}

    /** Resultado de {@link #encode}: o item pronto ou o motivo da recusa. */
    public record Encoded(@Nullable EncodedItem item, @Nullable CompoundTag tag, @Nullable TransferRejection rejection) {
        public boolean ok() {
            return item != null;
        }
    }

    /** Resultado de {@link #decode}: a situação e, se {@link ItemCompatibility#OK}, o item (quantidade 1). */
    public record Decoded(@NotNull ItemCompatibility status, @Nullable ItemStack stack) {}

    /** Codifica com quantidade 1. {@code tag} vem junto para a heurística de referência ao mundo. */
    public static @NotNull Encoded encode(@NotNull ItemStack stack, @NotNull HolderLookup.Provider registries, int maxBytes) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        DataResult<Tag> result = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1));
        if (result.error().isPresent() || !(result.result().orElse(null) instanceof CompoundTag tag)) {
            TcCloud.LOG.debug("Item não codificável para a nuvem: {} ({})", stack,
                    result.error().map(e -> e.message()).orElse("não é compound"));
            return new Encoded(null, null, TransferRejection.ENCODE_FAILED);
        }
        byte[] bytes = toBytes(tag);
        if (bytes.length > maxBytes) {
            return new Encoded(null, tag, TransferRejection.TOO_LARGE);
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        EncodedItem item = new EncodedItem(ItemFingerprint.of(tag), itemId, stack.getHoverName().getString(), bytes);
        return new Encoded(item, tag, null);
    }

    /**
     * Decodifica neste servidor.
     *
     * @param isModLoaded diz se um namespace (id de mod) está carregado ({@code ModList.get()::isLoaded} no jogo)
     */
    public static @NotNull Decoded decode(byte @NotNull [] bytes, @NotNull HolderLookup.Provider registries,
                                          @NotNull Predicate<String> isModLoaded) {
        CompoundTag tag;
        try {
            tag = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), NbtAccounter.create(READ_QUOTA_BYTES));
        } catch (IOException | RuntimeException e) {
            TcCloud.LOG.debug("Bytes de item ilegíveis na nuvem: {}", e.toString());
            return new Decoded(ItemCompatibility.DATA_INVALID, null);
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null) return new Decoded(ItemCompatibility.DATA_INVALID, null);
        if (!isModLoaded.test(id.getNamespace())) return new Decoded(ItemCompatibility.MOD_MISSING, null);
        if (!BuiltInRegistries.ITEM.containsKey(id)) return new Decoded(ItemCompatibility.ITEM_MISSING, null);

        DataResult<ItemStack> result = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag);
        if (result.error().isPresent() || result.result().isEmpty()) {
            TcCloud.LOG.debug("Item {} com dados inválidos neste servidor: {}", id,
                    result.error().map(e -> e.message()).orElse("vazio"));
            return new Decoded(ItemCompatibility.DATA_INVALID, null);
        }
        return new Decoded(ItemCompatibility.OK, result.result().get());
    }

    private static byte[] toBytes(CompoundTag tag) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(tag, out);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e); // ByteArrayOutputStream não falha
        }
        return bytes.toByteArray();
    }
}
