package org.tinycore.cloud.block;

import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** O que a ligação com a rede ({@link LinkNetwork}) precisa saber do TC Cloud Link dono. */
public interface LinkHost {
    @Nullable UUID owner();

    @NotNull NetworkAccess access();

    int priority();

    /** Canal escolhido na tela do Link, ou null (usa o padrão do dono). */
    @Nullable UUID channel();

    /** O canal deste Link não pôde ser montado (já está em outro Link) ou voltou a poder. */
    void onMountConflict(@Nullable GlobalPos holder);
}
