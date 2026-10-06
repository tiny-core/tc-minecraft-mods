package org.tinycore.colonybridge.integration;

import com.minecolonies.api.colony.IColony;

/**
 * Referência opaca a uma colônia, achada por {@link ColonyAccess#colonyAt}. Serve para a lógica dos blocos guardar
 * "a colônia deste ciclo" e passá-la de volta ao {@link ColonyAccess} sem importar o {@code IColony} do MineColonies
 * (o objeto fica privado ao pacote {@code integration}).
 * <p>
 * Vale só durante o ciclo em que foi obtida: não guarde entre ciclos (a colônia pode ser apagada).
 */
public final class ColonyRef {

    final IColony colony;

    ColonyRef(IColony colony) {
        this.colony = colony;
    }
}
