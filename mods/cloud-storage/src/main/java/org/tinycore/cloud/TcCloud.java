package org.tinycore.cloud;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entrada do <b>TC Cloud Storage</b>: armazenamento de itens fora do mundo, no banco do TCMine, que a rede
 * do AE2 enxerga como um disco. Plano completo em {@code docs/planos/tc-cloud-storage.md} (raiz do workspace).
 *
 * <p>Fase 1 (atual): só o núcleo de regras puras, sem bloco nem rede:
 * <ul>
 *   <li>{@code cloud}: saldos do jogador, "débito cedo, crédito tarde" e lotes numerados;</li>
 *   <li>{@code cloud.journal}: diário em disco (lotes, confirmações, marcas de save) e checkpoint;</li>
 *   <li>{@code item}: impressão digital canônica do item e detecção de referência ao mundo;</li>
 *   <li>{@code item.policy}: regras de item do dono da nuvem.</li>
 * </ul>
 * {@code @Mod} (≈ atributo em C#) diz ao NeoForge que esta classe é a entrada do mod {@value #MOD_ID}.
 */
@Mod(TcCloud.MOD_ID)
public final class TcCloud {

    public static final String MOD_ID = "tccloud";
    public static final Logger LOG = LogUtils.getLogger();

    public TcCloud() {
        // Nada a registrar na fase 1: o núcleo é usado pelos blocos e eventos das fases seguintes.
    }
}
