package org.tinycore.cloud.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A ligação do TC Cloud Link com uma rede de armazenamento, sem tipos de mod externo: o block entity só conhece
 * esta interface. Com o AE2 instalado, a implementação é o {@code CloudLinkNode} (nó da grade que monta o canal);
 * sem ele, {@link #NONE} (o Link funciona só pela tela). Quem escolhe é o {@code Ae2Compat}.
 */
public interface LinkNetwork {

    /** Cria a ligação no mundo (primeiro tick do block entity, só no servidor). */
    void create(@NotNull Level level, @NotNull BlockPos pos);

    void destroy();

    void setOwner(@NotNull Player player);

    /** true se a rede está ligada e o Link faz parte dela. */
    boolean isActive();

    /** Algo mudou (dono, canal, modo, prioridade): a rede deve perguntar de novo o que o Link monta. */
    void refreshMounts();

    void save(@NotNull CompoundTag tag);

    void load(@NotNull CompoundTag tag);

    /** Sem mod de rede instalado: nada a ligar. */
    LinkNetwork NONE = new LinkNetwork() {
        @Override public void create(@NotNull Level level, @NotNull BlockPos pos) {}
        @Override public void destroy() {}
        @Override public void setOwner(@NotNull Player player) {}
        @Override public boolean isActive() { return false; }
        @Override public void refreshMounts() {}
        @Override public void save(@NotNull CompoundTag tag) {}
        @Override public void load(@NotNull CompoundTag tag) {}
    };
}
