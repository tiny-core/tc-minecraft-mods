package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Bloco que pode ser mostrado num Monitor da Colônia (hoje: Ponte e Abastecedor). O cartão de ligação e
 * o monitor só conhecem esta interface, então um bloco novo entra nos monitores sem mexer neles.
 * ({@code interface} em Java ≈ {@code interface} em C#.)
 */
public interface MonitorSource {

    /** Dados atuais para a tela (chamado no servidor, 1×/s por tela ligada). */
    MonitorData monitorData();

    /** O jogador pode ligar um monitor a este bloco (mesma permissão de abrir a tela dele). */
    boolean canConfigure(Player player);

    BlockPos getBlockPos();
}
