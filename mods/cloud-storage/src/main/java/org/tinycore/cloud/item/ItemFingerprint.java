package org.tinycore.cloud.item;

import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Impressão digital de um item: SHA-256 (hex minúsculo) dos bytes canônicos ({@link CanonicalNbt}) do
 * item codificado com quantidade 1. Dois itens com a mesma impressão digital são o mesmo item para a
 * nuvem (empilham juntos); ID ou qualquer componente diferente gera outra impressão digital.
 *
 * <p>É a chave do item no TCMine ({@code cloud_item_types.Fingerprint}), do mesmo jeito que o blob store de
 * lá endereça arquivos por SHA-256.
 */
public final class ItemFingerprint {

    private ItemFingerprint() {}

    public static @NotNull String of(@NotNull Tag encodedItem) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(CanonicalNbt.toBytes(encodedItem));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM sem SHA-256", e); // toda JVM tem; nunca acontece
        }
    }
}
