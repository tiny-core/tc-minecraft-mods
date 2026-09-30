package org.tinycore.colonybridge.block;

import net.minecraft.util.StringRepresentable;
import org.tinycore.colonybridge.logic.BridgeStatus;

/**
 * Estado visual da ponte, guardado no blockstate ({@code status=...}) para o modelo/textura mudar.
 * <p>
 * Existe separado de {@link BridgeStatus} porque o visual precisa de poucos valores (cada valor
 * multiplica os modelos a manter) e porque mudar o blockstate custa um pacote para os clientes:
 * vários status diferentes que parecem iguais não devem gerar atualização.
 * <p>
 * {@code StringRepresentable} é a interface que o Minecraft usa para gravar o enum no blockstate
 * e nos arquivos JSON (o nome vira {@code offline}, {@code error}...).
 */
public enum BridgeVisualState implements StringRepresentable {
    /** Sem rede ME ativa (sem energia, sem canal, cabo inválido, iniciando). */
    OFFLINE("offline"),
    /** Rede ok, mas a colônia não está utilizável (fora de colônia, sem permissão, sem armazém). */
    ERROR("error"),
    /** Tudo ok, nenhum pedido em aberto. */
    IDLE("idle"),
    /** Tratando pedidos. */
    WORKING("working");

    private final String name;

    BridgeVisualState(String name) {
        this.name = name;
    }

    /** Converte o status detalhado da lógica no estado visual. */
    public static BridgeVisualState of(BridgeStatus status) {
        return switch (status) {
            case STARTING, OFFLINE, INVALID_CABLE, PAUSED -> OFFLINE;
            case NO_COLONY, NO_PERMISSION, NO_WAREHOUSE, DUPLICATE_BRIDGE, NO_BRIDGE, DUPLICATE_IN_COLONY -> ERROR;
            case IDLE -> IDLE;
            case WORKING -> WORKING;
        };
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
