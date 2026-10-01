package org.tinycore.cloud.item;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Um item como a nuvem o guarda: os bytes do NBT ({@code ItemStack.CODEC}, quantidade 1) e os dados que o
 * painel do TCMine mostra sem decodificar o item (id e nome).
 *
 * <p>{@code equals}/{@code hashCode} reescritos porque o {@code record} compara arrays por referência.
 *
 * @param fingerprint {@link ItemFingerprint} do NBT
 * @param itemId      {@code mod:item}
 * @param displayName nome visível no servidor que guardou (só para o painel)
 * @param bytes       NBT sem compressão ({@code NbtIo.write})
 */
public record EncodedItem(@NotNull String fingerprint, @NotNull String itemId, @NotNull String displayName,
                          byte @NotNull [] bytes) {

    /** Tamanho máximo do nome guardado (o painel corta; evita textos gigantes de itens renomeados). */
    public static final int MAX_NAME_LENGTH = 128;

    public EncodedItem {
        if (displayName.length() > MAX_NAME_LENGTH) displayName = displayName.substring(0, MAX_NAME_LENGTH);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EncodedItem other && fingerprint.equals(other.fingerprint) && itemId.equals(other.itemId)
                && displayName.equals(other.displayName) && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return fingerprint.hashCode();
    }

    /** Id do mod dono do item. */
    public @NotNull String modId() {
        int colon = itemId.indexOf(':');
        return colon < 0 ? "minecraft" : itemId.substring(0, colon);
    }
}
