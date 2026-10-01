package org.tinycore.cloud.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serialização de NBT com <b>ordem estável</b>: as chaves de todo {@link CompoundTag} saem em ordem
 * alfabética, em qualquer nível.
 *
 * <p>Por que existe (fase 0): o {@code CompoundTag} guarda as chaves num {@code HashMap}, então o NBT
 * "normal" do mesmo item pode sair com bytes diferentes de uma vez para outra. A impressão digital
 * ({@link ItemFingerprint}) precisa ser sempre igual para o mesmo item, senão o mesmo item viraria duas
 * linhas na nuvem. Os bytes daqui servem só para isso: o item guardado no TCMine usa o NBT comum.
 */
public final class CanonicalNbt {

    private CanonicalNbt() {}

    public static byte @NotNull [] toBytes(@NotNull Tag tag) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            write(tag, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // ByteArrayOutputStream não falha
        }
        return bytes.toByteArray();
    }

    private static void write(Tag tag, DataOutput out) throws IOException {
        out.writeByte(tag.getId());
        switch (tag) {
            case CompoundTag compound -> {
                List<String> keys = new ArrayList<>(compound.getAllKeys());
                Collections.sort(keys);
                out.writeInt(keys.size());
                for (String key : keys) {
                    out.writeUTF(key);
                    write(compound.get(key), out);
                }
            }
            case ListTag list -> {
                out.writeInt(list.size());
                for (int i = 0; i < list.size(); i++) write(list.get(i), out);
            }
            // Números, textos e arrays não têm ordem interna variável: o formato padrão já é estável.
            default -> tag.write(out);
        }
    }
}
