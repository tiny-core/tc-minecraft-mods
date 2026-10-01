package org.tinycore.cloud.cloud;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifica um saldo na nuvem: qual canal do jogador e qual item. O item é a impressão digital
 * ({@code ItemFingerprint}: SHA-256 da codificação canônica), e não o {@code ItemStack}, para que estas
 * regras não dependam do Minecraft carregado e possam ser testadas em JUnit.
 *
 * <p>{@code record} em Java ≈ {@code record} em C#: classe imutável de dados com equals/hashCode prontos.
 *
 * @param channelId   id do canal (GUID gerado pelo TCMine)
 * @param fingerprint impressão digital do item (hex minúsculo)
 */
public record BalanceKey(@NotNull UUID channelId, @NotNull String fingerprint) {

    public BalanceKey {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
